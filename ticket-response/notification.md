# SPEC: Thông báo tự động — cảnh báo hết giờ, no-show, quá giờ, bảo trì, hoàn tiền xong

| Mục | Nội dung |
|---|---|
| Loại | Tính năng mới |
| Liên kết | UC17, UC24, UC25, UC26; FR-NOTI-03, FR-NOTI-04, FR-NOTI-05, FR-SYS-02, FR-SYS-03, FR-CHK-09, FR-OPS-07, FR-PAY-08; BR-05, BR-16 |
| Người viết | huydao123-tech (code và spec do AI viết) |
| Ngày | 2026-11-10 |

---

## 1. Mục tiêu (Goal)

- Khách đang ngồi nhận cảnh báo **trước giờ kết thúc** đúng số phút họ chọn (10 / 15 / 30, mặc định 15), kèm lời nhắc gia hạn. Mỗi mốc chỉ nhận một lần.
- Khách nhận tin khi: **đổi chỗ** (đã có từ `booking.md` Prompt 2), **hoàn tiền xong**, **vị trí của đơn chuyển bảo trì**, **bị đánh dấu no-show**.
- Lễ tân của chi nhánh nhận tin khi có khách **quá giờ** (đơn chuyển OVERSTAY).
- Hệ thống tự đánh dấu **no-show** (BR-16) cho đơn đã thanh toán mà khách không check-in, nhả chỗ, không hoàn tiền.

Đã xử lý: `ticket-request/notification.md` **Prompt 1** (xong). Không còn prompt `open` / `reopen` nào khác (`authentication.md`, `booking.md`, `tra-cuu-cho-trong.md`, `membership.md` đều `close`).

Câu đã hỏi lại người dùng:

| Câu hỏi | Trả lời |
|---|---|
| "Hoàn tiền xong": code chỉ ghi `refund` REQUESTED, chưa có chỗ nào chuyển REFUNDED. Làm thế nào? | Thêm API để quản lý đánh dấu đã hoàn xong rồi nhắn khách. |
| "Đền bù": dự án chưa có luồng đền bù (UC18, BR-18). Làm thế nào? | Để ticket riêng, lần này không làm. |

### 1.1. Bug (chỉ điền nếu là sửa lỗi)

Không có — đây là tính năng mới. (Trong lúc làm có sửa một lỗi tiềm ẩn: `LoggingNotificationSender.maskPhone` ném `NullPointerException` khi người nhận không có SĐT; trước đây mọi người nhận đều có SĐT nên chưa lộ ra. Nay nhân viên nhận tin theo email nên phải sửa.)

### 1.2. Ngoài phạm vi (Out of scope)

- Không làm thông báo **đền bù** và luồng đền bù (UC18, BR-18) — theo trả lời của người dùng.
- Không làm **nhắc lịch trước 30 phút giờ bắt đầu** (FR-NOTI-02) — request không yêu cầu.
- Không gửi qua kênh thật SMS / Zalo / Email / Push; `LoggingNotificationSender` vẫn chỉ ghi log. Không chuyển việc gửi ra sau commit.
- Không gọi API hoàn tiền của cổng; quản lý tự hoàn tay rồi bấm xác nhận.
- Không chuyển đơn `REFUND_PENDING` → `REFUNDED` (đơn tiền về trễ chưa có dòng `refund`, xem mục 8).
- Không sắp xếp lại / hủy / hoàn tiền tự động cho đơn bị ảnh hưởng bởi bảo trì; chỉ nhắn khách. Không trả danh sách đơn bị ảnh hưởng trong response API bảo trì.
- Không chặn check-in tại `BookingService.checkIn` khi đã quá mốc no-show mà job chưa chạy.
- Không làm giao diện (nút "gia hạn nhanh" là việc của app).

---

## 2. Context — code hiện tại liên quan

### 2.1. Luồng hiện tại

