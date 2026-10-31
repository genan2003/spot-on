package com.smartparking.poc.pricing;

import com.smartparking.poc.spot.SpotType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Percentages added per spot type (negative for a discount), plus one for EV charging. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BaySurcharges {

    @Column(name = "compact_surcharge")
    private int compact;

    @Column(name = "large_surcharge")
    private int large;

    @Column(name = "motorcycle_surcharge")
    private int motorcycle;

    @Column(name = "ev_charging_surcharge")
    private int evCharging;

    public static BaySurcharges none() {
        return new BaySurcharges(0, 0, 0, 0);
    }

    /** The total percentage for one spot: its type's surcharge plus the EV one when it has a charger. */
    public int percentFor(SpotType type, boolean hasCharger) {
        int byType = switch (type) {
            case STANDARD -> 0;
            case COMPACT -> compact;
            case LARGE -> large;
            case MOTORCYCLE -> motorcycle;
        };
        return byType + (hasCharger ? evCharging : 0);
    }
}
