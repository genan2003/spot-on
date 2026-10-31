package com.smartparking.parking.location.mapper;

import com.smartparking.parking.location.domain.BaySurcharges;
import com.smartparking.parking.location.domain.LocationSummary;
import com.smartparking.parking.location.domain.PriceBand;
import com.smartparking.parking.location.domain.PricingRule;
import com.smartparking.parking.location.dto.LocationResponse;
import com.smartparking.parking.location.dto.PricingRuleDto;
import com.smartparking.parking.location.dto.PricingRuleDto.BaySurchargesDto;
import com.smartparking.parking.location.dto.PricingRuleDto.PriceBandDto;
import java.util.List;
import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LocationMapper {

	@Mapping(target = "id", source = "location.id")
	@Mapping(target = "name", source = "location.name")
	@Mapping(target = "address", source = "location.address")
	@Mapping(target = "latitude", source = "location.latitude")
	@Mapping(target = "longitude", source = "location.longitude")
	@Mapping(target = "pricing", source = "location.pricingRule")
	@Mapping(target = "opensAt", source = "location.opensAt")
	@Mapping(target = "closesAt", source = "location.closesAt")
	LocationResponse toResponse(LocationSummary summary);

	/** Written by hand: the band list is left out (null) unless the rule is tiered, like the unused rates. */
	default PricingRuleDto toDto(PricingRule rule) {
		List<PriceBandDto> bands = rule.getBands().isEmpty() ? null
				: rule.getBands().stream().map(b -> new PriceBandDto(b.getFromHour(), b.getHourlyRate())).toList();
		BaySurcharges s = Objects.requireNonNullElse(rule.getSurcharges(), BaySurcharges.none());
		return new PricingRuleDto(rule.getStrategyType(), rule.getHourlyRate(), rule.getFlatRate(), bands, rule.getDayRate(),
				rule.getNightWeekendRate(), rule.getNightStart(), rule.getNightEnd(), rule.getBillingIncrementMinutes(),
				rule.getGracePeriodMinutes(), rule.getDailyCap(),
				new BaySurchargesDto(s.getCompact(), s.getLarge(), s.getMotorcycle(), s.getEvCharging()));
	}

	default PricingRule toPricingRule(PricingRuleDto dto) {
		List<PriceBand> bands = dto.bands() == null ? List.of()
				: dto.bands().stream().map(b -> new PriceBand(b.fromHour(), b.hourlyRate())).toList();
		BaySurchargesDto s = dto.surcharges();
		BaySurcharges surcharges = s == null ? BaySurcharges.none()
				: new BaySurcharges(s.compact(), s.large(), s.motorcycle(), s.evCharging());
		return PricingRule.of(dto.strategy(), dto.hourlyRate(), dto.flatRate(), bands, dto.dayRate(), dto.nightWeekendRate(),
				dto.nightStart(), dto.nightEnd(),
				Objects.requireNonNullElse(dto.billingIncrementMinutes(), PricingRule.DEFAULT_BILLING_INCREMENT_MINUTES),
				Objects.requireNonNullElse(dto.gracePeriodMinutes(), 0), dto.dailyCap(), surcharges);
	}

}