```
Job (BookingJobs, @Scheduled)                      (business/job)
  markNoShows()     → BookingService.findNoShowIds()     → BookingRepository.findConfirmedIdsStartedBefore
                    → BookingService.markNoShow(id)      → NotificationService.noShow
  warnEndingSoon()  → BookingService.findEndingSoonIds() → BookingRepository.findCheckedInIdsEndingAfter
                    → BookingService.warnEndingSoon(id)  → NotificationService.endWarning
                                                             → NotificationJobRepository (chống trùng, bảng notification_job)
  flagOverstays()   → BookingService.flagOverstay(id)    → NotificationService.overstay → StaffRepository (bảng staff)

POST /api/resources/{id}/maintenance               (presentation)
  → ResourceController.markMaintenance()
  → ResourceService.markMaintenance()              (business)
      → BookingRepository.findByResourceIdAndStatusAndPeriodEndAtAfterOrderByPeriodStartAtAsc
      → NotificationService.resourceMaintenance (mỗi đơn)

GET  /api/refunds?status=        → RefundController.list()     → RefundService.list()     → RefundRepository.findWithBookingByStatus
POST /api/refunds/{id}/complete  → RefundController.complete() → RefundService.complete() → RefundRepository.findByIdForUpdate
                                                                    → NotificationService.refundCompleted
  → bảng: refund, payment, booking, customer
```

### 2.2. File sẽ phải sửa / tạo

| File | Sửa hay tạo mới | Sửa cái gì |
|---|---|---|
| `backend/src/main/java/com/cowork/config/BookingProperties.java` | Sửa | Thêm `noShowGrace` (30m), `noShowGracePercent` (50) |
| `backend/src/main/resources/application.yml` | Sửa | `booking.no-show-grace`, `no-show-grace-percent`, `jobs.no-show-delay-ms`, `jobs.end-warning-delay-ms` |
| `business/service/BookingService.java` | Sửa | `flagOverstay` báo lễ tân; thêm `markNoShow`, `noShowDeadline`, `findNoShowIds`, `warnEndingSoon`, `findEndingSoonIds` |
| `business/service/NotificationService.java` | Sửa | Constructor thêm `NotificationJobRepository`, `StaffRepository`, `Clock`; thêm `refundCompleted`, `noShow`, `resourceMaintenance`, `endWarning`, `overstay` |
| `business/service/ResourceService.java` | Sửa | Constructor thêm `BookingRepository`, `NotificationService`, `Clock`; `markMaintenance` nhắn khách, bỏ qua nếu đã bảo trì |
| `business/service/RefundService.java` | Sửa | Constructor thêm `NotificationService`, `Clock`; thêm `list`, `complete` |
| `business/job/BookingJobs.java` | Sửa | Thêm job `markNoShows`, `warnEndingSoon` |
| `dataaccess/repository/BookingRepository.java` | Sửa | `findConfirmedIdsStartedBefore`, `findCheckedInIdsEndingAfter`, `findIdsByStatusAndStartBefore`, `findIdsByStatusAndEndAfter`, `findByResourceIdAndStatusAndPeriodEndAtAfterOrderByPeriodStartAtAsc` |
| `dataaccess/repository/NotificationJobRepository.java` | Tạo mới | `existsByBookingIdAndTypeAndSendAt` |
| `dataaccess/repository/StaffRepository.java` | Sửa | `findByBranchIdAndRoleAndStatusOrderByIdAsc` |
| `dataaccess/repository/RefundRepository.java` | Sửa | `findWithBookingByStatus` (join fetch), `findByIdForUpdate` (khóa ghi) |
| `dataaccess/integration/LoggingNotificationSender.java` | Sửa | `maskPhone` chịu được SĐT null (bỏ `private` để test) |
| `dto/RefundDtos.java` | Tạo mới | `RefundResponse` |
| `presentation/controller/RefundController.java` | Tạo mới | `GET /api/refunds`, `POST /api/refunds/{id}/complete` |
| `test/.../business/service/BookingServiceTest.java` | Sửa | Tham số `BookingProperties` mới; test 187–203 |
| `test/.../business/service/BookingWindowPolicyTest.java` | Sửa | Tham số `BookingProperties` mới |
| `test/.../business/service/NotificationServiceTest.java` | Sửa | Constructor mới; test 012–025 |
| `test/.../business/service/ResourceServiceTest.java` | Sửa | Constructor mới; test 013–016 |
| `test/.../business/service/RefundServiceTest.java` | Sửa | Constructor mới; test 008–014 |
| `test/.../dataaccess/integration/LoggingNotificationSenderTest.java` | Tạo mới | Test 001–006 |
| `test/.../NotificationIntegrationTest.java` | Tạo mới | 4 test luồng API + job |
| `README.md`, `backend/README.md`, `docs/database.md` | Sửa | Bảng service, API, quy tắc, ngày test, việc cần làm, ghi chú `notification_job` / `refund` |
| `checklist-use-case.md` | Sửa | Trạng thái FR, tổng quan, lịch sử |

