package com.smartparking.reservation.stats.controller;

import com.smartparking.reservation.stats.dto.DailyStatsResponse;
import com.smartparking.reservation.stats.service.DailyStatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin dashboard: a car park's revenue and occupancy per day. Under {@code /parking-sessions}, so the gateway's
 * existing route to this service covers it.
 */
@RestController
@RequestMapping("/api/v1/parking-sessions/daily-stats")
@RequiredArgsConstructor
public class DailyStatsController {

	private final DailyStatsService statsService;

	@GetMapping
	public List<DailyStatsResponse> lastDays(@RequestParam UUID locationId,
			@RequestParam(defaultValue = "7") @Min(1) @Max(31) int days) {
		return statsService.lastDays(locationId, days);
	}

}
