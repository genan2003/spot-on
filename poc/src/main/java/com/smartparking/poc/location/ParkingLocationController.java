package com.smartparking.poc.location;

import com.smartparking.poc.spot.SpotStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parking-locations")
@RequiredArgsConstructor
public class ParkingLocationController {

    private final ParkingLocationService locationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse create(@Valid @RequestBody CreateLocationRequest request) {
        return LocationResponse.from(locationService.createLocation(request.name(), request.address(),
                request.latitude(), request.longitude(), request.pricingRule().toPricingRule()));
    }

    @PostMapping("/{id}/spots")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SpotResponse> addSpots(@PathVariable UUID id, @Valid @RequestBody AddSpotsRequest request) {
        return locationService.addSpots(id, request.level(), request.zone(), request.count(), request.type(),
                request.evCharging(), request.accessible()).stream().map(SpotResponse::from).toList();
    }

    @PutMapping("/{id}/pricing-rule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePricingRule(@PathVariable UUID id, @Valid @RequestBody PricingRuleDto request) {
        locationService.updatePricingRule(id, request.toPricingRule());
    }

    @PatchMapping("/{id}/spots/{spotId}")
    public SpotResponse setOutOfService(@PathVariable UUID id, @PathVariable UUID spotId, @RequestBody SpotServiceRequest request) {
        return SpotResponse.from(locationService.setOutOfService(spotId, request.outOfService()));
    }

    @GetMapping("/{id}/availability")
    public Map<SpotStatus, Long> getAvailability(@PathVariable UUID id) {
        return locationService.getAvailability(id);
    }
}
