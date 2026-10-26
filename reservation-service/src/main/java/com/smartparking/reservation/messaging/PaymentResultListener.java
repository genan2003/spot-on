package com.smartparking.reservation.messaging;

import com.smartparking.reservation.session.domain.PaymentStatus;
import com.smartparking.reservation.session.service.ParkingSessionFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Observer pattern: Observer</b> (over RabbitMQ). Copies the payment outcome onto the session,
 * so the driver's receipt shows "paid" or offers a retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultListener {

	private final ParkingSessionFacade sessionFacade;
	private final ProcessedEvents processedEvents;

	@Transactional
	@RabbitListener(queues = MessagingConfig.PAYMENT_RESULTS_QUEUE)
	public void onPaymentResult(PaymentResultEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
		if (!processedEvents.firstTime(event.eventId())) {
			log.debug("Skipping duplicate {} {}", routingKey, event.eventId());
			return;
		}
		PaymentStatus status = "payment.completed".equals(routingKey) ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;
		sessionFacade.recordPayment(event.sessionId(), event.paymentId(), status);
	}

}
