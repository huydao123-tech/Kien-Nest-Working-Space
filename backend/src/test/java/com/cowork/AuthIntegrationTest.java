package com.cowork;

import com.cowork.dataaccess.entity.Customer;
import com.cowork.dataaccess.entity.MembershipPass;
import com.cowork.dataaccess.entity.MembershipPlan;
import com.cowork.dataaccess.repository.CustomerRepository;
import com.cowork.dataaccess.repository.MembershipPassRepository;
import com.cowork.dataaccess.repository.MembershipPlanRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC01, UC23 qua REST: đăng ký SĐT + OTP / email, đăng nhập, JWT, 401 / 403, hồ sơ / mật khẩu (FR-AUTH-05). Ngày riêng: 10/11/2026, vị trí HD-04. */
class AuthIntegrationTest extends IntegrationTest {

    private static final LocalDateTime TUESDAY = LocalDateTime.of(2026, 11, 10, 8, 0);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CustomerRepository customers;

    @Autowired
    MembershipPlanRepository plans;

    @Autowired
    MembershipPassRepository passes;

    @Test
    void phoneRegistrationWithOtpClaimsEarlierGuestBookings() throws Exception {
        clock.set(TUESDAY);
        String phone = "0933000001";
        Map<String, Object> hold = Map.of("resourceId", resource("HD-04").getId(),
                "startAt", "2026-11-10T10:00:00", "endAt", "2026-11-10T12:00:00", "partySize", 1,
                "contact", Map.of("fullName", "Khách Vãng Lai", "phone", phone));
        String guestBookingId = body(postJson("/api/bookings", hold, null).andExpect(status().isCreated())).get("id").asText();

        postJson("/api/auth/otp", Map.of("phone", phone), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value("2026-11-10T08:05:00"));
        String token = bearer(postJson("/api/auth/register/phone",
                Map.of("phone", phone, "otp", TEST_OTP, "fullName", "Phan Văn Đăng", "password", "matkhau123"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.phoneVerified").value(true)));

        mvc.perform(get("/api/me/bookings").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(Long.valueOf(guestBookingId)))
                .andExpect(jsonPath("$.items[0].customerName").value("Phan Văn Đăng"));
        mvc.perform(get("/api/bookings/{id}", guestBookingId).header(AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CUSTOMER"))
                .andExpect(jsonPath("$.phone").value(phone));

        postJson("/api/auth/login", Map.of("login", phone, "password", "matkhau123"), null).andExpect(status().isOk());
        postJson("/api/auth/login", Map.of("login", phone, "password", "sai-mat-khau"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        postJson("/api/auth/register/phone",
                Map.of("phone", phone, "otp", TEST_OTP, "fullName", "Ai Đó", "password", "matkhau123"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    void emailRegistrationAndLogin() throws Exception {
        clock.set(TUESDAY);
        postJson("/api/auth/register/email",
                Map.of("email", "dang.ky@example.com", "phone", "0933000002", "fullName", "Email User", "password", "matkhau123"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.phoneVerified").value(false));

        postJson("/api/auth/login", Map.of("login", "DANG.KY@example.com", "password", "matkhau123"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("dang.ky@example.com"));
        postJson("/api/auth/register/email",
                Map.of("email", "dang.ky@example.com", "phone", "0933000099", "fullName", "Trùng Email", "password", "matkhau123"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
        postJson("/api/auth/register/email",
                Map.of("email", "ngan@example.com", "phone", "0933000098", "fullName", "Mật khẩu ngắn", "password", "123"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void otpRulesAreEnforcedThroughApi() throws Exception {
        clock.set(TUESDAY);
        String phone = "0933000003";
        postJson("/api/auth/otp", Map.of("phone", phone), null).andExpect(status().isOk());
        postJson("/api/auth/otp", Map.of("phone", phone), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OTP_RESEND_TOO_SOON"));

        Map<String, Object> wrongOtp = Map.of("phone", phone, "otp", "000000", "fullName", "OTP User", "password", "matkhau123");
        postJson("/api/auth/register/phone", wrongOtp, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));

        clock.set(TUESDAY.plusMinutes(5));
        postJson("/api/auth/register/phone",
                Map.of("phone", phone, "otp", TEST_OTP, "fullName", "OTP User", "password", "matkhau123"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void protectedApisNeedValidTokenAndRole() throws Exception {
        clock.set(TUESDAY);
        mvc.perform(get("/api/branches")).andExpect(status().isOk());
        mvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/bookings").header(AUTHORIZATION, "Bearer khong-phai-jwt"))
                .andExpect(status().isUnauthorized());

        String customer = bearer(postJson("/api/auth/register/email",
                Map.of("email", "rbac@example.com", "phone", "0933000004", "fullName", "RBAC", "password", "matkhau123"), null));
        mvc.perform(get("/api/bookings").header(AUTHORIZATION, customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/bookings").header(AUTHORIZATION, technicianAuth())).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings").header(AUTHORIZATION, receptionistAuth())).andExpect(status().isOk());
        mvc.perform(get("/api/me/bookings").header(AUTHORIZATION, receptionistAuth())).andExpect(status().isForbidden());

        String manager = managerAuth();
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
        clock.set(TUESDAY.plusHours(9)); // token hạn 8 giờ (+ 60 giây dung sai của bộ kiểm tra)
        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, manager))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockedOrWrongStaffCannotLogIn() throws Exception {
        clock.set(TUESDAY);
        postJson("/api/auth/staff/login", Map.of("email", "lan.hoang@cowork.example", "password", DEMO_PASSWORD), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        postJson("/api/auth/staff/login", Map.of("email", "admin@cowork.example", "password", "sai"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        postJson("/api/auth/staff/login", Map.of("email", "admin@cowork.example", "password", DEMO_PASSWORD), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    /** authentication.md Prompt 2 (FR-AUTH-05): sửa hồ sơ, xem số phút gói còn lại, đổi / quên mật khẩu. */
    @Test
    void memberManagesProfileAndPassword() throws Exception {
        clock.set(TUESDAY);
        String phone = "0933000005";
        String token = bearer(postJson("/api/auth/register/email",
                Map.of("email", "ho.so@example.com", "phone", phone, "fullName", "Hồ Sơ", "password", "matkhau123"), null));
        Customer customer = customers.findByPhone(phone).orElseThrow();
        MembershipPlan plan = plans.findAllByOrderByIdAsc().get(0);
        passes.save(new MembershipPass(customer, plan, 600, TUESDAY.minusDays(1), TUESDAY.plusDays(29)));
        passes.save(new MembershipPass(customer, plan, 300, TUESDAY.minusDays(40), TUESDAY.minusDays(10))); // đã hết hạn

        mvc.perform(get("/api/auth/me").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.remainingMinutes").value(600))
                .andExpect(jsonPath("$.membership.passes.length()").value(2))
                .andExpect(jsonPath("$.membership.passes[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.membership.passes[1].usable").value(true));

        mvc.perform(put("/api/auth/me").header(AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Hồ Sơ Mới", "email", "moi@example.com",
                                "notifyBeforeMinutes", 30, "preferredChannel", "EMAIL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Hồ Sơ Mới"))
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.notifyBeforeMinutes").value(30))
                .andExpect(jsonPath("$.preferredChannel").value("EMAIL"));
        mvc.perform(put("/api/auth/me").header(AUTHORIZATION, managerAuth()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Nhân viên"))))
                .andExpect(status().isForbidden());

        postJson("/api/auth/me/password", Map.of("currentPassword", "sai-mat-khau", "newPassword", "matkhaumoi1"), token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        postJson("/api/auth/me/password", Map.of("currentPassword", "matkhau123", "newPassword", "matkhaumoi1"), token)
                .andExpect(status().isNoContent());
        postJson("/api/auth/login", Map.of("login", "moi@example.com", "password", "matkhaumoi1"), null)
                .andExpect(status().isOk());

        // Quên mật khẩu: xin OTP rồi đặt lại, đăng nhập luôn; SĐT được đánh dấu đã xác thực.
        postJson("/api/auth/otp", Map.of("phone", phone), null).andExpect(status().isOk());
        postJson("/api/auth/password/reset", Map.of("phone", phone, "otp", TEST_OTP, "newPassword", "matkhaumoi2"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.phoneVerified").value(true));
        postJson("/api/auth/login", Map.of("login", phone, "password", "matkhaumoi2"), null).andExpect(status().isOk());
        postJson("/api/auth/password/reset", Map.of("phone", "0933000097", "otp", TEST_OTP, "newPassword", "matkhaumoi3"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private ResultActions postJson(String url, Object body, String auth) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (auth != null) {
            request.header(AUTHORIZATION, auth);
        }
        return mvc.perform(request);
    }

    private String bearer(ResultActions result) throws Exception {
        return "Bearer " + body(result).get("accessToken").asText();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
