package com.cowork;

import com.cowork.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC19–UC21, UC23: CRUD bảng danh mục và phân quyền theo vai trò. Không đặt chỗ nên không dùng ngày / vị trí nào;
 * dữ liệu tạo mới có mã riêng (DN-01, HD-99, TEST50...) và không gắn vào chi nhánh mẫu để không ảnh hưởng test tìm chỗ.
 */
class AdminCrudIntegrationTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void setUp() {
        clock.set(LocalDateTime.of(2026, 11, 3, 8, 0));
    }

    @Test
    void adminCreatesBranchManagerUpdatesStaffReads() throws Exception {
        Map<String, Object> branch = Map.of("code", "DN-01", "name", "Cowork Đà Nẵng", "address", "1 Bạch Đằng",
                "openTime", "08:00:00", "closeTime", "20:00:00");
        send(post("/api/admin/branches"), branch, managerAuth()).andExpect(status().isForbidden());
        String id = body(send(post("/api/admin/branches"), branch, adminAuth())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))).get("id").asText();
        send(post("/api/admin/branches"), branch, adminAuth())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_ALREADY_EXISTS"));

        Map<String, Object> renamed = new HashMap<>(branch);
        renamed.put("name", "Cowork Sông Hàn");
        send(put("/api/admin/branches/" + id), renamed, managerAuth())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cowork Sông Hàn"));
        renamed.put("closeTime", "07:00:00");
        send(put("/api/admin/branches/" + id), renamed, managerAuth())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
        mvc.perform(get("/api/admin/branches/" + id).header(AUTHORIZATION, technicianAuth())).andExpect(status().isOk());

        mvc.perform(delete("/api/admin/branches/" + id).header(AUTHORIZATION, adminAuth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/branches/" + id).header(AUTHORIZATION, adminAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void cannotDeleteBranchThatStillHasZones() throws Exception {
        String hcm = hcmBranchId();

        mvc.perform(delete("/api/admin/branches/" + hcm).header(AUTHORIZATION, adminAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }

    @Test
    void managerManagesResourceAndItsAmenities() throws Exception {
        Map<String, Object> resource = new HashMap<>(Map.of("code", "HD-99", "name", "Hot Desk thử", "type", "HOT_DESK",
                "capacity", 1, "amenities", List.of(Map.of("code", "power_outlet", "quantity", 2))));
        send(post("/api/admin/resources"), resource, receptionistAuth()).andExpect(status().isForbidden());
        String id = body(send(post("/api/admin/resources"), resource, managerAuth())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.amenities[0].code").value("POWER_OUTLET"))).get("id").asText();

        resource.put("amenities", List.of(Map.of("code", "MONITOR", "quantity", 1), Map.of("code", "POWER_OUTLET", "quantity", 4)));
        send(put("/api/admin/resources/" + id), resource, managerAuth())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amenities.length()").value(2))
                .andExpect(jsonPath("$.amenities[1].quantity").value(4));
        resource.put("amenities", List.of(Map.of("code", "SAUNA", "quantity", 1)));
        send(put("/api/admin/resources/" + id), resource, managerAuth())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_AMENITY"));
        resource.put("type", "SAUNA");
        send(put("/api/admin/resources/" + id), resource, managerAuth())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(delete("/api/admin/resources/" + id).header(AUTHORIZATION, managerAuth())).andExpect(status().isNoContent());
    }

    @Test
    void overlappingPriceRuleIsRejectedAndTechnicianCannotSeePrices() throws Exception {
        send(post("/api/admin/price-rules"), Map.of("type", "HOT_DESK", "dayType", "WEEKDAY", "startMinute", 600,
                "endMinute", 700, "pricePerBlock", 1, "label", "Trùng cao điểm"), managerAuth())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_RULE_OVERLAP"));
        mvc.perform(get("/api/admin/price-rules").header(AUTHORIZATION, receptionistAuth())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/price-rules").header(AUTHORIZATION, technicianAuth())).andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminManagesStaffAccounts() throws Exception {
        Map<String, Object> staff = new HashMap<>(Map.of("fullName", "Lễ Tân Mới", "email", "le.tan.moi@cowork.example",
                "password", "matkhau123", "role", "RECEPTIONIST", "compensationLimit", 50_000));
        send(post("/api/admin/staff"), staff, managerAuth()).andExpect(status().isForbidden());
        String id = body(send(post("/api/admin/staff"), staff, adminAuth())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))).get("id").asText();
        send(post("/api/admin/staff"), staff, adminAuth())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
        mvc.perform(get("/api/admin/staff").header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email").value(hasItem("le.tan.moi@cowork.example")));

        send(post("/api/auth/staff/login"), Map.of("email", "le.tan.moi@cowork.example", "password", "matkhau123"), null)
                .andExpect(status().isOk());
        staff.remove("password");
        staff.put("status", "LOCKED");
        send(put("/api/admin/staff/" + id), staff, adminAuth()).andExpect(status().isOk());
        send(post("/api/auth/staff/login"), Map.of("email", "le.tan.moi@cowork.example", "password", "matkhau123"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        staff.put("role", "BOSS");
        send(put("/api/admin/staff/" + id), staff, adminAuth())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String adminId = body(mvc.perform(get("/api/auth/me").header(AUTHORIZATION, adminAuth()))).get("id").asText();
        Map<String, Object> lockSelf = Map.of("fullName", "Quản trị hệ thống", "email", "admin@cowork.example",
                "role", "ADMIN", "compensationLimit", 0, "status", "LOCKED");
        send(put("/api/admin/staff/" + adminId), lockSelf, adminAuth())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));

        mvc.perform(delete("/api/admin/staff/" + id).header(AUTHORIZATION, adminAuth())).andExpect(status().isNoContent());
    }

    @Test
    void vouchersAndMembershipPlansAreManagedByManagers() throws Exception {
        Map<String, Object> voucher = new HashMap<>(Map.of("code", "test50", "name", "Giảm 50%", "discountType", "PERCENT",
                "discountValue", 150, "minOrderAmount", 0, "validFrom", "2026-11-01T00:00:00", "validTo", "2026-12-01T00:00:00",
                "perCustomerLimit", 1));
        send(post("/api/admin/vouchers"), voucher, managerAuth())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DISCOUNT"));
        voucher.put("discountValue", 50);
        voucher.put("resourceTypes", List.of("MEETING_ROOM"));
        send(post("/api/admin/vouchers"), voucher, managerAuth())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TEST50"))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.resourceTypes[0]").value("MEETING_ROOM"));
        mvc.perform(get("/api/admin/vouchers").header(AUTHORIZATION, receptionistAuth())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/vouchers").header(AUTHORIZATION, technicianAuth())).andExpect(status().isForbidden());

        Map<String, Object> plan = new HashMap<>(Map.of("name", "Gói thử nghiệm", "totalMinutes", 600, "validDays", 14,
                "price", 400_000, "resourceTypes", List.of("HOT_DESK")));
        String planId = body(send(post("/api/admin/membership-plans"), plan, managerAuth())
                .andExpect(status().isCreated())).get("id").asText();
        plan.put("resourceTypes", List.of("HOT_DESK", "FIXED_DESK"));
        send(put("/api/admin/membership-plans/" + planId), plan, managerAuth())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceTypes.length()").value(2));
        mvc.perform(delete("/api/admin/membership-plans/" + planId).header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isNoContent());
    }

    @Test
    void addonsAndMenuAreManagedByManagers() throws Exception {
        String branchId = hcmBranchId();
        String addonId = body(send(post("/api/admin/addons"), Map.of("branchId", branchId, "code", "speaker",
                "name", "Loa di động", "stock", 3, "pricePerBlock", 20_000), managerAuth())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SPEAKER"))).get("id").asText();
        mvc.perform(get("/api/addons").param("branchId", branchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem("SPEAKER")));
        mvc.perform(delete("/api/admin/addons/" + addonId).header(AUTHORIZATION, managerAuth())).andExpect(status().isNoContent());

        String categoryId = body(send(post("/api/admin/menu-categories"), Map.of("name", "Trà thử", "sortOrder", 9), managerAuth())
                .andExpect(status().isCreated())).get("id").asText();
        send(post("/api/admin/menu-items"), Map.of("branchId", branchId, "categoryId", categoryId, "name", "Trà đào",
                "price", 35_000, "available", true, "drink", true), managerAuth())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.drink").value(true));
        mvc.perform(delete("/api/admin/menu-categories/" + categoryId).header(AUTHORIZATION, managerAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }

    @Test
    void receptionistManagesCustomersButOnlyAdminDeletes() throws Exception {
        Map<String, Object> customer = new HashMap<>(Map.of("fullName", "Khách Quầy", "phone", "0944000001"));
        String id = body(send(post("/api/admin/customers"), customer, receptionistAuth())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasAccount").value(false))
                .andExpect(jsonPath("$.notifyBeforeMinutes").value(15))).get("id").asText();
        send(post("/api/admin/customers"), customer, receptionistAuth())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_USED"));
        customer.put("notifyBeforeMinutes", 20);
        send(put("/api/admin/customers/" + id), customer, receptionistAuth())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_NOTIFY_BEFORE"));
        mvc.perform(get("/api/admin/customers").header(AUTHORIZATION, technicianAuth())).andExpect(status().isForbidden());

        mvc.perform(delete("/api/admin/customers/" + id).header(AUTHORIZATION, receptionistAuth())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/customers/" + id).header(AUTHORIZATION, adminAuth())).andExpect(status().isNoContent());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body, String auth) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (auth != null) {
            request.header(AUTHORIZATION, auth);
        }
        return mvc.perform(request);
    }

    private String hcmBranchId() throws Exception {
        for (JsonNode branch : body(mvc.perform(get("/api/branches")))) {
            if ("HCM-Q1".equals(branch.get("code").asText())) {
                return branch.get("id").asText();
            }
        }
        throw new IllegalStateException("Thiếu chi nhánh mẫu HCM-Q1");
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
