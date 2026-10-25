package com.smartparking.poc.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PaymentProcessorFactoryTest {

    private final Payment payment = new Payment(null, new BigDecimal("18.00"), PaymentMethod.CARD);

    @Test
    void createsTheProcessorForEachMethod() {
        PaymentProcessorFactory factory = new PaymentProcessorFactory(0.0, new Random(1));
        assertThat(factory.create(PaymentMethod.CARD)).isInstanceOf(CardPaymentProcessor.class);
        assertThat(factory.create(PaymentMethod.WALLET)).isInstanceOf(WalletPaymentProcessor.class);
    }

    @Test
    void processorsSucceedWhenTheFailureRateIsZero() {
        PaymentProcessorFactory factory = new PaymentProcessorFactory(0.0, new Random(1));
        assertThat(factory.create(PaymentMethod.CARD).process(payment).success()).isTrue();
        assertThat(factory.create(PaymentMethod.WALLET).process(payment).success()).isTrue();
    }

    @Test
    void processorsFailWhenTheFailureRateIsOne() {
        PaymentProcessorFactory factory = new PaymentProcessorFactory(1.0, new Random(1));
        assertThat(factory.create(PaymentMethod.CARD).process(payment).success()).isFalse();
        assertThat(factory.create(PaymentMethod.WALLET).process(payment).success()).isFalse();
    }
}
