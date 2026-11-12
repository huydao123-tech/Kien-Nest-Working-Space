# SPEC: Add-on, tổng tiền (thuê + add-on − voucher − membership), đơn của tôi, hủy / đổi chỗ trước 2 giờ (đắt hơn thì bù tiền)

| Mục | Nội dung |
|---|---|
| Loại | Tính năng mới |
| Liên kết | ticket-request/booking.md Prompt 1, Prompt 2 · UC03, UC05, UC06 · FR-BOOK-02, FR-BOOK-06, FR-BOOK-08, FR-BOOK-09, FR-PROMO-01..03 · BR-01, BR-02, BR-03, BR-12, BR-13 (thay BR-15) · SRS Q8 |
| Người viết | huydao123-tech (code và spec do AI viết) |
| Ngày | 2026-11-03 (Prompt 1), 2026-11-08 (Prompt 2) |

---

## 1. Mục tiêu (Goal)

- Khi giữ chỗ, khách chọn **add-on kèm số lượng**; hệ thống kiểm tra tồn kho add-on **theo khung giờ** ngay lúc đặt và cộng tiền thuê add-on vào tổng.
- Mọi khoản tiền của đơn nằm trong `booking_charge`, tính theo block: **Tổng = tiền thuê + add-on − voucher − khấu trừ membership**.
- Khách xem được "đơn của tôi"; nhân viên xem được toàn bộ đơn và lọc theo khách (id hoặc SĐT), trạng thái, khoảng ngày.
- Khách đã thanh toán được **hủy** đơn nếu còn ≥ 2 giờ trước giờ bắt đầu, **hoàn 100%**; cùng quy tắc cho **đổi khung giờ**.

Đã xử lý: **ticket-request/booking.md — Prompt 1** (2026-11-03) và **Prompt 2** (2026-11-08).

**Prompt 2 (FR-BOOK-09):** khách (hoặc lễ tân) đổi chỗ đơn đã thanh toán trước giờ check-in **≥ 2 tiếng**, được đổi **vị trí, khung giờ và thời lượng**. Chỗ mới rẻ hơn hoặc bằng → đổi ngay, hoàn phần chênh. Chỗ mới **đắt hơn** → hệ thống **giữ chỗ mới 10 phút** chờ khách **bù phần chênh** qua cổng thanh toán; trả xong đơn mới chuyển sang chỗ mới, quá hạn / thanh toán lỗi thì đơn giữ nguyên chỗ cũ.

Câu đã hỏi lại người dùng ở Prompt 2: "Chỗ mới đắt hơn thì phần bù xử lý thế nào?" (3 lựa chọn: đổi ngay ghi nợ / giữ chỗ mới chờ trả / thu khi check-out) → người dùng chọn **giữ chỗ mới chờ trả** (phải thêm cột vào bảng `booking`, người dùng tự chạy DDL ở mục 3.2).

Unit test của luồng này (Voucher, Membership, Addon, Payment, Refund, Booking) được viết lại đủ ngày 2026-11-08 theo yêu cầu chat "unit test cho tất cả các file service" — chi tiết ở `ticket-response/authentication.md` mục 7.

Câu đã hỏi lại người dùng: voucher và membership chưa có code → người dùng chọn **làm đủ** (BR-12, BR-13, không dùng chung – Q8); chính sách hủy → **theo prompt** (≥ 2 giờ hoàn 100%, < 2 giờ từ chối), thay cho BR-15 trong SRS (≥ 24h 100%, 2–24h 50%, < 2h 0%).

Phần "xem đơn của tôi" cần biết ai đang gọi → dùng đăng nhập JWT của ticket `authentication.md`.

### 1.1. Bug (chỉ điền nếu là sửa lỗi)

Không có — tính năng mới. (Quy tắc hủy cũ BR-15 trong `BookingService.cancel` được thay theo yêu cầu, không phải bug.)

### 1.2. Ngoài phạm vi (Out of scope)

- Không làm giao diện.
- Không gọi API hoàn tiền của cổng thanh toán: hủy / đổi giờ rẻ hơn chỉ ghi dòng `refund` trạng thái `REQUESTED`. Đơn trả tiền ngoài cổng (thu tại quầy) không có giao dịch để ghi refund → chỉ ghi log cảnh báo.
- Prompt 1: đổi khung giờ chỉ cùng vị trí, cùng thời lượng, đắt hơn thì từ chối — **đã thay bằng Prompt 2**. Prompt 2 vẫn **không** làm:
  - đổi add-on / số lượng, đổi voucher, chuyển sang dùng membership khi đổi chỗ;
  - đổi sang vị trí ở **chi nhánh khác** khi đơn có add-on (add-on thuộc chi nhánh cũ) → `409 ADDON_NOT_AVAILABLE`; đơn không có add-on thì đổi được;
  - trừ thêm phút membership khi thời lượng mới dài hơn (phần dài thêm tính tiền thường);
  - hủy yêu cầu đổi chỗ đang chờ bằng API (chờ 10 phút tự hết hạn, hoặc hủy cả đơn);
  - khóa giá lúc tạo yêu cầu: lúc tiền về hệ thống tính lại giá; nếu quản lý sửa bảng giá trong 10 phút đó thì tổng có thể lệch số đã thu (thấp hơn → hoàn chênh, cao hơn → còn nợ, thu khi check-out).
- Hủy / đổi giờ chỉ cho đơn `CONFIRMED` (đã thanh toán). Đơn `HELD` chưa trả tiền không có API hủy (tự hết hạn sau 10 phút).
- Không thêm / bớt add-on sau khi đã đặt. Add-on chỉ áp tiền thuê theo block của khung giờ đặt, không tính vào phần gia hạn / quá giờ.
- Membership chỉ trừ vào tiền thuê (không trừ add-on); không cho khách chọn gói cụ thể (tự lấy gói hết hạn sớm nhất).
- Không kích hoạt lại đơn có voucher / membership khi webhook thanh toán về trễ sau lúc hết hạn: đơn chuyển `REFUND_PENDING` (lượt / phút đã trả lại lúc hết hạn).
- Không cập nhật thông báo hẹn giờ (`notification_job`) khi đổi giờ (UC26 chưa làm).
- Không tách dòng `RENTAL` theo từng khung giá (cao điểm / thấp điểm): một dòng `RENTAL` = tổng giá các block.

---

## 2. Context — code hiện tại liên quan

### 2.1. Luồng hiện tại

```
POST /api/bookings                         (công khai; gửi token khách thì đơn thuộc tài khoản)
  → BookingController.hold()
  → BookingService.hold(request, actor)    (business)
      → SpaceResourceRepository.findByIdForUpdate()      khóa vị trí (BR-01)
      → BookingWindowPolicy.validate(), ResourceService.requireBookable(), existsOccupying()
      → PricingService.quote()                           số block + tiền thuê (BR-03, BR-04)
      → AddonService.reserve()                           khóa add-on, kiểm tồn kho theo khung giờ, tính tiền
      → bookings.save()                                  booking + booking_charge (RENTAL, ADDON...) + booking_addon
      → VoucherService.redeem()  hoặc  MembershipService.deduct()   → dòng VOUCHER_DISCOUNT / MEMBERSHIP_DEDUCTION
      → tổng = 0 → CONFIRMED ngay (không qua cổng)
  → bảng: booking, booking_charge, booking_addon, booking_status_history, voucher, voucher_redemption,
          membership_pass, membership_usage

POST /api/bookings/{id}/cancel         → BookingService.cancel(id, actor)      → RefundService.request(), Voucher/MembershipService.release()
POST /api/bookings/{id}/reschedule     → BookingService.reschedule(id, request, actor)   (Prompt 2)
      → lockResource(vị trí mới) → BookingWindowPolicy.validate → ResourceService.requireBookable → existsOccupying (gồm chỗ đang chờ đổi)
      → quoteChange: AddonService.recheck(booking, chi nhánh mới, khung mới), PricingService.quote,
                     VoucherService.quoteChange, MembershipService.quoteChange
      → tổng mới ≤ đã trả: applyChange (VoucherService.applyChange, MembershipService.applyChange, sửa booking_charge,
                           đổi resource / period, RefundService.request phần chênh) → bookings.save → NotificationService.bookingRescheduled
      → tổng mới > đã trả: ghi booking.pending_* (giữ chỗ mới 10 phút) → bookings.save
POST /api/bookings/{id}/payments       → PaymentService.initiate: đơn CONFIRMED có pending → số tiền = pending_amount
Webhook thành công                     → BookingService.onPaymentSucceeded: CONFIRMED + pending → applyPendingChange
Webhook thất bại / BookingJobs.expirePendingChanges (30 giây) → xóa pending_*, NotificationService.rescheduleCancelled
GET  /api/me/bookings                  → BookingService.listMine(actor, ...)   → BookingRepository.findAll(Specification, Pageable)
GET  /api/bookings?customerId=&phone=  → BookingService.search(filter, ...)
Thanh toán thành công → BookingService.confirm() → markUsed (voucher USED, membership USED)
Hết hạn / thất bại    → expireHold() / onPaymentFailed() → release (trả lượt voucher, trả phút)
```

