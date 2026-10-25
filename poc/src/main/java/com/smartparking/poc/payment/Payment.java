package com.smartparking.poc.payment;

import com.smartparking.poc.session.ParkingSession;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Payment for one completed parking session. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private ParkingSession session;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    private Instant processedAt;

    public Payment(ParkingSession session, BigDecimal amount, PaymentMethod method) {
        this.session = session;
        this.amount = amount;
        this.method = method;
    }

    public void markCompleted(Instant at) {
        this.status = PaymentStatus.COMPLETED;
        this.processedAt = at;
    }

    public void markFailed(Instant at) {
        this.status = PaymentStatus.FAILED;
        this.processedAt = at;
    }
}
