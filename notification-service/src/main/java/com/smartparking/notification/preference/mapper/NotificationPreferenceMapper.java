package com.smartparking.notification.preference.mapper;

import com.smartparking.notification.preference.domain.NotificationPreference;
import com.smartparking.notification.preference.dto.NotificationPreferenceResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationPreferenceMapper {

	NotificationPreferenceResponse toResponse(NotificationPreference preference);

}
