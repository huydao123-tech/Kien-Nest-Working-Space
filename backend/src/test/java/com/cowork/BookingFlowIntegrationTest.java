package com.cowork;

import com.cowork.business.service.BookingService;
import com.cowork.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Đi hết các luồng chính qua REST API: UC02 → UC03 → UC04 → UC07 → UC08 → UC09 → UC16, và UC25. */
class BookingFlowIntegrationTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    BookingService bookingService;

    @Test
    void fullLifecycleFromSearchToCompletion() throws Exception {
        LocalDateTime monday = LocalDateTime.of(2026, 11, 2, 0, 0);
        clock.set(monday.withHour(8));

        mvc.perform(get("/api/availability")
                        .param("type", "MEETING_ROOM")
                        .param("startAt", "2026-11-02T09:00:00")
                        .param("endAt", "2026-11-02T11:00:00")
                        .param("partySize", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("MR-02"))
                .andExpect(jsonPath("$[0].quote.total.amount").value(600_000));

        UUID roomId = resource("MR-02").getId();
        JsonNode booking = body(holdRequest(roomId, "2026-11-02T09:00:00", "2026-11-02T11:00:00", 8)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.totalAmount").value(600_000)));
        String bookingId = booking.get("id").asText();

        holdRequest(roomId, "2026-11-02T10:00:00", "2026-11-02T12:00:00", 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        String txn = initiatePayment(bookingId);

        mvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "forged")
                        .content("{\"gatewayTransactionId\":\"" + txn + "\",\"status\":\"SUCCESS\",\"amount\":600000}"))
                .andExpect(status().isUnauthorized());

        completeSandboxPayment(txn, 600_000).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        completeSandboxPayment(txn, 600_000).andExpect(status().isOk());
        assertStatus(bookingId, "CONFIRMED");

        clock.set(monday.withHour(8).withMinute(50));
        mvc.perform(post("/api/check-ins").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("qrToken", booking.get("qrToken").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        clock.set(monday.withHour(10).withMinute(50));
        mvc.perform(post("/api/bookings/{id}/extend", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEndAt\":\"2026-11-02T11:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(750_000))
                .andExpect(jsonPath("$.outstandingAmount").value(150_000));

        clock.set(monday.withHour(11).withMinute(50));
        mvc.perform(post("/api/check-outs").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("qrToken", booking.get("qrToken").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.outstandingAmount").value(300_000))
                .andExpect(jsonPath("$.charges[*].type").value(
                        org.hamcrest.Matchers.contains("RENTAL", "EXTENSION", "OVERSTAY_SURCHARGE")));

        mvc.perform(post("/api/bookings/{id}/settle", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":300000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void latePaymentAfterSlotWasTakenGoesToRefund() throws Exception {
        LocalDateTime tuesday = LocalDateTime.of(2026, 11, 3, 0, 0);
        clock.set(tuesday.withHour(8));
        UUID deskId = resource("HD-01").getId();

        JsonNode firstBooking = body(holdRequest(deskId, "2026-11-03T13:00:00", "2026-11-03T15:00:00", 1));
        String first = firstBooking.get("id").asText();
        String txn = initiatePayment(first);

        clock.set(tuesday.withHour(8).withMinute(11));
        bookingService.expireHold(UUID.fromString(first));
        assertStatus(first, "EXPIRED");

        JsonNode second = body(holdRequest(deskId, "2026-11-03T13:00:00", "2026-11-03T15:00:00", 1)
                .andExpect(status().isCreated()));
        assertThat(second.get("customerId")).as("cùng số điện thoại thì là cùng một khách")
                .isEqualTo(firstBooking.get("customerId"));

        completeSandboxPayment(txn, 100_000);
        assertStatus(first, "REFUND_PENDING");
    }

    @Test
    void latePaymentWhileSlotIsStillFreeReactivatesBooking() throws Exception {
        LocalDateTime wednesday = LocalDateTime.of(2026, 11, 4, 0, 0);
        clock.set(wednesday.withHour(8));
        UUID deskId = resource("HD-03").getId();

        String bookingId = body(holdRequest(deskId, "2026-11-04T13:00:00", "2026-11-04T15:00:00", 1)).get("id").asText();
        String txn = initiatePayment(bookingId);

        clock.set(wednesday.withHour(8).withMinute(11));
        bookingService.expireHold(UUID.fromString(bookingId));

        completeSandboxPayment(txn, 100_000);
        assertStatus(bookingId, "CONFIRMED");
    }

    @Test
    void cannotBookResourceUnderMaintenance() throws Exception {
        clock.set(LocalDateTime.of(2026, 11, 6, 8, 0));
        UUID boothId = resource("FB-02").getId();

        mvc.perform(post("/api/resources/{id}/maintenance", boothId)).andExpect(status().isOk());
        holdRequest(boothId, "2026-11-06T09:00:00", "2026-11-06T10:00:00", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_BOOKABLE"));

        mvc.perform(post("/api/resources/{id}/reactivate", boothId)).andExpect(status().isOk());
        holdRequest(boothId, "2026-11-06T09:00:00", "2026-11-06T10:00:00", 1).andExpect(status().isCreated());
    }

    private ResultActions holdRequest(UUID resourceId, String startAt, String endAt, int partySize) throws Exception {
        Map<String, Object> request = Map.of(
                "resourceId", resourceId,
                "startAt", startAt,
                "endAt", endAt,
                "partySize", partySize,
                "contact", Map.of("fullName", "Trần Thị B", "phone", "0987654321", "email", "b@example.com"));
        return mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)));
    }

    private String initiatePayment(String bookingId) throws Exception {
        return body(mvc.perform(post("/api/bookings/{id}/payments", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"VIETQR\"}"))
                .andExpect(status().isCreated()))
                .get("gatewayTransactionId").asText();
    }

    private ResultActions completeSandboxPayment(String txn, long amount) throws Exception {
        return mvc.perform(post("/api/payments/sandbox/{txn}/complete", txn)
                        .param("result", "SUCCESS")
                        .param("amount", String.valueOf(amount)))
                .andExpect(status().isOk());
    }

    private void assertStatus(String bookingId, String expected) throws Exception {
        JsonNode booking = body(mvc.perform(get("/api/bookings/{id}", bookingId)).andExpect(status().isOk()));
        assertThat(booking.get("status").asText()).isEqualTo(expected);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
