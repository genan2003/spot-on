package com.smartparking.notification.notification.dto;

import com.smartparking.notification.notification.domain.ChannelType;
import com.smartparking.notification.notification.domain.NotificationKind;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationKind kind, ChannelType channelType, String message, Instant sentAt) {
}
