# Cowork Booking — Backend (Spring Boot, DDD)

Backend cho hệ thống đặt chỗ Co-Working Space theo giờ. Đặc tả: [SRS](../docs/SRS.md) · [Use Case](../docs/use-cases.md) · [Sơ đồ](../docs/diagrams.md).

> Bản này cài đặt **khoảng 30% phạm vi**: phần lõi đặt chỗ – thanh toán – sử dụng. Danh sách phần chưa làm ở cuối file.

## Chạy

Yêu cầu: JDK 21, Maven 3.9+, PostgreSQL 16+ với pgAdmin 4.

Trước khi chạy: tạo database `working-space` trong pgAdmin, rồi chạy [db/schema.sql](db/schema.sql) trong Query Tool bằng tài khoản `postgres` — file tạo 36 bảng, user `cowork` và cấp quyền. Hướng dẫn: [README gốc mục 4.2](../README.md#42-tạo-database-và-bảng-trong-pgadmin-4). Thiết kế: [docs/database.md](../docs/database.md).

```bash
mvn spring-boot:run
```

```bash
mvn test
```

- Kết nối mặc định: `jdbc:postgresql://localhost:5432/working-space`, user/mật khẩu `cowork`. Ghi đè bằng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- Ứng dụng không tạo hay sửa bảng: Hibernate chạy `ddl-auto: validate`, entity lệch với bảng thì dừng ngay khi khởi động.
- Dữ liệu mẫu (14 vị trí, bảng giá cao điểm 09:00–18:00 ngày thường, 5 tài khoản nhân viên, add-on, voucher, gói thành viên) được nạp khi bảng trống. Mật khẩu nhân viên mẫu: `Cowork@123` (`admin@`, `ha.do@`, `huy.ngo@`, `khoa.bui@cowork.example`; `lan.hoang@` bị khóa).
- Đặt `JWT_SECRET` (>= 32 ký tự) trước khi chạy production; giá trị mặc định trong `application.yml` chỉ dùng cho dev.
- Profile `h2` (`-Dspring-boot.run.profiles=h2`): chạy không cần PostgreSQL, Hibernate tự tạo bảng, có `/h2-console`. Integration test dùng profile này.

## Kiến trúc

Layered architecture 3 tầng:

```
com.cowork
├── presentation
│   └── controller    REST controller, xử lý webhook, ApiExceptionHandler
├── business
│   ├── service       Nghiệp vụ + transaction: BookingService, PaymentService, PricingService, CustomerService, ResourceService, AvailabilityService, NotificationService,
│   │                 AuthService, OtpService, TokenService, StaffService, CatalogService, AddonService, VoucherService, MembershipService, MenuService, RefundService
│   ├── job           BookingJobs: hết hạn giữ chỗ, đánh dấu quá giờ, no-show, cảnh báo sắp hết giờ
│   └── exception     DomainException, ConflictException, NotFoundException
├── dataaccess
│   ├── repository    Mỗi bảng 1 interface extends JpaRepository
│   ├── entity        36 entity JPA, mỗi bảng 1 class trùng tên bảng (Booking, SpaceResource, BookingCharge, OtpCode...)
│   ├── enums         Enum trạng thái / loại (BookingStatus, ResourceTypeCode...)
│   ├── valueobject   Kiểu giá trị (Money, TimeRange, ContactInfo)
│   └── integration   Hệ thống ngoài: PaymentGateway (sandbox), NotificationSender (log), WebhookSignature
├── dto               (dùng chung) BookingDtos, AuthDtos, CatalogDtos, CommerceDtos, PeopleDtos, RefundDtos, Actor, PriceQuote
└── config            Properties, Clock, SecurityConfig (JWT + RBAC), dữ liệu mẫu
```

Chiều phụ thuộc: `presentation → business → dataaccess`. `business` không import gì từ `presentation`.

`dataaccess` không import gì từ `business` hay `presentation`: entity chỉ có field + getter/setter. Mọi quy tắc nghiệp vụ nằm ở `business.service`, mỗi nghiệp vụ gọn trong một service: đơn đặt chỗ trong `BookingService`, thanh toán trong `PaymentService`, khách hàng trong `CustomerService`, chỗ ngồi trong `ResourceService`, tính giá trong `PricingService`. Các service gọi thẳng nhau, không dùng event.

**Đặc điểm khác**
- `PaymentService` gọi thẳng `BookingService.onPaymentSucceeded/onPaymentFailed`; lưu giao dịch và cập nhật đơn nằm chung một transaction.
- `BookingService` gọi thẳng `NotificationService` sau khi lưu đơn (xác nhận, hết hạn, thất bại, hủy, đổi chỗ, hoàn tiền vì thanh toán trễ, no-show, quá giờ → báo lễ tân, cảnh báo sắp hết giờ). `ResourceService` (bảo trì) và `RefundService` (hoàn tiền xong) cũng gọi `NotificationService`.

## Các quy tắc nghiệp vụ đã cài đặt

| Quy tắc | Nơi cài đặt |
| :--- | :--- |
| BR-01 chống double-booking | `BookingService.hold/extend/onPaymentSucceeded`: khóa bi quan hàng `SpaceResource` (`SELECT … FOR UPDATE`) rồi kiểm tra chồng lấn. Test: `ConcurrentHoldIntegrationTest` (10 luồng, đúng 1 thành công) |
| BR-02 giữ chỗ tạm 10 phút | `BookingService.hold/expireHold`, `BookingJobs.expireHolds` |
| BR-03/04 tính giá theo block, tách cao điểm/thấp điểm | `PricingService.quote` |
| BR-06 phụ phí quá giờ sau ân hạn | `BookingService.checkOut`, `PricingService.overstaySurcharge` |
| BR-07 gia hạn chỉ khi vị trí còn trống | `BookingService.extend` |
| BR-09 vị trí bảo trì không nhận đặt | `ResourceService.requireBookable` |
| BR-11 webhook về trễ: kích hoạt lại hoặc chờ hoàn tiền | `BookingService.onPaymentSucceeded` |
| BR-14 sức chứa | `ResourceService.requireBookable` |
| Hủy / đổi chỗ đơn đã thanh toán: trước giờ bắt đầu ≥ 2 giờ (`booking.change-notice`), hủy hoàn 100% — thay BR-15 theo ticket booking.md; đổi chỗ đắt hơn giữ chỗ mới chờ trả phần chênh | `BookingService.cancel/reschedule`, `RefundService`, `BookingJobs.expirePendingChanges` |
| FR-BOOK-02 add-on + tồn kho theo khung giờ | `AddonService.reserve/recheck` (khóa dòng add-on, tính lúc dùng nhiều nhất) |
| FR-BOOK-06 tổng tiền = thuê + add-on − voucher − membership | dòng `booking_charge` (`ChargeType.isDeduction`), `Booking.getTotal` |
| BR-12 voucher, FR-PROMO-03 giữ / trả lượt | `VoucherService.redeem/markUsed/release` |
| BR-13 khấu trừ giờ membership | `MembershipService.deduct/markUsed/release` |
| UC01, UC23 đăng nhập, RBAC | `AuthService`, `OtpService`, `TokenService`, `SecurityConfig`, `@PreAuthorize` trong controller (`Roles`) |
| BR-17 check-in sớm 15 phút | `BookingService.checkIn` |
| BR-16 no-show: quá min(30 phút, 50% thời lượng) sau giờ bắt đầu chưa check-in → NO_SHOW, không hoàn tiền (`booking.no-show-grace`, `no-show-grace-percent`) | `BookingService.markNoShow`, `BookingJobs.markNoShows` |
| BR-05, FR-NOTI-03 cảnh báo sắp hết giờ trước 10 / 15 / 30 phút (theo `customer.notify_before_minutes`), mỗi mốc một lần (`notification_job`) | `NotificationService.endWarning`, `BookingService.warnEndingSoon`, `BookingJobs.warnEndingSoon` |
| FR-SYS-02 quá giờ → OVERSTAY, báo lễ tân chi nhánh | `BookingService.flagOverstay`, `NotificationService.overstay` |
| FR-OPS-07 vị trí bảo trì → nhắn khách các đơn CONFIRMED chưa kết thúc | `ResourceService.markMaintenance` |
| FR-PAY-02/03 webhook idempotent, chữ ký HMAC | `PaymentService.handleGatewayResult`, `WebhookSignature` |

Đơn `HELD` đã quá hạn nhưng job chưa chạy **vẫn chiếm chỗ**. Nhờ vậy, khi webhook về đúng khoảng trễ đó, việc xác nhận đơn không thể gây trùng chỗ.

## API

Gửi token bằng header `Authorization: Bearer <accessToken>`. Cột **Quyền**: `public` = không cần token; `KH` = khách có tài khoản (role `CUSTOMER`); `AD` ADMIN, `QL` MANAGER, `LT` RECEPTIONIST, `KT` TECHNICIAN. Bảng phân quyền đầy đủ theo bảng dữ liệu: [ticket-response/authentication.md](../ticket-response/authentication.md) mục 5.1.

**Đăng nhập (UC01, UC23)**

| Method | Path | Quyền | Use case |
| :--- | :--- | :--- | :--- |
| POST | `/api/auth/otp` `{phone}` | public | UC01 gửi OTP (5 phút, gửi lại sau 60 giây) |
| POST | `/api/auth/register/phone` `{phone, otp, fullName, password, email?}` | public | UC01 đăng ký bằng SĐT + OTP, nhận lại đơn vãng lai cũ (FR-AUTH-03) |
| POST | `/api/auth/register/email` `{email, phone, fullName, password}` | public | UC01 đăng ký bằng email |
| POST | `/api/auth/login` `{login, password}` | public | UC01 `login` là SĐT hoặc email |
| POST | `/api/auth/login/otp` `{phone, otp}` | public | UC01 |
| POST | `/api/auth/staff/login` `{email, password}` | public | UC23 |
| GET | `/api/auth/me` | đã đăng nhập | Hồ sơ người đang đăng nhập; khách có thêm `membership` (số phút gói còn lại, FR-AUTH-05) |
| PUT | `/api/auth/me` `{fullName, email?, notifyBeforeMinutes?, preferredChannel?}` | KH | FR-AUTH-05 sửa hồ sơ (không đổi SĐT) |
| POST | `/api/auth/me/password` `{currentPassword, newPassword}` | KH | FR-AUTH-05 đổi mật khẩu, trả 204 |
| POST | `/api/auth/password/reset` `{phone, otp, newPassword}` | public | FR-AUTH-05 quên mật khẩu (OTP lấy qua `/api/auth/otp`), trả token |

**Tra cứu, đặt chỗ, sử dụng**

| Method | Path | Quyền | Use case |
| :--- | :--- | :--- | :--- |
| GET | `/api/branches` | public | UC02 chọn chi nhánh |
| GET | `/api/amenities` | public | UC02 danh mục tiện ích để lọc |
| GET | `/api/availability?branchId=&type=&startAt=&endAt=&partySize=&amenities=` | public | UC02 vị trí còn trống + giá dự kiến (`branchId` bắt buộc) |
| GET | `/api/branches/{id}/floor-plan?type=&startAt=&endAt=` | public | UC02 sơ đồ mặt bằng, trạng thái từng vị trí |
| GET | `/api/addons?branchId=` | public | FR-BOOK-02 add-on đang cho thuê của chi nhánh |
| POST | `/api/bookings` `{..., addons?, voucherCode?, useMembership?}` | public (gửi token khách thì đơn thuộc tài khoản) | UC03 giữ chỗ kèm add-on / voucher / giờ membership |
| GET | `/api/bookings/{id}` | KH (đơn của mình), LT, QL, AD | UC06 chi tiết đơn |
| GET | `/api/me/bookings?status=&page=&size=` | KH | Đơn của tôi |
| GET | `/api/bookings?customerId=&phone=&status=&from=&to=&page=&size=` | LT, QL, AD | Toàn bộ đơn, lọc theo khách |
| POST | `/api/bookings/{id}/payments` | public | UC04 tạo giao dịch: đơn HELD, hoặc phần chênh của yêu cầu đổi chỗ đang chờ (đơn CONFIRMED) |
| POST | `/api/payments/webhook` (header `X-Signature`) | public (chữ ký HMAC) | UC04/UC25 |
| POST | `/api/payments/sandbox/{txn}/complete?result=SUCCESS&amount=` | public, chỉ dev | Giả lập cổng |
| POST | `/api/bookings/{id}/cancel` | KH (đơn của mình), LT, QL, AD | UC06 hủy trước ≥ 2 giờ, hoàn 100% |
| POST | `/api/bookings/{id}/reschedule` `{resourceId?, startAt, endAt}` | KH (đơn của mình), LT, QL, AD | Đổi vị trí / khung giờ / thời lượng trước ≥ 2 giờ. Rẻ hơn: đổi ngay, hoàn chênh. Đắt hơn: `paymentRequired = true`, giữ chỗ mới 10 phút chờ trả `amountDue` |
| POST | `/api/check-ins` `{qrToken}` | LT, QL, AD | UC07 |
| POST | `/api/bookings/{id}/extend` `{newEndAt}` | LT, QL, AD | UC08 |
| POST | `/api/check-outs` `{qrToken}` | LT, QL, AD | UC09 |
| POST | `/api/bookings/{id}/settle` `{amount}` | LT, QL, AD | UC16 |
| GET | `/api/resources` | mọi nhân viên | — |
| POST | `/api/resources/{id}/maintenance` · `/reactivate` | KT, QL, AD | UC17 (một phần). Bảo trì: nhắn khách của các đơn CONFIRMED chưa kết thúc trên vị trí (FR-OPS-07); vị trí đã bảo trì thì giữ nguyên, không nhắn lại |
| GET | `/api/refunds?status=REQUESTED` | QL, AD | UC25 yêu cầu hoàn tiền theo trạng thái (mặc định `REQUESTED`), cũ nhất trước |
| POST | `/api/refunds/{id}/complete` | QL, AD | FR-NOTI-04 xác nhận đã hoàn tay xong: `REQUESTED` → `REFUNDED`, nhắn khách. Trạng thái khác → `422 INVALID_REFUND_STATE` |

**Quản trị danh mục (CRUD: `GET` danh sách, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`)**

| Path | Xem | Tạo | Sửa | Xóa |
| :--- | :--- | :--- | :--- | :--- |
| `/api/admin/branches` | mọi nhân viên | AD | AD, QL | AD |
| `/api/admin/zones` | mọi nhân viên | AD, QL | AD, QL | AD, QL |
| `/api/admin/resource-types` | mọi nhân viên | AD | AD (chỉ tên, mô tả) | AD |
| `/api/admin/resources` | mọi nhân viên | AD, QL | AD, QL | AD, QL |
| `/api/admin/amenities` | mọi nhân viên | AD, QL | AD, QL | AD, QL |
| `/api/admin/price-rules` | LT, QL, AD | AD, QL | AD, QL | AD, QL |
| `/api/admin/addons` | mọi nhân viên | AD, QL | AD, QL | AD, QL |
| `/api/admin/menu-categories`, `/api/admin/menu-items` | LT, QL, AD | AD, QL | AD, QL | AD, QL |
| `/api/admin/vouchers` | LT, QL, AD | AD, QL | AD, QL | AD, QL |
| `/api/admin/membership-plans` | LT, QL, AD | AD, QL | AD, QL | AD, QL |
| `/api/admin/staff` | QL, AD | AD | AD | AD |
| `/api/admin/customers` | LT, QL, AD | LT, QL, AD | LT, QL, AD | AD |

Xóa dòng đang được dữ liệu khác dùng (chi nhánh còn khu vực, vị trí đã có đơn...) trả `409 DATA_CONFLICT`; hãy đổi trạng thái (INACTIVE / PAUSED / STOPPED / LOCKED) thay vì xóa.

Lỗi trả về dạng `ProblemDetail` kèm trường `code` (ví dụ `SLOT_UNAVAILABLE`, `CHECK_IN_TOO_EARLY`). Chưa đăng nhập / token hết hạn → `401 UNAUTHORIZED`; sai vai trò → `403 FORBIDDEN`; sai mật khẩu → `401 INVALID_CREDENTIALS`.

Ví dụ một lượt đặt chỗ:

```bash
curl "localhost:8080/api/availability?type=HOT_DESK&startAt=2026-11-02T09:00:00&endAt=2026-11-02T11:00:00"
```

```bash
curl -X POST localhost:8080/api/bookings -H "Content-Type: application/json" -d "{\"resourceId\":\"<id>\",\"startAt\":\"2026-11-02T09:00:00\",\"endAt\":\"2026-11-02T11:00:00\",\"partySize\":1,\"contact\":{\"fullName\":\"Nguyen Van A\",\"phone\":\"0912345678\"}}"
```

```bash
curl -X POST localhost:8080/api/bookings/<bookingId>/payments -H "Content-Type: application/json" -d "{\"method\":\"VIETQR\"}"
```

```bash
curl -X POST "localhost:8080/api/payments/sandbox/<gatewayTransactionId>/complete?result=SUCCESS&amount=<totalAmount>"
```

## Chưa cài đặt

- UC01 phần còn lại: khách sửa hồ sơ, đổi / quên mật khẩu, mua gói thành viên, refresh token / thu hồi token. Phân quyền theo chi nhánh (FR-AUTH-04): nhân viên chi nhánh A vẫn thấy dữ liệu chi nhánh B.
- UC10, UC11, UC17 đầy đủ, UC18: gọi dịch vụ, ticket sự cố, sắp xếp lại booking khi bảo trì, đền bù.
- UC13–UC15: màn hình quầy, walk-in thu tiền mặt, đổi chỗ.
- UC22: báo cáo.
- UC25: job đối soát chủ động, gọi API hoàn tiền của cổng (hiện ghi `refund` REQUESTED, quản lý hoàn tay rồi xác nhận REFUNDED).
- UC26: nhắc lịch trước 30 phút, thông báo đền bù, kênh thật SMS/Zalo/Email (mọi tin, kể cả OTP, hiện chỉ ghi log).
- Hóa đơn điện tử, tích hợp cửa kiểm soát/Wi-Fi.

## Lưu ý trước khi lên production

- **Thông báo:** tin nhắn đang được gửi trong transaction, trước khi commit. Khi nối SMS / Zalo thật cần chuyển sang gửi sau commit (hoặc qua bảng `notification_job` + job gửi).
- **Test trên PostgreSQL:** integration test đang chạy trên H2, nên exclusion constraint `ex_booking_no_overlap` và các partial index chưa được kiểm tra tự động. Nên thêm Testcontainers.
- **Cấu hình:** đặt `DB_*`, `PAYMENT_WEBHOOK_SECRET`, `JWT_SECRET`; tắt `payment.sandbox.enabled`, `app.seed-demo-data`.
- **Bảo mật:** đổi mật khẩu các tài khoản nhân viên mẫu (`Cowork@123`); thêm giới hạn số lần gọi (rate limit) cho `/api/auth/*`, đặc biệt `/api/auth/otp`.
