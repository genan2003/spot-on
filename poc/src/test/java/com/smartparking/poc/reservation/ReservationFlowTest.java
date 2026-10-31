package com.smartparking.poc.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartparking.poc.TestData;
import com.smartparking.poc.location.ParkingLocationService;
import com.smartparking.poc.notification.Notification;
import com.smartparking.poc.notification.NotificationRepository;
import com.smartparking.poc.spot.ParkingSpotRepository;
import com.smartparking.poc.spot.SpotStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Observer end to end: events published by the service reach the listener after the commit. */
@SpringBootTest
@Import(TestData.class)
class ReservationFlowTest {

    @Autowired ReservationService reservationService;
    @Autowired ParkingSpotRepository spotRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TestData testData;
    @Autowired ParkingLocationService locationService;

    @Test
    void reservingNotifiesTheDriverThroughTheirChannel() {
        UUID driver = testData.driver();
        UUID spot = testData.spot();

        reservationService.reserve(driver, spot);

        assertThat(spotRepository.findById(spot).orElseThrow().getStatus()).isEqualTo(SpotStatus.RESERVED);
        assertThat(messagesFor(driver)).singleElement().asString().contains("is reserved for you");
    }

    @Test
    void anOverdueReservationExpiresFreesTheSpotAndNotifies() {
        UUID driver = testData.driver();
        UUID spot = testData.spot();
        Reservation reservation = reservationService.reserve(driver, spot);

        int expired = reservationService.expireOverdue(reservation.getExpiresAt().plus(Duration.ofSeconds(1)));

        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(spotRepository.findById(spot).orElseThrow().getStatus()).isEqualTo(SpotStatus.AVAILABLE);
        assertThat(messagesFor(driver)).anyMatch(m -> m.contains("expired"));
    }

    @Test
    void cancellingFreesTheSpot() {
        UUID driver = testData.driver();
        UUID spot = testData.spot();
        Reservation reservation = reservationService.reserve(driver, spot);

        reservationService.cancel(reservation.getId());

        assertThat(spotRepository.findById(spot).orElseThrow().getStatus()).isEqualTo(SpotStatus.AVAILABLE);
        assertThat(messagesFor(driver)).anyMatch(m -> m.contains("cancelled"));
    }

    @Test
    void closingAReservedSpotCancelsTheReservationAndTellsTheDriver() {
        UUID driver = testData.driver();
        UUID spot = testData.spot();
        Reservation reservation = reservationService.reserve(driver, spot);

        locationService.setOutOfService(spot, true);

        assertThat(spotRepository.findById(spot).orElseThrow().getStatus()).isEqualTo(SpotStatus.OUT_OF_SERVICE);
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(messagesFor(driver)).anyMatch(m -> m.contains("was closed by the car park"));
    }

    private List<String> messagesFor(UUID driverId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(driverId))
                .map(Notification::getMessage)
                .toList();
    }
}