### 2.3. File KHÔNG được đụng vào

- `ticket-request/*.md`, `ticket-response/AI-response-template.md`.
- `docs/SRS.md`, `docs/use-cases.md`.
- `backend/db/schema.sql`, `backend/db/data.sql`, `config/DemoDataSeeder.java` — không đổi bảng / dữ liệu mẫu.
- Entity (`dataaccess/entity/*`) — không thêm field.

### 2.4. Quy ước code phải theo

- Đổi trạng thái đơn chỉ qua `BookingService.changeStatus` (ghi `booking_status_history`).
- Gửi thông báo đặt **sau** lệnh lưu.
- Thời gian lấy `LocalDateTime.now(clock)`; khoảng `[start, end)`.
- Tham số nghiệp vụ (30 phút, 50%, chu kỳ job) trong `application.yml` + `BookingProperties`.
- Lỗi: `DomainException` (422), `NotFoundException` (404); vai trò kiểm bằng `@PreAuthorize(Roles.ADMIN_MANAGER)`.
- Response cần quan hệ LAZY (refund → payment → booking) chuyển DTO trong service.
- Unit test theo `.claude/rules/unit-test.md` (JUnit 4, tên `test_<hàm>_<tình huống>_<số>`, viewpoint, `TestCaseLogRule`).

---

## 3. Data Model Change — thay đổi database

### 3.1. Thay đổi bảng

Không có. Bảng `notification_job` và cột `customer.notify_before_minutes`, `customer.preferred_channel`, `refund.completed_at` đã có sẵn trong `schema.sql`; giá trị enum dùng (`END_WARNING`, `SENT`, `REFUNDED`, `NO_SHOW`) đã nằm trong các ràng buộc `CHECK`.

### 3.2. SQL

Không có SQL phải chạy.

### 3.3. Dữ liệu cũ

- Đơn CONFIRMED cũ đã qua giờ bắt đầu quá thời gian chờ: lượt job no-show đầu tiên sau khi deploy sẽ chuyển chúng sang `NO_SHOW` và nhắn khách. Nếu database thật có đơn test cũ như vậy, khách (số điện thoại test) sẽ nhận tin — hiện chỉ ghi log nên không ảnh hưởng.
- `refund` REQUESTED cũ: xác nhận được bằng API mới như yêu cầu mới.

### 3.4. Entity / DTO cần cập nhật

| Lớp | Field mới | Kiểu |
|---|---|---|
| `BookingProperties` | `noShowGrace` | `Duration` (mặc định `30m`) |
| `BookingProperties` | `noShowGracePercent` | `int` (mặc định `50`) |
| `RefundDtos.RefundResponse` (mới) | `id, bookingId, bookingCode, amount, reason, status, requestedAt, completedAt` | `Long, Long, String, long, String, RefundStatus, LocalDateTime, LocalDateTime` |

---

## 4. API Contract

### `GET /api/refunds?status=REQUESTED` — QL, AD

`status` tùy chọn: `REQUESTED` (mặc định) / `REFUNDED` / `FAILED`.

**Response thành công** — `200`
```json
[
  {
    "id": 3,
    "bookingId": 41,
    "bookingCode": "BK7XQ2M9PA",
    "amount": 200000,
    "reason": "CUSTOMER_CANCELLED",
    "status": "REQUESTED",
    "requestedAt": "2026-11-17T07:00:00",
    "completedAt": null
  }
]
```