### 2.2. File sẽ phải sửa / tạo

| File | Sửa hay tạo mới | Sửa cái gì |
|---|---|---|
| `backend/db/schema.sql` | Sửa | `ck_booking_charge_type` thêm `ADDON`, `VOUCHER_DISCOUNT`, `MEMBERSHIP_DEDUCTION`; index `idx_booking_addon_addon` |
| `backend/db/data.sql` | Sửa | Đơn 2, 5 có dòng `ADDON` (tiền + thanh toán + hóa đơn khớp); đơn 7 dòng `VOUCHER_DISCOUNT`; đơn 8 dòng `MEMBERSHIP_DEDUCTION` (30 phút) |
| `backend/src/main/resources/application.yml` | Sửa | `booking.change-notice: 2h`; `hibernate.default_batch_fetch_size: 50` (danh sách đơn không N+1) |
| `config/BookingProperties.java` | Sửa | Field `changeNotice` |
| `config/DemoDataSeeder.java` | Sửa | Add-on, voucher, gói thành viên mẫu (giống `data.sql`) |
| `dataaccess/enums/ChargeType.java` | Sửa | Thêm `ADDON`, `VOUCHER_DISCOUNT`, `MEMBERSHIP_DEDUCTION`; `isDeduction()` |
| `dataaccess/entity/Booking.java` | Sửa | `getTotal()` = phải thu − giảm trừ; `sumOf(type)`; danh sách `addons` (EAGER) |
| `dataaccess/entity/BookingAddon.java` | Sửa | `addon` nạp EAGER (hiển thị mã / tên ngoài transaction) |
| `dataaccess/repository/BookingRepository.java` | Sửa | `JpaSpecificationExecutor`, `findAll(spec, pageable)` + `@EntityGraph(LOAD)` |
| `dataaccess/repository/BookingAddonRepository.java`, `VoucherRedemptionRepository.java`, `MembershipPassRepository.java`, `MembershipUsageRepository.java`, `RefundRepository.java` | Tạo | Truy vấn tồn kho, lượt voucher, gói còn dùng được (khóa ghi), refund |
| `dataaccess/repository/AddonRepository.java`, `VoucherRepository.java` | Tạo | `findByIdForUpdate`, `findByCodeForUpdate` (khóa ghi) |
| `business/service/BookingService.java` | Sửa | `hold` kèm add-on / voucher / membership; `cancel(id, actor)` quy tắc 2 giờ; `reschedule`; `get(id, actor)`, `listMine`, `search`; release / markUsed theo trạng thái |
| `business/service/AddonService.java` | Tạo | `reserve`, `recheck`, `peakUsage` (+ CRUD của ticket authentication.md) |
| `business/service/VoucherService.java` | Tạo | `redeem`, `markUsed`, `release`, `revalue`, `discountFor` (+ CRUD) |
| `business/service/MembershipService.java` | Tạo | `deduct`, `markUsed`, `release`, `revalue` (+ CRUD gói) |
| `business/service/RefundService.java` | Tạo | Ghi `refund` REQUESTED |
| `business/service/NotificationService.java` | Sửa | `bookingRescheduled(...)` |
| `dto/BookingDtos.java` | Sửa | `AddonSelection`, `HoldBookingRequest` (+ `addons`, `voucherCode`, `useMembership`, `contact` không bắt buộc khi đăng nhập), `RescheduleRequest/Response`, `BookingFilter`, `BookingSummary`, `BookingAddonResponse`, `PageResponse` |
| `presentation/controller/BookingController.java` | Sửa | `GET /api/bookings`, `GET /api/me/bookings`, `POST /reschedule`, quyền |
| `presentation/controller/CommerceController.java` | Tạo | `GET /api/addons?branchId=` (công khai) |
| `src/test/.../business/service/BookingServiceTest.java` | Sửa | Test mới mục 7.1; test hủy cũ (50% / 0%) thay theo quy tắc mới |
| `src/test/.../business/service/AddonServiceTest.java`, `VoucherServiceTest.java`, `MembershipServiceTest.java` | Tạo | Mục 7.1 |
| `src/test/.../BookingExtrasIntegrationTest.java` | Tạo | Mục 7.1 |
| `src/test/.../business/service/AvailabilityServiceTest.java`, `BookingWindowPolicyTest.java` | Sửa | Constructor `BookingProperties` thêm `changeNotice` |
| `README.md`, `backend/README.md`, `docs/database.md` | Sửa | API, quy tắc, ngày test, `booking_charge` |
| **Prompt 2 (2026-11-08)** | | |
| `backend/db/schema.sql` | Sửa | Bảng `booking` thêm 5 cột `pending_*`, `fk_booking_pending_resource`, `ck_booking_pending`, 2 index |
| `dataaccess/entity/Booking.java` | Sửa | 5 field `pending*`, `hasPendingChange()`, `getPendingPeriod()` |
| `dataaccess/repository/BookingRepository.java` | Sửa | `existsOccupying` kiểm cả chỗ đang chờ đổi (`existsPendingOverlap`); `findPendingResourceIds` / `findPendingOccupancy`; `findPendingChangeIdsExpiredAt` |
| `dataaccess/repository/BookingAddonRepository.java` | Sửa | `findPendingReservations` (add-on của đơn đang chờ đổi chiếm tồn kho ở khung mới) |
| `business/service/BookingService.java` | Sửa | `reschedule(id, RescheduleRequest, actor)` viết lại; `quoteChange`, `applyChange`, `applyPendingChange`, `refundUnexpectedPayment`, `clearPendingChange`; `onPaymentSucceeded` / `onPaymentFailed` xử lý đơn CONFIRMED có pending; `cancel` xóa pending; `expirePendingChange`, `findExpiredPendingChangeIds` |
| `business/service/AddonService.java` | Sửa | `recheck(booking, newBranch, newPeriod)` kiểm chi nhánh; `requireStock` cộng thêm add-on của đơn đang chờ đổi |
| `business/service/AvailabilityService.java` | Sửa | Chỗ đang chờ đổi hiện `HELD` trên sơ đồ, không trả trong tìm chỗ trống |
| `business/service/VoucherService.java` | Sửa | Bỏ `revalue`; thêm `quoteChange` (kiểm loại chỗ, giá trị tối thiểu ở chỗ mới), `applyChange`; tách `appliesTo` |
| `business/service/MembershipService.java` | Sửa | Bỏ `revalue`; thêm `quoteChange` (gói phải cho loại chỗ mới), `applyChange` (trả bớt phút khi thời lượng ngắn hơn); tách `returnMinutes` |
| `business/service/PaymentService.java` | Sửa | `initiate` cho đơn CONFIRMED có yêu cầu đổi chỗ; giao dịch đang mở khác số tiền → `UNCONFIRMED` — **ngoài mục 2.3 của Prompt 1** (Prompt 1 không đụng `PaymentService`), cần sửa vì phần bù đi qua cổng |
| `business/service/NotificationService.java` | Sửa | `rescheduleCancelled` |
| `business/job/BookingJobs.java` | Sửa | `expirePendingChanges` (cùng chu kỳ 30 giây với hết hạn giữ chỗ) |
| `dto/BookingDtos.java` | Sửa | `RescheduleRequest.resourceId`; `RescheduleResponse` thêm `paymentRequired`, `amountDue`; `PendingChangeResponse`; `BookingResponse.pendingChange` |
| `presentation/controller/BookingController.java` | Sửa | Truyền `RescheduleRequest`, trả response mới |
| `src/test/.../business/service/BookingServiceTest.java` | Sửa | Sửa 042, 061–079 theo hành vi mới; thêm 170–186 |
| `src/test/.../BookingExtrasIntegrationTest.java` | Sửa | Bỏ kiểm tra `RESCHEDULE_PRICE_INCREASE` / `DURATION_MISMATCH`; thêm `rescheduleToPricierSlotHoldsNewSlotUntilTopUpPaid` (ngày 16/11) |
| `README.md` (ngày test), `backend/README.md` (API), `docs/database.md` (cột `pending_*`), `checklist-use-case.md` | Sửa | |

### 2.3. File KHÔNG được đụng vào

- `ticket-request/*.md`, `ticket-response/AI-response-template.md`.
- `PricingService` (giữ nguyên cách tính block). `PaymentService`: Prompt 1 không đụng; Prompt 2 phải sửa `initiate` (xem 2.2), đơn HELD vẫn thanh toán theo `getOutstanding()`.
- `backend/db/data.sql`, `config/DemoDataSeeder.java`: Prompt 2 không cần đổi (cột mới cho phép rỗng; `data.sql` chèn `booking` theo danh sách cột).
- `docs/SRS.md`: BR-15 vẫn ghi chính sách cũ — người dùng tự cập nhật nếu muốn (xem mục 8).

### 2.4. Quy ước code phải theo

- Đổi trạng thái đơn chỉ qua `BookingService.changeStatus`.
- Thứ tự khóa: vị trí → add-on (id tăng dần) → voucher → gói thành viên, tránh hai giao dịch khóa chéo nhau.
- `booking_charge.amount` luôn ≥ 0; dòng giảm trừ nhận biết qua `ChargeType.isDeduction()`.
- Tiền dùng `Money`; mốc 2 giờ đọc từ `booking.change-notice`, không viết số cứng.
- Thông báo gửi sau khi lưu.

