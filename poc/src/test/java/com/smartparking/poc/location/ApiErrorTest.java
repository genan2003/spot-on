package com.smartparking.poc.location;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartparking.poc.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Errors reach clients as RFC 7807 problem details with the right status. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestData.class)
class ApiErrorTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestData testData;

    @Test
    void reservingAReservedSpotReturns409() throws Exception {
        UUID spot = testData.spot();
        String body = """
                {"driverId": "%s", "spotId": "%s"}""";

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(testData.driver(), spot)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(testData.driver(), spot)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Spot A1 is reserved, so it can't be reserved."));
    }

    @Test
    void anUnknownSpotReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\": \"%s\", \"spotId\": \"%s\"}".formatted(testData.driver(), UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anInvalidRequestReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
