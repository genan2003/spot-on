package com.smartparking.poc;

import com.smartparking.poc.location.ParkingLocation;
import com.smartparking.poc.location.ParkingLocationService;
import com.smartparking.poc.notification.ChannelType;
import com.smartparking.poc.payment.PaymentMethod;
import com.smartparking.poc.pricing.PricingRule;
import com.smartparking.poc.spot.ParkingSpot;
import com.smartparking.poc.spot.SpotType;
import com.smartparking.poc.user.Driver;
import com.smartparking.poc.user.DriverRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

/**
 * Creates fresh locations, spots and drivers for an integration test, so tests sharing a Spring
 * context (and its in-memory database) never depend on each other's data.
 */
@TestComponent
@RequiredArgsConstructor
public class TestData {

    private final ParkingLocationService locationService;
    private final DriverRepository driverRepository;

    public List<UUID> spots(int count) {
        ParkingLocation location = locationService.createLocation("Test Garage " + UUID.randomUUID(), "Test street 1",
                46.77, 23.59, PricingRule.hourly(new BigDecimal("6.00")));
        return locationService.addSpots(location.getId(), 0, "A", count, SpotType.STANDARD, false, false).stream()
                .map(ParkingSpot::getId)
                .toList();
    }

    public UUID spot() {
        return spots(1).getFirst();
    }

    public List<UUID> drivers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> driverRepository.save(new Driver("Driver " + i, "driver" + i + "@example.com",
                        "+40 700 000 " + i, ChannelType.PUSH, PaymentMethod.CARD)).getId())
                .toList();
    }

    public UUID driver() {
        return drivers(1).getFirst();
    }
}