---

## 3. Data Model Change — thay đổi database

### 3.1. Thay đổi bảng

| Bảng | Thay đổi | Cột | Kiểu | Null? | Mặc định | Ràng buộc / Ghi chú |
|---|---|---|---|---|---|---|
| `booking_charge` | Sửa CHECK | `charge_type` | `varchar(30)` | Không | | `ck_booking_charge_type`: thêm `ADDON`, `VOUCHER_DISCOUNT`, `MEMBERSHIP_DEDUCTION` |
| `booking_addon` | Thêm index | `addon_id` | | | | `idx_booking_addon_addon` — đếm tồn kho add-on theo khung giờ |

Các bảng `addon`, `booking_addon`, `voucher*`, `membership*`, `refund` đã có từ trước, không đổi cột.

**Prompt 2** — bảng `booking` (đổi chỗ chờ thanh toán phần chênh; năm cột cùng rỗng hoặc cùng có giá trị):

| Bảng | Thay đổi | Cột | Kiểu | Null? | Mặc định | Ràng buộc / Ghi chú |
|---|---|---|---|---|---|---|
| `booking` | Thêm cột | `pending_resource_id` | `bigint` | Có | `NULL` | `fk_booking_pending_resource` → `space_resource(id)` |
| `booking` | Thêm cột | `pending_start_at` | `timestamp(6)` | Có | `NULL` | Khung giờ mới đang giữ |
| `booking` | Thêm cột | `pending_end_at` | `timestamp(6)` | Có | `NULL` | `> pending_start_at` |
| `booking` | Thêm cột | `pending_amount` | `bigint` | Có | `NULL` | `> 0`, số tiền phải bù |
| `booking` | Thêm cột | `pending_expires_at` | `timestamp(6)` | Có | `NULL` | Hạn trả (`now + booking.hold-duration`) |
| `booking` | Thêm CHECK | | | | | `ck_booking_pending`: cả 5 rỗng, hoặc cả 5 có giá trị hợp lệ |
| `booking` | Thêm index | | | | | `idx_booking_pending_resource`, `idx_booking_pending_expiry` (chỉ dòng có pending) |

Chỗ đang chờ đổi **không** nằm trong `ex_booking_no_overlap` (ràng buộc này chỉ xét `resource_id`, `start_at`, `end_at`): chỉ được bảo vệ ở tầng service bằng khóa dòng `space_resource` (mọi thao tác chiếm chỗ đều khóa vị trí trước khi kiểm tra trùng).

### 3.2. SQL

Chạy bằng tài khoản **`postgres`**:

```sql
ALTER TABLE booking_charge DROP CONSTRAINT IF EXISTS ck_booking_charge_type;
ALTER TABLE booking_charge ADD CONSTRAINT ck_booking_charge_type CHECK (charge_type IN (
    'RENTAL', 'ADDON', 'EXTENSION', 'OVERSTAY_SURCHARGE', 'VOUCHER_DISCOUNT', 'MEMBERSHIP_DEDUCTION'));

CREATE INDEX IF NOT EXISTS idx_booking_addon_addon ON booking_addon (addon_id);
```

Máy dev: có thể chạy lại cả `schema.sql` rồi `data.sql` thay cho 2 câu trên (mất dữ liệu cũ).

**Prompt 2 — bắt buộc chạy trước khi khởi động app** (profile mặc định `ddl-auto: validate` sẽ dừng với `Schema-validation: missing column [pending_amount] in table [booking]` nếu chưa chạy). Tài khoản **`postgres`**, database `working-space`; chạy lại nhiều lần không lỗi:

```sql
ALTER TABLE booking ADD COLUMN IF NOT EXISTS pending_resource_id bigint;
ALTER TABLE booking ADD COLUMN IF NOT EXISTS pending_start_at    timestamp(6);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS pending_end_at      timestamp(6);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS pending_amount      bigint;
ALTER TABLE booking ADD COLUMN IF NOT EXISTS pending_expires_at  timestamp(6);

ALTER TABLE booking DROP CONSTRAINT IF EXISTS fk_booking_pending_resource;
ALTER TABLE booking ADD CONSTRAINT fk_booking_pending_resource
    FOREIGN KEY (pending_resource_id) REFERENCES space_resource (id);

ALTER TABLE booking DROP CONSTRAINT IF EXISTS ck_booking_pending;
ALTER TABLE booking ADD CONSTRAINT ck_booking_pending CHECK (
    (pending_resource_id IS NULL AND pending_start_at IS NULL AND pending_end_at IS NULL
        AND pending_amount IS NULL AND pending_expires_at IS NULL)
    OR (pending_resource_id IS NOT NULL AND pending_end_at > pending_start_at
        AND pending_amount > 0 AND pending_expires_at IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_booking_pending_resource ON booking (pending_resource_id) WHERE pending_resource_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_booking_pending_expiry ON booking (pending_expires_at) WHERE pending_expires_at IS NOT NULL;
```

User `cowork` đã có quyền trên bảng `booking` nên không cần `GRANT` thêm.

### 3.3. Dữ liệu cũ

- Đơn cũ chỉ có dòng `RENTAL` / `EXTENSION` / `OVERSTAY_SURCHARGE` → tổng tiền không đổi.
- Đơn cũ có `booking_addon` nhưng **không có dòng `ADDON`** (dữ liệu mẫu cũ của đơn 2, 5) → tiền add-on không nằm trong tổng. `data.sql` mới đã bổ sung; database đang có dữ liệu mẫu cũ thì chạy lại `data.sql`.
- Voucher / gói đã có `used_count`, `remaining_minutes` cũ → giữ nguyên.
- Prompt 2: mọi đơn đang có nhận `pending_* = NULL` (không có yêu cầu đổi chỗ) → hành vi như cũ. Không cần cập nhật dữ liệu.

### 3.4. Entity / DTO cần cập nhật

| Lớp | Field mới | Kiểu Java |
|---|---|---|
| `ChargeType` | `ADDON`, `VOUCHER_DISCOUNT`, `MEMBERSHIP_DEDUCTION`, `deduction` | enum, `boolean` |
| `Booking` | `addons` | `List<BookingAddon>` |
| `BookingProperties` | `changeNotice` | `Duration` |
| `HoldBookingRequest` | `addons`, `voucherCode`, `useMembership` | `List<AddonSelection>`, `String`, `boolean` |
| `BookingResponse` | `addons` | `List<BookingAddonResponse>` |
| `Booking` (Prompt 2) | `pendingResource`, `pendingStartAt`, `pendingEndAt`, `pendingAmount`, `pendingExpiresAt` | `SpaceResource` (LAZY), `LocalDateTime`, `LocalDateTime`, `Long`, `LocalDateTime` |
| `RescheduleRequest` (Prompt 2) | `resourceId` (không bắt buộc) | `Long` |
| `RescheduleResponse` (Prompt 2) | `paymentRequired`, `amountDue` | `boolean`, `long` |
| `BookingResponse` (Prompt 2) | `pendingChange` | `PendingChangeResponse` (null nếu không có) |
| `BookingService.RescheduleResult` (Prompt 2) | `amountDue`, `paymentRequired()` | `Money`, `boolean` |

---

## 4. API Contract

### `POST /api/bookings` — sửa (công khai)

**Request**
```json
{
  "resourceId": "số — bắt buộc",
  "startAt": "ISO — bắt buộc", "endAt": "ISO — bắt buộc",
  "partySize": "số >= 1",
  "contact": "{fullName, phone, email?} — bắt buộc với khách vãng lai; bỏ trống khi gửi token khách",
  "addons": "[{ addonId: số — bắt buộc, quantity: số >= 1 }] — không bắt buộc; mỗi add-on một dòng",
  "voucherCode": "chuỗi <= 30 — không bắt buộc, không phân biệt hoa thường",
  "useMembership": "true/false — mặc định false; cần token khách; không dùng chung với voucherCode"
}
```