**Response lỗi**

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `status` không phải giá trị hợp lệ |
| 401 | `UNAUTHORIZED` | Không có / sai token |
| 403 | `FORBIDDEN` | Lễ tân, kỹ thuật, khách |

### `POST /api/refunds/{id}/complete` — QL, AD

Không có body. Chuyển `REQUESTED` → `REFUNDED`, ghi `completedAt`, nhắn khách "Đã hoàn X VND cho đơn ...".

**Response thành công** — `200`: một `RefundResponse` như trên, `status = "REFUNDED"`, `completedAt` = thời điểm xác nhận.

**Response lỗi**

| HTTP | `code` | Khi nào |
|---|---|---|
| 401 | `UNAUTHORIZED` | Không có / sai token |
| 403 | `FORBIDDEN` | Không phải QL / AD |
| 404 | `NOT_FOUND` | Không có yêu cầu hoàn với id này |
| 422 | `INVALID_REFUND_STATE` | Yêu cầu đã `REFUNDED` hoặc `FAILED` (bấm lần hai) |

### `POST /api/resources/{id}/maintenance` — KT, QL, AD (đổi hành vi, không đổi request / response)

- Sau khi chuyển `MAINTENANCE`, nhắn khách của mọi đơn `CONFIRMED` trên vị trí có giờ kết thúc sau thời điểm hiện tại.
- Vị trí **đã** `MAINTENANCE`: trả `200` với vị trí hiện tại, không lưu, không nhắn lại (trước đây lưu lại lần nữa).

---

## 5. Logic Flow — các bước xử lý

**A. No-show** — `BookingJobs.markNoShows` mỗi `booking.jobs.no-show-delay-ms` (60 giây)

1. `BookingService.findNoShowIds()` → id các đơn `CONFIRMED` có `start_at < now`.
2. Mỗi id gọi `BookingService.markNoShow(id)` (transaction riêng, lỗi một đơn không dừng cả lượt — `runSafely`).
3. Không có đơn → `NotFoundException`. Đơn không còn `CONFIRMED` (đã check-in, hủy...) → bỏ qua.
4. Mốc chờ = `start_at + min(no-show-grace, duration × no-show-grace-percent / 100)` (`noShowDeadline`). `now` chưa **sau** mốc → bỏ qua.
5. Xóa yêu cầu đổi chỗ đang chờ (nếu còn), `changeStatus(NO_SHOW)`, lưu. Không trả lượt voucher / phút gói, không ghi `refund`.
6. `NotificationService.noShow(booking)`.

**B. Cảnh báo sắp hết giờ** — `BookingJobs.warnEndingSoon` mỗi `booking.jobs.end-warning-delay-ms` (30 giây)

1. `BookingService.findEndingSoonIds()` → id đơn `CHECKED_IN` có `end_at > now`.
2. `BookingService.warnEndingSoon(id)`: đơn không còn `CHECKED_IN` → bỏ qua; còn → `NotificationService.endWarning(booking)`.
3. `endWarning`: `sendAt = end_at − customer.notify_before_minutes`. `now < sendAt` hoặc `now ≥ end_at` → không gửi.
4. Đã có `notification_job` (booking, `END_WARNING`, `sendAt`) → không gửi.
5. Lưu `notification_job` (`END_WARNING`, kênh `customer.preferred_channel`, `send_at = sendAt`, `status = SENT`, `attempts = 1`, `sent_at = now`), rồi gửi tin có giờ kết thúc + nhắc "Gia hạn".

**C. Quá giờ** — `BookingService.flagOverstay` (job sẵn có)

1. Như cũ: `CHECKED_IN` và `now > end_at + overstay-grace` → `OVERSTAY`, lưu.
2. Mới: `NotificationService.overstay(booking)`: vị trí chưa gán khu vực → không gửi; tìm nhân viên `RECEPTIONIST` + `ACTIVE` cùng chi nhánh của vị trí; không có ai → log WARN; có → gửi mỗi người một tin theo email (SĐT rỗng).

**D. Bảo trì** — `ResourceService.markMaintenance(id)`

