package com.cowork.dto;

import com.cowork.dataaccess.enums.StaffRole;

/**
 * Người đang gọi chức năng, lấy từ JWT ở tầng presentation rồi truyền xuống service (UC01, UC23).
 * Service dùng để kiểm tra quyền sở hữu dữ liệu (khách chỉ thấy đơn của mình), không cần biết HTTP hay JWT.
 *
 * @param id   id khách ({@code customer.id}) hoặc id nhân viên ({@code staff.id}); rỗng với khách vãng lai
 * @param role vai trò của nhân viên; rỗng với khách
 */
public record Actor(Type type, Long id, StaffRole role) {

    /** Tên role của khách có tài khoản trong JWT / Spring Security ({@code ROLE_CUSTOMER}). */
    public static final String CUSTOMER_ROLE = "CUSTOMER";

    /** Khách vãng lai: không gửi token. */
    public static final Actor GUEST = new Actor(Type.GUEST, null, null);

    public enum Type {
        GUEST,
        CUSTOMER,
        STAFF
    }

    public static Actor customer(Long customerId) {
        return new Actor(Type.CUSTOMER, customerId, null);
    }

    public static Actor staff(Long staffId, StaffRole role) {
        return new Actor(Type.STAFF, staffId, role);
    }

    public boolean isCustomer() {
        return type == Type.CUSTOMER;
    }

    public boolean isStaff() {
        return type == Type.STAFF;
    }

    /** Role ghi vào claim {@code roles} của JWT. */
    public String roleName() {
        return isStaff() ? role.name() : CUSTOMER_ROLE;
    }
}
