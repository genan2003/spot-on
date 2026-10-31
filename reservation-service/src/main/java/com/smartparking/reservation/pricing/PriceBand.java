package com.smartparking.reservation.pricing;

import java.math.BigDecimal;

/** One band of a tiered rule: from hour {@code fromHour} of the stay on, each hour costs {@code hourlyRate}. */
public record PriceBand(int fromHour, BigDecimal hourlyRate) {
}
