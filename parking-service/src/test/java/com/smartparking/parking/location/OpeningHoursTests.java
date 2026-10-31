package com.smartparking.parking.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartparking.parking.location.domain.ParkingLocation;
import com.smartparking.parking.location.domain.PricingRule;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class OpeningHoursTests {

	private final ParkingLocation location = new ParkingLocation("Central Garage", "Strada X 1", 46.77, 23.59, PricingRule.defaultRule());

	@Test
	void withoutHoursItIsAlwaysOpen() {
		assertThat(location.isOpenAllDay()).isTrue();
		assertThat(location.isOpenAt(LocalTime.of(3, 0))).isTrue();
	}

	@Test
	void openFromOpeningTimeUntilJustBeforeClosingTime() {
		location.changeOpeningHours(LocalTime.of(7, 0), LocalTime.of(22, 0));
		assertThat(location.isOpenAt(LocalTime.of(6, 59))).isFalse();
		assertThat(location.isOpenAt(LocalTime.of(7, 0))).isTrue();
		assertThat(location.isOpenAt(LocalTime.of(21, 59))).isTrue();
		assertThat(location.isOpenAt(LocalTime.of(22, 0))).isFalse();
	}

	@Test
	void hoursPastMidnightWrapAround() {
		location.changeOpeningHours(LocalTime.of(22, 0), LocalTime.of(6, 0));
		assertThat(location.isOpenAt(LocalTime.of(23, 30))).isTrue();
		assertThat(location.isOpenAt(LocalTime.of(2, 0))).isTrue();
		assertThat(location.isOpenAt(LocalTime.of(6, 0))).isFalse();
		assertThat(location.isOpenAt(LocalTime.of(12, 0))).isFalse();
	}

	@Test
	void hoursNeedBothTimesAndTheyMustDiffer() {
		assertThatThrownBy(() -> location.changeOpeningHours(LocalTime.of(7, 0), null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> location.changeOpeningHours(LocalTime.of(7, 0), LocalTime.of(7, 0)))
			.isInstanceOf(IllegalArgumentException.class);
		location.changeOpeningHours(LocalTime.of(7, 0), LocalTime.of(22, 0));
		location.changeOpeningHours(null, null);
		assertThat(location.isOpenAllDay()).isTrue();
	}

}
