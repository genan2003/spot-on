package com.smartparking.poc.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.poc.event.DomainEvent;
import com.smartparking.poc.event.PaymentCompletedEvent;
import com.smartparking.poc.event.PaymentFailedEvent;
import com.smartparking.poc.event.SessionCompletedEvent;
import com.smartparking.poc.location.ParkingLocation;
import com.smartparking.poc.notification.ChannelType;
import com.smartparking.poc.payment.Payment;
import com.smartparking.poc.payment.PaymentMethod;
import com.smartparking.poc.payment.PaymentProcessor;
import com.smartparking.poc.payment.PaymentProcessorFactory;
import com.smartparking.poc.payment.PaymentRepository;
import com.smartparking.poc.payment.PaymentResult;
import com.smartparking.poc.payment.PaymentStatus;
import com.smartparking.poc.pricing.BaySurcharges;
import com.smartparking.poc.pricing.PricingRule;
import com.smartparking.poc.reservation.ReservationRepository;
import com.smartparking.poc.spot.IllegalSpotStateTransitionException;
import com.smartparking.poc.spot.ParkingSpot;
import com.smartparking.poc.spot.ParkingSpotRepository;
import com.smartparking.poc.spot.SpotStatus;
import com.smartparking.poc.spot.SpotType;
import com.smartparking.poc.user.Driver;
import com.smartparking.poc.user.DriverRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** The facade's orchestration, with every subsystem mocked. */
@ExtendWith(MockitoExtension.class)
class ParkingSessionFacadeTest {

    private static final Instant ENTRY = Instant.parse("2026-03-04T08:00:00Z");
    private static final Instant EXIT = ENTRY.plus(Duration.ofMinutes(130));

    @Mock ParkingSessionRepository sessionRepository;
    @Mock ParkingSpotRepository spotRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock DriverRepository driverRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentProcessorFactory paymentProcessorFactory;
    @Mock PaymentProcessor paymentProcessor;
    @Mock ApplicationEventPublisher eventPublisher;

    private ParkingSessionFacade facade;
    private ParkingSpot spot;
    private Driver driver;

    @BeforeEach
    void setUp() {
        facade = new ParkingSessionFacade(sessionRepository, spotRepository, reservationRepository, driverRepository,
                paymentRepository, paymentProcessorFactory, eventPublisher, Clock.fixed(EXIT, ZoneOffset.UTC));
        ParkingLocation location = new ParkingLocation("Central Garage", "Strada Memorandumului 21", 46.77, 23.59,
                PricingRule.tiered(new BigDecimal("8.00"), new BigDecimal("4.00")));
        spot = new ParkingSpot("A1", 0, "A", SpotType.STANDARD, false, false);
        location.addSpot(spot);
        driver = new Driver("Ana", "ana@example.com", "+40 700 000 011", ChannelType.EMAIL, PaymentMethod.CARD);
    }

    private ParkingSession occupiedSession() {
        spot.occupy();
        ParkingSession session = new ParkingSession(driver, spot, null, ENTRY);
        when(sessionRepository.findById(any())).thenReturn(Optional.of(session));
        when(paymentProcessorFactory.create(PaymentMethod.CARD)).thenReturn(paymentProcessor);
        return session;
    }

    @Test
    void theRulesDecoratorsAddTheSpotsSurcharge() {
        ParkingSpot large = new ParkingSpot("A9", 0, "A", SpotType.LARGE, true, false);
        spot.getLocation().addSpot(large);
        spot.getLocation().changePricingRule(PricingRule.hourly(new BigDecimal("6.00"))
                .withSurcharges(new BaySurcharges(0, 20, 0, 10)));
        spot = large;
        ParkingSession session = occupiedSession();
        when(paymentProcessor.process(any())).thenReturn(new PaymentResult(true, "ok"));

        facade.endSession(UUID.randomUUID());

        // Decorator: 3 started hours × 6 lei = 18, +20% large +10% EV = 23.40
        assertThat(session.getCost()).isEqualByComparingTo("23.40");
    }

    @Test
    void endSessionChargesWithTheStrategyPaysAndFreesTheSpot() {
        ParkingSession session = occupiedSession();
        when(paymentProcessor.process(any())).thenReturn(new PaymentResult(true, "ok"));

        facade.endSession(UUID.randomUUID());

        // Strategy: tiered pricing, 3 started hours → 8 + 2 × 4
        assertThat(session.getCost()).isEqualByComparingTo("16.00");
        assertThat(session.getExitTime()).isEqualTo(EXIT);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        // Factory: the driver's preferred method picked the processor
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("16.00");
        // State: Occupied → Available
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.AVAILABLE);
        // Observer: session and payment events, in that order
        assertThat(publishedEvents()).hasExactlyElementsOfTypes(SessionCompletedEvent.class, PaymentCompletedEvent.class);
    }

    @Test
    void endSessionStillFreesTheSpotWhenPaymentFails() {
        occupiedSession();
        when(paymentProcessor.process(any())).thenReturn(new PaymentResult(false, "Card declined by the bank."));

        facade.endSession(UUID.randomUUID());

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.AVAILABLE);
        assertThat(publishedEvents()).hasExactlyElementsOfTypes(SessionCompletedEvent.class, PaymentFailedEvent.class);
    }

    @Test
    void startSessionRefusesASpotReservedByAnotherDriver() {
        spot.reserve();
        when(spotRepository.findById(any())).thenReturn(Optional.of(spot));
        when(driverRepository.findById(any())).thenReturn(Optional.of(driver));
        when(reservationRepository.findByDriverIdAndSpotIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.startSession(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalSpotStateTransitionException.class)
                .hasMessageContaining("reserved by another driver");
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.RESERVED);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void startSessionOccupiesAFreeSpot() {
        when(spotRepository.findById(any())).thenReturn(Optional.of(spot));
        when(driverRepository.findById(any())).thenReturn(Optional.of(driver));
        when(reservationRepository.findByDriverIdAndSpotIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ParkingSession session = facade.startSession(UUID.randomUUID(), UUID.randomUUID());

        assertThat(spot.getStatus()).isEqualTo(SpotStatus.OCCUPIED);
        assertThat(session.getEntryTime()).isEqualTo(EXIT);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    private List<DomainEvent> publishedEvents() {
        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        return events.getAllValues();
    }
}
