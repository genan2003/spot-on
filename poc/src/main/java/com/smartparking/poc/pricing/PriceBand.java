package com.smartparking.poc.pricing;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One band of a tiered rule: from hour {@code fromHour} of the stay on, each hour costs {@code hourlyRate}. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PriceBand {

    private int fromHour;
    private BigDecimal hourlyRate;

    public int fromHour() {
        return fromHour;
    }

    public BigDecimal hourlyRate() {
        return hourlyRate;
    }
}
