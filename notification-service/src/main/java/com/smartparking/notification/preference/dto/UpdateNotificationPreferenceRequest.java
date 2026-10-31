package com.smartparking.notification.preference.dto;

import com.smartparking.notification.notification.domain.ChannelType;
import jakarta.validation.constraints.NotNull;

/** The channel the driver's next notifications go out on. */
public record UpdateNotificationPreferenceRequest(@NotNull ChannelType channel) {
}