1. Không có vị trí → 404. `INACTIVE` → `422 RESOURCE_INACTIVE`. Đã `MAINTENANCE` → trả nguyên, dừng.
2. Chuyển `MAINTENANCE`, lưu.
3. Lấy đơn `CONFIRMED` của vị trí có `end_at > now`, sớm nhất trước; nhắn từng khách (`resourceMaintenance`).

**E. Hoàn tiền xong** — `RefundService.complete(id)`

1. `findByIdForUpdate` (khóa dòng). Không có → 404.
2. Không phải `REQUESTED` → `422 INVALID_REFUND_STATE`.
3. `REFUNDED`, `completed_at = now`, lưu; nhắn khách của đơn (`refundCompleted`); trả DTO trong transaction.

### 5.1. Quy tắc nghiệp vụ / công thức

| Quy tắc | Công thức | Ví dụ |
|---|---|---|
| BR-16 thời gian chờ no-show | `min(30 phút, 50% × thời lượng)`; đánh dấu khi `now > start + chờ` | Đơn 09:00–11:00: chờ 30 phút → 09:30 chưa, 09:30:01 NO_SHOW. Đơn 09:00–09:40: chờ 20 phút → 09:20 chưa, 09:20:01 NO_SHOW. Đơn 1 giờ: hai mức bằng nhau, 30 phút |
| BR-05 / FR-NOTI-03 mốc cảnh báo | `send_at = end − notify_before_minutes`; gửi khi `send_at ≤ now < end` | Đơn tới 11:00, khách chọn 15: 10:44:59 chưa, 10:45 gửi, 11:00 không gửi. Khách chọn 30: gửi từ 10:30 |
| Gia hạn sau khi đã cảnh báo | Giờ kết thúc mới → `send_at` mới → cảnh báo lại một lần | Gia hạn 11:00 → 12:00: cảnh báo lần hai lúc 11:45 |

### 5.2. Transaction và đồng thời

- Mỗi đơn trong job chạy một transaction riêng (`runSafely` bắt `OptimisticLockingFailureException`, `DomainException`). Job no-show và lễ tân check-in cùng lúc: `booking.version` chặn ghi đè, bên thua lỗi optimistic lock — job bỏ qua, lượt sau thấy đơn đã `CHECKED_IN`; API check-in thua thì trả `409 CONCURRENT_UPDATE`.
- Cảnh báo hết giờ: job `@Scheduled` chạy tuần tự trên một instance nên không gửi trùng. Nhiều instance cùng chạy có thể gửi trùng một lần (bảng không có ràng buộc unique `(booking_id, type, send_at)`) — ghi ở mục 8.
- Hoàn tiền xong: khóa ghi dòng `refund` (`SELECT … FOR UPDATE`) trước khi kiểm trạng thái → hai người cùng bấm thì người sau nhận `422 INVALID_REFUND_STATE`, khách chỉ nhận một tin.
- Gửi tin vẫn nằm trong transaction (trước commit) như các thông báo cũ.

---

## 6. Edge Cases — mục quan trọng nhất