**Response thành công** — `201` (thêm `addons`, các dòng `charges` mới)
```json
{
  "id": 31, "status": "HELD", "totalAmount": 840000, "paidAmount": 0, "outstandingAmount": 840000,
  "charges": [
    { "type": "RENTAL", "amount": 600000, "startAt": "2026-11-11T09:00:00", "endAt": "2026-11-11T11:00:00" },
    { "type": "ADDON",  "amount": 240000, "startAt": "2026-11-11T09:00:00", "endAt": "2026-11-11T11:00:00" }
  ],
  "addons": [ { "addonId": 1, "code": "PROJECTOR", "name": "Máy chiếu", "quantity": 2, "unitPrice": 30000 } ]
}
```
Membership phủ hết tiền → `status: "CONFIRMED"`, `totalAmount: 0`.

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `quantity < 1`, thiếu `addonId`, `voucherCode` > 30 ký tự |
| 404 | `NOT_FOUND` | Add-on / mã voucher không tồn tại |
| 409 | `ADDON_OUT_OF_STOCK` | Tồn kho add-on không đủ trong khung giờ |
| 409 | `ADDON_NOT_AVAILABLE` | Add-on `INACTIVE` hoặc thuộc chi nhánh khác |
| 409 | `VOUCHER_USED_UP` | Voucher hết tổng lượt |
| 422 | `DUPLICATE_ADDON` | Một add-on xuất hiện 2 lần |
| 422 | `VOUCHER_INACTIVE` / `VOUCHER_EXPIRED` / `VOUCHER_CUSTOMER_LIMIT` / `VOUCHER_NOT_APPLICABLE` / `VOUCHER_MIN_ORDER` | Voucher tạm dừng / ngoài hạn / khách hết lượt / sai loại chỗ / chưa đạt giá trị tối thiểu |
| 422 | `VOUCHER_MEMBERSHIP_CONFLICT` | Gửi cả voucher và `useMembership` |
| 422 | `MEMBERSHIP_LOGIN_REQUIRED` · `NO_USABLE_MEMBERSHIP` | Dùng membership khi chưa đăng nhập · không có gói còn hiệu lực, còn ≥ 1 block, đúng loại chỗ |
| 422 | `INVALID_CONTACT` | Khách vãng lai không gửi `contact` |
| 409 / 422 | `SLOT_UNAVAILABLE`, `RESOURCE_NOT_BOOKABLE`, `CAPACITY_EXCEEDED`, `INVALID_TIME_RANGE`, `START_IN_PAST`, `DURATION_TOO_SHORT`, `OUTSIDE_OPENING_HOURS`, `BRANCH_CLOSED` | Như cũ |

### `GET /api/me/bookings?status=&page=0&size=20` — mới (role CUSTOMER)

**Response** `200`
```json
{ "items": [ { "id": 31, "code": "BK...", "customerId": 7, "customerName": "...", "customerPhone": "...",
               "resourceId": 12, "resourceCode": "MR-01", "startAt": "...", "endAt": "...", "status": "HELD",
               "totalAmount": 840000, "paidAmount": 0, "createdAt": "..." } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```
Sắp xếp: giờ bắt đầu mới nhất trước.

### `GET /api/bookings?customerId=&phone=&status=&from=&to=&page=&size=` — mới (RECEPTIONIST, MANAGER, ADMIN)

Mọi tham số tùy chọn; `from`/`to` lọc theo giờ bắt đầu `[from, to)`. Response như trên.

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `status` không thuộc enum, ngày sai định dạng |
| 401 / 403 | `UNAUTHORIZED` / `FORBIDDEN` | Không token / sai vai trò (khách, kỹ thuật) |
| 422 | `INVALID_TIME_RANGE` · `INVALID_PAGE` | `to <= from` · `page < 0`, `size` ngoài 1..100 |

### `GET /api/bookings/{id}` — sửa quyền

Khách chỉ xem đơn của mình (đơn người khác → `404 NOT_FOUND`); nhân viên quầy xem mọi đơn. Response thêm `addons`.

### `POST /api/bookings/{id}/cancel` — sửa quy tắc

**Response** `200` `{ "bookingId": 31, "status": "CANCELLED", "refundAmount": 550000 }`

| HTTP | `code` | Khi nào |
|---|---|---|
| 404 | `NOT_FOUND` | Đơn không tồn tại / không phải của khách |
| 422 | `INVALID_BOOKING_STATE` | Đơn không ở `CONFIRMED` (chưa thanh toán, đã check-in, đã hủy...) |
| 422 | `CHANGE_DEADLINE_PASSED` | Còn ít hơn 2 giờ tới giờ bắt đầu |

### `POST /api/bookings/{id}/reschedule` — Prompt 2 sửa (khách: đơn của mình; RECEPTIONIST, MANAGER, ADMIN)

**Request**
```json
{
  "resourceId": "number — không bắt buộc — bỏ trống = giữ vị trí hiện tại",
  "startAt": "ISO — bắt buộc",
  "endAt": "ISO — bắt buộc — được khác thời lượng cũ (tối thiểu 1 giờ)"
}
```

**Response** `200` — hai trường hợp:

```json
{ "booking": { "...BookingResponse ở chỗ mới": "", "pendingChange": null },
  "paymentRequired": false, "amountDue": 0, "refundAmount": 40000 }
```
```json
{ "booking": { "...BookingResponse vẫn ở chỗ cũ": "",
               "pendingChange": { "resourceId": 7, "startAt": "2026-11-16T10:00:00", "endAt": "2026-11-16T13:00:00",
                                  "amountDue": 110000, "expiresAt": "2026-11-16T07:10:00" } },
  "paymentRequired": true, "amountDue": 110000, "refundAmount": 0 }
```

Trường hợp 2: gọi `POST /api/bookings/{id}/payments {method}` để thanh toán `amountDue` trước `expiresAt`. Thanh toán thành công → đơn chuyển sang chỗ mới (`GET /api/bookings/{id}` thấy khung giờ, vị trí, tổng tiền mới; `pendingChange = null`).

Trường hợp đổi khác: `RESCHEDULE_PRICE_INCREASE` và `RESCHEDULE_DURATION_MISMATCH` của Prompt 1 **không còn**.

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Thiếu `startAt` / `endAt` |
| 404 | `NOT_FOUND` | Đơn không tồn tại / không phải của khách; `resourceId` không tồn tại |
| 409 | `SLOT_UNAVAILABLE` · `ADDON_OUT_OF_STOCK` · `RESOURCE_NOT_BOOKABLE` | Chỗ mới đã có người đặt / đang được giữ · add-on không đủ ở khung mới · vị trí bảo trì |
| 409 | `ADDON_NOT_AVAILABLE` | Đơn có add-on mà vị trí mới ở chi nhánh khác |
| 409 | `RESCHEDULE_PAYMENT_PENDING` | Đang có yêu cầu đổi chỗ chờ thanh toán, chưa hết hạn |
| 422 | `RESCHEDULE_SAME_SLOT` | Cùng vị trí, cùng khung giờ |
| 422 | `CAPACITY_EXCEEDED` | Vị trí mới nhỏ hơn số người của đơn |
| 422 | `VOUCHER_NOT_APPLICABLE` · `VOUCHER_MIN_ORDER` | Voucher của đơn không áp cho loại chỗ mới · chỗ mới chưa đạt giá trị tối thiểu |
| 422 | `MEMBERSHIP_NOT_APPLICABLE` | Gói thành viên của đơn không dùng cho loại chỗ mới |
| 422 | `CHANGE_DEADLINE_PASSED` · `INVALID_BOOKING_STATE` | Còn < 2 giờ tới giờ bắt đầu hiện tại · đơn không ở `CONFIRMED` |
| 422 | `INVALID_TIME_RANGE`, `START_IN_PAST`, `DURATION_TOO_SHORT`, `OUTSIDE_OPENING_HOURS`, `BRANCH_CLOSED` | Khung mới không hợp lệ |

### `POST /api/bookings/{id}/payments` — Prompt 2 sửa (công khai)

Ngoài đơn `HELD`, nhận thêm đơn `CONFIRMED` có `pendingChange`: số tiền = `pendingChange.amountDue`, hạn thanh toán gửi cổng = `pendingChange.expiresAt`. Đơn `CONFIRMED` không có yêu cầu đổi → `409 BOOKING_NOT_PAYABLE` (như cũ). Có giao dịch đang mở đúng số tiền → trả lại giao dịch đó; khác số tiền (của yêu cầu trước) → giao dịch cũ chuyển `UNCONFIRMED`, tạo giao dịch mới.

### `GET /api/bookings/{id}`, `POST /api/bookings` — Prompt 2: response thêm `pendingChange`

`BookingResponse` thêm trường `pendingChange` (null khi không có yêu cầu đổi chỗ). Thêm trường, không đổi trường cũ.

### `GET /api/addons?branchId=` — mới (công khai)

`200` `[ { "id": 1, "branchId": 1, "code": "MIC", "name": "Micro không dây", "stock": 4, "pricePerBlock": 15000, "status": "ACTIVE" } ]`

---

## 5. Logic Flow — các bước xử lý

