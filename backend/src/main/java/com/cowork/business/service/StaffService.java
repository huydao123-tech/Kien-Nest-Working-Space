package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.enums.StaffStatus;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.repository.StaffRepository;
import com.cowork.dto.Actor;
import com.cowork.dto.PeopleDtos.StaffRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** UC23: quản trị tài khoản nhân viên (chỉ ADMIN tạo / sửa / xóa; MANAGER được xem). */
@Service
@Transactional
public class StaffService {

    private final StaffRepository staff;
    private final BranchRepository branches;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public StaffService(StaffRepository staff, BranchRepository branches, PasswordEncoder passwordEncoder, Clock clock) {
        this.staff = staff;
        this.branches = branches;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Staff> list() {
        return staff.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Staff get(Long id) {
        return staff.findById(id).orElseThrow(() -> new NotFoundException("Nhân viên", id));
    }

    public Staff create(StaffRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new DomainException("PASSWORD_REQUIRED", "Phải đặt mật khẩu ban đầu cho nhân viên");
        }
        String email = request.email().trim();
        if (staff.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_USED", "Email đã được dùng cho nhân viên khác");
        }
        Staff member = new Staff();
        member.setCreatedAt(LocalDateTime.now(clock));
        member.setStatus(StaffStatus.ACTIVE);
        apply(member, request, email);
        return staff.save(member);
    }

    /** Không tự khóa hay tự đổi vai trò của chính mình (tránh hệ thống mất ADMIN cuối cùng do thao tác nhầm). */
    public Staff update(Long id, StaffRequest request, Actor actor) {
        Staff member = get(id);
        String email = request.email().trim();
        if (!email.equalsIgnoreCase(member.getEmail()) && staff.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_USED", "Email đã được dùng cho nhân viên khác");
        }
        boolean self = actor.isStaff() && id.equals(actor.id());
        if (self && (request.role() != member.getRole() || request.status() == StaffStatus.LOCKED)) {
            throw new DomainException("CANNOT_MODIFY_SELF", "Không thể tự đổi vai trò hoặc tự khóa tài khoản của mình");
        }
        apply(member, request, email);
        if (request.status() != null) {
            member.setStatus(request.status());
        }
        return staff.save(member);
    }

    /** Nhân viên đã gắn với dữ liệu khác (đơn gọi món, đền bù, nhật ký) thì không xóa được → 409 DATA_CONFLICT; hãy khóa thay vì xóa. */
    public void delete(Long id, Actor actor) {
        if (actor.isStaff() && id.equals(actor.id())) {
            throw new DomainException("CANNOT_MODIFY_SELF", "Không thể tự xóa tài khoản của mình");
        }
        staff.delete(get(id));
        staff.flush();
    }

    private void apply(Staff member, StaffRequest request, String email) {
        member.setFullName(request.fullName().trim());
        member.setEmail(email);
        member.setRole(request.role());
        member.setBranch(branchOf(request.branchId()));
        member.setCompensationLimit(request.compensationLimit());
        if (request.password() != null && !request.password().isBlank()) {
            member.setPasswordHash(passwordEncoder.encode(request.password()));
        }
    }

    private Branch branchOf(Long branchId) {
        if (branchId == null) {
            return null;
        }
        return branches.findById(branchId).orElseThrow(() -> new NotFoundException("Chi nhánh", branchId));
    }
}
