package com.smartparking.notification.notification.mapper;

import com.smartparking.notification.notification.domain.Notification;
import com.smartparking.notification.notification.dto.NotificationResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

	NotificationResponse toResponse(Notification notification);

}