**Giữ chỗ** (`BookingService.hold(request, actor)`):
1. Khóa vị trí (`findByIdForUpdate`), kiểm khung giờ, sức chứa, chồng lấn (như cũ).
2. Có `voucherCode` và `useMembership` → `VOUCHER_MEMBERSHIP_CONFLICT`. `useMembership` mà không phải khách đăng nhập → `MEMBERSHIP_LOGIN_REQUIRED`.
3. Khách: đăng nhập → hồ sơ tài khoản; vãng lai → bắt buộc `contact`, `registerOrUpdate` theo SĐT.
4. `PricingService.quote` → số block `n`, tiền thuê.
5. `AddonService.reserve(chi nhánh, khung giờ, n, addons)`: lặp add-on → `NOT_FOUND`; theo id tăng dần: khóa dòng add-on → phải `ACTIVE` và cùng chi nhánh vị trí (`ADDON_NOT_AVAILABLE`) → lấy các đơn `HELD/CONFIRMED/CHECKED_IN/OVERSTAY` có add-on này giao khung giờ → tính **lúc dùng nhiều nhất** trong khung → `số lượng > stock − đỉnh` thì `ADDON_OUT_OF_STOCK`. Tiền = `price_per_block × n × quantity`.
6. Tạo đơn `HELD`, dòng `RENTAL`, mỗi add-on một dòng `booking_addon` (đơn giá block lúc đặt) + một dòng `ADDON`; lưu (để có id).
7. Voucher (`VoucherService.redeem`): khóa voucher theo mã → kiểm lần lượt `ACTIVE` → `valid_from ≤ now < valid_to` → `used_count < total_limit` → lượt của khách (HELD + USED) `< per_customer_limit` → loại chỗ → tạm tính (thuê + add-on) `≥ min_order_amount` → tính tiền giảm (5.1) → `used_count + 1`, lưu `voucher_redemption` HELD → dòng `VOUCHER_DISCOUNT`.
8. Membership (`MembershipService.deduct`): khóa các gói `ACTIVE`, còn phút, `expires_at > giờ bắt đầu`, hết hạn sớm nhất trước → chọn gói đúng loại chỗ và còn ≥ 1 block → trừ phút (5.1) → `membership_usage` HELD → dòng `MEMBERSHIP_DEDUCTION`.
9. Tổng = 0 → `CONFIRMED` ngay, voucher / membership `USED`, gửi xác nhận. Ngược lại giữ `HELD`, chờ thanh toán `outstanding`.

**Thanh toán / hết hạn**: thành công → `CONFIRMED` + `markUsed`. Thất bại (`FAILED`) / hết hạn (`EXPIRED`) → `release`: redemption `RELEASED`, `used_count − 1`; usage `RELEASED`, cộng lại phút, gói `USED_UP` → `ACTIVE` (hoặc `EXPIRED` nếu đã quá hạn). Webhook về trễ cho đơn có voucher / membership → `REFUND_PENDING`.

**Hủy** (`cancel(id, actor)`): tải đơn (khách khác → 404) → phải `CONFIRMED` → `now ≤ start − 2h` → `CANCELLED` (ghi lịch sử) → trả lượt / phút → `RefundService.request(paid, "CUSTOMER_CANCELLED")` (giao dịch `SUCCEEDED` mới nhất; tiền 0 thì bỏ qua) → lưu → gửi tin.

**Đổi chỗ** (`BookingService.reschedule(id, request, actor)`, Prompt 2 — thay luồng đổi khung giờ của Prompt 1):
1. `loadFor`: khách khác → `404`. Đơn phải `CONFIRMED` → khác: `INVALID_BOOKING_STATE`.
2. `now ≤ giờ bắt đầu hiện tại − 2 giờ` → khác: `CHANGE_DEADLINE_PASSED`.
3. Đơn đang có yêu cầu chờ và `now < pending_expires_at` → `409 RESCHEDULE_PAYMENT_PENDING`. Yêu cầu đã quá hạn nhưng job chưa xóa → bị thay bằng yêu cầu mới.
4. Vị trí mới = `resourceId` hoặc vị trí hiện tại → `lockResource` (không có → `404`).
5. `BookingWindowPolicy.validate(start, end, chi nhánh của vị trí mới)`. Cùng vị trí và cùng khung → `RESCHEDULE_SAME_SLOT`.
6. `ResourceService.requireBookable(vị trí mới, partySize)`; `existsOccupying(vị trí mới, khung mới, trừ chính đơn)` — kiểm cả đơn đang chiếm và **chỗ đang chờ đổi của đơn khác** → `SLOT_UNAVAILABLE`.
7. `quoteChange`:
   - `AddonService.recheck(booking, chi nhánh mới, khung mới)`: add-on khác chi nhánh → `ADDON_NOT_AVAILABLE`; khóa add-on theo id tăng dần, tồn kho (đơn đang chiếm + đơn đang chờ đổi, trừ chính đơn) → `ADDON_OUT_OF_STOCK`.
   - Tiền thuê = `PricingService.quote(loại chỗ mới, khung mới)` (n block); mỗi add-on = đơn giá lúc đặt × n × số lượng.
   - `VoucherService.quoteChange` (loại chỗ, giá trị tối thiểu, số tiền giảm theo tạm tính mới); `MembershipService.quoteChange` (gói phải cho loại chỗ mới; phút = min(phút đã trừ, thời lượng mới)).
   - Tổng mới = max(0, thuê + add-on − voucher − membership).
8. **Tổng mới > đã trả**: ghi `pending_resource_id`, `pending_start_at/end_at`, `pending_amount = tổng mới − đã trả`, `pending_expires_at = now + 10 phút`; lưu; trả `paymentRequired = true`. Đơn, `booking_charge`, voucher, phút gói **chưa đổi**.
9. **Tổng mới ≤ đã trả** → `applyChange`: `VoucherService.applyChange` (ghi số tiền giảm mới), `MembershipService.applyChange` (thời lượng ngắn hơn → trả bớt phút cho gói, bớt từ lần trừ sau cùng), sửa số tiền + khung giờ từng dòng `booking_charge` (RENTAL, từng ADDON, VOUCHER_DISCOUNT, MEMBERSHIP_DEDUCTION), đổi `resource`, `period`, xóa pending; đã trả > tổng mới → `paid_amount = tổng mới`, `RefundService.request(chênh lệch, "RESCHEDULE_PRICE_DIFF")`; lưu; `NotificationService.bookingRescheduled`.

**Thanh toán phần chênh** (`PaymentService.initiate` → cổng → webhook → `BookingService.onPaymentSucceeded`):
- Đơn `CONFIRMED` còn pending: số tiền về ≠ `pending_amount` → `refundUnexpectedPayment` (ghi refund `UNEXPECTED_PAYMENT`, báo khách), giữ yêu cầu. Bằng → khóa vị trí mới, `quoteChange` lại, `paid_amount += số tiền về`, `applyChange` (nếu giá đã giảm trong lúc chờ thì hoàn phần dư), lưu, báo khách.
- Đơn `CONFIRMED` không còn pending (yêu cầu đã hết hạn / đơn đã đổi xong) → `refundUnexpectedPayment`.
- Thanh toán thất bại (`onPaymentFailed`) cho đơn `CONFIRMED` có pending → xóa pending, `NotificationService.rescheduleCancelled`. Trạng thái đơn không đổi.

**Hết hạn yêu cầu đổi** (`BookingJobs.expirePendingChanges`, 30 giây): `findExpiredPendingChangeIds(now)` → từng đơn `expirePendingChange`: không có pending → `NO_PENDING_CHANGE`; `now < pending_expires_at` → `PENDING_CHANGE_NOT_EXPIRED` (job bỏ qua); còn lại xóa pending, báo khách.

**Hủy đơn** đang có yêu cầu đổi chờ thanh toán: xóa pending (nhả chỗ mới) rồi hủy như cũ.

**Danh sách**: `Specification` ghép các điều kiện có giá trị (customerId, SĐT, trạng thái, from, to) — không dùng `(:param is null or ...)`; phân trang, sắp `start_at desc, id desc`.

### 5.1. Quy tắc nghiệp vụ / công thức

| Quy tắc | Công thức | Ví dụ |
|---|---|---|
| Tổng tiền (FR-BOOK-06) | Σ `RENTAL, ADDON, EXTENSION, OVERSTAY_SURCHARGE` − Σ `VOUCHER_DISCOUNT, MEMBERSHIP_DEDUCTION` | 600.000 + 240.000 − 50.000 = 790.000 |
| Tiền add-on | `price_per_block × số block × quantity` | Máy chiếu 30.000, 09:00–11:00 (4 block), 2 máy → 240.000 |
| Tồn kho add-on | `quantity ≤ stock − max(số lượng đang dùng tại mỗi thời điểm trong khung)`; khoảng nửa mở | Tồn 2; đơn A 09–11 dùng 2 → đơn B 10–12 cần 1: hết (`ADDON_OUT_OF_STOCK`); B 11–13: được |
| Voucher % | `floor(tạm tính × % / 100)`, tối đa `max_discount_amount` | 10% của 840.000 = 84.000, trần 50.000 → 50.000 |
| Voucher số tiền | `min(discount_value, tạm tính)` | HOP50K: 600.000 → giảm 50.000 → 550.000 |
| Voucher phút miễn phí | giá các block đầu khung giờ, số block = `floor(phút / 30)` | 45 phút → 1 block 09:00–09:30 = 25.000 |
| Hạn voucher | `valid_from ≤ now < valid_to`; `min_order_amount` so `≥` | Tạm tính đúng 100.000 với min 100.000 → được |
| Membership | phút trừ = `min(floor(còn lại / 30) × 30, số block × 30)`; đủ cả đơn → giá trị = toàn bộ tiền thuê, thiếu → giá các block đầu | Còn 75 phút, đơn 2 giờ → trừ 60 phút = giá 09:00–10:00 (50.000), còn 15 phút |
| Hủy / đổi giờ | được khi `now ≤ start_at − 2 giờ`; hủy hoàn 100% `paid_amount` | Bắt đầu 09:00: 07:00 được; 07:00:01 → `CHANGE_DEADLINE_PASSED` |
| Đổi chỗ rẻ hơn (Prompt 2) | `tổng mới ≤ đã trả` → đổi ngay, hoàn `đã trả − tổng mới` | FD-03 10–12 (4 × 35.000 = 140.000) → 18–20 (4 × 25.000 = 100.000): hoàn 40.000 |
| Đổi chỗ đắt hơn (Prompt 2) | `tổng mới > đã trả` → giữ chỗ mới 10 phút, phải bù `tổng mới − đã trả` | FD-02 18–20 (100.000 đã trả) → FD-03 10–13 (6 × 35.000 = 210.000): bù 110.000 |
| Add-on khi đổi thời lượng | `đơn giá lúc đặt × số block mới × số lượng` | Màn hình 10.000, 2 cái: 2 giờ (4 block) 80.000 → 4 giờ (8 block) 160.000 |
| Membership khi đổi thời lượng | phút = `min(phút đã trừ, thời lượng mới)`; ngắn hơn → trả phần dư cho gói; dài hơn → không trừ thêm | Đã trừ 120 phút, đổi còn 60 phút → trả 60 phút cho gói |

