package com.smartparking.parking.activity.service;

import com.smartparking.parking.activity.dto.ActivityItem;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * The admin's live activity feed: the newest events, kept in memory only (they are for watching, the data itself
 * lives in each service's database), and pushed to the browsers on {@code /topic/activity} as they arrive. Every
 * parking-service instance receives every event, so each one's list is complete since it started.
 */
@Service
@RequiredArgsConstructor
public class ActivityFeed {

	public static final String TOPIC = "/topic/activity";

	static final int SIZE = 50;

	private final SimpMessagingTemplate messagingTemplate;

	private final Deque<ActivityItem> recent = new ArrayDeque<>(SIZE);

	/** Adds the event and pushes it to the open dashboards; a redelivered event is shown once. */
	public void add(ActivityItem item) {
		synchronized (recent) {
			if (recent.stream().anyMatch(existing -> existing.eventId().equals(item.eventId()))) {
				return;
			}
			recent.addFirst(item);
			if (recent.size() > SIZE) {
				recent.removeLast();
			}
		}
		messagingTemplate.convertAndSend(TOPIC, item);
	}

	/** Newest first. */
	public List<ActivityItem> recent() {
		synchronized (recent) {
			return List.copyOf(recent);
		}
	}

}
