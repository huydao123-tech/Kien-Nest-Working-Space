package com.cowork.business.service;

import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import com.cowork.dataaccess.entity.Branch;
import com.cowork.dataaccess.entity.Staff;
import com.cowork.dataaccess.enums.StaffRole;
import com.cowork.dataaccess.enums.StaffStatus;
import com.cowork.dataaccess.repository.BranchRepository;
import com.cowork.dataaccess.repository.StaffRepository;
import com.cowork.dto.Actor;
import com.cowork.dto.PeopleDtos.StaffRequest;
import com.cowork.support.MutableClock;
import com.cowork.support.TestCaseLogRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC23: quản trị tài khoản nhân viên. Viết theo {@code .claude/rules/unit-test.md}. Mật khẩu băm bằng BCrypt thật (cost 4). */
public class StaffServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static final String EMAIL = "lan@cowork.example";
    private static final String PASSWORD = "Cowork@123";
    private static final Long STAFF_ID = 5L;
    private static final Actor ADMIN = Actor.staff(1L, StaffRole.ADMIN);

    @Rule
    public final TestCaseLogRule log = new TestCaseLogRule();

    private StaffRepository staff;
    private BranchRepository branches;
    private PasswordEncoder encoder;
    private StaffService service;
    private Branch branch;

    @Before
    public void setUp() {
        MutableClock clock = new MutableClock(ZoneId.of("Asia/Ho_Chi_Minh"));
        clock.set(NOW);
        staff = mock(StaffRepository.class);
        branches = mock(BranchRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new StaffService(staff, branches, encoder, clock);
        branch = new Branch("HCM-Q1", "Cowork Nguyễn Huệ", "72 Nguyễn Huệ", LocalTime.of(7, 0), LocalTime.of(22, 0));
        branch.setId(1L);
        when(branches.findById(1L)).thenReturn(Optional.of(branch));
        when(staff.save(any(Staff.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- get ----------

    // Viewpoint: VP7
    @Test
    public void test_get_Found_001() {
        // Có nhân viên.
        Staff member = member(StaffRole.RECEPTIONIST);
        log.input("id = {}", STAFF_ID);

        Staff result = service.get(STAFF_ID);

        log.output("id = {}", result.getId());
        assertSame(member, result);
    }

    // Viewpoint: VP7
    @Test
    public void test_get_NotFound_002() {
        // Không có nhân viên.
        log.input("id = {}", 99L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.get(99L));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- create ----------

    // Viewpoint: VP3, VP7
    @Test
    public void test_create_WithBranch_003() {
        // Tạo nhân viên thuộc chi nhánh: ACTIVE, băm mật khẩu, cắt khoảng trắng email / tên.
        StaffRequest request = new StaffRequest("  Lan  ", "  " + EMAIL + " ", PASSWORD, StaffRole.RECEPTIONIST, 1L, 200_000, null);
        log.input("request = {}", request);

        Staff created = service.create(request);

        boolean passwordMatches = encoder.matches(PASSWORD, created.getPasswordHash());
        log.output("fullName = {}, email = {}, status = {}, branch = {}, passwordMatches = {}, createdAt = {}",
                created.getFullName(), created.getEmail(), created.getStatus(), created.getBranch().getId(), passwordMatches,
                created.getCreatedAt());
        assertEquals("Lan", created.getFullName());
        assertEquals(EMAIL, created.getEmail());
        assertEquals(StaffStatus.ACTIVE, created.getStatus());
        assertSame(branch, created.getBranch());
        assertTrue(passwordMatches);
        assertEquals(NOW, created.getCreatedAt());
    }

    // Viewpoint: VP7
    @Test
    public void test_create_WithoutBranch_004() {
        // Nhân viên không thuộc chi nhánh (ADMIN toàn hệ thống).
        StaffRequest request = new StaffRequest("Admin", EMAIL, PASSWORD, StaffRole.ADMIN, null, 0, null);
        log.input("request = {}", request);

        Staff created = service.create(request);

        log.output("branch = {}", created.getBranch());
        assertNull(created.getBranch());
    }

    // Viewpoint: VP2
    @Test
    public void test_create_BlankPassword_005() {
        // Mật khẩu chỉ có khoảng trắng.
        StaffRequest request = new StaffRequest("Lan", EMAIL, "   ", StaffRole.RECEPTIONIST, null, 0, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("PASSWORD_REQUIRED", thrown.code());
    }

    // Viewpoint: VP7
    @Test
    public void test_create_NullPassword_006() {
        // Mật khẩu null.
        StaffRequest request = new StaffRequest("Lan", EMAIL, null, StaffRole.RECEPTIONIST, null, 0, null);
        log.input("request = {}", request);

        DomainException thrown = assertThrows(DomainException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("PASSWORD_REQUIRED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_create_EmailUsed_007() {
        // Email đã có.
        when(staff.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);
        StaffRequest request = new StaffRequest("Lan", EMAIL, PASSWORD, StaffRole.RECEPTIONIST, null, 0, null);
        log.input("request = {}", request);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("EMAIL_ALREADY_USED", thrown.code());
        verify(staff, never()).save(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_create_BranchNotFound_008() {
        // Chi nhánh không tồn tại.
        StaffRequest request = new StaffRequest("Lan", EMAIL, PASSWORD, StaffRole.RECEPTIONIST, 99L, 0, null);
        log.input("request = {}", request);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.create(request));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- update ----------

    // Viewpoint: VP3
    @Test
    public void test_update_AdminChangesOtherRoleAndLocks_009() {
        // ADMIN đổi vai trò và khóa nhân viên khác; mật khẩu bỏ trống thì giữ nguyên.
        Staff member = member(StaffRole.RECEPTIONIST);
        String oldHash = member.getPasswordHash();
        StaffRequest request = new StaffRequest("Lan", EMAIL, null, StaffRole.MANAGER, 1L, 0, StaffStatus.LOCKED);
        log.input("id = {}, request = {}, actor = {}", STAFF_ID, request, ADMIN);

        Staff updated = service.update(STAFF_ID, request, ADMIN);

        log.output("role = {}, status = {}, passwordUnchanged = {}", updated.getRole(), updated.getStatus(),
                oldHash.equals(updated.getPasswordHash()));
        assertEquals(StaffRole.MANAGER, updated.getRole());
        assertEquals(StaffStatus.LOCKED, updated.getStatus());
        assertEquals(oldHash, updated.getPasswordHash());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_EmailCaseOnlyChange_010() {
        // Đổi hoa / thường của chính email mình: không coi là trùng.
        member(StaffRole.RECEPTIONIST);
        when(staff.existsByEmailIgnoreCase("LAN@cowork.example")).thenReturn(true);
        StaffRequest request = new StaffRequest("Lan", "LAN@cowork.example", null, StaffRole.RECEPTIONIST, null, 0, null);
        log.input("id = {}, request = {}, actor = {}", STAFF_ID, request, ADMIN);

        Staff updated = service.update(STAFF_ID, request, ADMIN);

        log.output("email = {}, status = {}", updated.getEmail(), updated.getStatus());
        assertEquals("LAN@cowork.example", updated.getEmail());
        assertEquals(StaffStatus.ACTIVE, updated.getStatus());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_EmailUsedByOther_011() {
        // Đổi sang email của nhân viên khác.
        member(StaffRole.RECEPTIONIST);
        when(staff.existsByEmailIgnoreCase("khac@cowork.example")).thenReturn(true);
        StaffRequest request = new StaffRequest("Lan", "khac@cowork.example", null, StaffRole.RECEPTIONIST, null, 0, null);
        log.input("id = {}, request = {}, actor = {}", STAFF_ID, request, ADMIN);

        ConflictException thrown = assertThrows(ConflictException.class, () -> service.update(STAFF_ID, request, ADMIN));

        log.output("code = {}", thrown.code());
        assertEquals("EMAIL_ALREADY_USED", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_SelfChangesRole_012() {
        // Tự đổi vai trò của mình.
        member(StaffRole.ADMIN);
        Actor self = Actor.staff(STAFF_ID, StaffRole.ADMIN);
        StaffRequest request = new StaffRequest("Lan", EMAIL, null, StaffRole.MANAGER, null, 0, null);
        log.input("id = {}, request = {}, actor = {}", STAFF_ID, request, self);

        DomainException thrown = assertThrows(DomainException.class, () -> service.update(STAFF_ID, request, self));

        log.output("code = {}", thrown.code());
        assertEquals("CANNOT_MODIFY_SELF", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_SelfLocks_013() {
        // Tự khóa tài khoản của mình.
        member(StaffRole.ADMIN);
        Actor self = Actor.staff(STAFF_ID, StaffRole.ADMIN);
        StaffRequest request = new StaffRequest("Lan", EMAIL, null, StaffRole.ADMIN, null, 0, StaffStatus.LOCKED);
        log.input("id = {}, request = {}, actor = {}", STAFF_ID, request, self);

        DomainException thrown = assertThrows(DomainException.class, () -> service.update(STAFF_ID, request, self));

        log.output("code = {}", thrown.code());
        assertEquals("CANNOT_MODIFY_SELF", thrown.code());
    }

    // Viewpoint: VP3
    @Test
    public void test_update_SelfChangesNameAndPassword_014() {
        // Tự sửa tên, đặt mật khẩu mới, giữ vai trò: hợp lệ.
        member(StaffRole.ADMIN);
        Actor self = Actor.staff(STAFF_ID, StaffRole.ADMIN);
        StaffRequest request = new StaffRequest("Lan Mới", EMAIL, "matkhaumoi1", StaffRole.ADMIN, null, 0, StaffStatus.ACTIVE);
        log.input("id = {}, request = {}, actor = {}", STAFF_ID, request, self);

        Staff updated = service.update(STAFF_ID, request, self);

        boolean passwordMatches = encoder.matches("matkhaumoi1", updated.getPasswordHash());
        log.output("fullName = {}, passwordMatches = {}", updated.getFullName(), passwordMatches);
        assertEquals("Lan Mới", updated.getFullName());
        assertTrue(passwordMatches);
    }

    // Viewpoint: VP7
    @Test
    public void test_update_NotFound_015() {
        // Không có nhân viên.
        StaffRequest request = new StaffRequest("Lan", EMAIL, null, StaffRole.RECEPTIONIST, null, 0, null);
        log.input("id = {}, request = {}, actor = {}", 99L, request, ADMIN);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.update(99L, request, ADMIN));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    // ---------- delete ----------

    // Viewpoint: VP3
    @Test
    public void test_delete_Other_016() {
        // Xóa nhân viên khác: xóa và flush.
        Staff member = member(StaffRole.RECEPTIONIST);
        log.input("id = {}, actor = {}", STAFF_ID, ADMIN);

        service.delete(STAFF_ID, ADMIN);

        log.output("deletedId = {}", member.getId());
        assertEquals(STAFF_ID, member.getId());
        verify(staff).delete(member);
        verify(staff).flush();
    }

    // Viewpoint: VP3
    @Test
    public void test_delete_Self_017() {
        // Tự xóa mình.
        member(StaffRole.ADMIN);
        Actor self = Actor.staff(STAFF_ID, StaffRole.ADMIN);
        log.input("id = {}, actor = {}", STAFF_ID, self);

        DomainException thrown = assertThrows(DomainException.class, () -> service.delete(STAFF_ID, self));

        log.output("code = {}", thrown.code());
        assertEquals("CANNOT_MODIFY_SELF", thrown.code());
        verify(staff, never()).delete(any());
    }

    // Viewpoint: VP7
    @Test
    public void test_delete_NotFound_018() {
        // Không có nhân viên.
        log.input("id = {}, actor = {}", 99L, ADMIN);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> service.delete(99L, ADMIN));

        log.output("code = {}", thrown.code());
        assertEquals("NOT_FOUND", thrown.code());
    }

    private Staff member(StaffRole role) {
        Staff member = new Staff(null, "Lan", EMAIL, encoder.encode(PASSWORD), role, 0);
        member.setId(STAFF_ID);
        when(staff.findById(STAFF_ID)).thenReturn(Optional.of(member));
        return member;
    }
}
