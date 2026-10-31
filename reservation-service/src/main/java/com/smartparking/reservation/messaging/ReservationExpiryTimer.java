package com.smartparking.reservation.messaging;

import com.smartparking.reservation.event.ReservationCreatedEvent;
import java.time.Instant;
import java.util.UUID;
import com.smartparking.reservation.reservation.service.ReservationService;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Expires reservations with a TTL queue and a dead-letter exchange (see {@link MessagingConfig}).
 * The {@code @Scheduled} sweep in {@code ReservationExpirationScheduler} stays as a fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryTimer {

	private final RabbitTemplate rabbitTemplate;
	private final ReservationService reservationService;
	private final ReservationReminderTimer reminderTimer;
	private final Clock clock;

	/** Starts the timer: the message becomes visible on the expiry queue when the hold time is over. */
	@TransactionalEventListener(fallbackExecution = true)
	public void start(ReservationCreatedEvent event) {
		schedule(event.reservationId(), event.expiresAt());
	}

	private void schedule(UUID reservationId, Instant expiresAt) {
		long ttl = Math.max(0, Duration.between(clock.instant(), expiresAt).toMillis());
		rabbitTemplate.convertAndSend("", MessagingConfig.EXPIRY_DELAY_QUEUE, new ExpiryCheck(reservationId), message -> {
			message.getMessageProperties().setExpiration(String.valueOf(ttl));
			return message;
		});
	}

	/**
	 * Safe to receive twice or late: only a still-active, overdue reservation is expired.
	 * An extended reservation isn't due yet when its first timer fires, so the timer starts again for the
	 * remaining time. That is exactly one hold time, the same TTL as every other message on the delay
	 * queue: RabbitMQ only dead-letters from the head of the queue, so a longer TTL would hold back
	 * newer reservations' timers behind it.
	 */
	@RabbitListener(queues = MessagingConfig.EXPIRY_QUEUE)
	public void onTimerElapsed(ExpiryCheck check) {
		Instant now = clock.instant();
		if (reservationService.expireIfDue(check.reservationId(), now)) {
			log.info("Reservation {} expired (TTL queue)", check.reservationId());
			return;
		}
		reservationService.activeUntil(check.reservationId())
			.filter(expiresAt -> expiresAt.isAfter(now))
			.ifPresent(expiresAt -> {
				log.info("Reservation {} was extended; checking again at {}", check.reservationId(), expiresAt);
				schedule(check.reservationId(), expiresAt);
				reminderTimer.schedule(check.reservationId(), expiresAt);
			});
	}

}
