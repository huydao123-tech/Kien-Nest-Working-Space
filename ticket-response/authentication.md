# SPEC: Đăng ký, đăng nhập (JWT) và phân quyền RBAC + CRUD bảng danh mục

| Mục | Nội dung |
|---|---|
| Loại | Tính năng mới |
| Liên kết | ticket-request/authentication.md Prompt 1, Prompt 2 · UC01, UC23, UC19, UC20, UC21 · FR-AUTH-01..05, FR-ADM-04..07 |
| Người viết | huydao123-tech (code và spec do AI viết) |
| Ngày | 2026-11-03 (Prompt 1), 2026-11-08 (Prompt 2) |

---

## 1. Mục tiêu (Goal)

- Khách đăng ký tài khoản bằng **SĐT + OTP** hoặc **email + mật khẩu**, đăng nhập bằng SĐT/email + mật khẩu hoặc SĐT + OTP. Đăng ký bằng SĐT đã xác thực thì nhận lại toàn bộ đơn đã đặt khi còn là khách vãng lai (FR-AUTH-03).
- Nhân viên (ADMIN, MANAGER, RECEPTIONIST, TECHNICIAN) đăng nhập bằng tài khoản nội bộ. Mọi API (trừ tra cứu, đặt chỗ vãng lai, thanh toán, webhook) yêu cầu JWT; mỗi API chỉ mở cho các vai trò ở mục 5.1.
- Có API CRUD cho các bảng danh mục: chi nhánh, khu vực, loại chỗ, vị trí (+ tiện ích của vị trí), tiện ích, bảng giá, add-on, nhóm món, món, voucher, gói thành viên, nhân viên, khách hàng.

Đã xử lý: **ticket-request/authentication.md — Prompt 1** (2026-11-03) và **Prompt 2** (2026-11-08).

**Prompt 2 (FR-AUTH-05):** khách có tài khoản tự **sửa hồ sơ** (họ tên, email, nhắc trước, kênh nhận tin), **đổi mật khẩu** (phải nhập đúng mật khẩu hiện tại), **quên mật khẩu** (đặt lại bằng OTP gửi tới SĐT của tài khoản) và thấy **số giờ gói thành viên còn lại** ngay trên trang hồ sơ (`GET /api/auth/me`).

Câu đã hỏi lại người dùng ở Prompt 2: "Quên mật khẩu xác thực bằng gì?" → người dùng chọn **OTP qua SĐT** (dùng lại `POST /api/auth/otp` có sẵn; hệ thống chưa có kênh gửi email thật).

Cập nhật 2026-11-08, yêu cầu qua chat cùng lượt (không có prompt): "viết unit test cho tất cả các file service và chạy". Đã viết unit test cho **cả 19 file** trong `business/service/` (17 file test mới + bổ sung `AuthServiceTest`, `BookingServiceTest`), xem mục 7.

Cập nhật 2026-11-05, yêu cầu qua chat (không có prompt trong `ticket-request/`, AI không được sửa file request): xóa toàn bộ unit test đang có và viết lại `AuthServiceTest` theo `.claude/rules/unit-test.md`. Người dùng làm rõ trong chat: "ý tao là xóa hết mấy cái đang có". Đã xóa 9 file trong `src/test/.../business/service/` (101 test), giữ nguyên integration test và `support/`. Sau đó người dùng yêu cầu thêm: không dùng `assertThat`, phải dùng assert riêng như `assertNull`, `assertTrue`; đã thêm vào rule và đổi toàn bộ `AuthServiceTest` sang `org.junit.Assert`.

Các câu đã hỏi lại người dùng trước khi code (CLAUDE.md mục 1.8) và câu trả lời:

| Câu hỏi | Người dùng chọn |
|---|---|
| Thư viện JWT | `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` (Nimbus, không khai version riêng) |
| Đăng ký cho ai, API cũ thế nào | Khách đăng ký có **OTP SĐT** (thêm bảng `otp_code`); nhân viên chỉ đăng nhập, tài khoản do ADMIN tạo; tra cứu + đặt chỗ vãng lai + thanh toán + webhook vẫn công khai, API còn lại cần token |
| CRUD cho bảng nào | Chỉ **bảng danh mục**; bảng giao dịch (booking, payment, refund, invoice, audit_log...) chỉ đi qua luồng nghiệp vụ |

### 1.1. Bug (chỉ điền nếu là sửa lỗi)

Không có — đây là tính năng mới. Ghi chú: lúc bắt đầu, `mvn test` có sẵn 1 test đỏ không liên quan ticket này (`AvailabilityIntegrationTest.rejectsInvalidSearchRequests`: gọi `/api/availability` thiếu `branchId` trả `400 INVALID_INPUT` thay vì `VALIDATION_FAILED`). Nguyên nhân: `AvailabilityController.search` khai `branchId` là `required = false`, null đi xuống `findById(null)` → `IllegalArgumentException`. Đã sửa thành `@RequestParam Long branchId` (đúng spec `tra-cuu-cho-trong.md` mục 4: `branchId` bắt buộc) để toàn bộ test xanh.

### 1.2. Ngoài phạm vi (Out of scope)

- Không làm giao diện.
- Không có refresh token, đăng xuất / thu hồi token (JWT không trạng thái, hết hạn sau 8 giờ). Nhân viên bị khóa vẫn dùng được token cũ tới khi hết hạn.
- Prompt 1 không làm FR-AUTH-05 (đã làm ở Prompt 2). Prompt 2 vẫn **không** làm:
  - đổi SĐT của tài khoản (SĐT là định danh, đổi cần OTP của SĐT mới) — `PUT /api/auth/me` bỏ qua SĐT;
  - thu hồi token cũ sau khi đổi / đặt lại mật khẩu (JWT không trạng thái, token cũ còn dùng tới khi hết 8 giờ);
  - đặt lại mật khẩu qua email; nhân viên tự đổi mật khẩu (vẫn do ADMIN đặt qua `PUT /api/admin/staff/{id}`);
  - khách mua gói thành viên qua API; lịch sử từng lần trừ phút của gói.
- **Không phân quyền theo chi nhánh** (FR-AUTH-04 phần "theo chi nhánh"): token có `branchId` nhưng API chưa lọc theo nó — quản lý chi nhánh A vẫn sửa được dữ liệu chi nhánh B.
- Không gửi SMS thật: OTP đi qua `NotificationSender` hiện chỉ ghi log.
- Không giới hạn số lần gọi (rate limit) `/api/auth/*`; chỉ có chống gửi lại OTP trong 60 giây và khóa mã sau 5 lần nhập sai.
- Không CRUD bảng giao dịch / bảng con: `booking`, `booking_charge`, `booking_status_history`, `booking_addon`, `payment`, `refund`, `invoice`, `invoice_line`, `voucher_redemption`, `membership_pass`, `membership_usage`, `service_order(_item)`, `ticket(_attachment)`, `compensation`, `notification_job`, `audit_log`, `outbox_event`, `otp_code`. Bảng nối `resource_amenity`, `voucher_resource_type`, `membership_plan_resource_type` được sửa qua API của bảng cha.
- Không ghi `audit_log` khi quản trị sửa dữ liệu.

---

## 2. Context — code hiện tại liên quan

### 2.1. Luồng hiện tại

Trước khi làm: không có Spring Security, mọi API ai gọi cũng được; bảng `staff` có cột `password_hash` nhưng chứa giá trị giả; bảng `customer` không có mật khẩu.

Sau khi làm:

```
POST /api/auth/register/phone            (presentation, công khai)
  → AuthController.registerByPhone()
  → AuthService.registerByPhone()        (business)  ← kiểm tra SĐT / tài khoản / email trước
      → OtpService.verify()               transaction riêng (REQUIRES_NEW): ghi số lần sai kể cả khi lỗi
      → CustomerRepository.save()         dùng lại dòng customer vãng lai cùng SĐT (FR-AUTH-03)
      → TokenService.issue()              JWT HS256 (claims: uid, type, roles, name, branchId)
  → bảng: otp_code, customer

Mọi request khác:
  SecurityConfig (SecurityFilterChain)    URL công khai / phải đăng nhập; JWT → ROLE_xxx từ claim "roles"
  → XxxController  @PreAuthorize(Roles.XXX)   vai trò được gọi API
  → Roles.actorOf(jwt) → Actor               truyền xuống service để kiểm quyền sở hữu (khách chỉ thấy đơn của mình)

GET/POST/PUT/DELETE /api/admin/**
  → CatalogController / CommerceController / PeopleController
  → CatalogService / AddonService / MenuService / VoucherService / MembershipService / StaffService / CustomerService
  → repository tương ứng → bảng danh mục
```

