# Hệ thống đặt chỗ Co-Working Space theo giờ

Hướng dẫn này viết cho người **mới bắt đầu**: chưa quen Spring Boot, chưa biết DDD là gì, chỉ cần biết lập trình Java cơ bản. Đọc lần lượt từ trên xuống là có thể chạy được dự án và tự làm thêm chức năng mới.

## Mục lục
1. [Dự án này làm gì?](#1-dự-án-này-làm-gì)
2. [Trong thư mục có gì?](#2-trong-thư-mục-có-gì)
3. [Cài đặt công cụ (làm một lần)](#3-cài-đặt-công-cụ-làm-một-lần)
4. [Chạy dự án lần đầu](#4-chạy-dự-án-lần-đầu)
5. [Thử gọi API](#5-thử-gọi-api)
6. [Hiểu kiến trúc trong 10 phút](#6-hiểu-kiến-trúc-trong-10-phút)
7. [Một request đi qua code như thế nào](#7-một-request-đi-qua-code-như-thế-nào)
8. [Làm thêm chức năng mới — ví dụ từng bước](#8-làm-thêm-chức-năng-mới--ví-dụ-từng-bước)
9. [Quy ước code của dự án](#9-quy-ước-code-của-dự-án)
10. [Việc cần làm tiếp theo](#10-việc-cần-làm-tiếp-theo)
11. [Lỗi thường gặp](#11-lỗi-thường-gặp)
12. [Lưu công việc với Git](#12-lưu-công-việc-với-git)
13. [Từ điển thuật ngữ](#13-từ-điển-thuật-ngữ)

---

## 1. Dự án này làm gì?

Một co-working space cho khách thuê chỗ ngồi theo giờ. Hệ thống giúp:

- **Khách hàng:** tìm chỗ trống → đặt chỗ → thanh toán → nhận mã QR → quét QR để vào (check-in) → gia hạn nếu muốn ngồi thêm → quét QR để ra (check-out).
- **Lễ tân:** hỗ trợ khách tại quầy, thu phụ phí khi khách ngồi quá giờ.
- **Quản lý:** cấu hình chỗ ngồi, bảng giá, xem báo cáo.

Điều quan trọng nhất hệ thống phải bảo đảm: **một chỗ, trong một khung giờ, chỉ thuộc về đúng một khách** (không bao giờ bị đặt trùng).

Hiện tại mới có **backend** (phần máy chủ, cung cấp API), **chưa có giao diện**. Đã làm khoảng **30%** chức năng — xem [mục 10](#10-việc-cần-làm-tiếp-theo).

## 2. Trong thư mục có gì?

```
kien nest/
├── README.md                    ← bạn đang đọc file này
├── requirement-gathering.md     ← tài liệu thu thập yêu cầu ban đầu (đọc đầu tiên để hiểu bài toán)
├── docs/
│   ├── SRS.md                   ← đặc tả yêu cầu: mọi chức năng (FR-...), quy tắc nghiệp vụ (BR-...)
│   ├── use-cases.md             ← 26 use case (UC01–UC26), mỗi cái mô tả từng bước người dùng làm gì
│   ├── diagrams.md              ← sơ đồ: context, swimlane, state (vòng đời đơn đặt chỗ...)
│   └── database.md              ← thiết kế database: ERD, từng bảng, ràng buộc, các bảng sẽ làm sau
└── backend/                     ← toàn bộ mã nguồn Java
    ├── pom.xml                  ← khai báo thư viện, phiên bản Java (giống package.json bên Node)
    ├── README.md                ← tài liệu kỹ thuật ngắn gọn cho dev đã quen
    ├── db/setup-local.sql       ← script tạo database + user trên máy dev (chạy 1 lần)
    ├── docker-compose.yml       ← chạy PostgreSQL bằng Docker (nếu không muốn cài PostgreSQL)
    └── src/
        ├── main/java/com/cowork/   ← code chính
        ├── main/resources/application.yml      ← cấu hình chính (kết nối PostgreSQL, thời gian giữ chỗ, giá...)
        ├── main/resources/application-h2.yml   ← cấu hình chạy không cần PostgreSQL (dùng cho test)
        ├── main/resources/db/migration/        ← các file SQL tạo bảng (Flyway tự chạy)
        └── test/java/com/cowork/   ← code kiểm thử
```

**Thứ tự nên đọc tài liệu:** `requirement-gathering.md` → `docs/diagrams.md` (xem hình cho dễ hiểu) → `docs/use-cases.md` (use case nào đang làm thì đọc cái đó) → `docs/SRS.md` (tra cứu khi cần).

> Mẹo: trong code, các chú thích kiểu `UC07`, `BR-06`, `FR-PAY-03` chính là mã trong tài liệu. Thấy mã nào lạ thì tìm (Ctrl+F) mã đó trong `docs/` để biết đoạn code đó làm theo yêu cầu nào.

## 3. Cài đặt công cụ (làm một lần)

Cần 4 thứ:

| Công cụ | Để làm gì | Tải ở đâu |
| :--- | :--- | :--- |
| **JDK 21** | Chạy và biên dịch Java | https://adoptium.net (chọn Temurin 21, bản Windows `.msi`) |
| **Maven 3.9+** | Tải thư viện, build, chạy test | https://maven.apache.org/download.cgi |
| **PostgreSQL 16+** | Database lưu dữ liệu | https://www.postgresql.org/download/windows (bộ cài có kèm pgAdmin). **Ghi nhớ mật khẩu tài khoản `postgres`** bạn đặt lúc cài |
| **IntelliJ IDEA Community** (khuyên dùng) | Viết code | https://www.jetbrains.com/idea/download |

> Đã cài Docker thì có thể bỏ qua PostgreSQL: trong thư mục `backend` chạy `docker compose up -d` là có database sẵn.

Kiểm tra đã cài đúng chưa — mở PowerShell hoặc Terminal, gõ từng lệnh:

```bash
java -version
```

```bash
mvn -v
```

Phải thấy `21.x` ở cả hai. Nếu báo *"không nhận ra lệnh"* → xem [mục 11](#11-lỗi-thường-gặp).

**Tạo database cho dự án (làm một lần).** Mở PowerShell, chạy lệnh dưới (đổi `18` thành phiên bản PostgreSQL của bạn), rồi nhập mật khẩu tài khoản `postgres` khi được hỏi:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U postgres -f "D:\kien nest\backend\db\setup-local.sql"
```

Lệnh này tạo database `cowork` và user `cowork` (mật khẩu `cowork`, chỉ dùng cho máy dev). Bạn **không cần tự tạo bảng** — khi ứng dụng khởi động, **Flyway** tự chạy các file SQL trong `src/main/resources/db/migration` để tạo bảng.

**Mở dự án trong IntelliJ:** `File → Open` → chọn thư mục `backend` (chọn đúng thư mục có file `pom.xml`). Đợi góc dưới bên phải tải xong thư viện (lần đầu mất vài phút).

## 4. Chạy dự án lần đầu

### 4.1. Chạy test (để chắc mọi thứ đang ổn)

```bash
cd "D:/kien nest/backend"
```

```bash
mvn test
```

Kết quả đúng là dòng cuối có `Tests run: 23, Failures: 0, Errors: 0` và `BUILD SUCCESS`.

> Hãy chạy `mvn test` **trước khi bắt đầu** và **sau khi sửa xong** mỗi lần. Nếu trước khi sửa đã đỏ thì không phải lỗi của bạn.
>
> Test tự động chạy trên **H2** (database tạm trong RAM), nên chạy test **không cần** PostgreSQL.

### 4.2. Chạy ứng dụng

```bash
mvn spring-boot:run
```

Đợi đến khi thấy dòng `Started CoworkBookingApplication`. Ứng dụng đang chạy ở **http://localhost:8080**. Muốn dừng: bấm `Ctrl + C`.

Lần chạy đầu, log sẽ có dòng `Migrating schema "public" to version "1 - core schema"` — đó là Flyway đang tạo bảng. Những lần sau Flyway thấy bảng đã có thì bỏ qua.

Trong IntelliJ: mở file `CoworkBookingApplication.java` → bấm nút ▶ xanh cạnh hàm `main`.

**Chưa cài được PostgreSQL?** Chạy tạm bằng H2 (dữ liệu mất khi tắt ứng dụng):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

### 4.3. Xem dữ liệu trong database

Mở **pgAdmin** (cài kèm PostgreSQL) → chuột phải *Servers* → *Register → Server*:
- Tab *General*: Name `cowork`
- Tab *Connection*: Host `localhost`, Port `5432`, Database `cowork`, Username `cowork`, Password `cowork`

Sau đó mở *cowork → Databases → cowork → Schemas → public → Tables*. Chuột phải một bảng → *View/Edit Data → All Rows*. Hoặc dùng *Query Tool* để gõ SQL, ví dụ `SELECT * FROM booking ORDER BY created_at DESC;`.

(IntelliJ bản Ultimate hoặc DBeaver cũng kết nối được với thông số như trên.)

Các bảng hiện có (thiết kế chi tiết: [docs/database.md](docs/database.md)):

| Bảng | Chứa gì |
| :--- | :--- |
| `space_resource` | Chỗ ngồi / phòng (15 chỗ mẫu: `HD-01`…`HD-06`, `FD-01`…, `FB-01`, `MR-01`, `MR-02`, `EV-01`) |
| `price_rule` | Bảng giá (cao điểm 09:00–18:00 ngày thường, còn lại thấp điểm) |
| `booking` | Đơn đặt chỗ |
| `payment` | Giao dịch thanh toán |
| `flyway_schema_history` | Flyway tự tạo để ghi các file SQL đã chạy — **không sửa tay** |

Dữ liệu mẫu được nạp **một lần** khi bảng `space_resource` còn trống. Muốn làm sạch database để chạy lại từ đầu: trong pgAdmin chạy `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` (bằng user `cowork`), rồi khởi động lại ứng dụng.

## 5. Thử gọi API

API là các "địa chỉ" mà giao diện (web/app) sẽ gọi tới. Có thể thử bằng **Postman** (dễ cho người mới — tải ở https://www.postman.com/downloads) hoặc lệnh `curl`.

Làm theo đúng thứ tự dưới đây để đi hết một lượt đặt chỗ. Chọn một ngày **trong tương lai**, ví dụ `2026-12-01`.

**Bước 1 — Tìm chỗ trống** (GET)
```
http://localhost:8080/api/availability?type=HOT_DESK&startAt=2026-12-01T09:00:00&endAt=2026-12-01T11:00:00
```
Kết quả là danh sách chỗ trống kèm giá. Copy giá trị `resourceId` của một chỗ.

**Bước 2 — Giữ chỗ** (POST `http://localhost:8080/api/bookings`, Body → raw → JSON)
```json
{
  "resourceId": "<dán resourceId vào đây>",
  "startAt": "2026-12-01T09:00:00",
  "endAt": "2026-12-01T11:00:00",
  "partySize": 1,
  "contact": { "fullName": "Nguyen Van A", "phone": "0912345678", "email": "a@example.com" }
}
```
Kết quả có `"status": "HELD"` (đang giữ chỗ 10 phút). Copy `id`, `qrToken`, `totalAmount`.

**Bước 3 — Tạo giao dịch thanh toán** (POST `http://localhost:8080/api/bookings/<id>/payments`)
```json
{ "method": "VIETQR" }
```
Copy `gatewayTransactionId`.

**Bước 4 — Giả lập khách đã trả tiền** (POST, không cần body)
```
http://localhost:8080/api/payments/sandbox/<gatewayTransactionId>/complete?result=SUCCESS&amount=<totalAmount>
```
Đây là "cổng thanh toán giả" để test, chưa trừ tiền thật.

**Bước 5 — Xem đơn** (GET `http://localhost:8080/api/bookings/<id>`) → `"status": "CONFIRMED"`. Trong cửa sổ chạy ứng dụng sẽ thấy dòng log `[notify] ... Đặt chỗ thành công`.

**Bước 6 — Check-in** (POST `http://localhost:8080/api/check-ins`)
```json
{ "qrToken": "<qrToken>" }
```
⚠️ Chỉ check-in được từ 15 phút trước giờ bắt đầu. Nếu đặt ngày tương lai, bạn sẽ nhận lỗi `CHECK_IN_TOO_EARLY` — **đó là đúng**. Muốn thử trọn vẹn check-in/check-out, hãy đặt khung giờ bắt đầu ngay bây giờ, hoặc xem test `BookingFlowIntegrationTest` (test có thể "tua" đồng hồ).

Danh sách đầy đủ API: xem [backend/README.md](backend/README.md#api).

**Khi gọi API bị lỗi**, phản hồi có dạng:
```json
{ "status": 409, "detail": "Vị trí HD-01 đã có người đặt trong khung giờ này", "code": "SLOT_UNAVAILABLE" }
```
Đọc `code` và `detail` là biết lý do. Tìm `code` đó trong thư mục `src` để thấy chỗ ném lỗi.

## 6. Hiểu kiến trúc trong 10 phút

### 6.1. Spring Boot là gì?
Một framework Java giúp viết máy chủ web nhanh. Bạn chỉ cần nhớ vài annotation (chữ bắt đầu bằng `@`):

| Annotation | Ý nghĩa đơn giản |
| :--- | :--- |
| `@RestController` | Lớp này nhận request HTTP |
| `@GetMapping("/abc")`, `@PostMapping` | Hàm này xử lý request tới địa chỉ `/abc` |
| `@Service` | Lớp chứa logic xử lý, Spring tự tạo và đưa vào nơi cần dùng |
| `@Component`, `@Repository` | Tương tự `@Service`, cho các loại lớp khác |
| `@Transactional` | Mọi thao tác database trong hàm này hoặc thành công hết, hoặc hủy hết |
| `@Entity` | Lớp này được lưu thành một bảng trong database |
| `@Scheduled` | Hàm này tự chạy định kỳ (như hẹn giờ) |

**Dependency injection:** bạn không bao giờ tự `new BookingService(...)`. Chỉ cần khai báo trong constructor, Spring tự đưa vào:
```java
BookingController(BookingService bookingService) { this.bookingService = bookingService; }
```

### 6.2. DDD là gì?
DDD (Domain-Driven Design) là cách tổ chức code **theo nghiệp vụ** thay vì theo kỹ thuật. Ý tưởng chính:

1. **Chia hệ thống thành các vùng nghiệp vụ** (gọi là *bounded context*). Mỗi vùng là một thư mục:

   | Thư mục | Vùng nghiệp vụ |
   | :--- | :--- |
   | `space` | Chỗ ngồi, phòng: sức chứa, đang bảo trì hay không |
   | `pricing` | Bảng giá, cách tính tiền |
   | `booking` | **Trái tim hệ thống:** đơn đặt chỗ và vòng đời của nó |
   | `payment` | Thanh toán qua cổng |
   | `notification` | Gửi tin nhắn cho khách |
   | `shared` | Những thứ dùng chung: tiền (`Money`), khoảng thời gian (`TimeRange`) |

2. **Quy tắc nghiệp vụ nằm trong đối tượng nghiệp vụ.** Ví dụ, quy tắc "chỉ được check-in sớm tối đa 15 phút" nằm trong hàm `Booking.checkIn(...)`, không nằm rải rác ở controller. Không có hàm `setStatus(...)` — muốn đổi trạng thái phải gọi đúng hành động nghiệp vụ (`checkIn`, `cancel`, `extendTo`...). Nhờ vậy không ai có thể vô tình làm sai quy tắc.

3. **Mỗi vùng chia 4 tầng** (4 thư mục con):

   ```
   booking/
   ├── domain/          ← NGHIỆP VỤ THUẦN: Booking, BookingStatus, quy tắc. Không biết gì về HTTP.
   ├── application/     ← KỊCH BẢN: "giữ chỗ" = khóa vị trí → kiểm tra trùng → tính giá → tạo đơn → lưu.
   ├── infrastructure/  ← KỸ THUẬT: truy vấn database cụ thể (JPA, SQL).
   └── interfaces/      ← CỬA VÀO: controller nhận HTTP, DTO (dữ liệu vào/ra).
   ```
   **Quy tắc vàng:** tầng trên được gọi tầng dưới, **`domain` không được gọi ngược lên** tầng nào khác.

### 6.3. Các khái niệm DDD gặp trong code

| Khái niệm | Trong dự án | Giải thích |
| :--- | :--- | :--- |
| **Aggregate** | `Booking`, `Resource`, `Payment` | Đối tượng nghiệp vụ chính, có id, tự giữ quy tắc của mình |
| **Value Object** | `Money`, `TimeRange`, `ContactInfo` | Giá trị không có id, không đổi được; tự kiểm tra hợp lệ khi tạo (ví dụ `Money` không được âm) |
| **Domain Service** | `PricingPolicy`, `CancellationPolicy` | Quy tắc nghiệp vụ không thuộc riêng aggregate nào |
| **Repository** | `BookingRepository` (interface ở `domain`) + `JpaBookingRepository` (cài đặt ở `infrastructure`) | Nơi lưu/đọc aggregate. Domain chỉ biết interface, không biết SQL |
| **Domain Event** | `BookingConfirmed`, `PaymentSucceeded`... | "Thông báo" một việc đã xảy ra. Vùng khác lắng nghe để phản ứng mà không cần gọi trực tiếp |
| **Application Service** | `BookingService`, `PaymentService` | Điều phối một use case từ đầu đến cuối |

### 6.4. Vòng đời đơn đặt chỗ
Đây là thứ quan trọng nhất cần hiểu. Hình đầy đủ ở [docs/diagrams.md mục 3.1](docs/diagrams.md#31-vòng-đời-đơn-đặt-chỗ-booking).

```
HELD ──thanh toán──▶ CONFIRMED ──quét QR──▶ CHECKED_IN ──quét QR ra──▶ COMPLETED
  │                     │                       │                       ▲
  │ hết 10 phút         │ khách hủy             │ quá giờ               │ trả nốt phụ phí
  ▼                     ▼                       ▼                       │
EXPIRED              CANCELLED               OVERSTAY ──quét ra──▶ CHECKED_OUT
```

## 7. Một request đi qua code như thế nào

Lấy ví dụ **khách quét QR để check-in** — `POST /api/check-ins`. Mở từng file theo thứ tự để đọc:

| # | File | Làm gì |
| :--- | :--- | :--- |
| 1 | `booking/interfaces/BookingController.java` → hàm `checkIn` | Nhận JSON `{qrToken}`, gọi service |
| 2 | `booking/application/BookingService.java` → hàm `checkIn` | Tìm đơn theo QR, lấy giờ hiện tại, gọi aggregate, lưu, phát event |
| 3 | `booking/domain/Booking.java` → hàm `checkIn` | **Kiểm tra quy tắc**: đúng trạng thái? không quá sớm? không quá muộn? Rồi đổi trạng thái và ghi nhận event `BookingCheckedIn` |
| 4 | `booking/infrastructure/JpaBookingRepository.java` | Lưu xuống database |
| 5 | `booking/interfaces/BookingDtos.java` → `BookingResponse.from` | Chuyển đơn thành JSON trả về |

Luồng phức tạp hơn — **thanh toán thành công** — đi qua 2 vùng nhờ event:
```
SandboxPaymentController → PaymentWebhookHandler (kiểm tra chữ ký)
  → PaymentService.handleGatewayResult → Payment.succeed() phát event PaymentSucceeded
    → (sau khi lưu xong) booking/application/PaymentEventsListener nghe event
      → BookingService.onPaymentSucceeded → Booking.confirmPayment() phát event BookingConfirmed
        → notification/application/BookingNotificationListener gửi tin nhắn cho khách
```

**Cách nhanh nhất để hiểu:** mở `BookingFlowIntegrationTest.java`, đặt breakpoint (click lề trái cạnh số dòng), chuột phải → *Debug*, rồi bấm F7/F8 để đi từng bước.

## 8. Làm thêm chức năng mới — ví dụ từng bước

Quy trình chung cho **mọi** chức năng:

1. **Đọc yêu cầu** trong `docs/use-cases.md` (UC nào) và `docs/SRS.md` (FR/BR nào).
2. **Domain trước:** thêm hành vi vào aggregate + viết unit test cho nó.
3. **Application:** thêm hàm vào service để điều phối.
4. **Infrastructure:** thêm truy vấn database nếu cần.
5. **Interfaces:** thêm API (nếu người dùng gọi trực tiếp) hoặc job (nếu tự động).
6. **Chạy `mvn test`**, thử bằng Postman.
7. **Cập nhật tài liệu** (README này, mục 10) và commit.

### Ví dụ: tự động đánh dấu khách không đến (No-show) — UC24, BR-16

> **Yêu cầu (SRS BR-16):** đơn đã thanh toán nhưng sau 30 phút kể từ giờ bắt đầu khách vẫn chưa check-in → chuyển sang `NO_SHOW` và giải phóng chỗ.

**Bước 1 — Thêm cấu hình** vào `src/main/resources/application.yml`, dưới mục `booking:`
```yaml
  no-show-after: 30m
```
và thêm trường tương ứng vào `booking/application/BookingProperties.java` + `booking/domain/BookingRules.java`.

**Bước 2 — Thêm event** vào `booking/domain/event/BookingEvents.java`:
```java
public record BookingNoShow(UUID bookingId, UUID resourceId, LocalDateTime occurredAt) implements DomainEvent {
}
```

**Bước 3 — Thêm hành vi vào aggregate** `booking/domain/Booking.java` (trạng thái `NO_SHOW` đã có sẵn trong `BookingStatus`):
```java
/** UC24, BR-16: khách không đến sau thời gian ân hạn. */
public void markNoShow(BookingRules rules, LocalDateTime now) {
    requireStatus(EnumSet.of(CONFIRMED), "đánh dấu không đến");
    if (!now.isAfter(period.startAt().plus(rules.noShowAfter()))) {
        throw new DomainException("NOT_NO_SHOW_YET", "Chưa hết thời gian chờ khách");
    }
    status = BookingStatus.NO_SHOW;
    registerEvent(new BookingNoShow(id, resourceId, now));
}
```

**Bước 4 — Viết unit test** trong `src/test/java/com/cowork/booking/domain/BookingTest.java` (chép mẫu từ các test có sẵn):
```java
@Test
void unpaidGuestBecomesNoShowAfterGracePeriod() {
    Booking booking = confirmedAt(9, 11);

    assertThatThrownBy(() -> booking.markNoShow(RULES, NOW.withHour(9).withMinute(20)))
            .hasFieldOrPropertyWithValue("code", "NOT_NO_SHOW_YET");

    booking.markNoShow(RULES, NOW.withHour(9).withMinute(31));
    assertThat(booking.status()).isEqualTo(BookingStatus.NO_SHOW);
}
```
Chạy riêng test này: bấm ▶ cạnh tên hàm trong IntelliJ.

**Bước 5 — Thêm truy vấn database:**
- `booking/domain/BookingRepository.java`: thêm `List<UUID> findConfirmedIdsStartedBefore(LocalDateTime threshold);`
- `booking/infrastructure/SpringDataBookingRepository.java`: thêm `@Query` tương tự `findIdsByStatusAndEndBefore` nhưng so sánh `b.period.startAt`.
- `booking/infrastructure/JpaBookingRepository.java`: cài đặt hàm mới.

**Bước 6 — Thêm hàm vào service** `booking/application/BookingService.java` (bắt chước `expireHold` / `findExpiredHoldIds`):
```java
public void markNoShow(UUID bookingId) {
    Booking booking = load(bookingId);
    booking.markNoShow(rules, now());
    saveAndPublish(booking);
}
```

**Bước 7 — Cho job tự chạy** trong `booking/application/BookingJobs.java` (bắt chước `flagOverstays`).

**Bước 8 — Gửi tin nhắn cho khách** (không bắt buộc): thêm hàm `on(BookingNoShow event)` vào `notification/application/BookingNotificationListener.java`.

**Bước 9 —** `mvn test` → tất cả xanh → cập nhật mục 10 → commit.

Ví dụ này không cần sửa database vì trạng thái `NO_SHOW` đã có trong ràng buộc `ck_booking_status`.

### Khi chức năng cần thêm bảng hoặc cột

Ví dụ làm voucher (UC05) cần bảng `voucher`. Thiết kế sẵn ở [docs/database.md mục 4](docs/database.md#4-các-bảng-giai-đoạn-sau-).

1. Tạo file `backend/src/main/resources/db/migration/V2__voucher.sql` (số phiên bản tăng dần, **hai** dấu gạch dưới), viết `CREATE TABLE voucher (...)`.
2. Tạo entity Java khớp tên bảng và tên cột (Spring tự đổi `camelCase` trong Java thành `snake_case` trong DB: `discountValue` ↔ `discount_value`).
3. Chạy ứng dụng. Nếu entity và bảng không khớp, ứng dụng sẽ dừng ngay với lỗi `Schema-validation: missing column ...` — đọc lỗi, sửa cho khớp.
4. **Không sửa** `V1__core_schema.sql` hay bất kỳ file migration nào đã chạy. Muốn đổi bảng cũ thì viết file mới với `ALTER TABLE`.
5. Thêm giá trị mới vào một enum Java (ví dụ trạng thái mới)? Phải viết migration sửa ràng buộc `CHECK` tương ứng, nếu không database sẽ từ chối lưu.

> Không chắc đặt code ở đâu? Tự hỏi: *"Đây là quy tắc nghiệp vụ hay là thao tác kỹ thuật?"* — Quy tắc → `domain`. Gọi nhiều thứ theo thứ tự → `application`. SQL / gọi hệ thống ngoài → `infrastructure`. HTTP → `interfaces`.

## 9. Quy ước code của dự án

- **Không viết setter** trong aggregate. Đặt tên hàm theo hành động nghiệp vụ: `checkIn`, `cancel`, `markMaintenance`.
- **Lỗi nghiệp vụ** ném `DomainException("MA_LOI", "thông báo tiếng Việt")`. Dùng `ConflictException` khi xung đột dữ liệu (→ HTTP 409), `NotFoundException` khi không tìm thấy (→ 404). Không cần tự viết `try/catch` ở controller — `ApiExceptionHandler` lo hết.
- **Tiền** luôn dùng `Money` (VND, số nguyên), không dùng `double`.
- **Thời gian** luôn lấy từ `Clock` được inject (`LocalDateTime.now(clock)`), **không gọi** `LocalDateTime.now()` trực tiếp — để test có thể "tua" đồng hồ.
- **Ghi mã yêu cầu** trong chú thích khi cài đặt một quy tắc: `/** UC08: ... (BR-07) */`.
- **Mỗi chức năng mới phải có test.** Quy tắc nghiệp vụ → unit test ở `domain`. Luồng qua API → integration test (kế thừa `IntegrationTest`).
- Mỗi integration test dùng **ngày và vị trí riêng** (các test dùng chung một database). Đang dùng: 02/11 `MR-02`, 03/11 `HD-01`, 04/11 `HD-03`, 05/11 `HD-02`, 06/11 `FB-02`.
- **Đổi cấu hình** (thời gian giữ chỗ, giá...) trong `application.yml`, không sửa số cứng trong code.

## 10. Việc cần làm tiếp theo

Xếp theo thứ tự nên làm. Đánh dấu `[x]` khi xong.

**Ưu tiên cao — cần trước khi có người dùng thật**
- [x] Chuyển sang PostgreSQL + Flyway, có exclusion constraint chống trùng chỗ.
- [ ] **Integration test chạy trên PostgreSQL thật** (Testcontainers, cần Docker) — hiện test chạy trên H2 nên chưa kiểm tra tự động các ràng buộc riêng của PostgreSQL.
- [ ] **Đăng nhập & phân quyền** (UC01, UC23): Spring Security + JWT; phân quyền Khách / Lễ tân / Quản lý. Hiện API ai gọi cũng được.
- [ ] **Transactional outbox** cho domain event, để không mất xác nhận đơn nếu lỗi giữa chừng.
- [ ] **Tích hợp cổng thanh toán thật** (VNPay / MoMo / PayOS): viết lớp mới cài đặt interface `PaymentGateway`, thay `SandboxPaymentGateway`.

**Chức năng còn thiếu**
- [ ] No-show (UC24, BR-16) — có hướng dẫn chi tiết ở [mục 8](#8-làm-thêm-chức-năng-mới--ví-dụ-từng-bước), **nên làm đầu tiên để làm quen**.
- [ ] Nhắc lịch trước 30 phút, cảnh báo trước 15 phút hết giờ (UC26, UC12).
- [ ] Hoàn tiền thật qua cổng (UC25) — hiện chỉ phát event `BookingRefundRequired`.
- [ ] Job đối soát giao dịch chưa rõ kết quả (UC25, trạng thái `UNCONFIRMED`).
- [ ] Voucher và gói Membership (UC05, UC21).
- [ ] Add-on: máy chiếu, màn hình, đồ ăn (FR-BOOK-02).
- [ ] Đặt chỗ walk-in & thu tiền tại quầy (UC13, UC16 đầy đủ).
- [ ] Đổi chỗ khi có sự cố (UC15) và ticket sự cố (UC11, UC17).
- [ ] Đền bù (UC18).
- [ ] Gọi đồ ăn uống (UC10), cốc nước miễn phí (BR-10).
- [ ] Quản trị sơ đồ chỗ ngồi, bảng giá qua API (UC19, UC20).
- [ ] Báo cáo tỷ lệ lấp đầy, doanh thu (UC22).
- [ ] Giao diện web/app cho khách, quầy, quản lý.

**Chờ khách hàng trả lời:** 12 câu hỏi ở [docs/SRS.md mục 9](docs/SRS.md#9-giả-định--câu-hỏi-mở-cần-khách-hàng-xác-nhận) (block 30 phút? chính sách hủy?...). Code hiện đang dùng các giá trị giả định ghi ở đó.

## 11. Lỗi thường gặp

| Triệu chứng | Nguyên nhân & cách sửa |
| :--- | :--- |
| `'java' is not recognized` / `'mvn' is not recognized` | Chưa thêm vào PATH. Windows: tìm *"Edit the system environment variables"* → *Environment Variables* → sửa `Path`, thêm thư mục `bin` của JDK và Maven. Đóng mở lại terminal. |
| `invalid target release: 21` | Maven đang dùng Java cũ. Đặt biến môi trường `JAVA_HOME` trỏ tới thư mục JDK 21. |
| `Port 8080 was already in use` | Ứng dụng đang chạy ở cửa sổ khác — tắt nó đi. Hoặc chạy cổng khác: `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` |
| IntelliJ gạch đỏ khắp nơi dù `mvn test` vẫn chạy | Chuột phải `pom.xml` → *Maven* → *Reload project*. Kiểm tra *File → Project Structure → SDK* là 21. |
| Gọi API báo `START_IN_PAST` | Khung giờ đã qua. Dùng ngày trong tương lai. |
| `CHECK_IN_TOO_EARLY` | Đúng thiết kế: chỉ check-in từ 15 phút trước giờ bắt đầu. |
| `NO_PRICE_RULE` | Không có giá cho loại chỗ / khung giờ đó trong bảng `PRICE_RULE`. |
| `Connection to localhost:5432 refused` | PostgreSQL chưa chạy. Windows: mở *Services* (gõ `services.msc`), tìm `postgresql-x64-…` → *Start*. |
| `password authentication failed for user "cowork"` | Chưa chạy `db/setup-local.sql` (mục 3), hoặc đã đổi mật khẩu. Kiểm tra `spring.datasource` trong `application.yml`. |
| `database "cowork" does not exist` | Chưa chạy `db/setup-local.sql` (mục 3). |
| `Schema-validation: missing column` / `missing table` | Entity Java và bảng không khớp: thiếu migration, hoặc sai tên cột. Xem mục 8 phần "Khi chức năng cần thêm bảng hoặc cột". |
| `Validate failed: Migration checksum mismatch` | Đã sửa một file migration đã chạy. Hoàn tác thay đổi đó và viết file migration mới. Trên máy dev có thể xóa sạch schema (mục 4.3). |
| `violates check constraint "ck_..._status"` | Đã thêm giá trị enum mới trong Java mà chưa sửa `CHECK` trong database. |
| Dữ liệu biến mất sau khi chạy lại | Bạn đang chạy profile `h2` (dữ liệu trong RAM). Chạy không kèm `-Dspring-boot.run.profiles=h2` để dùng PostgreSQL. |
| Chữ tiếng Việt trong log bị vỡ (`??`) | Do bảng mã console Windows, không phải lỗi code. Chạy `chcp 65001` trước khi `mvn`. |
| Test vừa viết chạy riêng thì xanh, chạy chung thì đỏ | Test đang dùng trùng ngày / vị trí với test khác (xem mục 9). |

## 12. Lưu công việc với Git

```bash
git status
```
Xem đã sửa những file nào.

```bash
git checkout -b feature/no-show
```
Tạo nhánh riêng cho mỗi chức năng (đừng sửa trực tiếp trên `main`).

```bash
git add .
```

```bash
git commit -m "Them tu dong danh dau no-show (UC24)"
```

Không commit thư mục `backend/target/` (đã có trong `.gitignore`).

## 13. Từ điển thuật ngữ

| Thuật ngữ | Nghĩa |
| :--- | :--- |
| API / endpoint | Địa chỉ HTTP mà giao diện gọi tới để lấy hoặc gửi dữ liệu |
| JSON | Định dạng dữ liệu dạng `{ "key": "value" }` dùng để trao đổi qua API |
| DTO | Lớp chỉ chứa dữ liệu vào/ra của API (ví dụ `HoldBookingRequest`) |
| Entity | Lớp được lưu thành bảng trong database |
| JPA / Hibernate | Thư viện tự chuyển object Java ↔ bảng database, đỡ phải viết SQL |
| Transaction | Một nhóm thao tác database: thành công hết hoặc hủy hết |
| Flyway / migration | Công cụ tự chạy các file SQL `V1__...`, `V2__...` theo thứ tự để tạo/sửa bảng; nhớ file nào đã chạy rồi |
| Profile | Bộ cấu hình thay thế. `h2` = chạy không cần PostgreSQL |
| Exclusion constraint | Ràng buộc PostgreSQL cấm hai dòng "xung đột" (ở đây: cùng chỗ, giờ chồng nhau) cùng tồn tại |
| Soft lock / Hold | Giữ chỗ tạm 10 phút trong lúc khách thanh toán |
| Double-booking | Một chỗ bị hai người đặt trùng giờ — lỗi nghiêm trọng nhất cần tránh |
| Pessimistic lock | Khóa dòng trong database để hai người không sửa cùng lúc — dùng để chống double-booking |
| Webhook | Cổng thanh toán tự gọi ngược về hệ thống để báo kết quả giao dịch |
| HMAC / chữ ký | Mã bí mật đi kèm webhook để chứng minh nó thật sự đến từ cổng thanh toán |
| Idempotent | Gọi một lần hay nhiều lần kết quả vẫn như nhau (webhook có thể bị gửi lặp) |
| Overstay | Khách ngồi quá giờ đã đặt |
| Block giờ | Đơn vị tính tiền nhỏ nhất (30 phút). 40 phút được làm tròn lên thành 2 block |
| Sandbox | Môi trường giả lập để thử, không có tiền thật |
| Bounded context / Aggregate / Value Object / Domain Event | Xem [mục 6.3](#63-các-khái-niệm-ddd-gặp-trong-code) |
