package com.smartparking.reservation.messaging;

import com.smartparking.reservation.event.ReservationCreatedEvent;
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
	private final Clock clock;

	/** Starts the timer: the message becomes visible on the expiry queue when the hold time is over. */
	@TransactionalEventListener(fallbackExecution = true)
	public void start(ReservationCreatedEvent event) {
		long ttl = Math.max(0, Duration.between(clock.instant(), event.expiresAt()).toMillis());
		rabbitTemplate.convertAndSend("", MessagingConfig.EXPIRY_DELAY_QUEUE, new ExpiryCheck(event.reservationId()), message -> {
			message.getMessageProperties().setExpiration(String.valueOf(ttl));
			return message;
		});
	}

	/** Safe to receive twice or late: only a still-active, overdue reservation is expired. */
	@RabbitListener(queues = MessagingConfig.EXPIRY_QUEUE)
	public void onTimerElapsed(ExpiryCheck check) {
		if (reservationService.expireIfDue(check.reservationId(), clock.instant())) {
			log.info("Reservation {} expired (TTL queue)", check.reservationId());
		}
	}

}
