package com.smartparking.notification.preference.domain;

import com.smartparking.notification.notification.domain.ChannelType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** How a driver wants to be notified. {@code NotificationChannelFactory} picks the channel from it. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

	@Id
	private String driverId;

	@Enumerated(EnumType.STRING)
	private ChannelType channel;

	@Version
	private long version;

	public NotificationPreference(String driverId, ChannelType channel) {
		this.driverId = driverId;
		this.channel = channel;
	}

	public void choose(ChannelType channel) {
		this.channel = channel;
	}

}
