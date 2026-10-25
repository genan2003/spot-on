package com.smartparking.poc.location;

import com.smartparking.poc.pricing.PricingRule;
import com.smartparking.poc.pricing.PricingStrategy;
import com.smartparking.poc.spot.ParkingSpot;
import com.smartparking.poc.spot.SpotStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A car park. <b>Strategy pattern: Context.</b> It holds the {@link PricingRule} the admin chose
 * and hands out the matching {@link PricingStrategy}; changing the rule changes how every later
 * session at this location is charged, without touching the code that ends sessions.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private String address;

    private double latitude;

    private double longitude;

    @Embedded
    private PricingRule pricingRule;

    @OneToMany(mappedBy = "location", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParkingSpot> spots = new ArrayList<>();

    public ParkingLocation(String name, String address, double latitude, double longitude, PricingRule pricingRule) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pricingRule = pricingRule;
    }

    public PricingStrategy getPricingStrategy() {
        return pricingRule.toStrategy();
    }

    public void changePricingRule(PricingRule pricingRule) {
        this.pricingRule = pricingRule;
    }

    public void addSpot(ParkingSpot spot) {
        spot.assignTo(this);
        spots.add(spot);
    }

    public int countAvailable() {
        return (int) spots.stream().filter(s -> s.getStatus() == SpotStatus.AVAILABLE).count();
    }
}
