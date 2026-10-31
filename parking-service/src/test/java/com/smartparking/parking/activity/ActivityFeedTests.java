package com.smartparking.parking.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.smartparking.parking.activity.dto.ActivityItem;
import com.smartparking.parking.activity.service.ActivityFeed;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class ActivityFeedTests {

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
	private final ActivityFeed feed = new ActivityFeed(messagingTemplate);

	@Test
	void keepsTheNewest50NewestFirst() {
		for (int i = 0; i < 60; i++) {
			feed.add(item(UUID.randomUUID(), "A" + i));
		}
		assertThat(feed.recent()).hasSize(50);
		assertThat(feed.recent().getFirst().spotCode()).isEqualTo("A59");
		assertThat(feed.recent().getLast().spotCode()).isEqualTo("A10");
	}

	@Test
	void aRedeliveredEventIsShownAndPushedOnce() {
		UUID eventId = UUID.randomUUID();
		feed.add(item(eventId, "A1"));
		feed.add(item(eventId, "A1"));
		assertThat(feed.recent()).hasSize(1);
		verify(messagingTemplate, times(1)).convertAndSend(eq(ActivityFeed.TOPIC), any(Object.class));
	}

	private static ActivityItem item(UUID eventId, String spotCode) {
		return new ActivityItem(eventId, Instant.now(), "reservation.created", "driver-1", spotCode, null, UUID.randomUUID(),
				"Central Garage", null, null, null);
	}

}
