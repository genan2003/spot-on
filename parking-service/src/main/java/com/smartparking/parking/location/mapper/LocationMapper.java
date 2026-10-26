package com.smartparking.parking.location.mapper;

import com.smartparking.parking.location.domain.LocationSummary;
import com.smartparking.parking.location.domain.PricingRule;
import com.smartparking.parking.location.dto.LocationResponse;
import com.smartparking.parking.location.dto.PricingRuleDto;
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
	LocationResponse toResponse(LocationSummary summary);

	@Mapping(target = "strategy", source = "strategyType")
	PricingRuleDto toDto(PricingRule rule);

	default PricingRule toPricingRule(PricingRuleDto dto) {
		return PricingRule.of(dto.strategy(), dto.hourlyRate(), dto.flatRate(), dto.firstHourRate(),
				dto.laterHourRate(), dto.dayRate(), dto.nightWeekendRate());
	}

}
