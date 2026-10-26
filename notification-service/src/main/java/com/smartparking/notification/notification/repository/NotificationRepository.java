package com.smartparking.notification.notification.repository;

import com.smartparking.notification.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findTop50ByDriverIdOrderBySentAtDesc(String driverId);

}
