package com.smartparking.poc.user;

import com.smartparking.poc.notification.ChannelType;
import com.smartparking.poc.payment.PaymentMethod;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Finds, reserves and pays for parking spots. */
@Entity
@DiscriminatorValue("DRIVER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Driver extends User {

    private String phone;

    @Enumerated(EnumType.STRING)
    private ChannelType preferredChannel;

    @Enumerated(EnumType.STRING)
    private PaymentMethod preferredPaymentMethod;

    public Driver(String name, String email, String phone, ChannelType preferredChannel, PaymentMethod preferredPaymentMethod) {
        super(name, email);
        this.phone = phone;
        this.preferredChannel = preferredChannel;
        this.preferredPaymentMethod = preferredPaymentMethod;
    }
}
