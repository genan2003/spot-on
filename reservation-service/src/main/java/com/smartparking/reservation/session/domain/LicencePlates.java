package com.smartparking.reservation.session.domain;

import java.util.Locale;

/** Plates are stored the way the admin reads them on the car: upper case, single spaces ("cj  07 abc" → "CJ 07 ABC"). */
public final class LicencePlates {

	/** What a plate may look like before normalising: 2 to 12 letters, digits, spaces or hyphens. */
	public static final String PATTERN = "\\s*[A-Za-z0-9][A-Za-z0-9 -]{1,11}\\s*";

	private LicencePlates() {
	}

	public static String normalize(String plate) {
		return plate.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
	}

}
