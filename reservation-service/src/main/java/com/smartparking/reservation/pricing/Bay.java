package com.smartparking.reservation.pricing;

/** The bay a session uses, as far as pricing cares: its type and whether it has a charger. */
public record Bay(BayType type, boolean evCharging) {

	public static final Bay STANDARD = new Bay(BayType.STANDARD, false);

	public Bay {
		type = type == null ? BayType.STANDARD : type;
	}
}
