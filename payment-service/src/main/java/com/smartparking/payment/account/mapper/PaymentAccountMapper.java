package com.smartparking.payment.account.mapper;

import com.smartparking.payment.account.domain.PaymentAccount;
import com.smartparking.payment.account.dto.PaymentAccountResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentAccountMapper {

	PaymentAccountResponse toResponse(PaymentAccount account);

}
