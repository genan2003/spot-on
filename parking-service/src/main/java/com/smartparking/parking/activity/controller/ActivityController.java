package com.smartparking.parking.activity.controller;

import com.smartparking.parking.activity.dto.ActivityItem;
import com.smartparking.parking.activity.service.ActivityFeed;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The admin dashboard loads the recent activity once; new events then arrive on STOMP {@code /topic/activity}. */
@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityController {

	private final ActivityFeed feed;

	/** The newest events (at most 50), newest first. */
	@GetMapping
	public List<ActivityItem> recent() {
		return feed.recent();
	}

}
