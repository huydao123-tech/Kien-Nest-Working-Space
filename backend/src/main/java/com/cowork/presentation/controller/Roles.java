package com.cowork.presentation.controller;

import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dto.Actor;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * UC23 — RBAC: biểu thức {@code @PreAuthorize} dùng chung cho các controller, và chuyển JWT thành {@link Actor}.
 * Bảng "vai trò nào được làm gì" đầy đủ: ticket-response/authentication.md mục 5.1.
 */
final class Roles {

    static final String ADMIN = "hasRole('ADMIN')";
    static final String ADMIN_MANAGER = "hasAnyRole('ADMIN','MANAGER')";
    /** Nhân viên quầy: lễ tân và cấp quản lý. */
    static final String FRONT_DESK = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')";
    static final String ALL_STAFF = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','TECHNICIAN')";
    /** Đổi trạng thái bảo trì của vị trí: kỹ thuật và cấp quản lý. */
    static final String MAINTENANCE = "hasAnyRole('ADMIN','MANAGER','TECHNICIAN')";
    static final String CUSTOMER = "hasRole('CUSTOMER')";
    /** Khách (chỉ đơn của mình — service kiểm tra) hoặc nhân viên quầy (mọi đơn). */
    static final String FRONT_DESK_OR_CUSTOMER = "hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','CUSTOMER')";

    private Roles() {
    }

    /** Không có token (khách vãng lai) → {@link Actor#GUEST}. Claim do {@code TokenService} ghi. */
    static Actor actorOf(Jwt jwt) {
        if (jwt == null) {
            return Actor.GUEST;
        }
        Long id = ((Number) jwt.getClaim("uid")).longValue();
        if (Actor.Type.STAFF.name().equals(jwt.getClaimAsString("type"))) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            return Actor.staff(id, StaffRole.valueOf(roles.get(0)));
        }
        return Actor.customer(id);
    }
}
