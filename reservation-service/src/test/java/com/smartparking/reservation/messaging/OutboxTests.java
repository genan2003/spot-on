package com.smartparking.reservation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.reservation.PublishedEvents;
import com.smartparking.reservation.TestcontainersConfiguration;
import com.smartparking.reservation.client.HeldSpot;
import com.smartparking.reservation.client.ParkingClient;
import com.smartparking.reservation.event.ReservationCreatedEvent;
import com.smartparking.reservation.reservation.domain.Reservation;
import com.smartparking.reservation.reservation.service.ReservationService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** The transactional outbox: an event is sent if and only if its change committed, even when RabbitMQ is briefly down. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OutboxTests {

	@Autowired
	private OutboxEventRepository outbox;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private ReservationService reservationService;

	@MockitoSpyBean
	private RabbitTemplate rabbitTemplate;

	@MockitoBean
	private ParkingClient parkingClient;

	@Test
	void anEventFromARolledBackChangeIsNeverSaved() {
		ReservationCreatedEvent event = someEvent();

		transactionTemplate.executeWithoutResult(tx -> {
			eventPublisher.publishEvent(event);
			tx.setRollbackOnly();
		});

		assertThat(outbox.findById(event.eventId())).isEmpty();
	}

	@Test
	void publishingOutsideATransactionIsABug() {
		assertThatThrownBy(() -> eventPublisher.publishEvent(someEvent())).isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void anEventWaitsInTheOutboxWhileRabbitMqIsDown() {
		PublishedEvents events = new PublishedEvents(rabbitTemplate);
		// The first two sends fail (the after-commit attempt and the next poll), then RabbitMQ is "back".
		doThrow(new AmqpConnectException(new java.net.ConnectException("RabbitMQ is down")))
			.doThrow(new AmqpConnectException(new java.net.ConnectException("RabbitMQ is down")))
			.doCallRealMethod()
			.when(rabbitTemplate).send(eq(MessagingConfig.EXCHANGE), anyString(), any(Message.class));
		UUID spot = UUID.randomUUID();
		when(parkingClient.reserveSpot(eq(spot), eq("outbox-driver"), any()))
			.thenReturn(new HeldSpot(spot, "O1", UUID.randomUUID(), "Outbox garage", "RESERVED", "outbox-driver"));

		Reservation reservation = reservationService.reserve("outbox-driver", spot);

		// The reservation is saved at once; its event leaves once a send goes through.
		String created = events.await("reservation.created", reservation.getId().toString());
		assertThat(PublishedEvents.<String>read(created, "$.spotCode")).isEqualTo("O1");
		verify(rabbitTemplate, atLeast(3)).send(eq(MessagingConfig.EXCHANGE), anyString(), any(Message.class));
		await().atMost(Duration.ofSeconds(5)).until(() -> outbox.findAll().stream()
			.filter(e -> created.contains(e.getId().toString()))
			.allMatch(e -> e.getSentAt() != null));
		doCallRealMethod().when(rabbitTemplate).send(eq(MessagingConfig.EXCHANGE), anyString(), any(Message.class));
	}

	private static ReservationCreatedEvent someEvent() {
		Instant now = Instant.now();
		return new ReservationCreatedEvent(UUID.randomUUID(), now, UUID.randomUUID(), "driver-x", UUID.randomUUID(), "X1",
				UUID.randomUUID(), "Nowhere", now.plus(Duration.ofMinutes(15)));
	}

}
