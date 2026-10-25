package com.smartparking.poc.notification;

import com.smartparking.poc.event.PaymentCompletedEvent;
import com.smartparking.poc.event.PaymentFailedEvent;
import com.smartparking.poc.event.ReservationCancelledEvent;
import com.smartparking.poc.event.ReservationCreatedEvent;
import com.smartparking.poc.event.ReservationExpiredEvent;
import com.smartparking.poc.event.SessionCompletedEvent;
import com.smartparking.poc.user.Driver;
import com.smartparking.poc.user.DriverRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * <b>Observer pattern: Observer.</b> Reacts to domain events by notifying the driver. The
 * publishers (ReservationService, ParkingSessionFacade) don't know this class exists.
 *
 * <p>{@code @TransactionalEventListener} runs each method only after the publishing transaction
 * commits, so a driver is never told about a reservation that was rolled back. The listener then
 * needs its own transaction to save the notification, hence {@code REQUIRES_NEW}.
 *
 * <p>It is also a <b>Factory pattern: Client</b>: the channel comes from {@link NotificationChannelFactory}.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final DriverRepository driverRepository;
    private final NotificationChannelFactory channelFactory;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationCreated(ReservationCreatedEvent event) {
        notify(event.getDriverId(), "Spot %s is reserved for you. Start your session within the hold time.".formatted(event.getSpotCode()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationCancelled(ReservationCancelledEvent event) {
        notify(event.getDriverId(), "You cancelled your reservation for spot %s.".formatted(event.getSpotCode()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationExpired(ReservationExpiredEvent event) {
        notify(event.getDriverId(), "Your reservation for spot %s expired, so the spot is free again.".formatted(event.getSpotCode()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionCompleted(SessionCompletedEvent event) {
        notify(event.getDriverId(), "Your session at spot %s ended. Cost: %s lei.".formatted(event.getSpotCode(), event.getCost()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        notify(event.getDriverId(), "Paid %s lei. Thank you.".formatted(event.getAmount()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentFailed(PaymentFailedEvent event) {
        notify(event.getDriverId(), "Payment of %s lei failed. Pay again from your receipt.".formatted(event.getAmount()));
    }

    private void notify(UUID driverId, String message) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver " + driverId + " not found."));
        Notification notification = new Notification(driver, driver.getPreferredChannel(), message, clock.instant());
        channelFactory.create(driver.getPreferredChannel()).send(notification);
        notificationRepository.save(notification);
    }
}
