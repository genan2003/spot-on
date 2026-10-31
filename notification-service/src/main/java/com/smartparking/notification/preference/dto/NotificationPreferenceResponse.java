package com.smartparking.notification.preference.dto;

import com.smartparking.notification.notification.domain.ChannelType;

public record NotificationPreferenceResponse(String driverId, ChannelType channel) {
}