### 5.2. Transaction và đồng thời

- Cả `hold` / `cancel` / `reschedule` là một transaction: lỗi ở bất kỳ bước nào (voucher hết lượt, add-on hết) → rollback toàn bộ, không giữ lượt / phút / add-on.
- Hai đơn cùng lấy add-on cuối: dòng `addon` bị khóa `FOR UPDATE` trước khi đếm → xử lý lần lượt, đơn sau thấy đơn trước → `ADDON_OUT_OF_STOCK`. Khóa theo id tăng dần.
- Hai đơn cùng lấy lượt voucher cuối: `voucher` khóa `FOR UPDATE` (thêm `@Version`) → đơn sau `VOUCHER_USED_UP`.
- Hai đơn cùng trừ một gói: `membership_pass` khóa `FOR UPDATE` → đơn sau thấy số phút đã trừ.
- Hủy song song với thanh toán / check-in: `booking.version` (khóa lạc quan) → `409 CONCURRENT_UPDATE`.
- Prompt 2: hai người cùng lấy chỗ mới (một người đặt mới, một người đổi chỗ sang): cả `hold` và `reschedule` đều khóa dòng vị trí trước khi kiểm tra trùng, và `existsOccupying` kiểm cả chỗ đang chờ đổi → người sau nhận `409 SLOT_UNAVAILABLE`. Chỗ đang chờ đổi không có lớp bảo vệ cuối ở database (không nằm trong `ex_booking_no_overlap`).
- Prompt 2: tiền bù về đúng lúc job hủy yêu cầu: cả hai cùng sửa dòng `booking` → khóa lạc quan, một bên `409 CONCURRENT_UPDATE` / job bỏ qua; nếu job chạy trước thì webhook thấy đơn không còn pending → hoàn số tiền đó (`UNEXPECTED_PAYMENT`).
- Prompt 2: `PaymentService.initiate` chuyển giao dịch cũ sang `UNCONFIRMED` bằng `saveAndFlush` trước khi tạo giao dịch mới, vì chỉ mục `ux_payment_open_per_booking` chỉ cho một giao dịch đang mở mỗi đơn (Hibernate chạy INSERT trước UPDATE nếu không flush).

---

## 6. Edge Cases — mục quan trọng nhất

| # | Tình huống | Kết quả mong đợi | Dữ liệu sau đó |
|---|---|---|---|
| E1 | Khách gọi `GET /api/bookings` / kỹ thuật hủy đơn / không token xem đơn | `403 FORBIDDEN` / `403` / `401` | Không đổi |
| E2 | Khách xem / hủy đơn của khách khác | `404 NOT_FOUND` | Không đổi |
| E3 | Add-on lặp, số lượng < 1, add-on không tồn tại | `422 DUPLICATE_ADDON` / `400 VALIDATION_FAILED` / `404 NOT_FOUND` | Không tạo đơn |
| E4 | Add-on hết trong khung giờ | `409 ADDON_OUT_OF_STOCK` | Không tạo đơn |
| E5 | Biên: đơn khác kết thúc đúng lúc đơn mới bắt đầu | Không tính trùng → đặt được | — |
| E6 | Hai đơn không chồng nhau, cùng chồng khung mới | Tính theo lúc dùng nhiều nhất, không cộng thẳng | — |
| E7 | Add-on INACTIVE / chi nhánh khác / vị trí chưa có chi nhánh | `409 ADDON_NOT_AVAILABLE` | — |
| E8 | Voucher tạm dừng / ngoài hạn (đúng `valid_to`) / hết lượt / khách hết lượt / sai loại chỗ / chưa đủ min (đúng bằng min thì được) | `422 VOUCHER_*` / `409 VOUCHER_USED_UP` | Không giữ lượt |
| E9 | Voucher + membership cùng đơn | `422 VOUCHER_MEMBERSHIP_CONFLICT` | — |
| E10 | Membership khi chưa đăng nhập / không có gói phù hợp / còn < 1 block | `422 MEMBERSHIP_LOGIN_REQUIRED` / `NO_USABLE_MEMBERSHIP` | — |
| E11 | Membership phủ hết tiền thuê | `201` `CONFIRMED`, tổng 0, không cần thanh toán | Phút bị trừ, usage `USED` |
| E12 | Membership phủ một phần | Trừ theo block, giá các block đầu | Gói còn phần lẻ < 1 block |
| E13 | Gói về 0 phút | `USED_UP`; trả lại phút → `ACTIVE` | |
| E14 | Đơn hết hạn / thanh toán thất bại | Trả lượt voucher, trả phút | redemption / usage `RELEASED` |
| E15 | Webhook về trễ cho đơn có voucher, chỗ vẫn trống | `REFUND_PENDING` (không kích hoạt lại) | |
| E16 | Hủy đúng mốc 2 giờ | `200`, hoàn 100% | `CANCELLED`, refund `REQUESTED`, trả lượt / phút |
| E17 | Hủy khi còn 1 giờ 59 phút | `422 CHANGE_DEADLINE_PASSED` | Không đổi |
| E18 | Hủy đơn `HELD` / đã check-in | `422 INVALID_BOOKING_STATE` | Không đổi |
| E19 | Hủy đơn tổng 0 (membership) | `200`, `refundAmount = 0`, không ghi refund | Trả phút |
| E20 | Đổi sang khung rẻ hơn | `200`, hoàn chênh lệch | Khung giờ và các dòng `booking_charge` đổi, `paid_amount` = tổng mới |
| E21 | (Prompt 2) Đổi sang chỗ đắt hơn, kể cả đắt hơn 1 đồng | `200 paymentRequired = true`, `amountDue = chênh lệch` | Đơn giữ nguyên; `pending_*` ghi chỗ mới, hạn 10 phút |
| E22 | (Prompt 2) Đổi khác thời lượng → được; trùng vị trí + khung cũ / quá hạn 2 giờ (đúng mốc thì được) | `200` / `422 RESCHEDULE_SAME_SLOT` / `CHANGE_DEADLINE_PASSED` | Đổi / không đổi |
| E23 | Đổi sang khung đã có người đặt hoặc đang được giữ cho yêu cầu đổi của đơn khác | `409 SLOT_UNAVAILABLE` | Không đổi |
| E24 | Danh sách: `size = 0` hoặc > 100, `to ≤ from` | `422 INVALID_PAGE` / `INVALID_TIME_RANGE` | — |
| E25 | Nhân viên lọc theo SĐT / id khách + trạng thái / khoảng ngày | Chỉ đúng đơn khớp điều kiện | — |
| E26 | Hai đơn cùng tranh add-on / lượt voucher cuối | Một thành công, một `409` | — **chưa có test đa luồng** (cơ chế khóa giống `ConcurrentHoldIntegrationTest`) |
| E27 | DB lỗi giữa chừng khi giữ chỗ có voucher | Rollback toàn bộ (không mất lượt) | Không có test tự động |
| E28 | Cổng thanh toán hoàn tiền lỗi | Chưa gọi cổng (ngoài phạm vi) | refund nằm ở `REQUESTED` |
| E29 | Dữ liệu cũ có `booking_addon` mà không có dòng `ADDON` | Tổng không gồm add-on | Chạy lại `data.sql` (dev) |
| **Prompt 2** | | | |
| E30 | Đổi sang vị trí khác (cùng chi nhánh) | `200`, tiền thuê theo loại chỗ mới | `booking.resource_id` đổi |
| E31 | Vị trí mới không tồn tại / bảo trì / nhỏ hơn số người | `404` / `409 RESOURCE_NOT_BOOKABLE` / `422 CAPACITY_EXCEEDED` | Không đổi |
| E32 | Đơn có add-on, đổi sang chi nhánh khác; add-on hết ở khung mới | `409 ADDON_NOT_AVAILABLE`; `409 ADDON_OUT_OF_STOCK` | Không đổi |
| E33 | Voucher / gói của đơn không áp cho loại chỗ mới; chỗ mới dưới giá trị tối thiểu của voucher | `422 VOUCHER_NOT_APPLICABLE` / `MEMBERSHIP_NOT_APPLICABLE`; `422 VOUCHER_MIN_ORDER` | Không đổi |
| E34 | Đã có yêu cầu chờ thanh toán (còn hạn) mà gửi yêu cầu mới; yêu cầu cũ hết hạn đúng lúc này | `409 RESCHEDULE_PAYMENT_PENDING`; `200` (thay yêu cầu cũ) | Không đổi; pending mới |
| E35 | Người khác đặt đúng chỗ đang chờ đổi | `409 SLOT_UNAVAILABLE` | — |
| E36 | Trả đúng phần chênh | Đơn sang chỗ mới, `paid_amount` cộng thêm, pending xóa | `booking_charge` theo chỗ mới |
| E37 | Tiền về khác phần chênh / về sau khi yêu cầu đã hết hạn | Hoàn lại số đó (`refund UNEXPECTED_PAYMENT`) | Đơn giữ nguyên |
| E38 | Thanh toán phần chênh thất bại; quá 10 phút không trả | Xóa yêu cầu, báo khách | Đơn giữ chỗ cũ, `CONFIRMED` |
| E39 | Hủy đơn đang có yêu cầu chờ | `200` hủy như cũ | Pending xóa, chỗ mới được nhả |
| E40 | Thời lượng mới ngắn hơn số phút gói đã trừ | Trả phần dư cho gói | `membership_usage.minutes` giảm / `RELEASED`, `remaining_minutes` tăng |
| E41 | Thời lượng mới dài hơn, đơn có add-on | Add-on tính theo số block mới | Dòng `ADDON` đổi số tiền |
| E42 | Gọi `/payments` cho đơn CONFIRMED không có yêu cầu đổi | `409 BOOKING_NOT_PAYABLE` | — |
| E43 | Database thật chưa chạy SQL 3.2 Prompt 2 | App dừng lúc khởi động (`Schema-validation`) | Phải chạy SQL — không có test tự động |

