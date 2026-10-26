package com.smartparking.payment.payment.mapper;

import com.smartparking.payment.payment.domain.Payment;
import com.smartparking.payment.payment.dto.PaymentResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

	PaymentResponse toResponse(Payment payment);

}
