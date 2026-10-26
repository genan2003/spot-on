package com.smartparking.payment.payment.repository;

import com.smartparking.payment.payment.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findBySessionId(UUID sessionId);

	List<Payment> findByDriverIdOrderByCreatedAtDesc(String driverId);

}
