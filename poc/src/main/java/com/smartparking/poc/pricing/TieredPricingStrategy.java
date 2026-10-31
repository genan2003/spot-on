package com.smartparking.poc.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * <b>Strategy pattern: Concrete strategy.</b> The hourly price changes as the stay gets longer, e.g.
 * 8 lei for the first hour, 4 lei for hours 1 to 4, then 2 lei. Each band runs until the next one starts.
 */
public record TieredPricingStrategy(List<PriceBand> bands) implements PricingStrategy {

    public TieredPricingStrategy {
        if (bands.isEmpty() || bands.stream().mapToInt(PriceBand::fromHour).min().getAsInt() != 0) {
            throw new IllegalArgumentException("Tiered pricing needs a band that starts at hour 0.");
        }
        bands = bands.stream().sorted(Comparator.comparingInt(PriceBand::fromHour)).toList();
    }

    @Override
    public BigDecimal calculateCost(Instant entryTime, Instant exitTime) {
        Duration stay = Duration.between(entryTime, exitTime);
        BigDecimal cost = BigDecimal.ZERO;
        for (int i = 0; i < bands.size(); i++) {
            Duration from = Duration.ofHours(bands.get(i).fromHour());
            Duration to = i + 1 < bands.size() ? Duration.ofHours(bands.get(i + 1).fromHour()) : stay;
            Duration inBand = min(stay, to).minus(from);
            if (inBand.isPositive()) {
                cost = cost.add(PricingStrategy.forTime(bands.get(i).hourlyRate(), inBand));
            }
        }
        return PricingStrategy.toLei(cost);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