### 2.2. File sẽ phải sửa / tạo

| File | Sửa hay tạo mới | Sửa cái gì |
|---|---|---|
| `backend/pom.xml` | Sửa | Thêm `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (người dùng đã đồng ý) |
| `backend/db/schema.sql` | Sửa | `customer.password_hash`, index `ux_customer_email_account`, bảng 36 `otp_code`, số bảng 35 → 36 |
| `backend/db/data.sql` | Sửa | Hash BCrypt thật cho `staff` (mật khẩu `Cowork@123`), `customer.password_hash` cho khách 1, 2, 4; `TRUNCATE` thêm `otp_code` |
| `backend/src/main/resources/application.yml` | Sửa | Mục `auth:` (khóa JWT, hạn token, OTP) |
| `config/AuthProperties.java` | Tạo | Record tham số đăng nhập |
| `config/SecurityConfig.java` | Tạo | `SecurityFilterChain`, `JwtEncoder/Decoder` (HS256, hạn theo `Clock`), `PasswordEncoder` BCrypt, lỗi 401/403 dạng `ProblemDetail` |
| `config/DemoDataSeeder.java` | Sửa | 5 tài khoản nhân viên mẫu (1 bị khóa) |
| `business/exception/AuthenticationFailedException.java` | Tạo | Lỗi đăng nhập → HTTP 401 |
| `business/service/AuthService.java` | Tạo | Đăng ký SĐT/email, đăng nhập 3 kiểu, hồ sơ |
| `business/service/OtpService.java`, `OtpGenerator.java` | Tạo | Gửi / kiểm tra OTP |
| `business/service/TokenService.java` | Tạo | Phát hành JWT |
| `business/service/StaffService.java` | Tạo | CRUD nhân viên |
| `business/service/CatalogService.java` | Tạo | CRUD chi nhánh, khu vực, loại chỗ, vị trí, tiện ích, bảng giá |
| `business/service/MenuService.java` | Tạo | CRUD nhóm món, món |
| `business/service/AddonService.java`, `VoucherService.java`, `MembershipService.java` | Tạo | Phần CRUD (phần nghiệp vụ đặt chỗ thuộc ticket booking.md) |
| `business/service/CustomerService.java` | Sửa | CRUD khách; `validate`/`requireValidPhone` dùng chung; không ghi đè hồ sơ khách đã có tài khoản khi người khác đặt hộ bằng SĐT đó |
| `business/service/NotificationService.java` | Sửa | Thêm `otp(...)` |
| `dataaccess/entity/Customer.java` | Sửa | Field `passwordHash` |
| `dataaccess/entity/OtpCode.java` | Tạo | Entity bảng `otp_code` |
| `dataaccess/repository/OtpCodeRepository.java`, `StaffRepository.java`, `MenuCategoryRepository.java`, `MenuItemRepository.java`, `MembershipPlanRepository.java`, `AddonRepository.java`, `VoucherRepository.java` | Tạo | Truy vấn cho đăng nhập / CRUD |
| `dataaccess/repository/CustomerRepository.java`, `BranchRepository.java`, `ZoneRepository.java`, `ResourceTypeRepository.java`, `SpaceResourceRepository.java`, `AmenityRepository.java`, `PriceRuleRepository.java` | Sửa | Thêm hàm `findAll...OrderBy`, `existsByCode...`, `findAccountByEmail` |
| `dto/Actor.java` | Tạo | Người đang gọi (khách vãng lai / khách / nhân viên + vai trò) |
| `dto/AuthDtos.java`, `CatalogDtos.java`, `CommerceDtos.java`, `PeopleDtos.java` | Tạo | Request/response |
| `presentation/controller/Roles.java` | Tạo | Biểu thức `@PreAuthorize` dùng chung, `actorOf(Jwt)` |
| `presentation/controller/AuthController.java`, `CatalogController.java`, `CommerceController.java`, `PeopleController.java` | Tạo | Endpoint mục 4 |
| `presentation/controller/BookingController.java`, `ResourceController.java` | Sửa | `@PreAuthorize`, truyền `Actor` |
| `presentation/controller/ApiExceptionHandler.java` | Sửa | Handler `AuthenticationFailedException` (401), `HttpMessageNotReadableException` (400 `VALIDATION_FAILED`, ví dụ enum sai) |
| `presentation/controller/AvailabilityController.java` | Sửa | `branchId` bắt buộc (sửa test đỏ có sẵn, xem 1.1) — **ngoài danh sách dự kiến, thêm vì CLAUDE.md yêu cầu toàn bộ test phải qua** |
| `src/test/.../support/IntegrationTest.java` | Sửa | Mã OTP cố định `123456`, helper `adminAuth()`, `managerAuth()`, `receptionistAuth()`, `technicianAuth()` |
| `src/test/.../BookingFlowIntegrationTest.java` | Sửa | Gửi token nhân viên cho API đã có quyền |
| `src/test/.../AuthIntegrationTest.java`, `AdminCrudIntegrationTest.java` | Tạo | Mục 7.1 |
| `src/test/.../business/service/AuthServiceTest.java` | Viết lại (2026-11-05) | JUnit 4, assert của `org.junit.Assert`, tên `test_<hàm>_<số>`, ghi viewpoint; 81 testcase, mục 7.1 |
| `.claude/rules/unit-test.md` | Sửa (2026-11-05) | Cấm `assertThat`, thêm bảng chọn assert theo điều cần kiểm tra |
| `src/test/.../business/service/` gồm `AddonServiceTest`, `AvailabilityServiceTest`, `BookingServiceTest`, `BookingWindowPolicyTest`, `MembershipServiceTest`, `OtpServiceTest`, `PricingServiceTest`, `VoucherServiceTest` | Xóa (2026-11-05) | Người dùng yêu cầu xóa hết unit test cũ |
| `checklist-use-case.md`, `ticket-response/booking.md`, `ticket-response/tra-cuu-cho-trong.md` | Sửa (2026-11-05) | Trạng thái FR theo test còn lại; ghi chú test đã xóa |
| `README.md`, `backend/README.md`, `docs/database.md` | Sửa | Số bảng 36, bảng API + quyền, cách lấy token, ngày test |
| **Prompt 2 (2026-11-08)** | | |
| `business/service/AuthService.java` | Sửa | `updateProfile`, `changePassword`, `resetPassword`, `requireAccount`; `profile` kèm số phút gói; constructor thêm `MembershipService` |
| `business/service/MembershipService.java` | Sửa | `summaryOf(customerId)`: các gói của khách, tổng phút còn dùng được |
| `business/service/CustomerService.java` | Sửa | `applyPreferences(customer, notifyBeforeMinutes, preferredChannel)` thành `static` dùng chung |
| `dataaccess/repository/MembershipPassRepository.java` | Sửa | `findByCustomerIdOrderByExpiresAtAscIdAsc` (nạp kèm `plan`) |
| `dto/AuthDtos.java` | Sửa | `UpdateProfileRequest`, `ChangePasswordRequest`, `ResetPasswordRequest`; `ProfileResponse` thêm `notifyBeforeMinutes`, `preferredChannel`, `membership` |
| `dto/CommerceDtos.java` | Sửa | `MembershipPassResponse`, `MembershipSummary` |
| `presentation/controller/AuthController.java` | Sửa | `PUT /api/auth/me`, `POST /api/auth/me/password`, `POST /api/auth/password/reset` |
| `config/SecurityConfig.java` | Sửa | `/api/auth/password/reset` công khai |
| `src/test/.../AuthIntegrationTest.java` | Sửa | Thêm `memberManagesProfileAndPassword` |
| `src/test/.../business/service/AuthServiceTest.java` | Sửa | Setup thêm `MembershipService`; sửa 076, 078; thêm 082–100 |
| `src/test/.../business/service/*ServiceTest.java` (17 file mới: `AddonServiceTest`, `AvailabilityServiceTest`, `BookingWindowPolicyTest`, `CatalogServiceTest`, `CustomerServiceTest`, `MembershipServiceTest`, `MenuServiceTest`, `NotificationServiceTest`, `OtpGeneratorTest`, `OtpServiceTest`, `PaymentServiceTest`, `PricingServiceTest`, `RefundServiceTest`, `ResourceServiceTest`, `StaffServiceTest`, `TokenServiceTest`, `VoucherServiceTest`) | Tạo | Yêu cầu chat "unit test cho tất cả các file service" |
| `backend/README.md`, `checklist-use-case.md` | Sửa | Bảng API; FR-AUTH-05 → ✅ |

### 2.3. File KHÔNG được đụng vào

- `ticket-request/*.md`, `ticket-response/AI-response-template.md`.
- `CLAUDE.md` (không thuộc danh sách tài liệu AI được sửa) — xem mục 8: dòng "ĐÚNG 35 entity" giờ đã lệch.
- `PaymentService`, `PaymentWebhookHandler`, `SandboxPaymentController`: thanh toán giữ công khai, không đổi.

### 2.4. Quy ước code phải theo

- Kiểm tra vai trò ở controller bằng `@PreAuthorize(Roles.XXX)`; kiểm tra **quyền sở hữu** (khách chỉ đơn của mình) ở service qua `Actor` — service không import gì của Spring Security Web / HTTP.
- Token hết hạn theo `Clock` của ứng dụng (`JwtTimestampValidator.setClock`) để test tua được đồng hồ.
- Mật khẩu, mã OTP chỉ lưu dạng BCrypt. Thông báo lỗi đăng nhập không phân biệt "sai tài khoản" hay "sai mật khẩu".
- CRUD service trả DTO (chuyển trong transaction) khi response cần quan hệ LAZY (tiện ích của vị trí, loại chỗ của voucher / gói).
- Xóa bản ghi đang được tham chiếu: không tự xóa dây chuyền; database từ chối → `409 DATA_CONFLICT` (handler có sẵn). Service gọi `flush()` ngay sau `delete` để lỗi xảy ra trong lời gọi.
- Danh sách con (tiện ích của vị trí, loại chỗ của voucher / gói) cập nhật kiểu so khớp (sửa / thêm / bỏ), không xóa hết rồi thêm lại — Hibernate chạy INSERT trước DELETE sẽ trùng khóa.

---

## 3. Data Model Change — thay đổi database

### 3.1. Thay đổi bảng

| Bảng | Thay đổi | Cột | Kiểu | Null? | Mặc định | Ràng buộc / Ghi chú |
|---|---|---|---|---|---|---|
| `customer` | Thêm cột | `password_hash` | `varchar(100)` | Có | `NULL` | BCrypt. NULL = khách vãng lai |
| `customer` | Thêm index | `ux_customer_email_account` | unique `lower(email)` | — | — | Chỉ các dòng có `password_hash` và `email`: email đăng nhập không trùng; khách vãng lai vẫn được trùng email |
| `otp_code` | Tạo bảng (bảng thứ 36) | `id` | `bigint` identity | Không | | `pk_otp_code` |
| | | `phone` | `varchar(15)` | Không | | index `idx_otp_code_phone (phone, created_at)` |
| | | `code_hash` | `varchar(100)` | Không | | BCrypt của mã 6 số |
| | | `expires_at` | `timestamp(6)` | Không | | `ck_otp_code_period`: `expires_at > created_at` |
| | | `attempts` | `integer` | Không | `0` | `ck_otp_code_attempts >= 0` |
| | | `consumed_at` | `timestamp(6)` | Có | | Có giá trị = mã đã dùng |
| | | `created_at` | `timestamp(6)` | Không | | |

### 3.2. SQL

Chạy bằng tài khoản **`postgres`** trong pgAdmin (database `working-space`). User `cowork` không có quyền DDL. Chạy lại nhiều lần không lỗi.

```sql
ALTER TABLE customer ADD COLUMN IF NOT EXISTS password_hash varchar(100);

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_email_account ON customer (lower(email))
    WHERE password_hash IS NOT NULL AND email IS NOT NULL;

CREATE TABLE IF NOT EXISTS otp_code
(
    id          bigint       GENERATED BY DEFAULT AS IDENTITY,
    phone       varchar(15)  NOT NULL,
    code_hash   varchar(100) NOT NULL,
    expires_at  timestamp(6) NOT NULL,
    attempts    integer      NOT NULL DEFAULT 0,
    consumed_at timestamp(6),
    created_at  timestamp(6) NOT NULL,
    CONSTRAINT pk_otp_code PRIMARY KEY (id),
    CONSTRAINT ck_otp_code_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_otp_code_period CHECK (expires_at > created_at)
);
CREATE INDEX IF NOT EXISTS idx_otp_code_phone ON otp_code (phone, created_at);
GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON otp_code TO cowork;

-- Chỉ máy dev đang có dữ liệu mẫu cũ: đổi hash giả của nhân viên mẫu thành BCrypt của "Cowork@123"
UPDATE staff SET password_hash = '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G'
WHERE password_hash = 'demo-hash-chua-dung';
```

Hoặc trên máy dev: chạy lại cả `backend/db/schema.sql` rồi `backend/db/data.sql` (xóa sạch dữ liệu cũ).

**Prompt 2:** không đổi database (dùng cột có sẵn `customer.notify_before_minutes`, `preferred_channel`, `password_hash`, `phone_verified_at` và bảng `membership_pass`). Không có SQL phải chạy.

### 3.3. Dữ liệu cũ

- Khách đang có: `password_hash = NULL` → là khách vãng lai, vẫn đặt chỗ như trước; muốn có tài khoản thì đăng ký bằng SĐT + OTP (nhận lại đơn cũ).
- Nhân viên đang có: nếu `password_hash` không phải BCrypt thì không đăng nhập được → chạy câu `UPDATE` ở 3.2 (dev) hoặc ADMIN đặt lại mật khẩu qua `PUT /api/admin/staff/{id}`. Nếu database chưa có ADMIN nào dùng được thì phải tự `INSERT` một dòng `staff` role `ADMIN` với hash BCrypt.
- `data.sql`: khách 1, 2, 4 có mật khẩu `Cowork@123`; khách 3, 5 là vãng lai.

### 3.4. Entity / DTO cần cập nhật

| Lớp | Field mới | Kiểu Java |
|---|---|---|
| `Customer` | `passwordHash` | `String` |
| `OtpCode` (mới) | `id`, `phone`, `codeHash`, `expiresAt`, `attempts`, `consumedAt`, `createdAt` | `Long`, `String`, `String`, `LocalDateTime`, `int`, `LocalDateTime`, `LocalDateTime` |
| `Actor` (mới, dto) | `type`, `id`, `role` | `Actor.Type`, `Long`, `StaffRole` |
| `AuthDtos`, `CatalogDtos`, `CommerceDtos`, `PeopleDtos` (mới) | record request/response | — |
| `ProfileResponse` (Prompt 2) | `notifyBeforeMinutes`, `preferredChannel`, `membership` | `Integer`, `NotificationChannel`, `MembershipSummary` |
| `UpdateProfileRequest`, `ChangePasswordRequest`, `ResetPasswordRequest` (Prompt 2, mới) | xem mục 4 | — |
| `MembershipPassResponse`, `MembershipSummary` (Prompt 2, mới) | `id, planName, remainingMinutes, purchasedAt, expiresAt, status, usable` · `remainingMinutes, passes` | — |

---

## 4. API Contract

Header cho API cần quyền: `Authorization: Bearer <accessToken>`. Bảng đầy đủ (kèm vai trò từng API): `backend/README.md` mục API.

### `POST /api/auth/otp` — mới, công khai

**Request** `{ "phone": "string — bắt buộc — 0xxxxxxxxx hoặc +84xxxxxxxxx" }`

**Response thành công** — `200` `{ "phone": "0933000001", "expiresAt": "2026-11-10T08:05:00" }`

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Thiếu `phone` |
| 422 | `INVALID_CONTACT` | SĐT sai định dạng |
| 422 | `OTP_RESEND_TOO_SOON` | Gửi lại trong vòng 60 giây |

### `POST /api/auth/register/phone` — mới, công khai

**Request**
```json
{
  "phone": "string — bắt buộc",
  "otp": "string — bắt buộc — mã 6 số vừa nhận",
  "fullName": "string — bắt buộc — tối đa 255",
  "password": "string — bắt buộc — 8..72 ký tự",
  "email": "string — không bắt buộc — email hợp lệ"
}
```

**Response thành công** — `201`
```json
{
  "accessToken": "eyJ...", "tokenType": "Bearer", "expiresAt": "2026-11-10T09:00:00Z",
  "user": { "type": "CUSTOMER", "id": 7, "fullName": "Phan Văn Đăng", "phone": "0933000001", "email": null,
            "phoneVerified": true, "role": null, "branchId": null }
}
```

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Thiếu field, mật khẩu < 8 ký tự, email sai |
| 409 | `ACCOUNT_ALREADY_EXISTS` | SĐT đã có tài khoản (kiểm tra trước OTP, mã không bị tiêu) |
| 409 | `EMAIL_ALREADY_USED` | Email đã thuộc tài khoản khác |
| 422 | `INVALID_CONTACT` | SĐT / họ tên không hợp lệ |
| 422 | `OTP_INVALID` / `OTP_EXPIRED` / `OTP_TOO_MANY_ATTEMPTS` | Mã sai / quá 5 phút / sai quá 5 lần |

### `POST /api/auth/register/email` — mới, công khai

**Request** `{ "email": "bắt buộc", "phone": "bắt buộc (bảng customer định danh bằng SĐT)", "fullName": "bắt buộc", "password": "8..72" }`

**Response thành công** — `201`, giống trên, `user.phoneVerified = false`.

| HTTP | `code` | Khi nào |
|---|---|---|
| 409 | `EMAIL_ALREADY_USED` | Email đã thuộc tài khoản khác (không phân biệt hoa thường) |
| 409 | `ACCOUNT_ALREADY_EXISTS` | SĐT đã có tài khoản |
| 409 | `PHONE_VERIFICATION_REQUIRED` | SĐT đã có lịch sử đặt chỗ (khách vãng lai) — phải đăng ký bằng SĐT + OTP để nhận lại đơn |
| 400 / 422 | `VALIDATION_FAILED` / `INVALID_CONTACT` | Dữ liệu sai |

### `POST /api/auth/login` · `POST /api/auth/login/otp` · `POST /api/auth/staff/login` — mới, công khai

**Request** lần lượt: `{ "login": "SĐT hoặc email", "password": "..." }` · `{ "phone": "...", "otp": "..." }` · `{ "email": "...", "password": "..." }`

**Response thành công** — `200`, dạng như đăng ký (nhân viên: `user.type = "STAFF"`, có `role`, `branchId`).

| HTTP | `code` | Khi nào |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | Sai tài khoản / mật khẩu; SĐT chưa có tài khoản (khách vãng lai) |
| 401 | `ACCOUNT_LOCKED` | Nhân viên `status = LOCKED` |
| 422 | `OTP_*` | (login/otp) mã sai / hết hạn / quá số lần |

### `GET /api/auth/me` — mới, cần đăng nhập (Prompt 2: thêm trường)

**Response** `200` — `ProfileResponse` như trên. `401 UNAUTHORIZED` nếu không có / sai / hết hạn token.

Prompt 2 thêm 3 trường (thêm, không đổi trường cũ): `notifyBeforeMinutes`, `preferredChannel` (chỉ khách) và `membership` (chỉ ở `GET/PUT /api/auth/me` của khách; response đăng nhập / đăng ký và nhân viên để `null`):

```json
"membership": {
  "remainingMinutes": 600,
  "passes": [
    { "id": 4, "planName": "Gói Hot Desk 20 giờ", "remainingMinutes": 300, "purchasedAt": "2026-09-01T10:00:00",
      "expiresAt": "2026-10-01T10:00:00", "status": "EXPIRED", "usable": false },
    { "id": 5, "planName": "Gói Hot Desk 20 giờ", "remainingMinutes": 600, "purchasedAt": "2026-11-09T08:00:00",
      "expiresAt": "2026-12-09T08:00:00", "status": "ACTIVE", "usable": true }
  ]
}
```

`remainingMinutes` (phút, front-end tự đổi ra giờ) = tổng các gói `usable`. Gói `ACTIVE` đã quá `expiresAt` hiển thị `EXPIRED`.

### `PUT /api/auth/me` — mới (Prompt 2), role CUSTOMER

**Request**
```json
{
  "fullName": "string — bắt buộc — tối đa 255",
  "email": "string — không bắt buộc — email hợp lệ; bỏ trống = xóa email",
  "notifyBeforeMinutes": "10 | 15 | 30 — không bắt buộc, bỏ trống = giữ",
  "preferredChannel": "SMS | ZALO | EMAIL | PUSH — không bắt buộc, bỏ trống = giữ"
}
```

**Response** `200` — `ProfileResponse` có `membership`. SĐT không đổi.

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Thiếu `fullName`, email sai định dạng, kênh không tồn tại |
| 401 / 403 | `UNAUTHORIZED` / `FORBIDDEN` | Không token / token nhân viên |
| 409 | `EMAIL_ALREADY_USED` | Email là email đăng nhập của tài khoản khác |
| 422 | `INVALID_NOTIFY_BEFORE` · `INVALID_CONTACT` | Nhắc trước ngoài 10/15/30 · họ tên rỗng / email sai |

### `POST /api/auth/me/password` — mới (Prompt 2), role CUSTOMER

**Request** `{ "currentPassword": "bắt buộc", "newPassword": "bắt buộc — 8..72 ký tự" }` → **Response** `204`.

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Mật khẩu mới < 8 ký tự |
| 422 | `INVALID_CURRENT_PASSWORD` | Sai mật khẩu hiện tại (422, không dùng 401 để front-end không hiểu nhầm là hết phiên) |
| 422 | `PASSWORD_UNCHANGED` | Mật khẩu mới trùng mật khẩu hiện tại |

### `POST /api/auth/password/reset` — mới (Prompt 2), công khai

Trước đó lấy mã bằng `POST /api/auth/otp {phone}`.

**Request** `{ "phone": "bắt buộc", "otp": "bắt buộc", "newPassword": "bắt buộc — 8..72 ký tự" }` → **Response** `200` — `AuthResponse` (đăng nhập luôn).

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Thiếu field, mật khẩu < 8 ký tự |
| 401 | `INVALID_CREDENTIALS` | SĐT chưa có tài khoản (khách vãng lai hoặc không tồn tại) — không tốn mã OTP |
| 422 | `OTP_INVALID` / `OTP_EXPIRED` / `OTP_TOO_MANY_ATTEMPTS` | Như đăng ký bằng SĐT |

### CRUD `/api/admin/{branches|zones|resource-types|resources|amenities|price-rules|addons|menu-categories|menu-items|vouchers|membership-plans|staff|customers}` — mới

`GET` (danh sách) · `GET /{id}` · `POST` (`201`) · `PUT /{id}` (`200`) · `DELETE /{id}` (`204`). Body từng loại: record trong `CatalogDtos`, `CommerceDtos`, `PeopleDtos`. Ví dụ tạo nhân viên:

```json
{ "fullName": "Lễ Tân Mới", "email": "le.tan.moi@cowork.example", "password": "matkhau123 (bắt buộc khi tạo; bỏ trống khi sửa = giữ)",
  "role": "RECEPTIONIST", "branchId": 1, "compensationLimit": 50000, "status": "ACTIVE | LOCKED (chỉ khi sửa)" }
```

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Thiếu field, số âm, enum không tồn tại (`role: "BOSS"`, `type: "SAUNA"`) |
| 401 | `UNAUTHORIZED` | Không có / sai / hết hạn token |
| 403 | `FORBIDDEN` | Vai trò không được phép (mục 5.1) |
| 404 | `NOT_FOUND` | id không tồn tại (kể cả `branchId`, `zoneId`, `categoryId` trong body) |
| 409 | `CODE_ALREADY_EXISTS` | Trùng mã chi nhánh / vị trí / tiện ích / add-on (trong chi nhánh) / voucher / loại chỗ |
| 409 | `NAME_ALREADY_EXISTS` · `EMAIL_ALREADY_USED` · `PHONE_ALREADY_USED` | Trùng tên nhóm món · email nhân viên / tài khoản khách · SĐT khách |
| 409 | `PRICE_RULE_OVERLAP` | Khung giá chồng khung đã có (cùng loại chỗ, cùng loại ngày) |
| 409 | `DATA_CONFLICT` | Xóa dòng đang được tham chiếu (chi nhánh còn khu vực, nhóm còn món, vị trí đã có đơn...) |
| 422 | `INVALID_TIME_RANGE` | Giờ đóng cửa ≤ giờ mở; voucher `validTo ≤ validFrom`; phút kết thúc ≤ phút bắt đầu |
| 422 | `INVALID_DISCOUNT` · `VOUCHER_LIMIT_BELOW_USED` | % > 100 · tổng lượt mới < số lượt đã dùng |
| 422 | `UNKNOWN_AMENITY` · `DUPLICATE_AMENITY` | Mã tiện ích không có / bị lặp trong body vị trí |
| 422 | `CODE_IMMUTABLE` | Đổi mã loại chỗ (chỉ được sửa tên, mô tả) |
| 422 | `PASSWORD_REQUIRED` · `CANNOT_MODIFY_SELF` | Tạo nhân viên thiếu mật khẩu · ADMIN tự khóa / tự đổi vai trò / tự xóa mình |
| 422 | `INVALID_NOTIFY_BEFORE` · `INVALID_CONTACT` | Nhắc trước không thuộc 10/15/30 · SĐT / email khách sai |

### `GET /api/addons?branchId=` — mới, công khai

Add-on `ACTIVE` của chi nhánh (cho khách chọn khi đặt chỗ, ticket booking.md).

### API cũ đổi hành vi

| API | Trước | Sau |
|---|---|---|
| `GET /api/bookings/{id}` | Ai cũng xem | Khách (chỉ đơn của mình, đơn người khác → 404) hoặc RECEPTIONIST / MANAGER / ADMIN |
| `POST /api/bookings/{id}/cancel` | Ai cũng gọi | Như trên |
| `POST /api/check-ins`, `/api/check-outs`, `/api/bookings/{id}/extend`, `/settle` | Ai cũng gọi | RECEPTIONIST / MANAGER / ADMIN |
| `GET /api/resources` · `POST /api/resources/{id}/maintenance`, `/reactivate` | Ai cũng gọi | Mọi nhân viên · TECHNICIAN / MANAGER / ADMIN |
| `GET /api/availability` | `branchId` tùy chọn (lỗi 400 `INVALID_INPUT`) | `branchId` bắt buộc (400 `VALIDATION_FAILED`) |

Không đổi (vẫn công khai): `GET /api/branches`, `/api/amenities`, `/api/availability`, `/api/branches/{id}/floor-plan`, `POST /api/bookings`, `/api/bookings/{id}/payments`, `/api/payments/webhook`, `/api/payments/sandbox/**`.

---

## 5. Logic Flow — các bước xử lý

**Đăng ký bằng SĐT** (`AuthService.registerByPhone`):
1. `@Valid`: đủ field, mật khẩu 8–72 ký tự.
2. `CustomerService.validate`: họ tên, SĐT `^(0|\+84)\d{9}$`, email.
3. Tìm `customer` theo SĐT: đã có `password_hash` → `409 ACCOUNT_ALREADY_EXISTS`.
4. Có email → email đã thuộc tài khoản khác → `409 EMAIL_ALREADY_USED`.
5. `OtpService.verify(phone, otp)` (transaction riêng, không rollback khi mã sai):
   - không có mã / mã đã dùng → `OTP_INVALID`; `now >= expires_at` → `OTP_EXPIRED`; `attempts >= 5` → `OTP_TOO_MANY_ATTEMPTS`;
   - sai mã → `attempts + 1`, lưu, `OTP_INVALID`; đúng → `consumed_at = now`.
6. Dùng lại dòng khách vãng lai (nếu có) hoặc tạo mới; ghi họ tên, email (bỏ trống = giữ email cũ), `password_hash` BCrypt, `phone_verified_at = now`.
7. `TokenService.issue` → trả `201` + token.

**Đăng ký bằng email**: bước 1–2; email trùng → `EMAIL_ALREADY_USED`; SĐT đã có dòng khách → có mật khẩu: `ACCOUNT_ALREADY_EXISTS`, chưa có: `PHONE_VERIFICATION_REQUIRED`; tạo khách mới `phone_verified_at = NULL`.

**Đăng nhập**: `login` chứa `@` → tìm tài khoản theo email (không phân biệt hoa thường), ngược lại theo SĐT và phải có mật khẩu; `BCrypt.matches` sai → `401 INVALID_CREDENTIALS`. Đăng nhập OTP: SĐT phải có tài khoản → verify OTP → nếu chưa xác thực SĐT thì ghi `phone_verified_at`. Nhân viên: email + mật khẩu, `status = LOCKED` → `401 ACCOUNT_LOCKED`.

**Mỗi request có token**: `BearerTokenAuthenticationFilter` → `NimbusJwtDecoder` kiểm chữ ký HS256, `iss = cowork-booking`, `exp` so với `Clock` của ứng dụng (dung sai 60 giây) → claim `roles` thành `ROLE_xxx` → `@PreAuthorize` trên hàm controller → `Roles.actorOf(jwt)` → service.

**CRUD**: controller `@Valid` → service tải bản ghi (không có → 404) → kiểm tra trùng mã / quy tắc → lưu → trả DTO. Xóa: `delete` + `flush` → bị tham chiếu thì `409 DATA_CONFLICT`.

**Prompt 2 — hồ sơ và mật khẩu** (`AuthService`):
1. `requireAccount(actor)`: actor không phải khách → `422 CUSTOMER_ONLY` (ở API đã bị `@PreAuthorize(Roles.CUSTOMER)` chặn trước bằng 403); không tìm thấy khách → `404`.
2. `updateProfile`: email rỗng / chỉ khoảng trắng → `null`; `CustomerService.validate(họ tên, SĐT cũ, email)`; email khác rỗng mà `findAccountByEmail` trả tài khoản khác → `409 EMAIL_ALREADY_USED`; ghi liên hệ; `CustomerService.applyPreferences` (null = giữ, ngoài 10/15/30 → `INVALID_NOTIFY_BEFORE`); lưu; trả hồ sơ kèm `MembershipService.summaryOf`.
3. `changePassword`: `BCrypt.matches(current)` sai → `INVALID_CURRENT_PASSWORD`; `matches(new)` đúng → `PASSWORD_UNCHANGED`; lưu hash mới.
4. `resetPassword`: tìm khách theo SĐT **có mật khẩu**, không có → `401 INVALID_CREDENTIALS` (kiểm tra trước OTP để không tiêu mã); `OtpService.verify`; lưu hash mới; `phone_verified_at` rỗng thì ghi `now`; trả token.
5. `MembershipService.summaryOf`: đọc mọi gói của khách (hết hạn sớm nhất trước); `status` hiển thị = `EXPIRED` nếu gói `ACTIVE` mà `now >= expiresAt`; `usable = status ACTIVE && remainingMinutes > 0`; `remainingMinutes` = tổng các gói `usable`.

### 5.1. Quy tắc nghiệp vụ / công thức

| Quy tắc | Công thức | Ví dụ |
|---|---|---|
| Hạn OTP | hợp lệ khi `created_at ≤ now < created_at + 5 phút` | Gửi 08:00 → 08:04:59 dùng được, 08:05:00 `OTP_EXPIRED` |
| Gửi lại OTP | `now ≥ lần gửi trước + 60 giây` | 08:00:59 → `OTP_RESEND_TOO_SOON`; 08:01:00 → gửi được |
| Sai OTP | sai lần thứ 1..5 → `OTP_INVALID`; lần thứ 6 trở đi → `OTP_TOO_MANY_ATTEMPTS` (kể cả nhập đúng) | |
| Hạn token | `exp = iat + 8 giờ` (`auth.access-token-ttl`), dung sai 60 giây | Phát 08:00 → 17:00 bị `401` |
| Mật khẩu | 8–72 ký tự (giới hạn BCrypt 72 byte), lưu BCrypt cost 10 | |

**Phân quyền RBAC** — bảng trả lời yêu cầu "role nào được C, R, U, D dữ liệu gì" (AD = ADMIN, QL = MANAGER, LT = RECEPTIONIST, KT = TECHNICIAN, KH = khách có tài khoản; "—" = không có quyền):

| Bảng / dữ liệu | ADMIN | MANAGER | RECEPTIONIST | TECHNICIAN | Khách (KH) | Lý do |
|---|---|---|---|---|---|---|
| `branch` | CRUD | RU | R | R | R (công khai, chi nhánh ACTIVE) | Mở / đóng chi nhánh là quyết định cấp hệ thống |
| `zone` | CRUD | CRUD | R | R | R (qua sơ đồ) | Quản lý sắp xếp mặt bằng chi nhánh |
| `resource_type` | CRUD (chỉ sửa tên, mô tả) | R | R | R | — | Mã loại gắn với code tính giá |
| `space_resource` (+ `resource_amenity`) | CRUD | CRUD | R | R + đổi trạng thái bảo trì / mở lại | R (qua tìm chỗ) | Kỹ thuật báo hỏng, mở lại vị trí |
| `amenity` | CRUD | CRUD | R | R | R (công khai) | |
| `price_rule` | CRUD | CRUD | R | — | — (thấy giá qua tìm chỗ) | Kỹ thuật không cần xem giá |
| `addon` | CRUD | CRUD | R | R | R (công khai, add-on ACTIVE) | Kỹ thuật quản lý thiết bị |
| `menu_category`, `menu_item` | CRUD | CRUD | R | — | — | Lễ tân nhận gọi món |
| `voucher` (+ `voucher_resource_type`) | CRUD | CRUD | R | — | — (chỉ nhập mã khi đặt) | Lễ tân tư vấn khuyến mãi |
| `membership_plan` (+ `membership_plan_resource_type`) | CRUD | CRUD | R | — | — | |
| `staff` | CRUD (không tự khóa / tự đổi vai trò / tự xóa) | R | — | — | — | Tài khoản nội bộ chỉ ADMIN cấp |
| `customer` | CRUD | CRU | CRU | — | R hồ sơ của mình (`/api/auth/me`) | Lễ tân tạo / sửa hồ sơ tại quầy; chỉ ADMIN xóa |
| `booking` (xem) | Mọi đơn, lọc theo khách | Mọi đơn | Mọi đơn | — | Đơn của mình | |
| `booking` (hủy, đổi giờ) | Có | Có | Có | — | Đơn của mình | Theo quy tắc ≥ 2 giờ (ticket booking.md) |
| `booking` (check-in/out, gia hạn, thu nốt) | Có | Có | Có | — | — | Thao tác tại quầy |
| `booking` (giữ chỗ), `payment` (tạo, webhook) | Công khai | | | | Công khai | Khách vãng lai vẫn đặt được (FR-AUTH-01) |
| Bảng giao dịch khác (`payment`, `refund`, `invoice`, `audit_log`...) | Không có API CRUD | | | | | Chỉ thay đổi qua luồng nghiệp vụ |

### 5.2. Transaction và đồng thời

- `OtpService.verify` chạy `REQUIRES_NEW` + `noRollbackFor = DomainException`: số lần nhập sai được lưu dù đăng ký bị lỗi. Hệ quả: mã đúng được đánh dấu đã dùng ngay cả khi bước sau của đăng ký lỗi → vì vậy mọi kiểm tra khác (tài khoản, email) chạy **trước** khi verify OTP.
- Hai request đăng ký cùng SĐT cùng lúc: ràng buộc `uq_customer_phone` (và `@Version` trên `customer`) làm một request thất bại → `409 DATA_CONFLICT` / `CONCURRENT_UPDATE`. Hai tài khoản cùng email cùng lúc: `ux_customer_email_account` chặn trên PostgreSQL (H2 không có index này — xem mục 8).
- CRUD: các bảng có `version` (`voucher`, `space_resource`...) dùng khóa lạc quan → `409 CONCURRENT_UPDATE`. Trùng mã do hai request song song → ràng buộc UNIQUE → `409 DATA_CONFLICT`.

---

## 6. Edge Cases — mục quan trọng nhất

| # | Tình huống | Kết quả mong đợi | Dữ liệu sau đó |
|---|---|---|---|
| E1 | Gọi API cần quyền không có token / token rác / token hết hạn | `401 UNAUTHORIZED` | Không đổi |
| E2 | Có token nhưng sai vai trò (khách gọi `/api/bookings`, KT xem đơn, QL tạo chi nhánh, LT xóa khách...) | `403 FORBIDDEN` | Không đổi |
| E3 | API công khai gọi không token | `200` | — |
| E4 | Đăng ký SĐT đã có đơn vãng lai | `201`, dùng lại dòng khách, "đơn của tôi" thấy đơn cũ | `customer.password_hash`, `phone_verified_at` được ghi |
| E5 | Đăng ký SĐT đã có tài khoản | `409 ACCOUNT_ALREADY_EXISTS`, OTP không bị tiêu | Không đổi |
| E6 | Đăng ký email với SĐT của khách vãng lai | `409 PHONE_VERIFICATION_REQUIRED` | Không đổi |
| E7 | Email trùng (khác hoa thường) | `409 EMAIL_ALREADY_USED` | Không đổi |
| E8 | OTP sai | `422 OTP_INVALID` | `otp_code.attempts + 1` |
| E9 | OTP đúng mốc 5 phút | `422 OTP_EXPIRED` (nửa mở) | Không đổi |
| E10 | Sai OTP 5 lần rồi nhập đúng | `422 OTP_TOO_MANY_ATTEMPTS` | Phải xin mã mới |
| E11 | Dùng lại OTP đã dùng | `422 OTP_INVALID` | — |
| E12 | Xin OTP lần 2 trong 60 giây (đúng 60 giây thì được) | `422 OTP_RESEND_TOO_SOON` | Không tạo mã mới |
| E13 | Sai mật khẩu / tài khoản không tồn tại / khách vãng lai đăng nhập | `401 INVALID_CREDENTIALS` (cùng một thông báo) | — |
| E14 | Nhân viên bị khóa đăng nhập | `401 ACCOUNT_LOCKED` | — |
| E15 | Mật khẩu < 8 ký tự | `400 VALIDATION_FAILED` | — |
| E16 | Khách xem / hủy đơn của khách khác | `404 NOT_FOUND` (không lộ đơn tồn tại) | Không đổi |
| E17 | Tạo trùng mã chi nhánh / email nhân viên / SĐT khách | `409 CODE_ALREADY_EXISTS` / `EMAIL_ALREADY_USED` / `PHONE_ALREADY_USED` | Không đổi |
| E18 | Xóa chi nhánh còn khu vực / nhóm món còn món | `409 DATA_CONFLICT` | Rollback |
| E19 | Enum sai trong body (`role: "BOSS"`, `type: "SAUNA"`) | `400 VALIDATION_FAILED` | Không đổi |
| E20 | Giờ đóng cửa ≤ giờ mở; % giảm > 100; khung giá chồng | `422 INVALID_TIME_RANGE` / `422 INVALID_DISCOUNT` / `409 PRICE_RULE_OVERLAP` | Không đổi |
| E21 | ADMIN tự khóa mình | `422 CANNOT_MODIFY_SELF` | Không đổi |
| E22 | Nhân viên bị khóa qua `PUT` rồi đăng nhập | `401 ACCOUNT_LOCKED` | `staff.status = LOCKED` |
| E23 | Sửa danh sách tiện ích của vị trí (thêm, đổi số lượng, mã không tồn tại) | `200` đồng bộ đúng / `422 UNKNOWN_AMENITY` | `resource_amenity` khớp body |
| E24 | Hai request đăng ký cùng SĐT cùng lúc | Một thành công, một `409` | Một dòng customer — **chưa có test tự động** |
| E25 | DB lỗi giữa chừng khi đăng ký | Rollback transaction đăng ký (riêng OTP đã ghi `consumed_at` / `attempts`) | — |
| E26 | Dịch vụ SMS lỗi | Hiện chỉ ghi log nên không lỗi — Không có test | — |
| E27 | Dữ liệu cũ: nhân viên có hash giả | Không đăng nhập được → chạy SQL 3.2 | — |
| **Prompt 2** | | | |
| E28 | Khách sửa hồ sơ đủ trường | `200`, SĐT giữ nguyên, trả kèm `membership` | `customer` cập nhật |
| E29 | Email rỗng / chỉ khoảng trắng | `200`, email = null; tùy chọn bỏ trống thì giữ | — |
| E30 | Email trùng tài khoản khác · trùng chính mình | `409 EMAIL_ALREADY_USED` · `200` | Không đổi · cập nhật |
| E31 | Nhắc trước 20 phút; email sai định dạng | `422 INVALID_NOTIFY_BEFORE`; `422 INVALID_CONTACT` | Không đổi |
| E32 | Nhân viên gọi `PUT /api/auth/me` | `403 FORBIDDEN` (service: `422 CUSTOMER_ONLY`) | Không đổi |
| E33 | Đổi mật khẩu đúng / sai mật khẩu hiện tại / trùng mật khẩu cũ | `204` / `422 INVALID_CURRENT_PASSWORD` / `422 PASSWORD_UNCHANGED` | Hash mới / không đổi |
| E34 | Quên mật khẩu, OTP đúng | `200` + token, đăng nhập được bằng mật khẩu mới; SĐT chưa xác thực thì ghi `phone_verified_at` | Hash mới |
| E35 | Quên mật khẩu cho SĐT vãng lai / không tồn tại | `401 INVALID_CREDENTIALS`, OTP không bị tiêu | Không đổi |
| E36 | Quên mật khẩu, OTP sai | `422 OTP_INVALID` | Mật khẩu giữ nguyên, `attempts + 1` |
| E37 | Hồ sơ có gói hết hạn, gói dùng hết, gói còn phút | Gói hết hạn `EXPIRED`, gói 0 phút / USED_UP `usable = false`; tổng chỉ cộng gói còn dùng được | — |
| E38 | Hai request đổi mật khẩu cùng lúc | Khóa lạc quan `customer.version` → một cái `409 CONCURRENT_UPDATE` | Một hash — không có test tự động |

---

## 7. Tiêu chí hoàn thành (Acceptance Criteria)

- [x] Mọi edge case ở mục 6 có test, trừ E24–E27 và E38 (đồng thời, DB lỗi, SMS lỗi, dữ liệu cũ — ghi lý do ở bảng 7.1). E10, E11 có test lại từ 2026-11-08 trong `OtpServiceTest` mới.
- [x] `mvn test` qua toàn bộ (2026-11-08, sau Prompt 2 và unit test cho mọi service): **`Tests run: 612, Failures: 0, Errors: 0, Skipped: 0`** — 30 integration + 582 unit (19 file, mỗi file có log `UTLogs/<tên class>.log`, 582 dòng `result: PASS`). Lần trước (2026-11-05): 109.
- [x] Unit test cho **mọi file** trong `business/service/` (số testcase): `AddonServiceTest` 29, `AuthServiceTest` 100, `AvailabilityServiceTest` 13, `BookingServiceTest` 186, `BookingWindowPolicyTest` 14, `CatalogServiceTest` 34, `CustomerServiceTest` 28, `MembershipServiceTest` 26, `MenuServiceTest` 14, `NotificationServiceTest` 11, `OtpGeneratorTest` 2, `OtpServiceTest` 15, `PaymentServiceTest` 17, `PricingServiceTest` 11, `RefundServiceTest` 7, `ResourceServiceTest` 12, `StaffServiceTest` 18, `TokenServiceTest` 4, `VoucherServiceTest` 41. JUnit 4, tên `test_<hàm>_<tình huống>_<số>`, viewpoint trên mỗi hàm, log input / output. Hàm chỉ gọi thẳng repository rồi đổi sang DTO (`list...`) không có logic nên không viết test (rule mục 1).
- [x] `schema.sql` dùng `IF NOT EXISTS` cho bảng / index mới. **Chưa chạy thử trên PostgreSQL** (xem mục 8).
- [x] File sửa ngoài danh sách dự kiến: `AvailabilityController.java` (đã ghi lý do ở 2.2).
- [x] Đúng quy ước ở mục 2.4.
- [x] Số entity = số bảng = 36.

### 7.1. Test cần viết

| Test | Loại | Kiểm tra edge case |
|---|---|---|
| `AuthServiceTest.test_registerByPhone_1`–`_7` (SĐT mới, nhận khách cũ, các nhánh email) | Unit | Luồng chính, E4 |
| `AuthServiceTest.test_registerByPhone_8` | Unit | E5 |
| `AuthServiceTest.test_registerByPhone_9` | Unit | E7 |
| `AuthServiceTest.test_registerByPhone_10` | Unit | E8 |
| `AuthServiceTest.test_registerByPhone_11`–`_23` (biên SĐT, ký tự 1/2/3 byte, null) | Unit | Dữ liệu đầu vào |
| `AuthServiceTest.test_registerByEmail_24`–`_25` | Unit | Luồng chính, E7 |
| `AuthServiceTest.test_registerByEmail_26`–`_27` | Unit | E6 |
| `AuthServiceTest.test_registerByEmail_28`–`_39` (định dạng, biên SĐT, ký tự 1/2/3 byte, null) | Unit | Dữ liệu đầu vào |
| `AuthServiceTest.test_login_40`–`_41` | Unit | Luồng chính |
| `AuthServiceTest.test_login_42`–`_48` | Unit | E13 |
| `AuthServiceTest.test_login_49`–`_53` (ký tự 1/2/3 byte, null) | Unit | Dữ liệu đầu vào |
| `AuthServiceTest.test_loginWithOtp_54`–`_55` | Unit | Luồng chính |
| `AuthServiceTest.test_loginWithOtp_56`–`_57` | Unit | E13 |
| `AuthServiceTest.test_loginWithOtp_58` | Unit | E8 |
| `AuthServiceTest.test_loginWithOtp_59`–`_64` (null, rỗng, ký tự 1/2/3 byte) | Unit | Dữ liệu đầu vào |
| `AuthServiceTest.test_staffLogin_65`–`_66` | Unit | Luồng chính |
| `AuthServiceTest.test_staffLogin_67`–`_68` | Unit | E14 |
| `AuthServiceTest.test_staffLogin_69`–`_70` | Unit | E13 |
| `AuthServiceTest.test_staffLogin_71`–`_75` (ký tự 1/2/3 byte, null) | Unit | Dữ liệu đầu vào |
| `AuthServiceTest.test_profile_76`–`_81` (khách, nhân viên, vãng lai, null) | Unit | Hồ sơ người dùng |
| `OtpServiceTest.test_verify_FiveAttemptsBlocked_013`, `test_verify_FourAttemptsStillAllowed_012` | Unit (2026-11-08) | E10 |
| `OtpServiceTest.test_verify_AlreadyConsumed_009` | Unit (2026-11-08) | E11 |
| `OtpServiceTest.test_send_*_001`–`_006`, `test_verify_*_007`–`_015` | Unit (2026-11-08) | E8, E9, E12 ở mức service |
| `AuthServiceTest.test_profile_CustomerFound_076`, `test_profile_StaffFound_078` (sửa) | Unit (Prompt 2) | E28 (`membership` có với khách, rỗng với nhân viên) |
| `AuthServiceTest.test_updateProfile_AllFields_082`, `_FullName3Byte_088` | Unit (Prompt 2) | E28 |
| `AuthServiceTest.test_updateProfile_BlankEmailRemovesEmail_083` | Unit (Prompt 2) | E29 |
| `AuthServiceTest.test_updateProfile_EmailUsedByOtherAccount_084`, `_EmailOfOwnAccount_085` | Unit (Prompt 2) | E30 |
| `AuthServiceTest.test_updateProfile_InvalidNotifyBefore_086`, `_InvalidEmail_087` | Unit (Prompt 2) | E31 |
| `AuthServiceTest.test_updateProfile_StaffActor_089`, `_CustomerNotFound_090` | Unit (Prompt 2) | E32, không tồn tại |
| `AuthServiceTest.test_changePassword_CorrectCurrent_091`–`_NewPassword3Byte_095` | Unit (Prompt 2) | E33 |
| `AuthServiceTest.test_resetPassword_UnverifiedPhone_096`, `_AlreadyVerifiedKeepsTimestamp_097` | Unit (Prompt 2) | E34 |
| `AuthServiceTest.test_resetPassword_GuestWithoutAccount_098`, `_PhoneNotExisted_099` | Unit (Prompt 2) | E35 |
| `AuthServiceTest.test_resetPassword_WrongOtp_100` | Unit (Prompt 2) | E36 |
| `MembershipServiceTest.test_summaryOf_NoPasses_019`, `_MixedPasses_020`, `_ActiveWithZeroMinutes_021` | Unit (Prompt 2) | E37 |
| `AuthIntegrationTest.memberManagesProfileAndPassword` | Integration (Prompt 2) | E28, E32, E33, E34, E35, E37 qua API |
| `CustomerServiceTest`, `StaffServiceTest`, `CatalogServiceTest`, `MenuServiceTest`, `TokenServiceTest`, `NotificationServiceTest`, `OtpGeneratorTest` | Unit (2026-11-08) | Quy tắc CRUD / đăng nhập ở mức service: E17, E20, E21, E23 |
| `AuthIntegrationTest.phoneRegistrationWithOtpClaimsEarlierGuestBookings` | Integration | E4, E5, E13 |
| `AuthIntegrationTest.emailRegistrationAndLogin` | Integration | E7, E15 |
| `AuthIntegrationTest.otpRulesAreEnforcedThroughApi` | Integration | E8, E9, E12 |
| `AuthIntegrationTest.protectedApisNeedValidTokenAndRole` | Integration | E1, E2, E3 |
| `AuthIntegrationTest.lockedOrWrongStaffCannotLogIn` | Integration | E13, E14 |
| `AdminCrudIntegrationTest.adminCreatesBranchManagerUpdatesStaffReads` | Integration | E2, E17, E20 |
| `AdminCrudIntegrationTest.cannotDeleteBranchThatStillHasZones` | Integration | E18 |
| `AdminCrudIntegrationTest.managerManagesResourceAndItsAmenities` | Integration | E2, E19, E23 |
| `AdminCrudIntegrationTest.overlappingPriceRuleIsRejectedAndTechnicianCannotSeePrices` | Integration | E2, E20 |
| `AdminCrudIntegrationTest.onlyAdminManagesStaffAccounts` | Integration | E2, E17, E19, E21, E22 |
| `AdminCrudIntegrationTest.vouchersAndMembershipPlansAreManagedByManagers` | Integration | E2, E20 |
| `AdminCrudIntegrationTest.addonsAndMenuAreManagedByManagers` | Integration | E3, E18 |
| `AdminCrudIntegrationTest.receptionistManagesCustomersButOnlyAdminDeletes` | Integration | E2, E17 |
| `BookingExtrasIntegrationTest.customersSeeOnlyTheirBookingsAndStaffCanFilterByCustomer` (ticket booking.md) | Integration | E16 |
| `BookingFlowIntegrationTest`, `AvailabilityIntegrationTest` (cũ, thêm token) | Integration | Luồng cũ chạy đúng với phân quyền mới |
| — | — | E24 (đồng thời), E25 (DB lỗi), E26 (SMS lỗi), E27 (dữ liệu cũ): không có test tự động — dựa vào ràng buộc DB / chạy SQL tay |

### 7.2. Kiểm tra tay

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=h2

curl -X POST localhost:8080/api/auth/staff/login -H "Content-Type: application/json" -d "{\"email\":\"admin@cowork.example\",\"password\":\"Cowork@123\"}"
curl localhost:8080/api/admin/staff -H "Authorization: Bearer <accessToken>"

curl -X POST localhost:8080/api/auth/otp -H "Content-Type: application/json" -d "{\"phone\":\"0912000111\"}"
# mã OTP nằm trong log của ứng dụng: [notify] ... Ma xac thuc Cowork cua ban la xxxxxx
curl -X POST localhost:8080/api/auth/register/phone -H "Content-Type: application/json" -d "{\"phone\":\"0912000111\",\"otp\":\"xxxxxx\",\"fullName\":\"Nguyen Van A\",\"password\":\"matkhau123\"}"
```

Kết quả mong đợi: đăng nhập trả `accessToken`; `/api/admin/staff` trả 5 nhân viên mẫu; gọi lại không header → `401 UNAUTHORIZED`; đăng ký trả `201` kèm token.

Prompt 2:

```bash
curl -X PUT localhost:8080/api/auth/me -H "Authorization: Bearer <token khách>" -H "Content-Type: application/json" -d "{\"fullName\":\"Nguyen Van B\",\"notifyBeforeMinutes\":30}"
curl -X POST localhost:8080/api/auth/me/password -H "Authorization: Bearer <token khách>" -H "Content-Type: application/json" -d "{\"currentPassword\":\"matkhau123\",\"newPassword\":\"matkhaumoi1\"}"
curl -X POST localhost:8080/api/auth/otp -H "Content-Type: application/json" -d "{\"phone\":\"0912000111\"}"
curl -X POST localhost:8080/api/auth/password/reset -H "Content-Type: application/json" -d "{\"phone\":\"0912000111\",\"otp\":\"xxxxxx\",\"newPassword\":\"matkhaumoi2\"}"
```

Kết quả mong đợi: `PUT` trả hồ sơ có `membership.remainingMinutes`; đổi mật khẩu trả `204`; đặt lại trả `200` kèm token mới.

---

## 8. Sau khi AI làm xong — tự đánh giá spec

| Câu hỏi | Trả lời |
|---|---|
| Code chạy đúng ngay lần đầu? | Không hoàn toàn: lần chạy đầu 127/129 qua; 2 lỗi do `@EntityGraph` kiểu FETCH làm các danh sách EAGER của `Booking` thành LAZY (ticket booking.md) — đã đổi sang kiểu LOAD |
| Phải chat lại mấy lần để sửa? | 1 lần hỏi lại trước khi code (thư viện, phạm vi đăng ký, phạm vi CRUD) |
| AI làm sai ở đâu? | Không có lỗi logic phát hiện sau khi test; xem các điểm chưa kiểm chứng bên dưới |
| Spec bị hở ở mục nào (thiếu edge case, mơ hồ, thiếu tên file...)? | (1) Prompt liệt kê "RECEPTIONIST" 2 lần, không có role khách → AI thêm role `CUSTOMER` cho khách có tài khoản. (2) "CRUD tương ứng với các bảng" quá rộng → đã hỏi, chọn bảng danh mục. (3) AI tự quyết: email đăng nhập phải duy nhất trong các tài khoản; đăng ký email vẫn bắt nhập SĐT (cột `customer.phone` NOT NULL) và không được nhận dòng khách vãng lai (tránh chiếm đơn người khác); đăng nhập OTP chỉ cho SĐT đã có tài khoản; ADMIN không tự khóa / tự đổi vai trò; khách xem đơn người khác nhận 404 thay vì 403; token 8 giờ, không refresh. (4) **Chưa kiểm chứng trên PostgreSQL**: test chạy H2 — index từng phần `ux_customer_email_account` và câu migration 3.2 chưa chạy thật. (5) `CLAUDE.md` mục 3.1 ghi "ĐÚNG 35 entity" — nay 36 bảng / 36 entity; AI không được sửa CLAUDE.md, người dùng nên cập nhật. (6) Template `AI-response-template.md` đang có một dòng lạ ở mục 1 ("Viết tính năng đăng nhập, đăng kí bằng sđt(otp) hoặc email. Customer") — không phải dòng hướng dẫn `>`, AI không chép sang và không sửa template. (7) 2026-11-05: `unit-test.md` không nói "biến không phải object" gồm gì; `AuthService` không có tham số kiểu nguyên thủy nên AI áp VP1 cho SĐT (chuỗi chữ số: min, max, null, rỗng), các chuỗi khác dùng VP2 và VP7. Không dùng VP4, VP5, VP8, VP9, VP10 vì hàm không có vòng lặp, `try/catch`, đa luồng và lần này không sửa code chính. Test ghi nhận hành vi hiện tại khi gọi thẳng service: thiếu trường bắt buộc thì `NullPointerException`, `registerByEmail` với email rỗng vẫn tạo tài khoản (`test_registerByEmail_29`); ở API các trường hợp này do `@Valid` chặn |
| Prompt 2 — chạy đúng ngay lần đầu? | Có: test mới và cũ qua ngay; chỉ phải sửa `AuthServiceTest` vì constructor `AuthService` thêm `MembershipService` |
| Prompt 2 — spec bị hở | (1) Prompt không nói quên mật khẩu xác thực bằng gì → đã hỏi, chọn OTP SĐT. (2) AI tự quyết: không cho đổi SĐT trong hồ sơ; sai mật khẩu hiện tại trả `422` (không phải `401`); đặt lại mật khẩu xong đăng nhập luôn (trả token); hồ sơ trả số **phút** (không làm tròn ra giờ); gói `ACTIVE` quá hạn hiển thị `EXPIRED` dù database chưa đổi trạng thái; token cũ không bị thu hồi khi đổi mật khẩu. (3) Yêu cầu chat "unit test cho tất cả các file service": AI hiểu là 19 file trong `business/service/` (gồm `BookingWindowPolicy`, `OtpGenerator` là `@Component`); `business/job/BookingJobs` chỉ gọi service nên không viết unit test. Trong lúc viết test phát hiện `CatalogService.requireNoOverlap` gọi `r.getId().equals(...)` — nếu một mức giá chưa có id sẽ `NullPointerException`; với dữ liệu thật id luôn có nên chưa sửa |
| Lần sau cần bổ sung gì vào template? | Mục "Câu hỏi đã hỏi lại người dùng" (đang ghi tạm trong mục 1); cột "Quyền" trong mỗi khối API ở mục 4 |

---

## Checklist

- [x] Mục 2 có tên file và tên hàm thật, đã mở code ra kiểm tra.
- [x] Mục 3 ghi đủ kiểu dữ liệu, null, mặc định, dữ liệu cũ.
- [x] Mục 5 không có câu kiểu "xử lý cho phù hợp" hay "kiểm tra hợp lệ" mà không nói kiểm tra cái gì.
- [x] Mục 6 có ít nhất: quyền, đầu vào sai, không tồn tại, sai trạng thái, giá trị ở biên, đồng thời, lỗi giữa chừng.
- [x] Mỗi edge case có kết quả cụ thể: mã HTTP + mã lỗi + trạng thái dữ liệu.
- [x] Đã ghi mục "Ngoài phạm vi" và "File không được đụng".
