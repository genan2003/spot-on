package com.smartparking.reservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.reservation.TestcontainersConfiguration;
import com.smartparking.reservation.client.HeldSpot;
import com.smartparking.reservation.client.ParkingClient;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import com.smartparking.reservation.reservation.repository.ReservationRepository;
import com.smartparking.reservation.reservation.service.ReservationService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * No double reservations, second guard: even if parking-service let every request through (here it
 * is mocked to do exactly that), the partial unique index on open reservations per spot lets only
 * one of ten simultaneous reservations be saved. The losers undo their spot hold.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReservationConcurrencyTests {

	private static final int DRIVERS = 10;

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ReservationRepository reservationRepository;

	@MockitoBean
	private ParkingClient parkingClient;

	@Test
	void theDatabaseLetsOnlyOneReservationPerSpotThrough() throws Exception {
		UUID spot = UUID.randomUUID();
		when(parkingClient.reserveSpot(eq(spot), anyString(), any()))
			.thenAnswer(call -> new HeldSpot(spot, "A1", UUID.randomUUID(), "Race garage", "RESERVED", call.getArgument(1)));

		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(DRIVERS)) {
			for (int i = 0; i < DRIVERS; i++) {
				String driverId = "race-driver-" + i + "-" + spot;
				results.add(pool.submit(() -> {
					start.await();
					try {
						reservationService.reserve(driverId, spot);
						return true;
					}
					catch (DataIntegrityViolationException rejected) {
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
		assertThat(reservationRepository.findAll().stream()
			.filter(r -> r.getSpotId().equals(spot) && r.getStatus() == ReservationStatus.ACTIVE)).hasSize(1);
		verify(parkingClient, times(DRIVERS - 1)).releaseSpotIfHeld(eq(spot), any());
	}

}
