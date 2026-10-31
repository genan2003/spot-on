package com.smartparking.reservation.pricing;

/** Percentages added per bay type (negative for a discount), plus one for EV charging. */
public record BaySurcharges(int compact, int large, int motorcycle, int evCharging) {

	public static final BaySurcharges NONE = new BaySurcharges(0, 0, 0, 0);

	/** The total percentage for one bay: its type's surcharge plus the EV one when it has a charger. */
	public int percentFor(Bay bay) {
		int byType = switch (bay.type()) {
			case STANDARD -> 0;
			case COMPACT -> compact;
			case LARGE -> large;
			case MOTORCYCLE -> motorcycle;
		};
		return byType + (bay.evCharging() ? evCharging : 0);
	}
}
