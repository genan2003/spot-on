package com.smartparking.parking;

import org.springframework.boot.SpringApplication;

public class TestParkingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ParkingServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
