package com.smartparking.poc.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartparking.poc.TestData;
import com.smartparking.poc.spot.ParkingSpotRepository;
import com.smartparking.poc.spot.SpotStatus;
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

/**
 * The mandatory double-reservation test: many drivers reserve the same spot at the same moment,
 * through the real service and database. Exactly one may succeed.
 */
@SpringBootTest
@Import(TestData.class)
class ReservationConcurrencyTest {

    private static final int DRIVERS = 10;

    @Autowired ReservationService reservationService;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ParkingSpotRepository spotRepository;
    @Autowired TestData testData;

    @Test
    void exactlyOneOfManyParallelReservationsForTheSameSpotSucceeds() throws Exception {
        UUID spotId = testData.spot();
        List<UUID> drivers = testData.drivers(DRIVERS);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(DRIVERS);
        List<Future<Boolean>> attempts = new ArrayList<>();
        for (UUID driver : drivers) {
            attempts.add(pool.submit(() -> {
                start.await();
                try {
                    reservationService.reserve(driver, spotId);
                    return true;
                } catch (RuntimeException conflict) {
                    return false; // @Version check, unique index, or the spot was already RESERVED
                }
            }));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> attempt : attempts) {
            if (attempt.get()) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        long active = reservationRepository.findAll().stream()
                .filter(r -> r.getSpot().getId().equals(spotId) && r.getStatus() == ReservationStatus.ACTIVE)
                .count();
        assertThat(active).isEqualTo(1);
        assertThat(spotRepository.findById(spotId).orElseThrow().getStatus()).isEqualTo(SpotStatus.RESERVED);
    }
}
