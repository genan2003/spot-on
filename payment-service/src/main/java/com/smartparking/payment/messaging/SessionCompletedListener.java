package com.smartparking.payment.messaging;

import com.smartparking.payment.payment.domain.PaymentMethod;
import com.smartparking.payment.payment.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Observer pattern: Observer</b> (over RabbitMQ). Charges every completed session. The facade in
 * reservation-service doesn't wait for this, so ending a session never blocks on payment.
 */
@Slf4j
@Component
public class SessionCompletedListener {

	private final PaymentService paymentService;
	private final ProcessedEvents processedEvents;
	private final PaymentMethod defaultMethod;

	public SessionCompletedListener(PaymentService paymentService, ProcessedEvents processedEvents,
			@Value("${smartparking.payment.default-method}") PaymentMethod defaultMethod) {
		this.paymentService = paymentService;
		this.processedEvents = processedEvents;
		this.defaultMethod = defaultMethod;
	}

	@Transactional
	@RabbitListener(queues = MessagingConfig.SESSIONS_QUEUE)
	public void onSessionCompleted(SessionCompletedEvent event) {
		if (!processedEvents.firstTime(event.eventId())) {
			log.debug("Skipping duplicate session.completed {}", event.eventId());
			return;
		}
		paymentService.charge(event.sessionId(), event.driverId(), event.cost(), defaultMethod);
	}

}
