package com.cowork;

import com.cowork.business.service.BookingService;
import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.MembershipPass;
import com.cowork.dataaccess.entity.MembershipPlan;
import com.cowork.dataaccess.entity.Refund;
import com.cowork.dataaccess.entity.Voucher;
import com.cowork.dataaccess.enums.RefundStatus;
import com.cowork.dataaccess.repository.CustomerRepository;
import com.cowork.dataaccess.repository.MembershipPassRepository;
import com.cowork.dataaccess.repository.MembershipPlanRepository;
import com.cowork.dataaccess.repository.RefundRepository;
import com.cowork.dataaccess.repository.VoucherRepository;
import com.cowork.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ticket booking.md qua REST: add-on + tồn kho theo khung giờ, tổng tiền trong booking_charge (thuê + add-on − voucher
 * − membership), "đơn của tôi" / danh sách cho nhân viên, hủy và đổi chỗ (vị trí / khung giờ / thời lượng) trước ≥ 2 giờ.
 * Ngày riêng: 11/11 (MR-01, EV-01), 12/11 (MR-02), 13/11 (HD-05, FD-01, FD-02, FD-03), 16/11 (FD-02, FD-03 — đổi chỗ).
 */
class BookingExtrasIntegrationTest extends IntegrationTest {

    private static final LocalDateTime NOV_11 = LocalDateTime.of(2026, 11, 11, 7, 0);
    private static final LocalDateTime NOV_12 = LocalDateTime.of(2026, 11, 12, 7, 0);
    private static final LocalDateTime NOV_13 = LocalDateTime.of(2026, 11, 13, 7, 0);
    private static final LocalDateTime NOV_16 = LocalDateTime.of(2026, 11, 16, 7, 0);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    BookingService bookingService;

    @Autowired
    VoucherRepository vouchers;

    @Autowired
    RefundRepository refunds;

    @Autowired
    CustomerRepository customers;

    @Autowired
    MembershipPlanRepository plans;

    @Autowired
    MembershipPassRepository passes;

