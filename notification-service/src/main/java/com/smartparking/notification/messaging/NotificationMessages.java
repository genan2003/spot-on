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
			case RESERVATION_CHANGED -> e.previousSpotCode() != null
					? "Your reservation moved from %s to %s. It is held until %s.".formatted(e.previousSpotCode(), e.spotCode(), TIME.format(e.expiresAt()))
					: "%s is now held for you until %s.".formatted(e.spotCode(), TIME.format(e.expiresAt()));
			case RESERVATION_EXPIRING -> "%s at %s is held for you only until %s. Start your session or extend the hold before then."
					.formatted(e.spotCode(), e.locationName(), TIME.format(e.expiresAt()));
			case RESERVATION_CANCELLED -> "SPOT_CLOSED".equals(e.reason())
					? "%s at %s was closed by the car park, so your reservation was cancelled. Pick another free spot."
						.formatted(e.spotCode(), e.locationName())
					: "You cancelled your reservation for %s.".formatted(e.spotCode());
			case RESERVATION_EXPIRED -> "Your reservation for %s expired, so the spot is free again.".formatted(e.spotCode());
			case SESSION_COMPLETED -> "Your session at %s, %s ended. It cost %s.".formatted(e.spotCode(), e.locationName(), lei(e.cost()));
			// The method says which processor the Factory in payment-service used.
			case PAYMENT_COMPLETED -> switch (String.valueOf(e.method())) {
				case "CARD" -> "Paid %s by card for your parking session.".formatted(lei(e.amount()));
				case "WALLET" -> "Paid %s from your wallet for your parking session.".formatted(lei(e.amount()));
				default -> "Paid %s for your parking session.".formatted(lei(e.amount()));
			};
			case PAYMENT_FAILED -> switch (String.valueOf(e.method())) {
				case "CARD" -> "Your card was declined for %s. Try payment again from your receipt, or pay from your wallet."
					.formatted(lei(e.amount()));
				case "WALLET" -> "Your wallet doesn't have enough money for %s. Add money or pay by card from your receipt."
					.formatted(lei(e.amount()));
				default -> "Payment of %s failed. Try payment again from your receipt.".formatted(lei(e.amount()));
			};
		};
	}

	private static String lei(BigDecimal amount) {
		return "%.2f lei".formatted(amount);
	}

}
