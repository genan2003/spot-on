package com.smartparking.notification.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTests {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MockMvc mvc;

	@Test
	void driversSeeOnlyTheirOwnNotificationsNewestFirst() throws Exception {
		String driver = "driver-" + UUID.randomUUID();
		notificationService.send(driver, NotificationKind.RESERVATION_CREATED, "A1 at Central Garage is held for you.");
		notificationService.send(driver, NotificationKind.RESERVATION_CANCELLED, "You cancelled your reservation for A1.");
		notificationService.send("someone-else", NotificationKind.SESSION_COMPLETED, "Not for this driver.");

		mvc.perform(get("/api/v1/notifications").param("driverId", driver))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].kind").value("RESERVATION_CANCELLED"))
			.andExpect(jsonPath("$[0].channelType").value("PUSH"))
			.andExpect(jsonPath("$[1].message").value("A1 at Central Garage is held for you."));
	}

	@Test
	void driverIdIsRequired() throws Exception {
		mvc.perform(get("/api/v1/notifications")).andExpect(status().isBadRequest());
	}

}