    @Test
    void addonStockIsCheckedPerTimeSlotAndChargedPerBlock() throws Exception {
        clock.set(NOV_11);
        Long projector = addonId("PROJECTOR"); // tồn 2 máy, 30.000đ / block
        Long room = resource("MR-01").getId();
        Long event = resource("EV-01").getId();

        String first = body(hold(guest(room, "2026-11-11T09:00:00", "2026-11-11T11:00:00", "0966000001",
                List.of(Map.of("addonId", projector, "quantity", 2))), null)
                .andExpect(status().isCreated())
                // 4 block × 150.000 + 2 máy × 30.000 × 4 block
                .andExpect(jsonPath("$.totalAmount").value(840_000))
                .andExpect(jsonPath("$.charges[*].type").value(contains("RENTAL", "ADDON")))
                .andExpect(jsonPath("$.addons[0].code").value("PROJECTOR"))
                .andExpect(jsonPath("$.addons[0].quantity").value(2))).get("id").asText();

        hold(guest(event, "2026-11-11T10:00:00", "2026-11-11T12:00:00", "0966000002",
                List.of(Map.of("addonId", projector, "quantity", 1))), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDON_OUT_OF_STOCK"));
        // [09:00, 11:00) và [11:00, 13:00) không giao nhau
        hold(guest(event, "2026-11-11T11:00:00", "2026-11-11T13:00:00", "0966000002",
                List.of(Map.of("addonId", projector, "quantity", 2))), null)
                .andExpect(status().isCreated());

        clock.set(NOV_11.plusMinutes(11));
        bookingService.expireHold(Long.valueOf(first));
        hold(guest(event, "2026-11-11T09:00:00", "2026-11-11T11:00:00", "0966000003",
                List.of(Map.of("addonId", projector, "quantity", 2))), null)
                .andExpect(status().isCreated());
    }

    @Test
    void voucherDiscountAndCancellationTwoHoursBeforeStart() throws Exception {
        clock.set(NOV_12);
        String token = registerByEmail("voucher.khach@example.com", "0966000010");
        Long room = resource("MR-02").getId();
        int usedBefore = voucher("HOP50K").getUsedCount();

        Map<String, Object> request = mine(room, "2026-11-12T09:00:00", "2026-11-12T11:00:00");
        request.put("voucherCode", "hop50k");
        JsonNode booking = body(hold(request, token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(550_000))
                .andExpect(jsonPath("$.charges[*].type").value(contains("RENTAL", "VOUCHER_DISCOUNT"))));
        String id = booking.get("id").asText();
        pay(id, 550_000);
        assertThat(voucher("HOP50K").getUsedCount()).isEqualTo(usedBefore + 1);

        // Đúng 07:00, giờ bắt đầu 09:00: còn đúng 2 giờ → được hủy, hoàn 100%
        mvc.perform(post("/api/bookings/{id}/cancel", id).header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refundAmount").value(550_000));
        assertThat(voucher("HOP50K").getUsedCount()).as("trả lượt voucher").isEqualTo(usedBefore);
        List<Refund> requested = refunds.findByPaymentBookingIdOrderByIdAsc(Long.valueOf(id));
        assertThat(requested).singleElement().satisfies(r -> {
            assertThat(r.getAmount()).isEqualTo(550_000);
            assertThat(r.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        });

        Map<String, Object> afternoon = mine(room, "2026-11-12T13:00:00", "2026-11-12T15:00:00");
        String second = body(hold(afternoon, token).andExpect(status().isCreated())).get("id").asText();
        pay(second, 600_000);
        clock.set(NOV_12.withHour(11).withMinute(1));
        mvc.perform(post("/api/bookings/{id}/cancel", second).header(AUTHORIZATION, token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHANGE_DEADLINE_PASSED"));
        mvc.perform(post("/api/bookings/{id}/cancel", second).header(AUTHORIZATION, technicianAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    void membershipCoveringWholeRentalConfirmsWithoutPayment() throws Exception {
        clock.set(NOV_13);
        String token = registerByEmail("member.khach@example.com", "0966000020");
        Customer customer = customers.findByPhone("0966000020").orElseThrow();
        MembershipPlan hotDeskPlan = plans.findAllByOrderByIdAsc().stream()
                .filter(p -> p.getName().equals("Gói Hot Desk 20 giờ")).findFirst().orElseThrow();
        MembershipPass pass = passes.save(new MembershipPass(customer, hotDeskPlan, 1200, NOV_13.minusDays(1), NOV_13.plusDays(29)));
        Long desk = resource("HD-05").getId();

        Map<String, Object> request = mine(desk, "2026-11-13T09:00:00", "2026-11-13T11:00:00");
        request.put("useMembership", true);
        String id = body(hold(request, token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.charges[*].type").value(contains("RENTAL", "MEMBERSHIP_DEDUCTION")))).get("id").asText();
        assertThat(passes.findById(pass.getId()).orElseThrow().getRemainingMinutes()).isEqualTo(1080);

        Map<String, Object> both = mine(desk, "2026-11-13T13:00:00", "2026-11-13T15:00:00");
        both.put("useMembership", true);
        both.put("voucherCode", "WELCOME10");
        hold(both, token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_MEMBERSHIP_CONFLICT"));
        Map<String, Object> guest = guest(desk, "2026-11-13T13:00:00", "2026-11-13T15:00:00", "0966000021", List.of());
        guest.put("useMembership", true);
        hold(guest, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_LOGIN_REQUIRED"));

        mvc.perform(post("/api/bookings/{id}/cancel", id).header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount").value(0));
        assertThat(passes.findById(pass.getId()).orElseThrow().getRemainingMinutes()).as("trả lại phút").isEqualTo(1200);
    }

    @Test
    void customersSeeOnlyTheirBookingsAndStaffCanFilterByCustomer() throws Exception {
        clock.set(NOV_13);
        String alice = registerByEmail("alice@example.com", "0966000030");
        String bob = registerByEmail("bob@example.com", "0966000031");
        String aliceBooking = body(hold(mine(resource("FD-01").getId(), "2026-11-13T13:00:00", "2026-11-13T15:00:00"), alice)
                .andExpect(status().isCreated())).get("id").asText();
        String bobBooking = body(hold(mine(resource("FD-02").getId(), "2026-11-13T13:00:00", "2026-11-13T15:00:00"), bob)
                .andExpect(status().isCreated())).get("id").asText();
        String aliceId = body(mvc.perform(get("/api/auth/me").header(AUTHORIZATION, alice))).get("id").asText();

        mvc.perform(get("/api/me/bookings").header(AUTHORIZATION, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].customerId").value(everyItem(is(Integer.valueOf(aliceId)))))
                .andExpect(jsonPath("$.items[*].id").value(hasItem(Integer.valueOf(aliceBooking))))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem(Integer.valueOf(bobBooking)))));
        mvc.perform(get("/api/bookings/{id}", bobBooking).header(AUTHORIZATION, alice))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/{id}/cancel", bobBooking).header(AUTHORIZATION, alice))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/bookings").param("phone", "0966000031").header(AUTHORIZATION, receptionistAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(Integer.valueOf(bobBooking)))
                .andExpect(jsonPath("$.items[0].resourceCode").value("FD-02"));
        mvc.perform(get("/api/bookings").param("customerId", aliceId).param("status", "HELD")
                        .header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/bookings").param("from", "2026-11-13T00:00:00").param("to", "2026-11-14T00:00:00")
                        .header(AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(hasItem(Integer.valueOf(aliceBooking))));
        mvc.perform(get("/api/bookings").param("size", "0").header(AUTHORIZATION, receptionistAuth()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE"));
    }

    @Test
    void rescheduleKeepsDurationAndRefundsCheaperSlot() throws Exception {
        clock.set(NOV_13);
        String token = registerByEmail("doi.gio@example.com", "0966000040");
        String id = body(hold(mine(resource("FD-03").getId(), "2026-11-13T10:00:00", "2026-11-13T12:00:00"), token)
                .andExpect(jsonPath("$.totalAmount").value(140_000))).get("id").asText(); // cao điểm 4 × 35.000
        pay(id, 140_000);

        reschedule(id, "2026-11-13T18:00:00", "2026-11-13T20:00:00", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAmount").value(40_000)) // thấp điểm 4 × 25.000 = 100.000
                .andExpect(jsonPath("$.booking.startAt").value("2026-11-13T18:00:00"))
                .andExpect(jsonPath("$.booking.totalAmount").value(100_000))
                .andExpect(jsonPath("$.booking.paidAmount").value(100_000))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        clock.set(NOV_13.withHour(16).withMinute(1)); // giờ bắt đầu 18:00 → hạn chót 16:00
        reschedule(id, "2026-11-13T19:00:00", "2026-11-13T21:00:00", receptionistAuth())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHANGE_DEADLINE_PASSED"));
    }

    /** booking.md Prompt 2: đổi sang vị trí khác, dài hơn, đắt hơn → giữ chỗ mới chờ trả phần chênh. Ngày riêng 16/11. */
    @Test
    void rescheduleToPricierSlotHoldsNewSlotUntilTopUpPaid() throws Exception {
        clock.set(NOV_16);
        String token = registerByEmail("doi.cho@example.com", "0966000041");
        Long fd03 = resource("FD-03").getId();
        String id = body(hold(mine(resource("FD-02").getId(), "2026-11-16T18:00:00", "2026-11-16T20:00:00"), token)
                .andExpect(jsonPath("$.totalAmount").value(100_000))).get("id").asText(); // thấp điểm 4 × 25.000
        pay(id, 100_000);

        // FD-03, 10:00–13:00 cao điểm 6 × 35.000 = 210.000 → bù 110.000; đơn chưa đổi.
        reschedule(id, fd03, "2026-11-16T10:00:00", "2026-11-16T13:00:00", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentRequired").value(true))
                .andExpect(jsonPath("$.amountDue").value(110_000))
                .andExpect(jsonPath("$.booking.startAt").value("2026-11-16T18:00:00"))
                .andExpect(jsonPath("$.booking.pendingChange.resourceId").value(fd03))
                .andExpect(jsonPath("$.booking.pendingChange.expiresAt").value("2026-11-16T07:10:00"));
        // Chỗ mới đang được giữ: người khác không đặt được, yêu cầu đổi thứ hai bị chặn.
        hold(guest(fd03, "2026-11-16T11:00:00", "2026-11-16T12:00:00", "0966000042", List.of()), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
        reschedule(id, null, "2026-11-16T19:00:00", "2026-11-16T20:00:00", token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESCHEDULE_PAYMENT_PENDING"));

        pay(id, 110_000);
        mvc.perform(get("/api/bookings/{id}", id).header(AUTHORIZATION, token))
                .andExpect(jsonPath("$.resourceId").value(fd03))
                .andExpect(jsonPath("$.startAt").value("2026-11-16T10:00:00"))
                .andExpect(jsonPath("$.endAt").value("2026-11-16T13:00:00"))
                .andExpect(jsonPath("$.totalAmount").value(210_000))
                .andExpect(jsonPath("$.paidAmount").value(210_000))
                .andExpect(jsonPath("$.pendingChange").doesNotExist());

        // Yêu cầu đắt hơn nữa (13:00–17:00 = 280.000) nhưng không trả: quá 10 phút thì hủy, đơn giữ chỗ đã đổi.
        reschedule(id, null, "2026-11-16T13:00:00", "2026-11-16T17:00:00", token)
                .andExpect(jsonPath("$.amountDue").value(70_000));
        clock.set(NOV_16.plusMinutes(10));
        bookingService.expirePendingChange(Long.valueOf(id));
        mvc.perform(get("/api/bookings/{id}", id).header(AUTHORIZATION, token))
                .andExpect(jsonPath("$.startAt").value("2026-11-16T10:00:00"))
                .andExpect(jsonPath("$.pendingChange").doesNotExist());
    }

    // ---------- Hàm phụ ----------

    private Map<String, Object> guest(Long resourceId, String startAt, String endAt, String phone, List<?> addons) {
        Map<String, Object> request = mine(resourceId, startAt, endAt);
        request.put("contact", Map.of("fullName", "Khách " + phone, "phone", phone));
        request.put("addons", addons);
        return request;
    }

    /** Đặt chỗ của khách đã đăng nhập: không cần thông tin liên hệ. */
    private static Map<String, Object> mine(Long resourceId, String startAt, String endAt) {
        Map<String, Object> request = new HashMap<>();
        request.put("resourceId", resourceId);
        request.put("startAt", startAt);
        request.put("endAt", endAt);
        request.put("partySize", 1);
        return request;
    }

    private ResultActions hold(Map<String, Object> request, String auth) throws Exception {
        var builder = post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request));
        if (auth != null) {
            builder.header(AUTHORIZATION, auth);
        }
        return mvc.perform(builder);
    }

    private ResultActions reschedule(String id, String startAt, String endAt, String auth) throws Exception {
        return reschedule(id, null, startAt, endAt, auth);
    }

    /** {@code resourceId} rỗng = giữ vị trí hiện tại. */
    private ResultActions reschedule(String id, Long resourceId, String startAt, String endAt, String auth) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("resourceId", resourceId);
        body.put("startAt", startAt);
        body.put("endAt", endAt);
        return mvc.perform(post("/api/bookings/{id}/reschedule", id).header(AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private void pay(String bookingId, long amount) throws Exception {
        String txn = body(mvc.perform(post("/api/bookings/{id}/payments", bookingId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"VIETQR\"}"))
                .andExpect(status().isCreated())).get("gatewayTransactionId").asText();
        mvc.perform(post("/api/payments/sandbox/{txn}/complete", txn).param("amount", String.valueOf(amount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    private String registerByEmail(String email, String phone) throws Exception {
        JsonNode response = body(mvc.perform(post("/api/auth/register/email").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "phone", phone, "fullName", "Khách " + phone,
                                "password", "matkhau123"))))
                .andExpect(status().isCreated()));
        return "Bearer " + response.get("accessToken").asText();
    }

    private Long addonId(String code) throws Exception {
        String branchId = null;
        for (JsonNode branch : body(mvc.perform(get("/api/branches")))) {
            if ("HCM-Q1".equals(branch.get("code").asText())) {
                branchId = branch.get("id").asText();
            }
        }
        for (JsonNode addon : body(mvc.perform(get("/api/addons").param("branchId", branchId)))) {
            if (code.equals(addon.get("code").asText())) {
                return addon.get("id").asLong();
            }
        }
        throw new IllegalStateException("Thiếu add-on mẫu " + code);
    }

    private Voucher voucher(String code) {
        return vouchers.findAll().stream().filter(v -> v.getCode().equals(code)).findFirst().orElseThrow();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