| # | Tình huống | Kết quả mong đợi | Dữ liệu sau đó |
|---|---|---|---|
| E1 | Gọi API hoàn tiền không token / lễ tân | `401 UNAUTHORIZED` / `403 FORBIDDEN` | Không đổi |
| E2 | `status` của `GET /api/refunds` sai (`DONE`) | `400 VALIDATION_FAILED` | Không đổi |
| E3 | Refund / đơn / vị trí không tồn tại | `404 NOT_FOUND` | Không đổi |
| E4 | Xác nhận hoàn lần hai (REFUNDED) hoặc refund FAILED | `422 INVALID_REFUND_STATE`, không nhắn | Không đổi |
| E5 | No-show đúng mốc 30 phút / quá 1 giây (đơn 2 giờ) | Đúng mốc: giữ CONFIRMED; quá 1 giây: NO_SHOW + nhắn | `status = NO_SHOW`, 1 dòng lịch sử |
| E6 | No-show đơn ngắn 40 phút: đúng 20 phút / quá 1 giây | Đúng mốc giữ; quá 1 giây NO_SHOW | |
| E7 | No-show đơn 1 giờ (hai mức bằng nhau) | 25 phút giữ; 30 phút 1 giây NO_SHOW | |
| E8 | Job no-show đọc danh sách xong thì khách check-in | Bỏ qua, không đổi trạng thái | `CHECKED_IN` |
| E9 | No-show khi đơn còn yêu cầu đổi chỗ chờ trả | Xóa yêu cầu, nhả chỗ mới | 5 cột `pending_*` rỗng |
| E10 | No-show không trả voucher / phút gói, không hoàn tiền | Không gọi `release`, không ghi `refund` | |
| E11 | No-show giải phóng chỗ | Khách khác đặt được phần còn lại của khung giờ | Đơn mới HELD |
| E12 | Cảnh báo: trước mốc 1 giây / đúng mốc / giữa khoảng / đúng giờ kết thúc | Không / gửi / gửi / không | 1 dòng `notification_job` SENT khi gửi |
| E13 | Job cảnh báo quét lần hai cùng mốc | Không gửi lại | Vẫn 1 dòng |
| E14 | Khách chọn 30 phút + kênh Zalo / chọn 10 phút | Gửi từ 30 phút trước, ghi kênh ZALO / còn 11 phút chưa gửi | |
| E15 | Đơn đã OVERSTAY / check-out trước lượt quét cảnh báo | Không cảnh báo | |
| E16 | Quá giờ: chi nhánh có 2 / 1 / 0 lễ tân ACTIVE | 2 / 1 / 0 tin (0 → log WARN) | `OVERSTAY` |
| E17 | Quá giờ: vị trí chưa gán khu vực | Không tìm nhân viên, không gửi | `OVERSTAY` |
| E18 | Chưa quá ân hạn quá giờ | Không đổi trạng thái, không báo quầy | |
| E19 | Nhân viên nhận tin không có SĐT | Log ghi `-` thay SĐT, không lỗi | |
| E20 | Bảo trì: 2 / 1 / 0 đơn CONFIRMED chưa kết thúc | 2 / 1 / 0 tin | `MAINTENANCE` |
| E21 | Bấm bảo trì lần hai | `200`, không lưu, không nhắn lại | Giữ `MAINTENANCE` |
| E22 | Hai người cùng xác nhận một refund | Một `200`, một `422` | 1 tin cho khách (khóa dòng) |
| E23 | DB lỗi giữa chừng (lưu `notification_job` / đơn) | Rollback transaction của đơn đó; lượt job sau thử lại | Như trước |
| E24 | Dịch vụ gửi tin lỗi | Ngoại lệ làm rollback transaction của đơn; job thử lại lượt sau (dòng `notification_job` cũng rollback nên không mất cảnh báo) | Như trước |
| E25 | Đơn CONFIRMED cũ (trước tính năng) đã quá giờ bắt đầu | Lượt job đầu chuyển NO_SHOW | Xem mục 3.3 |

---

## 7. Tiêu chí hoàn thành (Acceptance Criteria)

- [x] Mọi edge case ở mục 6 có test và test qua, trừ E22, E23, E24 (xem 7.1 và mục 8).
- [x] `mvn test` qua toàn bộ: **`Tests run: 664, Failures: 0, Errors: 0, Skipped: 0`** (trước ticket 612, thêm 52: 48 unit + 4 integration).
- [x] Không có SQL ở mục 3.2.
- [x] Không sửa file nào ngoài danh sách ở mục 2.2.
- [x] Đúng quy ước ở mục 2.4.
- [x] Đã cập nhật `checklist-use-case.md`.

### 7.1. Test cần viết