---

## 7. Tiêu chí hoàn thành (Acceptance Criteria)

- [x] Mọi edge case ở mục 6 có test, trừ E26–E29 và E43 (lý do ở 7.1).
- [x] `mvn test` qua toàn bộ (2026-11-08, sau Prompt 2): **`Tests run: 612, Failures: 0, Errors: 0, Skipped: 0`** (30 integration + 582 unit, chung cả dự án). Lần trước: 109 (2026-11-05), 129 (2026-11-03).
- [x] SQL ở 3.2 chạy lại được nhiều lần (`DROP ... IF EXISTS`, `IF NOT EXISTS`). **Chưa chạy trên PostgreSQL thật** — test chạy H2 (`ddl-auto: create-drop` dựng bảng từ entity).
- [x] File sửa ngoài danh sách Prompt 1: `PaymentService.java` (đã ghi lý do ở 2.2).
- [x] Đúng quy ước ở mục 2.4 (khóa vị trí trước khi kiểm tra trùng, đổi trạng thái qua `changeStatus`, gửi tin sau khi lưu).

### 7.1. Test cần viết

Tên unit test theo `.claude/rules/unit-test.md` (`test_<hàm>_<tình huống>_<số>`). Unit test của Prompt 1 bị xóa ngày 2026-11-05 đã được viết lại đủ ngày 2026-11-08.

| Test | Loại | Kiểm tra edge case |
|---|---|---|
| `AddonServiceTest.test_reserve_OneAddon_003`, `_ManyAddonsLockedByIdOrder_004`, `_NullSelections_001`, `_EmptySelections_002` | Unit | Công thức add-on, thứ tự khóa |
| `AddonServiceTest.test_reserve_QuantityEqualsAvailable_010`, `_QuantityOverAvailable_011` | Unit | E4 |
| `AddonServiceTest.test_peakUsage_TouchingNotSummed_019`, `_OutsideWindowIgnored_021` | Unit | E5 |
| `AddonServiceTest.test_peakUsage_OverlappingSummed_020`, `_NoReservations_018` | Unit | E6 |
| `AddonServiceTest.test_reserve_InactiveAddon_007`, `_OtherBranch_008`, `_NullBranch_009` | Unit | E7 |
| `AddonServiceTest.test_reserve_DuplicateAddon_005`, `_AddonNotFound_006` | Unit | E3 |
| `VoucherServiceTest.test_discountFor_PercentCapped_027`–`_FreeMinutesOverBooking_031`, `test_redeem_Valid_001` | Unit | Công thức voucher |
| `VoucherServiceTest.test_redeem_NotFound_002`–`_ZeroDiscount_014` | Unit | E8 |
| `VoucherServiceTest.test_markUsed_*_015`–`_016`, `test_release_*_017`–`_020` | Unit | E14 |
| `MembershipServiceTest.test_deduct_CoversWholeBooking_001` | Unit | E11 |
| `MembershipServiceTest.test_deduct_PartialRoundedToBlocks_002` | Unit | E12 |
| `MembershipServiceTest.test_deduct_UsesUpPass_003`, `test_release_*_008`–`_011` | Unit | E13, E14 |
| `MembershipServiceTest.test_deduct_NoPass_004`, `_SkipsUnusablePasses_005`, `_PassBelowOneBlock_006` | Unit | E10 |
| `BookingServiceTest.test_hold_*_001`–`_032`, `_169` | Unit | Công thức tổng, E9, E10, E11, khách vãng lai / đăng nhập |
| `BookingServiceTest.test_onPaymentFailed_Held_047`, `test_expireHold_*_155`–`_160` | Unit | E14 |
| `BookingServiceTest.test_onPaymentSucceeded_ExpiredWithDeductions_040` | Unit | E15 |
| `BookingServiceTest.test_cancel_ExactDeadline_053` | Unit | E16 |
| `BookingServiceTest.test_cancel_OneSecondAfterDeadline_054` | Unit | E17 |
| `BookingServiceTest.test_cancel_NotConfirmed_055` | Unit | E18 |
| `BookingServiceTest.test_cancel_OtherCustomer_056`, `test_reschedule_OtherCustomer_072` | Unit | E2 |
| `BookingServiceTest.test_cancel_PaidZero_060` | Unit | E19 |
| `BookingServiceTest.test_reschedule_CheaperSlot_061`, `_SamePrice_062`, `_DeductionsEqualSubtotal_078`, `_DeductionsExceedSubtotal_079` | Unit | E20 |
| `BookingServiceTest.test_reschedule_PriceOneDongHigher_063` (sửa ở Prompt 2) | Unit | E21 |
| `BookingServiceTest.test_reschedule_DifferentDuration_065` (sửa ở Prompt 2), `_SameSlot_064`, `_ExactDeadline_069`, `_OneSecondAfterDeadline_070` | Unit | E22 |
| `BookingServiceTest.test_reschedule_SlotTaken_067` | Unit | E23 |
| `BookingServiceTest.test_listMine_*_130`–`_140`, `test_search_*_141`–`_154` | Unit | E24, E25 |
| `BookingServiceTest.test_reschedule_OtherResource_172` | Unit (Prompt 2) | E30 |
| `BookingServiceTest.test_reschedule_OtherResourceNotFound_173`, `_ResourceNotBookable_066` | Unit (Prompt 2) | E31 |
| `BookingServiceTest.test_reschedule_AddonUnavailable_068`; `AddonServiceTest.test_recheck_*_013`–`_017` | Unit (Prompt 2) | E32 |
| `BookingServiceTest.test_reschedule_VoucherNotApplicable_175`; `VoucherServiceTest.test_quoteChange_*_021`–`_025`; `MembershipServiceTest.test_quoteChange_*_012`–`_015` | Unit (Prompt 2) | E33 |
| `BookingServiceTest.test_reschedule_PendingPaymentNotExpired_170`, `_PendingPaymentExactExpiry_171` | Unit (Prompt 2) | E34 |
| `AddonServiceTest.test_reserve_PendingChangeCounted_012`; `AvailabilityServiceTest.test_search_OccupiedAndPendingExcluded_006`, `test_floorPlan_StatusesAndZones_011` | Unit (Prompt 2) | E35 |
| `BookingServiceTest.test_onPaymentSucceeded_PendingChangeExactAmount_177`, `test_reschedule_AppliesVoucherAndMembership_176` | Unit (Prompt 2) | E36 |
| `BookingServiceTest.test_onPaymentSucceeded_PendingChangeAmountMismatch_178`, `_AlreadyConfirmed_042` (sửa ở Prompt 2) | Unit (Prompt 2) | E37 |
| `BookingServiceTest.test_onPaymentFailed_PendingChange_179`, `test_expirePendingChange_*_181`–`_184`, `test_findExpiredPendingChangeIds_*_185`–`_186` | Unit (Prompt 2) | E38 |
| `BookingServiceTest.test_cancel_ClearsPendingChange_180` | Unit (Prompt 2) | E39 |
| `MembershipServiceTest.test_applyChange_*_016`–`_018`; `BookingServiceTest.test_reschedule_DifferentDuration_065` | Unit (Prompt 2) | E40 |
| `BookingServiceTest.test_reschedule_AddonAmountByNewBlocks_174`, `_ManyChargeTypes_077` | Unit (Prompt 2) | E41 |
| `PaymentServiceTest.test_initiate_ConfirmedWithPendingChange_004`, `_OpenPaymentOfOldAmount_005`, `_ConfirmedWithoutPendingChange_006` | Unit (Prompt 2) | E42 |
| `RefundServiceTest.test_request_*_001`–`_007` | Unit | Ghi refund cho E16, E20, E37 |
| `BookingExtrasIntegrationTest.addonStockIsCheckedPerTimeSlotAndChargedPerBlock` | Integration | E4, E5, E14 |
| `BookingExtrasIntegrationTest.voucherDiscountAndCancellationTwoHoursBeforeStart` | Integration | E1, E16, E17, E14 |
| `BookingExtrasIntegrationTest.membershipCoveringWholeRentalConfirmsWithoutPayment` | Integration | E9, E10, E11, E19 |
| `BookingExtrasIntegrationTest.customersSeeOnlyTheirBookingsAndStaffCanFilterByCustomer` | Integration | E1, E2, E24, E25 |
| `BookingExtrasIntegrationTest.rescheduleKeepsDurationAndRefundsCheaperSlot` (Prompt 2: bỏ 2 kiểm tra hành vi cũ) | Integration | E20, E22 |
| `BookingExtrasIntegrationTest.rescheduleToPricierSlotHoldsNewSlotUntilTopUpPaid` (Prompt 2, ngày 16/11, FD-02 → FD-03) | Integration | E21, E30, E34, E35, E36, E38 qua API |
| — | — | E26 (đồng thời add-on / voucher), E27 (DB lỗi), E28 (cổng hoàn tiền), E29 (dữ liệu cũ), E43 (database chưa chạy SQL): không có test tự động |

