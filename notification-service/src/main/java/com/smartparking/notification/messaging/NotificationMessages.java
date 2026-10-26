package com.smartparking.notification.messaging;

import com.smartparking.notification.notification.domain.NotificationKind;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Turns an event into the sentence the driver reads. Same wording as the frontend's demo data. */
final class NotificationMessages {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Bucharest"));

	private NotificationMessages() {
	}

	static NotificationKind kind(String routingKey) {
		return NotificationKind.valueOf(routingKey.replace('.', '_').toUpperCase());
	}

	static String message(NotificationKind kind, DriverEvent e) {
		return switch (kind) {
			case RESERVATION_CREATED -> "%s at %s is held for you until %s.".formatted(e.spotCode(), e.locationName(), TIME.format(e.expiresAt()));
			case RESERVATION_CANCELLED -> "You cancelled your reservation for %s.".formatted(e.spotCode());
			case RESERVATION_EXPIRED -> "Your reservation for %s expired, so the spot is free again.".formatted(e.spotCode());
			case SESSION_COMPLETED -> "Your session at %s, %s ended. It cost %s.".formatted(e.spotCode(), e.locationName(), lei(e.cost()));
			case PAYMENT_COMPLETED -> "Paid %s for your parking session.".formatted(lei(e.amount()));
			case PAYMENT_FAILED -> "Payment of %s failed. Try payment again from your receipt.".formatted(lei(e.amount()));
		};
	}

	private static String lei(BigDecimal amount) {
		return "%.2f lei".formatted(amount);
	}

}
