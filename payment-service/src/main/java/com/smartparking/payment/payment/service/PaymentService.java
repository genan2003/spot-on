package com.smartparking.payment.payment.service;

import com.smartparking.payment.account.service.PaymentAccountService;
import com.smartparking.payment.event.PaymentCompletedEvent;
import com.smartparking.payment.event.PaymentFailedEvent;
import com.smartparking.payment.payment.domain.Payment;
import com.smartparking.payment.payment.domain.PaymentMethod;
import com.smartparking.payment.payment.domain.PaymentStatus;
import com.smartparking.payment.payment.processor.PaymentProcessorFactory;
import com.smartparking.payment.payment.processor.PaymentResult;
import com.smartparking.payment.payment.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charges completed sessions and retries failed payments. <b>Factory pattern: Client</b>: the
 * processor comes from {@link PaymentProcessorFactory}, so this class never names a card or wallet.
 * {@link #charge} runs for every {@code session.completed} event; every attempt publishes
 * {@code payment.completed} or {@code payment.failed} (<b>Observer pattern: Publisher</b>).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final PaymentProcessorFactory processorFactory;
	private final PaymentAccountService accounts;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/** Charges a session with the method the driver chose (card unless they picked the wallet). */
	@Transactional
	public Payment charge(UUID sessionId, String driverId, BigDecimal amount) {
		return charge(sessionId, driverId, amount, accounts.get(driverId).getPreferredMethod());
	}

	/**
	 * Charges a session once. Charging the same session again returns the existing payment, so a
	 * redelivered event can never charge the driver twice (the unique index on session_id backs this up).
	 */
	@Transactional
	public Payment charge(UUID sessionId, String driverId, BigDecimal amount, PaymentMethod method) {
		return paymentRepository.findBySessionId(sessionId).orElseGet(() -> {
			// Saved first so the payment has its id before the outcome event is published.
			Payment payment = paymentRepository.save(new Payment(sessionId, driverId, amount, method, clock.instant()));
			attempt(payment);
			return payment;
		});
	}

	@Transactional
	public Payment retry(UUID paymentId) {
		return retry(paymentId, null);
	}

	/** Tries a failed payment again, with another method if {@code method} is given ("Pay by card instead"). */
	@Transactional
	public Payment retry(UUID paymentId, PaymentMethod method) {
		Payment payment = find(paymentId);
		if (payment.getStatus() != PaymentStatus.FAILED) {
			throw new IllegalStateException("Only a failed payment can be retried; this one is " + payment.getStatus().name().toLowerCase() + ".");
		}
		if (method != null) {
			payment.switchTo(method);
		}
		attempt(payment);
		return payment;
	}

	@Transactional(readOnly = true)
	public List<Payment> history(String driverId) {
		return paymentRepository.findByDriverIdOrderByCreatedAtDesc(driverId);
	}

	@Transactional(readOnly = true)
	public Payment findBySession(UUID sessionId) {
		return paymentRepository.findBySessionId(sessionId)
			.orElseThrow(() -> new EntityNotFoundException("No payment for session " + sessionId + " yet."));
	}

	@Transactional(readOnly = true)
	public Payment find(UUID paymentId) {
		return paymentRepository.findById(paymentId)
			.orElseThrow(() -> new EntityNotFoundException("Payment " + paymentId + " not found."));
	}

	private void attempt(Payment payment) {
		// A free stay (e.g. within the grace period) has nothing to charge, so it can't fail.
		PaymentResult result = payment.getAmount().signum() == 0
				? new PaymentResult(true, "Nothing to pay")
				: processorFactory.create(payment.getMethod()).process(payment);
		Instant now = clock.instant();
		payment.recordAttempt(result.success(), now);
		// Sent to RabbitMQ after the commit.
		eventPublisher.publishEvent(result.success()
				? PaymentCompletedEvent.of(payment, result.message(), now)
				: PaymentFailedEvent.of(payment, result.message(), now));
	}

}