### 7.2. Kiểm tra tay

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=h2

curl "localhost:8080/api/addons?branchId=1"
curl -X POST localhost:8080/api/bookings -H "Content-Type: application/json" -d "{\"resourceId\":<id MR-01>,\"startAt\":\"2026-12-01T09:00:00\",\"endAt\":\"2026-12-01T11:00:00\",\"partySize\":2,\"contact\":{\"fullName\":\"Nguyen Van A\",\"phone\":\"0912345678\"},\"addons\":[{\"addonId\":<id PROJECTOR>,\"quantity\":2}],\"voucherCode\":\"WELCOME10\"}"
# thanh toán: POST /api/bookings/<id>/payments rồi /api/payments/sandbox/<txn>/complete?amount=<totalAmount>
curl -X POST localhost:8080/api/bookings/<id>/cancel -H "Authorization: Bearer <token lễ tân: huy.ngo@cowork.example / Cowork@123>"
```

Kết quả mong đợi: đơn có `charges` = `RENTAL 600000`, `ADDON 240000`, `VOUCHER_DISCOUNT 50000`, `totalAmount = 790000` (01/12/2026 là thứ Ba, cao điểm); hủy trước ≥ 2 giờ trả `refundAmount = 790000`, `status = CANCELLED`.

Prompt 2 (đổi sang chỗ đắt hơn):

```bash
curl -X POST localhost:8080/api/bookings/<id>/reschedule -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"resourceId\":<id FD-03>,\"startAt\":\"2026-12-01T10:00:00\",\"endAt\":\"2026-12-01T13:00:00\"}"
# → paymentRequired = true, amountDue = X
curl -X POST localhost:8080/api/bookings/<id>/payments -H "Content-Type: application/json" -d "{\"method\":\"VIETQR\"}"
curl -X POST "localhost:8080/api/payments/sandbox/<txn>/complete?result=SUCCESS&amount=<X>"
curl localhost:8080/api/bookings/<id> -H "Authorization: Bearer <token>"
```

Kết quả mong đợi: sau bước 1 đơn vẫn ở chỗ cũ, có `pendingChange`; sau khi thanh toán, đơn ở FD-03 10:00–13:00, `pendingChange = null`, `paidAmount = totalAmount`.

---

## 8. Sau khi AI làm xong — tự đánh giá spec

| Câu hỏi | Trả lời |
|---|---|
| Code chạy đúng ngay lần đầu? | Không: 2 lỗi ở lần chạy đầu — mock thiếu `revalue` trong unit test, và `@EntityGraph` kiểu FETCH làm danh sách đơn ném `LazyInitializationException` (đã đổi sang kiểu LOAD) |
| Phải chat lại mấy lần để sửa? | 1 lần hỏi lại trước khi code (voucher / membership, chính sách hủy) |
| AI làm sai ở đâu? | Xem dòng trên; không còn lỗi đã biết |
| Spec bị hở ở mục nào (thiếu edge case, mơ hồ, thiếu tên file...)? | (1) Prompt trái BR-15 trong SRS → đã hỏi, theo prompt; **`docs/SRS.md` BR-15 chưa cập nhật** (AI không sửa SRS). (2) "Tính tổng tiền thuê trong booking_charge, theo block" — AI hiểu là tiền thuê tính theo block và mọi khoản (kể cả giảm trừ) là dòng trong `booking_charge`; không tách mỗi block một dòng. (3) "Áp dụng rule tương tự cho đổi khung giờ" không nói tiền xử lý thế nào → AI tự quyết: cùng vị trí + cùng thời lượng, đắt hơn thì từ chối, rẻ hơn thì hoàn chênh lệch, hạn 2 giờ tính theo giờ bắt đầu cũ. (4) AI tự quyết: tồn kho add-on tính theo lúc dùng nhiều nhất (không cộng thẳng); add-on chỉ cùng chi nhánh; voucher % tính trên thuê + add-on; membership chỉ trừ tiền thuê, theo bội số block, chọn gói hết hạn sớm nhất; đơn tổng 0 xác nhận ngay; webhook trễ cho đơn có voucher / membership → `REFUND_PENDING`; hủy hoàn cả lượt voucher và phút membership; khách vãng lai không có API xem / hủy đơn (cần đăng nhập). (5) Chưa gọi API hoàn tiền của cổng — chỉ ghi `refund REQUESTED`. (6) **Chưa kiểm chứng trên PostgreSQL**: khóa `FOR UPDATE` trên `addon` / `voucher` / `membership_pass` và CHECK mới chỉ chạy trên H2 |
| Prompt 2 — chạy đúng ngay lần đầu? | Có (main code); test cũ của đổi giờ phải sửa vì hành vi đổi (`RESCHEDULE_PRICE_INCREASE`, `RESCHEDULE_DURATION_MISMATCH` không còn) |
| Prompt 2 — spec bị hở | (1) "Khung đắt hơn thì phải bù thêm tiền" không nói bù lúc nào → đã hỏi, chọn giữ chỗ mới chờ trả (cần thêm 5 cột `booking`). (2) "Chỉ cho đổi khi thời gian đến lúc check-in ≥ 2 tiếng" → AI hiểu là giờ bắt đầu **hiện tại** của đơn; giờ bắt đầu mới chỉ cần hợp lệ như lúc đặt (không quá khứ). (3) AI tự quyết: thời gian giữ chỗ mới = `booking.hold-duration` (10 phút, như BR-02); chỉ một yêu cầu đổi chờ thanh toán mỗi lúc (`RESCHEDULE_PAYMENT_PENDING`); add-on giữ đơn giá lúc đặt, tính theo số block mới, không cho đổi sang chi nhánh khác nếu có add-on; voucher / gói phải áp được cho loại chỗ mới; thời lượng dài hơn không trừ thêm phút gói; tiền về sai số / về muộn thì hoàn lại (`UNEXPECTED_PAYMENT`) thay vì báo lỗi webhook; lúc tiền về hệ thống tính lại giá (không lưu giá lúc tạo yêu cầu). (4) **Chưa kiểm chứng trên PostgreSQL**: SQL 3.2 Prompt 2, partial index `ux_payment_open_per_booking` cùng `saveAndFlush` chỉ chạy trên H2 (H2 không có index này). (5) Chỗ đang chờ đổi không được `ex_booking_no_overlap` bảo vệ — chỉ dựa vào khóa dòng vị trí. (6) SRS / use case cần cập nhật: UC06 / FR-BOOK-09 nên ghi luồng "đổi chỗ đắt hơn → giữ chỗ mới chờ trả phần chênh"; BR-15 vẫn chưa cập nhật từ Prompt 1 |
| Lần sau cần bổ sung gì vào template? | Mục "Quy tắc nghiệp vụ thay đổi so với SRS" để nhắc cập nhật SRS; mục "Câu hỏi đã hỏi lại người dùng" |

---

## Checklist

- [x] Mục 2 có tên file và tên hàm thật, đã mở code ra kiểm tra.
- [x] Mục 3 ghi đủ kiểu dữ liệu, null, mặc định, dữ liệu cũ.
- [x] Mục 5 không có câu kiểu "xử lý cho phù hợp" hay "kiểm tra hợp lệ" mà không nói kiểm tra cái gì.
- [x] Mục 6 có ít nhất: quyền, đầu vào sai, không tồn tại, sai trạng thái, giá trị ở biên, đồng thời, lỗi giữa chừng.
- [x] Mỗi edge case có kết quả cụ thể: mã HTTP + mã lỗi + trạng thái dữ liệu.
- [x] Đã ghi mục "Ngoài phạm vi" và "File không được đụng".
