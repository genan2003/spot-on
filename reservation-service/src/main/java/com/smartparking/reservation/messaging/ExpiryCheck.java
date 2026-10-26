package com.smartparking.reservation.messaging;

import java.util.UUID;

/** Message on the expiry timer: "check whether this reservation should expire now". */
public record ExpiryCheck(UUID reservationId) {
}
