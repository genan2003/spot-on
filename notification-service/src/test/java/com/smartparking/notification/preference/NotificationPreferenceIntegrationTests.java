package com.smartparking.notification.preference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartparking.notification.TestcontainersConfiguration;
import com.smartparking.notification.notification.domain.NotificationKind;
import com.smartparking.notification.notification.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** A driver's channel choice decides which channel the Factory hands out for their next notifications. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationPreferenceIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private NotificationService notificationService;

	@Test
	void aDriverWhoNeverChoseGetsTheDefaultChannel() throws Exception {
		mvc.perform(get("/api/v1/notification-preferences/{driverId}", "driver-" + UUID.randomUUID()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.channel").value("PUSH"));
	}

	@Test
	void theChosenChannelIsUsedForTheNextNotifications() throws Exception {
		String driver = "driver-" + UUID.randomUUID();
		notificationService.send(driver, NotificationKind.RESERVATION_CREATED, "Before the choice.");

		mvc.perform(patch("/api/v1/notification-preferences/{driverId}", driver)
				.contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"SMS\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.channel").value("SMS"));
		mvc.perform(patch("/api/v1/notification-preferences/{driverId}", driver)
				.contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"EMAIL\"}"))
			.andExpect(jsonPath("$.channel").value("EMAIL"));
		notificationService.send(driver, NotificationKind.RESERVATION_CANCELLED, "After the choice.");

		mvc.perform(get("/api/v1/notification-preferences/{driverId}", driver)).andExpect(jsonPath("$.channel").value("EMAIL"));
		mvc.perform(get("/api/v1/notifications").param("driverId", driver))
			.andExpect(jsonPath("$[0].channelType").value("EMAIL"))
			.andExpect(jsonPath("$[1].channelType").value("PUSH"));
	}

	@Test
	void anUnknownOrMissingChannelIsRefused() throws Exception {
		String driver = "driver-" + UUID.randomUUID();
		mvc.perform(patch("/api/v1/notification-preferences/{driverId}", driver)
				.contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"PIGEON\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/v1/notification-preferences/{driverId}", driver)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
	}

}
