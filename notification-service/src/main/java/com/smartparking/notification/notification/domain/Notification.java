package com.smartparking.notification.notification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A message sent to a driver, kept so the driver app can list it. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String driverId;

	@Enumerated(EnumType.STRING)
	private NotificationKind kind;

	@Enumerated(EnumType.STRING)
	private ChannelType channelType;

	private String message;

	private Instant sentAt;

	public Notification(String driverId, NotificationKind kind, ChannelType channelType, String message, Instant sentAt) {
		this.driverId = driverId;
		this.kind = kind;
		this.channelType = channelType;
		this.message = message;
		this.sentAt = sentAt;
	}

}
