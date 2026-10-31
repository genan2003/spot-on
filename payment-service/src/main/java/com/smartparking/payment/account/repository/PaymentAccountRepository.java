package com.smartparking.payment.account.repository;

import com.smartparking.payment.account.domain.PaymentAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, String> {

}
