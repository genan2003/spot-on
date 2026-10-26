package com.smartparking.reservation.driver.controller;

import com.smartparking.reservation.driver.dto.CurrentParkingResponse;
import com.smartparking.reservation.driver.service.CurrentParkingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CurrentParkingController {

	private final CurrentParkingService currentParkingService;

	@GetMapping("/api/v1/reservations/current")
	public CurrentParkingResponse current(@RequestParam String driverId) {
		return currentParkingService.current(driverId);
	}

}
