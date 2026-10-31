package com.smartparking.payment.payment.controller;

import com.smartparking.payment.payment.dto.PaymentResponse;
import com.smartparking.payment.payment.dto.RetryPaymentRequest;
import com.smartparking.payment.payment.mapper.PaymentMapper;
import com.smartparking.payment.payment.service.PaymentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;
	private final PaymentMapper mapper;

	/** A driver's payment history, newest first. */
	@GetMapping(params = "driverId")
	public List<PaymentResponse> history(@RequestParam String driverId) {
		return paymentService.history(driverId).stream().map(mapper::toResponse).toList();
	}

	/** The payment for one parking session. */
	@GetMapping(params = "sessionId")
	public PaymentResponse forSession(@RequestParam UUID sessionId) {
		return mapper.toResponse(paymentService.findBySession(sessionId));
	}

	@GetMapping("/{id}")
	public PaymentResponse get(@PathVariable UUID id) {
		return mapper.toResponse(paymentService.find(id));
	}

	/** Tries a failed payment again; a body {@code {"method": "CARD"}} pays it another way ("Pay by card instead"). */
	@PostMapping("/{id}/retry")
	public PaymentResponse retry(@PathVariable UUID id, @RequestBody(required = false) RetryPaymentRequest request) {
		return mapper.toResponse(paymentService.retry(id, request == null ? null : request.method()));
	}

}
