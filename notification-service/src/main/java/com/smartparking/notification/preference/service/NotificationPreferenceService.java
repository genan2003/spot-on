package com.smartparking.notification.preference.service;

import com.smartparking.notification.notification.domain.ChannelType;
import com.smartparking.notification.preference.domain.NotificationPreference;
import com.smartparking.notification.preference.repository.NotificationPreferenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drivers' notification channels. There are no driver accounts, so a driver who never chose one gets the
 * configured default channel; a row is stored only once they choose.
 */
@Service
public class NotificationPreferenceService {

	private final NotificationPreferenceRepository preferenceRepository;
	private final ChannelType defaultChannel;

	public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository,
			@Value("${smartparking.notification.default-channel}") ChannelType defaultChannel) {
		this.preferenceRepository = preferenceRepository;
		this.defaultChannel = defaultChannel;
	}

	/** The driver's choice, or the default (not saved) if they haven't chosen. */
	@Transactional(readOnly = true)
	public NotificationPreference get(String driverId) {
		return preferenceRepository.findById(driverId).orElseGet(() -> new NotificationPreference(driverId, defaultChannel));
	}

	/** The channel the next notification goes out on. Called for every event, so it never writes. */
	@Transactional(readOnly = true)
	public ChannelType channelFor(String driverId) {
		return get(driverId).getChannel();
	}

	@Transactional
	public NotificationPreference choose(String driverId, ChannelType channel) {
		NotificationPreference preference = preferenceRepository.findById(driverId)
			.orElseGet(() -> new NotificationPreference(driverId, channel));
		preference.choose(channel);
		return preferenceRepository.save(preference);
	}

}
