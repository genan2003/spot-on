package com.smartparking.reservation.messaging;

import com.smartparking.reservation.event.ReservationCreatedEvent;
import com.smartparking.reservation.reservation.service.ReservationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reminds the driver shortly before a reservation expires, with a TTL queue and the dead-letter exchange,
 * like {@link ReservationExpiryTimer}. The message waits on {@code reservation.reminder-delay} for
 * (hold time − lead), then reaches {@code reservation.reminders}; if the reservation is still waiting for the
 * driver, {@code reservation.expiring} is published and notification-service sends the reminder.
 * <p>
 * The lead is {@code smartparking.reservation.reminder-before} (5 minutes), but at most half the hold time,
 * so a short demo hold still gets a reminder. Every message on the delay queue has the same TTL, so they
 * leave in order (RabbitMQ only dead-letters from the head of the queue). The {@code @Scheduled} sweep in
 * {@code ReservationExpirationScheduler} is the fallback, as for expiry.
 */
@Slf4j
@Component
public class ReservationReminderTimer {

	private final RabbitTemplate rabbitTemplate;
	private final ReservationService reservationService;
	private final Clock clock;
	public ReservationReminderTimer(RabbitTemplate rabbitTemplate, ReservationService reservationService, Clock clock) {
		this.rabbitTemplate = rabbitTemplate;
		this.reservationService = reservationService;
		this.clock = clock;
	}

	@TransactionalEventListener(fallbackExecution = true)
	public void start(ReservationCreatedEvent event) {
		schedule(event.reservationId(), event.expiresAt());
	}

	/**
	 * Starts the reminder for the hold ending at {@code expiresAt}. Also called by the expiry timer when an
	 * extended reservation's first check finds it not yet due: the remaining time is then one hold time, so
	 * this message has the same TTL as every other one.
	 */
	public void schedule(UUID reservationId, Instant expiresAt) {
		long ttl = Math.max(0, Duration.between(clock.instant(), expiresAt.minus(reservationService.reminderLead())).toMillis());
		try {
			rabbitTemplate.convertAndSend("", MessagingConfig.REMINDER_DELAY_QUEUE, new ExpiryCheck(reservationId), message -> {
				message.getMessageProperties().setExpiration(String.valueOf(ttl));
				return message;
			});
		}
		catch (AmqpException ex) {
			// The minute sweep sends the reminder instead.
			log.warn("Couldn't start the reminder timer for reservation {}: {}", reservationId, ex.getMessage());
		}
	}

	/**
	 * Safe to receive twice, late, or for a reservation that was used, cancelled or extended meanwhile:
	 * only an active reservation whose hold ends within the lead, and that wasn't reminded about yet, is reminded.
	 */
	@RabbitListener(queues = MessagingConfig.REMINDER_QUEUE)
	public void onReminderDue(ExpiryCheck check) {
		if (reservationService.remindIfDue(check.reservationId(), clock.instant())) {
			log.info("Reminded the driver of reservation {} that it expires soon", check.reservationId());
		}
	}

}
