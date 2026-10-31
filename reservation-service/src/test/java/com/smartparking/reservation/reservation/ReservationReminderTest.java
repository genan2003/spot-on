package com.smartparking.reservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartparking.reservation.reservation.domain.CancelReason;
import com.smartparking.reservation.reservation.domain.Reservation;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** When the "expires soon" reminder may go out: once per expiry time, only while the driver can still act. */
class ReservationReminderTest {

	private static final Instant CREATED = Instant.parse("2026-03-04T10:00:00Z");
	private static final Duration HOLD = Duration.ofMinutes(15);
	private static final Duration LEAD = Duration.ofMinutes(5);

	private final Reservation reservation = new Reservation(UUID.randomUUID(), "driver-1", UUID.randomUUID(), "A1",
			UUID.randomUUID(), "Central Garage", CREATED, CREATED.plus(HOLD));

	private static Instant at(int minutes) {
		return CREATED.plus(Duration.ofMinutes(minutes));
	}

	@Test
	void remindsOnceFiveMinutesBeforeTheEnd() {
		assertThat(reservation.needsReminder(at(5), LEAD)).isFalse();
		assertThat(reservation.needsReminder(at(10), LEAD)).isTrue();
		reservation.markReminded();
		// A redelivered timer message doesn't remind twice.
		assertThat(reservation.needsReminder(at(11), LEAD)).isFalse();
	}

	@Test
	void anExtendedReservationIsRemindedAgainBeforeItsNewEnd() {
		reservation.markReminded();
		reservation.extend(HOLD, at(11));
		// The old reminder time finds plenty of time left.
		assertThat(reservation.needsReminder(at(12), LEAD)).isFalse();
		assertThat(reservation.needsReminder(at(25), LEAD)).isTrue();
	}

	@Test
	void anEndedReservationIsNotReminded() {
		reservation.cancel(CancelReason.DRIVER, at(3));
		assertThat(reservation.needsReminder(at(10), LEAD)).isFalse();
		assertThat(new Reservation(UUID.randomUUID(), "d", UUID.randomUUID(), "A2", UUID.randomUUID(), "G", CREATED, CREATED.plus(HOLD))
			.needsReminder(at(16), LEAD)).as("overdue").isFalse();
	}

}
