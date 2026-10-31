package com.smartparking.payment.account.controller;

import com.smartparking.payment.account.dto.PaymentAccountResponse;
import com.smartparking.payment.account.dto.TopUpRequest;
import com.smartparking.payment.account.dto.UpdatePaymentAccountRequest;
import com.smartparking.payment.account.mapper.PaymentAccountMapper;
import com.smartparking.payment.account.service.PaymentAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A driver's payment method and wallet. Reading an unknown driver creates their account. */
@RestController
@RequestMapping("/api/v1/payment-accounts/{driverId}")
@RequiredArgsConstructor
public class PaymentAccountController {

	private final PaymentAccountService accountService;
	private final PaymentAccountMapper mapper;

	@GetMapping
	public PaymentAccountResponse get(@PathVariable String driverId) {
		return mapper.toResponse(accountService.get(driverId));
	}

	/** Chooses card or wallet for the driver's next payments. */
	@PatchMapping
	public PaymentAccountResponse choose(@PathVariable String driverId, @Valid @RequestBody UpdatePaymentAccountRequest request) {
		return mapper.toResponse(accountService.choose(driverId, request.preferredMethod()));
	}

	@PostMapping("/top-ups")
	public PaymentAccountResponse topUp(@PathVariable String driverId, @Valid @RequestBody TopUpRequest request) {
		return mapper.toResponse(accountService.topUp(driverId, request.amount()));
	}

}
