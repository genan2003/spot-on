package com.smartparking.reservation.messaging;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartparking.reservation.PublishedEvents;
import com.smartparking.reservation.TestcontainersConfiguration;
import com.smartparking.reservation.client.HeldSpot;
import com.smartparking.reservation.client.ParkingClient;
import com.smartparking.reservation.reservation.domain.Reservation;
import com.smartparking.reservation.reservation.domain.ReservationStatus;
import com.smartparking.reservation.reservation.repository.ReservationRepository;
import com.smartparking.reservation.reservation.service.ReservationService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Expiry through the TTL queue and dead-letter exchange, on a real RabbitMQ: a 2-second hold, with
 * the fallback sweep switched off so only the queue can expire it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "smartparking.reservation.duration=2s", "smartparking.reservation.expiry-check=1h" })
class ReservationExpiryTimerTests {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@MockitoBean
	private ParkingClient parkingClient;

	@Test
	void anUnusedReservationExpiresWhenItsTimerMessageDeadLetters() {
		PublishedEvents events = new PublishedEvents(rabbitTemplate);
		UUID spot = UUID.randomUUID();
		when(parkingClient.reserveSpot(eq(spot), eq("ttl-driver"), any()))
			.thenReturn(new HeldSpot(spot, "T1", UUID.randomUUID(), "Timer garage", "RESERVED", "ttl-driver"));

		Reservation reservation = reservationService.reserve("ttl-driver", spot);

		await().atMost(Duration.ofSeconds(15)).until(() ->
				reservationRepository.findById(reservation.getId()).orElseThrow().getStatus() == ReservationStatus.EXPIRED);
		events.await("reservation.expired", reservation.getId().toString());
	}

}
