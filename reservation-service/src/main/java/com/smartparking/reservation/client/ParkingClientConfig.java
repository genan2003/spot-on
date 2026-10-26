package com.smartparking.reservation.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient for parking-service, with timeouts: a slow parking-service gives the driver a clear
 * 503 after a few seconds instead of a request that hangs.
 */
@Configuration
public class ParkingClientConfig {

	@Bean
	RestClient parkingRestClient(@Value("${smartparking.parking-service.url}") String baseUrl,
			@Value("${smartparking.parking-service.connect-timeout}") Duration connectTimeout,
			@Value("${smartparking.parking-service.read-timeout}") Duration readTimeout) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(connectTimeout).build());
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

}