| Test | Loại | Kiểm tra edge case |
|---|---|---|
| `RefundServiceTest.test_complete_Requested_008` | Unit | Luồng chính E |
| `RefundServiceTest.test_complete_AlreadyRefunded_009`, `test_complete_Failed_010` | Unit | E4 |
| `RefundServiceTest.test_complete_NotFound_011` | Unit | E3 |
| `RefundServiceTest.test_list_ManyRefunds_012`, `_OneRefund_013`, `_NoRefunds_014` | Unit | Danh sách (VP4) |
| `BookingServiceTest.test_markNoShow_OneSecondAfterThirtyMinutes_189`, `_ExactlyThirtyMinutes_190` | Unit | E5, E10 |
| `BookingServiceTest.test_markNoShow_ShortBookingUsesHalfDuration_191`, `_ShortBookingAtHalfDuration_192` | Unit | E6 |
| `BookingServiceTest.test_markNoShow_OneHourBookingBothRulesEqual_193` | Unit | E7 |
| `BookingServiceTest.test_markNoShow_AlreadyCheckedIn_194` | Unit | E8 |
| `BookingServiceTest.test_markNoShow_ClearsPendingChange_195` | Unit | E9 |
| `BookingServiceTest.test_markNoShow_BookingNotFound_196` | Unit | E3 |
| `BookingServiceTest.test_findNoShowIds_HasIds_197`, `_NoIds_198` | Unit | Ứng viên job |
| `BookingServiceTest.test_warnEndingSoon_CheckedIn_199`, `_NotCheckedIn_200`, `_BookingNotFound_201` | Unit | E15, E3 |
| `BookingServiceTest.test_findEndingSoonIds_HasIds_202`, `_NoIds_203` | Unit | Ứng viên job |
| `BookingServiceTest.test_flagOverstay_NotifiesFrontDesk_187`, `_NotOverdueNoNotification_188` | Unit | Luồng C, E18 |
| `NotificationServiceTest.test_refundCompleted_Message_012`, `test_noShow_Message_013`, `test_resourceMaintenance_Message_014` | Unit | Nội dung tin |
| `NotificationServiceTest.test_endWarning_ExactSendTime_015`, `_OneSecondBeforeSendTime_016`, `_InsideWindow_017`, `_AtEndTime_018` | Unit | E12 |
| `NotificationServiceTest.test_endWarning_AlreadySent_019` | Unit | E13 |
| `NotificationServiceTest.test_endWarning_CustomerChoseThirtyMinutes_020`, `_CustomerChoseTenMinutes_021` | Unit | E14 |
| `NotificationServiceTest.test_overstay_ManyReceptionists_022`, `_OneReceptionist_023`, `_NoReceptionist_024` | Unit | E16 |
| `NotificationServiceTest.test_overstay_ResourceWithoutZone_025` | Unit | E17 |
| `ResourceServiceTest.test_markMaintenance_NotifiesManyAffectedBookings_013`, `_NotifiesOneAffectedBooking_014`, `_NoAffectedBookings_015` | Unit | E20 |
| `ResourceServiceTest.test_markMaintenance_AlreadyMaintenance_016` | Unit | E21 |
| `LoggingNotificationSenderTest.test_maskPhone_*_001..005`, `test_send_StaffWithoutPhone_006` | Unit | E19 |
| `NotificationIntegrationTest.noShowReleasesSlotAfterThirtyMinutes` | Integration | E5, E11 (query thật trên H2) |
| `NotificationIntegrationTest.endWarningSentOnceThenOverstayFlagged` | Integration | E12, E13, E15, luồng C (truy vấn `staff` thật) |
| `NotificationIntegrationTest.managerCompletesRefundOnce` | Integration | E1, E2, E3, E4, luồng E |
| `NotificationIntegrationTest.maintenanceTwiceKeepsStatus` | Integration | E21, luồng D (truy vấn đơn bị ảnh hưởng thật) |

Không có test: E22 (hai request đồng thời — dựa vào `SELECT … FOR UPDATE` giống `ConcurrentHoldIntegrationTest` nhưng chưa viết test đa luồng riêng), E23, E24 (rollback do Spring `@Transactional`, không có code riêng để test). E25 chính là E5 trên dữ liệu cũ.

### 7.2. Kiểm tra tay

Chạy `mvn spring-boot:run -Dspring-boot.run.profiles=h2`, xem log `[notify]`:

