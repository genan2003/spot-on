package com.smartparking.parking.spot;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartparking.parking.TestcontainersConfiguration;
import com.smartparking.parking.location.domain.LocationSummary;
import com.smartparking.parking.location.service.ParkingLocationService;
import com.smartparking.parking.spot.domain.ParkingSpot;
import com.smartparking.parking.spot.domain.SpotStatus;
import com.smartparking.parking.spot.domain.SpotType;
import com.smartparking.parking.spot.repository.ParkingSpotRepository;
import com.smartparking.parking.spot.service.SpotHoldService;
import com.smartparking.parking.spot.service.SpotService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * No double reservations: ten drivers reserve the same spot at the same moment. The spot's state
 * and its {@code @Version} column must let exactly one through.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpotConcurrencyTests {

	private static final int DRIVERS = 10;

	@Autowired
	private ParkingLocationService locationService;

	@Autowired
	private SpotService spotService;

	@Autowired
	private SpotHoldService holdService;

	@Autowired
	private ParkingSpotRepository spotRepository;

	@Test
	void onlyOneOfManySimultaneousReservationsWins() throws Exception {
		LocationSummary location = locationService.create("Race garage", "Test street 1", 46.77, 23.59);
		UUID spotId = spotService.addSpots(location.location().getId(), 0, "R", 1, SpotType.STANDARD, false, false)
			.getFirst().getId();

		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(DRIVERS)) {
			for (int i = 0; i < DRIVERS; i++) {
				String driverId = "driver-" + i;
				results.add(pool.submit(() -> {
					start.await();
					try {
						holdService.reserve(spotId, driverId, UUID.randomUUID());
						return true;
					}
					catch (IllegalStateException | ConcurrencyFailureException rejected) {
						// IllegalStateException: the spot was already RESERVED when this request read it.
						// ConcurrencyFailureException: both read AVAILABLE, the @Version check failed this one.
						return false;
					}
				}));
			}
			start.countDown();
		}

		long winners = 0;
		for (Future<Boolean> result : results) {
			if (result.get()) {
				winners++;
			}
		}
		assertThat(winners).isEqualTo(1);
		ParkingSpot spot = spotRepository.findById(spotId).orElseThrow();
		assertThat(spot.getStatus()).isEqualTo(SpotStatus.RESERVED);
		assertThat(spot.getVersion()).isEqualTo(1);
	}

}
