package com.smartparking.notification.notification.domain;

/** What the notification is about; one per event the service consumes. */
public enum NotificationKind {
	RESERVATION_CREATED,
	RESERVATION_CHANGED,
	RESERVATION_EXPIRING,
	RESERVATION_CANCELLED,
	RESERVATION_EXPIRED,
	SESSION_COMPLETED,
	PAYMENT_COMPLETED,
	PAYMENT_FAILED
}
