package com.smartparking.poc.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Checks every cell of the transition table in {@link SpotState}. */
class SpotStateTest {

    private static final SpotStatus NOT_ALLOWED = null;

    static Stream<Arguments> transitions() {
        return Stream.of(
                // from, action, expected status (null = rejected)
                Arguments.of(SpotStatus.AVAILABLE, "reserve", SpotStatus.RESERVED),
                Arguments.of(SpotStatus.AVAILABLE, "occupy", SpotStatus.OCCUPIED),
                Arguments.of(SpotStatus.AVAILABLE, "release", NOT_ALLOWED),
                Arguments.of(SpotStatus.AVAILABLE, "markOutOfService", SpotStatus.OUT_OF_SERVICE),
                Arguments.of(SpotStatus.AVAILABLE, "returnToService", NOT_ALLOWED),

                Arguments.of(SpotStatus.RESERVED, "reserve", NOT_ALLOWED),
                Arguments.of(SpotStatus.RESERVED, "occupy", SpotStatus.OCCUPIED),
                Arguments.of(SpotStatus.RESERVED, "release", SpotStatus.AVAILABLE),
                Arguments.of(SpotStatus.RESERVED, "markOutOfService", SpotStatus.OUT_OF_SERVICE),
                Arguments.of(SpotStatus.RESERVED, "returnToService", NOT_ALLOWED),

                Arguments.of(SpotStatus.OCCUPIED, "reserve", NOT_ALLOWED),
                Arguments.of(SpotStatus.OCCUPIED, "occupy", NOT_ALLOWED),
                Arguments.of(SpotStatus.OCCUPIED, "release", SpotStatus.AVAILABLE),
                Arguments.of(SpotStatus.OCCUPIED, "markOutOfService", NOT_ALLOWED),
                Arguments.of(SpotStatus.OCCUPIED, "returnToService", NOT_ALLOWED),

                Arguments.of(SpotStatus.OUT_OF_SERVICE, "reserve", NOT_ALLOWED),
                Arguments.of(SpotStatus.OUT_OF_SERVICE, "occupy", NOT_ALLOWED),
                Arguments.of(SpotStatus.OUT_OF_SERVICE, "release", NOT_ALLOWED),
                Arguments.of(SpotStatus.OUT_OF_SERVICE, "markOutOfService", NOT_ALLOWED),
                Arguments.of(SpotStatus.OUT_OF_SERVICE, "returnToService", SpotStatus.AVAILABLE));
    }

    @ParameterizedTest(name = "{0} + {1}() → {2}")
    @MethodSource("transitions")
    void followsTheTransitionTable(SpotStatus from, String action, SpotStatus expected) {
        ParkingSpot spot = spotIn(from);
        Consumer<ParkingSpot> act = action(action);

        if (expected == NOT_ALLOWED) {
            assertThatThrownBy(() -> act.accept(spot)).isInstanceOf(IllegalSpotStateTransitionException.class)
                    .hasMessageContaining("A1");
            assertThat(spot.getStatus()).isEqualTo(from);
        } else {
            act.accept(spot);
            assertThat(spot.getStatus()).isEqualTo(expected);
            assertThat(spot.getState().getStatus()).isEqualTo(expected);
        }
    }

    private static Consumer<ParkingSpot> action(String name) {
        return switch (name) {
            case "reserve" -> ParkingSpot::reserve;
            case "occupy" -> ParkingSpot::occupy;
            case "release" -> ParkingSpot::release;
            case "markOutOfService" -> ParkingSpot::markOutOfService;
            case "returnToService" -> ParkingSpot::returnToService;
            default -> throw new IllegalArgumentException(name);
        };
    }

    /** Builds a spot in the given state using only legal transitions. */
    private static ParkingSpot spotIn(SpotStatus status) {
        ParkingSpot spot = new ParkingSpot("A1", 0, "A", SpotType.STANDARD, false, false);
        switch (status) {
            case AVAILABLE -> { }
            case RESERVED -> spot.reserve();
            case OCCUPIED -> spot.occupy();
            case OUT_OF_SERVICE -> spot.markOutOfService();
        }
        return spot;
    }
}
