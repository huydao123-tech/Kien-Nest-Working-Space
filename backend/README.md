# Cowork Booking — Backend (Spring Boot, DDD)

Backend cho hệ thống đặt chỗ Co-Working Space theo giờ. Đặc tả: [SRS](../docs/SRS.md) · [Use Case](../docs/use-cases.md) · [Sơ đồ](../docs/diagrams.md).

> Bản này cài đặt **khoảng 30% phạm vi**: phần lõi đặt chỗ – thanh toán – sử dụng. Danh sách phần chưa làm ở cuối file.

## Chạy

Yêu cầu: JDK 21, Maven 3.9+, PostgreSQL 16+ với pgAdmin 4.

Trước khi chạy: tạo database `working-space` trong pgAdmin, rồi chạy [db/schema.sql](db/schema.sql) trong Query Tool bằng tài khoản `postgres` — file tạo 35 bảng, user `cowork` và cấp quyền. Hướng dẫn: [README gốc mục 4.2](../README.md#42-tạo-database-và-bảng-trong-pgadmin-4). Thiết kế: [docs/database.md](../docs/database.md).

```bash
mvn spring-boot:run
```

```bash
mvn test
```

- Kết nối mặc định: `jdbc:postgresql://localhost:5432/working-space`, user/mật khẩu `cowork`. Ghi đè bằng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- Ứng dụng không tạo hay sửa bảng: Hibernate chạy `ddl-auto: validate`, entity lệch với bảng thì dừng ngay khi khởi động.
- Dữ liệu mẫu (14 vị trí, bảng giá cao điểm 09:00–18:00 ngày thường) được nạp khi bảng trống.
- Profile `h2` (`-Dspring-boot.run.profiles=h2`): chạy không cần PostgreSQL, Hibernate tự tạo bảng, có `/h2-console`. Integration test dùng profile này.

## Kiến trúc

Modular monolith, mỗi bounded context là một package, bên trong chia 4 lớp theo DDD:

```
com.cowork
├── shared         Shared kernel: Money, TimeRange, ResourceType, AggregateRoot, DomainEvent, xử lý lỗi API
├── space          Vị trí cho thuê và trạng thái khai thác (Resource aggregate)
├── pricing        Bảng giá & tính tiền theo block giờ (PriceRule, PricingPolicy)
├── customer       Khách hàng, định danh bằng số điện thoại (Customer aggregate)
├── booking        Lõi nghiệp vụ: vòng đời đơn đặt chỗ (Booking aggregate, kèm các khoản tiền và lịch sử trạng thái)
├── payment        Giao dịch thanh toán, webhook có ký, cổng sandbox (Payment aggregate)
├── notification   Gửi thông báo khi đơn đổi trạng thái
└── bootstrap      Dữ liệu mẫu
```

| Lớp | Vai trò | Phụ thuộc |
| :--- | :--- | :--- |
| `domain` | Aggregate, value object, domain service, domain event, interface repository | Chỉ `shared.domain` (và annotation JPA) |
| `application` | Use case, điều phối giao dịch, port ra ngoài (`PaymentGateway`, `NotificationSender`…) | `domain` |
| `infrastructure` | Cài đặt repository (Spring Data JPA), adapter cổng thanh toán, ACL giữa các context | `application`, `domain` |
| `interfaces` | REST controller, DTO | `application` |

**Giao tiếp giữa các context**
- Payment → Booking: qua domain event `PaymentSucceeded` / `PaymentFailed`, Booking nghe ở pha `AFTER_COMMIT` trong giao dịch mới.
- Payment cần thông tin đơn: qua port `PayableBookingProvider` (anti-corruption layer, cài đặt ở `payment.infrastructure.BookingContextAdapter`).
- Booking → Notification: qua các event trong `BookingEvents`.
- Booking dùng Space và Pricing qua `ResourceRepository` và `PricingService`.

**Đánh đổi có chủ đích:** aggregate được đánh annotation JPA trực tiếp thay vì tách persistence model riêng, để giảm mapping trùng lặp; hành vi vẫn nằm hoàn toàn trong aggregate (không có setter).

## Các quy tắc nghiệp vụ đã cài đặt

| Quy tắc | Nơi cài đặt |
| :--- | :--- |
| BR-01 chống double-booking | `BookingService.hold/extend/onPaymentSucceeded`: khóa bi quan hàng `Resource` (`SELECT … FOR UPDATE`) rồi kiểm tra chồng lấn. Test: `ConcurrentHoldIntegrationTest` (10 luồng, đúng 1 thành công) |
| BR-02 giữ chỗ tạm 10 phút | `Booking.hold`, `BookingJobs.expireHolds` |
| BR-03/04 tính giá theo block, tách cao điểm/thấp điểm | `PricingPolicy` |
| BR-06 phụ phí quá giờ sau ân hạn | `Booking.overstayPeriod`, `PricingService.overstaySurcharge` |
| BR-07 gia hạn chỉ khi vị trí còn trống | `BookingService.extend` |
| BR-09 vị trí bảo trì không nhận đặt | `Resource.ensureCanHost` |
| BR-11 webhook về trễ: kích hoạt lại hoặc chờ hoàn tiền | `BookingService.onPaymentSucceeded` |
| BR-14 sức chứa | `Resource.ensureCanHost` |
| BR-15 hoàn tiền khi hủy (giả định) | `CancellationPolicy` |
| BR-17 check-in sớm 15 phút | `Booking.checkIn` |
| FR-PAY-02/03 webhook idempotent, chữ ký HMAC | `Payment.succeed/fail`, `WebhookSignature` |

Đơn `HELD` đã quá hạn nhưng job chưa chạy **vẫn chiếm chỗ**. Nhờ vậy, khi webhook về đúng khoảng trễ đó, việc xác nhận đơn không thể gây trùng chỗ.

## API

| Method | Path | Use case |
| :--- | :--- | :--- |
| GET | `/api/availability?type=&startAt=&endAt=&partySize=` | UC02 |
| POST | `/api/bookings` | UC03 giữ chỗ |
| GET | `/api/bookings/{id}` | UC06 |
| POST | `/api/bookings/{id}/payments` | UC04 tạo giao dịch |
| POST | `/api/payments/webhook` (header `X-Signature`) | UC04/UC25 |
| POST | `/api/payments/sandbox/{txn}/complete?result=SUCCESS&amount=` | Giả lập cổng (dev) |
| POST | `/api/bookings/{id}/cancel` | UC06 |
| POST | `/api/check-ins` `{qrToken}` | UC07 |
| POST | `/api/bookings/{id}/extend` `{newEndAt}` | UC08 |
| POST | `/api/check-outs` `{qrToken}` | UC09 |
| POST | `/api/bookings/{id}/settle` `{amount}` | UC16 |
| GET | `/api/resources` | — |
| POST | `/api/resources/{id}/maintenance` · `/reactivate` | UC17 (một phần) |

Lỗi trả về dạng `ProblemDetail` kèm trường `code` (ví dụ `SLOT_UNAVAILABLE`, `CHECK_IN_TOO_EARLY`).

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

## Chưa cài đặt (≈70% còn lại)

- UC01, UC23: tài khoản, đăng nhập, phân quyền (hiện API mở hoàn toàn).
- UC05, UC21: voucher, Membership.
- UC10, UC11, UC17 đầy đủ, UC18: gọi dịch vụ, ticket sự cố, sắp xếp lại booking khi bảo trì, đền bù.
- UC13–UC15: màn hình quầy, walk-in thu tiền mặt, đổi chỗ.
- UC19, UC20, UC22: quản trị sơ đồ, bảng giá, báo cáo.
- UC24: no-show. UC25: job đối soát chủ động, gọi API hoàn tiền (hiện chỉ tạo event `BookingRefundRequired`).
- UC26: lịch nhắc trước 30 phút / cảnh báo trước 15 phút, kênh thật SMS/Zalo/Email.
- Add-on, nhiều chi nhánh, hóa đơn điện tử, tích hợp cửa kiểm soát/Wi-Fi.

## Lưu ý trước khi lên production

- **Outbox:** event giữa các context hiện được phát trong tiến trình. Nếu listener lỗi sau khi giao dịch thanh toán đã commit, đơn sẽ không được cập nhật, nên cần transactional outbox kết hợp job đối soát.
- **Test trên PostgreSQL:** integration test đang chạy trên H2, nên exclusion constraint `ex_booking_no_overlap` và các partial index chưa được kiểm tra tự động. Nên thêm Testcontainers.
- **Cấu hình:** đặt `DB_*`, `PAYMENT_WEBHOOK_SECRET`; tắt `payment.sandbox.enabled`, `app.seed-demo-data`.
