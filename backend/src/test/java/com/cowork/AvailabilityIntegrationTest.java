package com.cowork;

import com.cowork.business.service.BookingService;
import com.cowork.dto.BookingDtos.ContactRequest;
import com.cowork.dto.BookingDtos.HoldBookingRequest;
import com.cowork.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC02 qua REST API: chọn chi nhánh → lọc tiện ích → tìm chỗ trống → xem sơ đồ mặt bằng.
 * Dùng ngày thứ Hai 09/11/2026 để không đụng dữ liệu của các integration test khác.
 */
class AvailabilityIntegrationTest extends IntegrationTest {

    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 11, 9, 0, 0);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    BookingService bookingService;

    private String branchId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(MONDAY.withHour(8));
        String body = mvc.perform(get("/api/branches")).andReturn().getResponse().getContentAsString();
        branchId = json.readTree(body).get(0).get("id").asText();
    }

    @Test
    void listsActiveBranchesWithOpeningHoursAndAmenityCatalog() throws Exception {
        mvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("HCM-Q1"))
                .andExpect(jsonPath("$[0].openTime").value("07:00:00"))
                .andExpect(jsonPath("$[0].closeTime").value("22:00:00"));

        mvc.perform(get("/api/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[*].code", hasItem("QUIET")));
    }

    @Test
    void filtersByAmenitiesRequiringAllOfThem() throws Exception {
        search("HOT_DESK", "09:00", "11:00", 1, "QUIET")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        search("FOCUS_BOOTH", "09:00", "11:00", 1, "quiet,POWER_OUTLET")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("FB-01", "FB-02")))
                .andExpect(jsonPath("$[0].zoneName").value("Khu yên tĩnh"))
                .andExpect(jsonPath("$[0].posX").value(25))
                .andExpect(jsonPath("$[0].amenities[*].code", hasItem("QUIET")));

        search("MEETING_ROOM", "09:00", "11:00", 8, "WHITEBOARD,TV")
                .andExpect(jsonPath("$[*].code").value(contains("MR-02")));
    }

    @Test
    void floorPlanShowsHeldResourceWhileSearchHidesIt() throws Exception {
        Long boothId = resource("FB-01").getId();
        bookingService.hold(new HoldBookingRequest(boothId, MONDAY.withHour(9), MONDAY.withHour(11), 1,
                new ContactRequest("Lê Văn C", "0901234567", null)));

        mvc.perform(get("/api/branches/{id}/floor-plan", branchId)
                        .param("type", "FOCUS_BOOTH")
                        .param("startAt", "2026-11-09T10:00:00")
                        .param("endAt", "2026-11-09T12:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones.length()").value(1))
                .andExpect(jsonPath("$.zones[0].floorPlanUrl").value("/floor-plans/hcm-q1-tang-2.png"))
                .andExpect(jsonPath("$.zones[0].resources[0].code").value("FB-01"))
                .andExpect(jsonPath("$.zones[0].resources[0].status").value("HELD"))
                .andExpect(jsonPath("$.zones[0].resources[0].posY").value(70))
                .andExpect(jsonPath("$.zones[0].resources[1].status").value("AVAILABLE"));

        search("FOCUS_BOOTH", "10:00", "12:00", 1, null)
                .andExpect(jsonPath("$[*].code").value(contains("FB-02")));

        // Khoảng thời gian nửa mở [start, end): đơn 09:00–11:00 không chặn khung bắt đầu đúng 11:00.
        search("FOCUS_BOOTH", "11:00", "13:00", 1, null)
                .andExpect(jsonPath("$[*].code").value(contains("FB-01", "FB-02")));
    }

    @Test
    void rejectsInvalidSearchRequests() throws Exception {
        mvc.perform(get("/api/availability").param("type", "HOT_DESK")
                        .param("startAt", "2026-11-09T09:00:00").param("endAt", "2026-11-09T11:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        search("SAUNA", "09:00", "11:00", 1, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get("/api/availability").param("branchId", "999999").param("type", "HOT_DESK")
                        .param("startAt", "2026-11-09T09:00:00").param("endAt", "2026-11-09T11:00:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        search("HOT_DESK", "21:00", "23:00", 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OUTSIDE_OPENING_HOURS"));

        search("HOT_DESK", "09:00", "09:30", 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DURATION_TOO_SHORT"));

        search("HOT_DESK", "09:00", "11:00", 1, "JACUZZI")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_AMENITY"));
    }

    @Test
    void holdAppliesTheSameOpeningHoursRule() throws Exception {
        Map<String, Object> request = Map.of(
                "resourceId", resource("HD-06").getId(),
                "startAt", "2026-11-09T21:00:00",
                "endAt", "2026-11-09T23:00:00",
                "partySize", 1,
                "contact", Map.of("fullName", "Lê Văn C", "phone", "0901234567"));

        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OUTSIDE_OPENING_HOURS"));
    }

    private ResultActions search(String type, String from, String to, int partySize, String amenities) throws Exception {
        var request = get("/api/availability")
                .param("branchId", branchId)
                .param("type", type)
                .param("startAt", "2026-11-09T" + from + ":00")
                .param("endAt", "2026-11-09T" + to + ":00")
                .param("partySize", String.valueOf(partySize));
        if (amenities != null) {
            request.param("amenities", amenities);
        }
        return mvc.perform(request);
    }
}