```bash
# Lấy token quản lý
curl -X POST localhost:8080/api/auth/staff/login -H "Content-Type: application/json" -d "{\"email\":\"ha.do@cowork.example\",\"password\":\"Cowork@123\"}"
# Danh sách yêu cầu hoàn đang chờ (sau khi hủy một đơn đã trả tiền)
curl localhost:8080/api/refunds -H "Authorization: Bearer <token>"
# Xác nhận đã hoàn
curl -X POST localhost:8080/api/refunds/<id>/complete -H "Authorization: Bearer <token>"
```

Kết quả mong đợi: lần đầu `200`, `status = REFUNDED`, log có `[notify] ... Đã hoàn ... VND cho đơn ...`; lần hai `422 INVALID_REFUND_STATE`. Cảnh báo hết giờ / no-show / quá giờ: đặt + trả + check-in một đơn rồi chờ tới mốc, log có `[notify]` tương ứng (job chạy 30 / 60 giây).

---

## 8. Sau khi AI làm xong — tự đánh giá spec

| Câu hỏi | Trả lời |
|---|---|
| Code chạy đúng ngay lần đầu? | Có — lần chạy `mvn test` đầu tiên qua 664/664. |
| Phải chat lại mấy lần để sửa? | 0 (hỏi lại 1 lần trước khi code, 2 câu ở mục 1). |
| AI làm sai ở đâu? | Không có lỗi phát hiện. |
| Spec bị hở ở mục nào (thiếu edge case, mơ hồ, thiếu tên file...)? | Request chỉ có 4 gạch đầu dòng. AI tự quyết: (1) "cảnh báo hết giờ sau 15 phút" hiểu là **trước giờ kết thúc 15 phút** theo FR-NOTI-03 / BR-05, và dùng mốc khách tự chọn 10/15/30 (FR-SVC-04); (2) no-show theo đủ BR-16 `min(30 phút, 50% thời lượng)`, mốc đúng biên **chưa** đánh dấu (giống ân hạn quá giờ), không hoàn tiền, **không** trả lượt voucher / phút gói; (3) "thông báo cho quầy" = mọi `RECEPTIONIST` `ACTIVE` cùng chi nhánh, gửi theo email vì bảng `staff` không có SĐT; (4) bảo trì chỉ nhắn đơn `CONFIRMED` chưa kết thúc (không nhắn đơn `HELD` đang thanh toán, không nhắn khách đang `CHECKED_IN` ngồi tại chỗ); bấm bảo trì lần hai không nhắn lại; (5) thêm `GET /api/refunds` để quản lý biết id cần xác nhận; quyền QL, AD; lỗi trạng thái dùng 422 giống `INVALID_BOOKING_STATE`; (6) "đổi chỗ" đã gửi tin từ `booking.md` Prompt 2 nên không làm thêm; (7) chống trùng cảnh báo bằng dòng `notification_job` SENT, khóa là `(booking, END_WARNING, send_at)` — khách đổi số phút sau khi đã nhận cảnh báo có thể nhận thêm một cảnh báo cho mốc mới; chạy nhiều instance có thể gửi trùng vì bảng chưa có unique index. Vấn đề có sẵn chưa sửa: đơn `REFUND_PENDING` (tiền về trễ) chưa ghi dòng `refund` nên không xác nhận hoàn được qua API mới. **SRS / use case cần cập nhật:** BR-16 vẫn ghi **[Giả định]** — cần khách chốt 30 phút / 50%; UC24 / UC26 nên ghi "báo lễ tân qua email". |
| Lần sau cần bổ sung gì vào template? | Không có. |

---

## Checklist

- [x] Mục 2 có tên file và tên hàm thật, đã mở code ra kiểm tra.
- [x] Mục 3 ghi đủ kiểu dữ liệu, null, mặc định, dữ liệu cũ.
- [x] Mục 5 không có câu kiểu "xử lý cho phù hợp" hay "kiểm tra hợp lệ" mà không nói kiểm tra cái gì.
- [x] Mục 6 có ít nhất: quyền, đầu vào sai, không tồn tại, sai trạng thái, giá trị ở biên, đồng thời, lỗi giữa chừng.
- [x] Mỗi edge case có kết quả cụ thể: mã HTTP + mã lỗi + trạng thái dữ liệu.
- [x] Đã ghi mục "Ngoài phạm vi" và "File không được đụng".
