package com.smartparking.notification.preference.repository;

import com.smartparking.notification.preference.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

}
