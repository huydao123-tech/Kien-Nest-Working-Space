package com.cowork;

import com.cowork.business.service.BookingService;
import com.cowork.dataaccess.enums.NotificationType;
import com.cowork.dataaccess.repository.NotificationJobRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ticket notification.md qua REST + job (gọi thẳng hàm service mà job gọi, vì job tắt trong test): no-show (BR-16),
 * cảnh báo sắp hết giờ (FR-NOTI-03) và quá giờ (FR-SYS-02), xác nhận hoàn tiền xong (FR-NOTI-04), bảo trì vị trí (FR-OPS-07).
 * Ngày riêng: 17/11 (HD-01, HD-02, HD-03), 18/11 (HD-04 — bảo trì, mở lại cuối test).
 */
class NotificationIntegrationTest extends IntegrationTest {

    private static final LocalDateTime NOV_17 = LocalDateTime.of(2026, 11, 17, 7, 0);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    BookingService bookingService;

    @Autowired
    NotificationJobRepository notificationJobs;

    @Test
    void noShowReleasesSlotAfterThirtyMinutes() throws Exception {
        clock.set(NOV_17);
        Long desk = resource("HD-01").getId();
        Long id = paidBooking(desk, "2026-11-17T09:00:00", "2026-11-17T11:00:00", "0977000101").get("id").asLong();

        // Đúng 09:30 (hết 30 phút chờ) chưa đánh dấu; 09:30:01 thì NO_SHOW
        clock.set(NOV_17.withHour(9).withMinute(30));
        assertThat(bookingService.findNoShowIds()).contains(id);
        bookingService.markNoShow(id);
        bookingStatus(id).andExpect(jsonPath("$.status").value("CONFIRMED"));

        clock.set(NOV_17.withHour(9).withMinute(30).withSecond(1));
        bookingService.markNoShow(id);
        bookingStatus(id).andExpect(jsonPath("$.status").value("NO_SHOW"));
        assertThat(bookingService.findNoShowIds()).doesNotContain(id);

        // Chỗ được giải phóng: khách khác đặt được phần còn lại
        hold(desk, "2026-11-17T10:00:00", "2026-11-17T11:00:00", "0977000102").andExpect(status().isCreated());
    }

    @Test
    void endWarningSentOnceThenOverstayFlagged() throws Exception {
        clock.set(NOV_17);
        Long desk = resource("HD-02").getId();
        JsonNode booking = paidBooking(desk, "2026-11-17T13:00:00", "2026-11-17T15:00:00", "0977000201");
        Long id = booking.get("id").asLong();
        clock.set(NOV_17.withHour(13));
        mvc.perform(post("/api/check-ins").header(AUTHORIZATION, receptionistAuth()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("qrToken", booking.get("qrToken").asText()))))
                .andExpect(status().isOk());

        // 14:44:59 chưa tới mốc 15 phút; 14:45 gửi; lượt quét sau không gửi lại
        clock.set(NOV_17.withHour(14).withMinute(44).withSecond(59));
        bookingService.warnEndingSoon(id);
        assertThat(endWarnings(id)).isZero();
        clock.set(NOV_17.withHour(14).withMinute(45));
        assertThat(bookingService.findEndingSoonIds()).contains(id);
        bookingService.warnEndingSoon(id);
        bookingService.warnEndingSoon(id);
        assertThat(endWarnings(id)).isEqualTo(1);

        // Quá 5 phút ân hạn → OVERSTAY (báo lễ tân chi nhánh, không lỗi khi nhân viên không có SĐT)
        clock.set(NOV_17.withHour(15).withMinute(5).withSecond(1));
        assertThat(bookingService.findOverdueIds()).contains(id);
        bookingService.flagOverstay(id);
        bookingStatus(id).andExpect(jsonPath("$.status").value("OVERSTAY"));
        assertThat(bookingService.findEndingSoonIds()).doesNotContain(id);
    }

    @Test
    void managerCompletesRefundOnce() throws Exception {
        clock.set(NOV_17);
        Long desk = resource("HD-03").getId();
        Long id = paidBooking(desk, "2026-11-17T17:00:00", "2026-11-17T19:00:00", "0977000301").get("id").asLong();
        mvc.perform(post("/api/bookings/{id}/cancel", id).header(AUTHORIZATION, receptionistAuth()))
                .andExpect(status().isOk());

        JsonNode refunds = body(mvc.perform(get("/api/refunds").header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].bookingId", hasItem(id.intValue()))));
        Long refundId = null;
        for (JsonNode refund : refunds) {
            if (refund.get("bookingId").asLong() == id) {
                refundId = refund.get("id").asLong();
            }
        }

        mvc.perform(post("/api/refunds/{id}/complete", refundId)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/refunds/{id}/complete", refundId).header(AUTHORIZATION, receptionistAuth()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/refunds/{id}/complete", 999_999).header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/refunds/{id}/complete", refundId).header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.completedAt").value("2026-11-17T07:00:00"));
        mvc.perform(post("/api/refunds/{id}/complete", refundId).header(AUTHORIZATION, adminAuth()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REFUND_STATE"));
        mvc.perform(get("/api/refunds").param("status", "REFUNDED").header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(refundId.intValue())));
        mvc.perform(get("/api/refunds").param("status", "DONE").header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void maintenanceTwiceKeepsStatus() throws Exception {
        clock.set(NOV_17);
        Long desk = resource("HD-04").getId();
        paidBooking(desk, "2026-11-18T09:00:00", "2026-11-18T11:00:00", "0977000401");
        try {
            mvc.perform(post("/api/resources/{id}/maintenance", desk).header(AUTHORIZATION, technicianAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("MAINTENANCE"));
            mvc.perform(post("/api/resources/{id}/maintenance", desk).header(AUTHORIZATION, technicianAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("MAINTENANCE"));
        } finally {
            mvc.perform(post("/api/resources/{id}/reactivate", desk).header(AUTHORIZATION, technicianAuth()))
                    .andExpect(status().isOk());
        }
    }

    // ---------- Hàm phụ ----------

    /** Khách vãng lai giữ chỗ rồi trả đủ qua cổng sandbox; trả về đơn lúc giữ chỗ (có id, qrToken). */
    private JsonNode paidBooking(Long resourceId, String startAt, String endAt, String phone) throws Exception {
        JsonNode booking = body(hold(resourceId, startAt, endAt, phone).andExpect(status().isCreated()));
        String txn = body(mvc.perform(post("/api/bookings/{id}/payments", booking.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"VIETQR\"}"))
                .andExpect(status().isCreated())).get("gatewayTransactionId").asText();
        mvc.perform(post("/api/payments/sandbox/{txn}/complete", txn).param("amount", booking.get("totalAmount").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        return booking;
    }

    private ResultActions hold(Long resourceId, String startAt, String endAt, String phone) throws Exception {
        Map<String, Object> request = Map.of("resourceId", resourceId, "startAt", startAt, "endAt", endAt, "partySize", 1,
                "contact", Map.of("fullName", "Khách " + phone, "phone", phone));
        return mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }

    private ResultActions bookingStatus(Long id) throws Exception {
        return mvc.perform(get("/api/bookings/{id}", id).header(AUTHORIZATION, receptionistAuth())).andExpect(status().isOk());
    }

    private long endWarnings(Long bookingId) {
        return notificationJobs.findAll().stream()
                .filter(j -> j.getType() == NotificationType.END_WARNING && j.getBooking().getId().equals(bookingId))
                .count();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
