package com.smartparking.notification.notification.service;

import com.smartparking.notification.notification.channel.NotificationChannelFactory;
import com.smartparking.notification.notification.domain.ChannelType;
import com.smartparking.notification.notification.domain.Notification;
import com.smartparking.notification.notification.domain.NotificationKind;
import com.smartparking.notification.notification.live.NotificationSentEvent;
import com.smartparking.notification.notification.mapper.NotificationMapper;
import com.smartparking.notification.notification.repository.NotificationRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends a notification through the configured channel and keeps it for the driver app.
 * <b>Factory pattern: Client</b>: the channel comes from {@link NotificationChannelFactory}.
 * {@code DriverEventListener} calls {@link #send} for every reservation, session and payment event.
 */
@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationChannelFactory channelFactory;
	private final Clock clock;
	private final ChannelType channelType;
	private final ApplicationEventPublisher eventPublisher;
	private final NotificationMapper mapper;

	public NotificationService(NotificationRepository notificationRepository, NotificationChannelFactory channelFactory,
			Clock clock, @Value("${smartparking.notification.channel}") ChannelType channelType,
			ApplicationEventPublisher eventPublisher, NotificationMapper mapper) {
		this.notificationRepository = notificationRepository;
		this.channelFactory = channelFactory;
		this.clock = clock;
		this.channelType = channelType;
		this.eventPublisher = eventPublisher;
		this.mapper = mapper;
	}

	@Transactional
	public Notification send(String driverId, NotificationKind kind, String message) {
		Notification notification = new Notification(driverId, kind, channelType, message, clock.instant());
		channelFactory.create(channelType).send(notification);
		Notification saved = notificationRepository.save(notification);
		// Observer: after the commit, the driver's open app gets it at once (NotificationLiveRelay).
		eventPublisher.publishEvent(NotificationSentEvent.of(saved, mapper.toResponse(saved)));
		return saved;
	}

	/** The driver's latest notifications, newest first. */
	@Transactional(readOnly = true)
	public List<Notification> list(String driverId) {
		return notificationRepository.findTop50ByDriverIdOrderBySentAtDesc(driverId);
	}

}
