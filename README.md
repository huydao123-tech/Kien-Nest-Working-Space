# Hệ thống đặt chỗ Co-Working Space theo giờ

Hướng dẫn này viết cho người **mới bắt đầu**: chưa quen Spring Boot, chỉ cần biết lập trình Java cơ bản. Đọc lần lượt từ trên xuống là có thể chạy được dự án và tự làm thêm chức năng mới.

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
    ├── db/schema.sql            ← câu lệnh tạo bảng để THAM KHẢO khi bạn tự tạo bảng trong pgAdmin
    └── src/
        ├── main/java/com/cowork/   ← code chính
        ├── main/resources/application.yml      ← cấu hình chính (kết nối PostgreSQL, thời gian giữ chỗ, giá...)
        ├── main/resources/application-h2.yml   ← cấu hình chạy không cần PostgreSQL (dùng cho test)
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
| **PostgreSQL 16+** và **pgAdmin 4** | Database lưu dữ liệu, và công cụ quản lý nó | https://www.postgresql.org/download/windows (bộ cài có kèm pgAdmin 4). **Ghi nhớ mật khẩu tài khoản `postgres`** bạn đặt lúc cài |
| **IntelliJ IDEA Community** (khuyên dùng) | Viết code | https://www.jetbrains.com/idea/download |

Kiểm tra đã cài đúng chưa — mở PowerShell hoặc Terminal, gõ từng lệnh:

```bash
java -version
```

```bash
mvn -v
```

Phải thấy `21.x` ở cả hai. Nếu báo *"không nhận ra lệnh"* → xem [mục 11](#11-lỗi-thường-gặp).

Database và các bảng **bạn tự tạo bằng pgAdmin 4** — làm theo [mục 4.2](#42-tạo-database-và-bảng-trong-pgadmin-4). Ứng dụng không tự tạo bảng.

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

### 4.2. Tạo database và bảng trong pgAdmin 4

Ứng dụng **không tự tạo bảng**. Bạn tạo database trong pgAdmin 4, rồi chạy file [backend/db/schema.sql](backend/db/schema.sql) để có đủ bảng. Database có **35 bảng** (danh sách ở [docs/database.md mục 2](docs/database.md#2-danh-sách-bảng)):
- **10 bảng Phần 1** — code đang dùng.
- **25 bảng Phần 2** — cho các chức năng làm sau.

**Bước A — Đăng nhập server.** Mở pgAdmin 4 → cây bên trái mở *Servers → PostgreSQL 18* → nhập mật khẩu tài khoản `postgres`.

**Bước B — Tạo database.** Chuột phải *Databases* → *Create → Database…* → Database: `working-space` → *Save*. (Đặt tên khác cũng được, nhớ sửa ở mục 4.3.)

**Bước C — Chạy file SQL.**
1. Bấm chọn *Databases → working-space* ở cây bên trái → menu *Tools → Query Tool*.
2. Kiểm tra tiêu đề tab Query Tool ghi `working-space/postgres@PostgreSQL 18`. Nếu ghi `postgres/...` là đang ở nhầm database.
3. Mở `backend/db/schema.sql` bằng Notepad / IntelliJ → *Ctrl + A* → *Ctrl + C* → dán vào Query Tool → bấm *Execute* (F5).
4. Tab *Messages* báo `Query returned successfully` là xong.

File này làm 3 việc:
- Tạo 35 bảng và thêm sẵn 5 dòng vào bảng danh mục `resource_type`.
- Tạo user **`cowork`** (mật khẩu `cowork`) — tài khoản riêng mà ứng dụng dùng để kết nối, không dùng tài khoản quản trị `postgres`.
- Cấp cho `cowork` quyền đọc/ghi dữ liệu trên mọi bảng, nhưng không cho xóa bảng hay sửa cấu trúc bảng.

Cả file chạy như một khối: có lỗi thì không bảng nào được tạo, sửa xong chạy lại. Chạy lại nhiều lần cũng không sao — bảng đã có thì bỏ qua.

**Bước D — Kiểm tra.** Chuột phải *working-space → Schemas → public → Tables* → *Refresh*: phải thấy 35 bảng. Chuột phải *Login/Group Roles* → *Refresh*: phải thấy `cowork`.

> **Muốn tự tạo từng bảng bằng giao diện?** Chuột phải *Tables* → *Create → Table…*, nhập theo đúng tên cột / kiểu trong [docs/database.md mục 4](docs/database.md#4-chi-tiết-phần-1-), tạo theo đúng thứ tự đánh số trong `schema.sql` (bảng sau có khóa ngoại trỏ tới bảng trước). Riêng ràng buộc `EXCLUDE` nên tạo bằng SQL vì giao diện khó nhập. Xong thì vẫn chạy **Phần 3** ở cuối `schema.sql` để tạo user `cowork` và cấp quyền.

**Những điều bắt buộc phải khớp với code Java**, nếu không ứng dụng sẽ báo lỗi khi khởi động:
- Tên bảng và tên cột viết thường, nối bằng gạch dưới: `start_at`, `paid_amount`... (code Java viết `startAt`, `paidAmount` — Spring tự đổi).
- Kiểu dữ liệu: id dùng `uuid`; tiền dùng `bigint`; thời gian dùng `timestamp` (không kèm múi giờ); trạng thái/loại dùng `varchar`; `version` dùng `bigint`.
- Đủ tất cả các cột như trong docs/database.md, kể cả `version`.

### 4.3. Kết nối project với database

Mở `backend/src/main/resources/application.yml`, phần kết nối:

```yaml
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/working-space}
    username: ${DB_USERNAME:cowork}
    password: ${DB_PASSWORD:cowork}
```

Đọc như sau: `${DB_URL:giá-trị-mặc-định}` nghĩa là dùng biến môi trường `DB_URL` nếu có, không thì dùng giá trị sau dấu `:`.
- Database tên `working-space` và đã chạy `schema.sql` (tạo user `cowork` / mật khẩu `cowork`) → **không cần sửa gì**.
- Nếu đặt tên khác → sửa phần sau dấu `:`. Ví dụ database tên `cowork_dev`:
  ```yaml
    url: ${DB_URL:jdbc:postgresql://localhost:5432/cowork_dev}
  ```
- Cấu trúc URL: `jdbc:postgresql://<máy>:<cổng>/<tên database>`. Cổng mặc định của PostgreSQL là `5432` (xem trong pgAdmin: chuột phải *PostgreSQL 18 → Properties → Connection*).

Trong code có dòng `ddl-auto: validate`: khi khởi động, ứng dụng **chỉ kiểm tra** bảng bạn tạo có khớp với code không — không tạo, không sửa bảng.

### 4.4. Chạy ứng dụng

```bash
mvn spring-boot:run
```

Đợi đến khi thấy dòng `Started CoworkBookingApplication`. Ứng dụng đang chạy ở **http://localhost:8080**. Muốn dừng: bấm `Ctrl + C`.

- Thấy `Started ...` → **kết nối thành công và các bảng đã khớp**.
- Ứng dụng dừng với lỗi → tra bảng ở [mục 11](#11-lỗi-thường-gặp). Lỗi hay gặp nhất là `Schema-validation: missing column [...] in table [...]`: đọc tên cột và tên bảng trong ngoặc vuông, thêm cột đó trong pgAdmin.

Lần chạy đầu, khi bảng `space_resource` còn trống, ứng dụng nạp **dữ liệu mẫu** (14 chỗ ngồi và bảng giá) để có cái mà thử API. Không muốn vậy thì sửa `app.seed-demo-data: false` trong `application.yml`, rồi tự thêm dữ liệu bằng pgAdmin.

Trong IntelliJ: mở file `CoworkBookingApplication.java` → bấm nút ▶ xanh cạnh hàm `main`.

**Chưa tạo xong database?** Chạy tạm bằng H2 (bảng tự tạo trong RAM, mất khi tắt ứng dụng):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

### 4.5. Xem dữ liệu trong pgAdmin

Mở *working-space → Schemas → public → Tables*, chuột phải một bảng → *View/Edit Data → All Rows*. Hoặc dùng *Query Tool* để gõ SQL, ví dụ `SELECT * FROM booking ORDER BY created_at DESC;`.

| Bảng | Chứa gì |
| :--- | :--- |
| `resource_type` | 5 loại chỗ (có sẵn sau khi chạy `schema.sql`) |
| `space_resource` | Chỗ ngồi / phòng (14 chỗ mẫu: `HD-01`…`HD-06`, `FD-01`…`FD-03`, `FB-01`, `FB-02`, `MR-01`, `MR-02`, `EV-01`) |
| `price_rule` | Bảng giá (cao điểm 09:00–18:00 ngày thường, còn lại thấp điểm) |
| `customer` | Khách hàng — tự tạo ở lần đặt chỗ đầu tiên của mỗi số điện thoại |
| `booking` | Đơn đặt chỗ |
| `booking_charge` | Các khoản tiền của từng đơn: thuê, gia hạn, phụ phí quá giờ |
| `booking_status_history` | Đơn đã đi qua những trạng thái nào, lúc nào |
| `payment` | Giao dịch thanh toán |
| `branch`, `zone` | Chưa có dữ liệu (code chưa dùng) |

Một câu SQL hay dùng — xem một đơn kèm khách và các khoản tiền:

```sql
SELECT b.code, c.full_name, c.phone, b.status, ch.charge_type, ch.amount, ch.start_at, ch.end_at
FROM booking b
JOIN customer c ON c.id = b.customer_id
JOIN booking_charge ch ON ch.booking_id = b.id
ORDER BY b.created_at DESC, ch.line_no;
```

Xóa hết dữ liệu nhưng giữ bảng (lần chạy sau ứng dụng nạp lại dữ liệu mẫu; `resource_type` giữ nguyên):

```sql
TRUNCATE payment, booking_status_history, booking_charge, booking, customer, price_rule, space_resource;
```

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

### 6.2. Kiến trúc phân tầng (Layered Architecture)
Code chia thành **3 tầng**, mỗi tầng là một package gốc trong `backend/src/main/java/com/cowork/`:

```
com.cowork
├── presentation/          ① TẦNG TRÌNH BÀY — giao tiếp với người dùng (HTTP)
│   ├── controller/           các API + ApiExceptionHandler (đổi lỗi thành mã HTTP)
│   └── dto/                  dữ liệu vào/ra của API (BookingDtos)
├── business/              ② TẦNG NGHIỆP VỤ — xử lý dữ liệu và nghiệp vụ
│   ├── service/              BookingService, PaymentService, PricingService, CustomerService...
│   │                         NotificationService + dữ liệu vào/ra của service (HoldBookingCommand, PriceQuote)
│   ├── job/                  việc chạy định kỳ (BookingJobs)
│   └── exception/            lỗi nghiệp vụ (DomainException, ConflictException, NotFoundException)
├── dataaccess/            ③ TẦNG TRUY CẬP DỮ LIỆU — làm việc với database và nguồn dữ liệu ngoài
│   ├── repository/           đọc / ghi database (Spring Data JPA)
│   ├── entity/               lớp ánh xạ với bảng: Booking, Payment, Customer, Resource...
│   └── integration/          hệ thống ngoài: cổng thanh toán, gửi SMS, chữ ký webhook
└── config/                (dùng chung) cấu hình, đồng hồ, dữ liệu mẫu
```

**Chiều gọi — chỉ đi từ trên xuống:**

```
presentation  ──▶  business  ──▶  dataaccess  ──▶  database / hệ thống ngoài
```

- `presentation` **không** gọi thẳng `dataaccess`: controller không dùng repository, mọi việc đi qua service.
- `business` **không** biết gì về HTTP: không dùng `HttpServletRequest`, không trả `ResponseEntity`, không import gì từ `presentation`.
- `dataaccess` **không** import gì từ `business` hay `presentation`. Entity chỉ chứa dữ liệu (field + getter/setter), không kiểm tra quy tắc, không ném lỗi nghiệp vụ.

**Quy tắc nghiệp vụ nằm ở đâu?** Toàn bộ ở tầng `business`:

| Nghiệp vụ | Lớp chứa quy tắc |
| :--- | :--- |
| Vòng đời đơn: giữ chỗ, thanh toán, hủy, check-in, gia hạn, quá giờ, check-out | `business/service/BookingLifecycle` |
| Hoàn tiền khi hủy | `business/service/CancellationPolicy` |
| Tính giá theo block giờ, phụ phí quá giờ | `business/service/PricingPolicy` |
| Thanh toán: tạo giao dịch, nhận kết quả từ cổng, chống xử lý trùng | `business/service/PaymentService` |
| Khách hàng: kiểm tra SĐT / email, tìm hoặc tạo khách | `business/service/CustomerService` |
| Chỗ ngồi: có nhận đặt không (bảo trì, sức chứa), bảo trì / mở lại | `business/service/ResourceService` |

`BookingLifecycle` **không đọc/ghi database**: nhận một `Booking`, kiểm tra quy tắc và đổi dữ liệu. `BookingService` lo phần đọc, khóa, lưu và gọi `NotificationService` khi cần báo cho khách. Tách như vậy để kiểm thử quy tắc mà không cần Spring hay database (`BookingLifecycleTest`).

**Các service gọi thẳng nhau**, đọc code là thấy ai gọi ai:

```
PaymentService ──gọi──▶ BookingService.onPaymentSucceeded / onPaymentFailed
BookingService ──gọi──▶ PricingService, CustomerService, ResourceService, NotificationService
NotificationService ──gọi──▶ CustomerService (lấy SĐT), NotificationSender (gửi tin)
```

Lưu kết quả thanh toán và xác nhận đơn nằm **chung một transaction**: một bên lỗi thì cả hai cùng hủy, cổng thanh toán sẽ gửi lại webhook.

### 6.3. Các loại lớp gặp trong code

| Loại lớp | Ví dụ | Nằm ở | Giải thích |
| :--- | :--- | :--- | :--- |
| **Controller** | `BookingController`, `PaymentController` | `presentation/controller/` | Nhận request, gọi service, trả JSON |
| **DTO** | `BookingDtos` | `presentation/dto/` | Dữ liệu vào/ra API |
| **Service** | `BookingService`, `PaymentService`, `CustomerService`, `ResourceService` | `business/service/` | Điều phối một chức năng từ đầu đến cuối, mở transaction, gọi service khác |
| **Lớp quy tắc** | `BookingLifecycle`, `PricingPolicy`, `CancellationPolicy` | `business/service/` | Quy tắc nghiệp vụ thuần, không đụng database, test được không cần Spring |
| **Repository** | `BookingRepository` (interface) + `JpaBookingRepository` (cài đặt) | `dataaccess/repository/` | Service chỉ dùng interface; phần cài đặt dùng JPA / SQL |
| **Entity** | `Booking`, `Resource`, `Payment`, `Customer`, `PriceRule` | `dataaccess/entity/` | Ánh xạ với bảng; chỉ có field + getter/setter |
| **Kiểu giá trị** | `Money`, `TimeRange`, `ContactInfo`, `BookingCharge`, `StatusChange` | `dataaccess/entity/` | Không có id, không đổi được sau khi tạo |

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
| 1 | `presentation/controller/BookingController.java` → hàm `checkIn` | Nhận JSON `{qrToken}`, gọi service |
| 2 | `business/service/BookingService.java` → hàm `checkIn` | Tìm đơn theo QR, lấy giờ hiện tại, gọi `BookingLifecycle`, lưu |
| 3 | `business/service/BookingLifecycle.java` → hàm `checkIn` | **Kiểm tra quy tắc**: đúng trạng thái? không quá sớm? không quá muộn? Rồi đổi trạng thái và ghi lịch sử |
| 4 | `dataaccess/repository/JpaBookingRepository.java` | Lưu xuống database |
| 5 | `presentation/dto/BookingDtos.java` → `BookingResponse.from` | Chuyển đơn thành JSON trả về |

Luồng phức tạp hơn — **thanh toán thành công** — đi qua nhiều service:
```
presentation/controller/SandboxPaymentController → presentation/controller/PaymentWebhookHandler (kiểm tra chữ ký)
  → business/service/PaymentService.handleGatewayResult: kiểm tra trạng thái + số tiền → lưu giao dịch
    → business/service/BookingService.onPaymentSucceeded → BookingLifecycle.confirmPayment → lưu đơn
      → business/service/NotificationService.bookingConfirmed → gửi tin nhắn cho khách
```

**Cách nhanh nhất để hiểu:** mở `BookingFlowIntegrationTest.java`, đặt breakpoint (click lề trái cạnh số dòng), chuột phải → *Debug*, rồi bấm F7/F8 để đi từng bước.

## 8. Làm thêm chức năng mới — ví dụ từng bước

Quy trình chung cho **mọi** chức năng:

1. **Đọc yêu cầu** trong `docs/use-cases.md` (UC nào) và `docs/SRS.md` (FR/BR nào).
2. **`dataaccess/entity/`:** thêm field nếu cần lưu dữ liệu mới (chỉ field + getter/setter).
3. **`dataaccess/repository/`:** thêm truy vấn database nếu cần.
4. **`business/service/`:** viết quy tắc nghiệp vụ + unit test cho nó, rồi thêm hàm vào service để điều phối.
5. **`presentation/controller/` + `presentation/dto/`** nếu người dùng gọi trực tiếp, hoặc **`business/job/`** nếu chạy tự động.
6. **Chạy `mvn test`**, thử bằng Postman.
7. **Cập nhật tài liệu** (README này, mục 10) và commit.

### Ví dụ: tự động đánh dấu khách không đến (No-show) — UC24, BR-16

> **Yêu cầu (SRS BR-16):** đơn đã thanh toán nhưng sau 30 phút kể từ giờ bắt đầu khách vẫn chưa check-in → chuyển sang `NO_SHOW` và giải phóng chỗ.

**Bước 1 — Thêm cấu hình** vào `src/main/resources/application.yml`, dưới mục `booking:`
```yaml
  no-show-after: 30m
```
và thêm trường tương ứng vào `config/BookingProperties.java` + `business/service/BookingRules.java`.

**Bước 2 — Thêm quy tắc** vào `business/service/BookingLifecycle.java` (trạng thái `NO_SHOW` đã có sẵn trong `BookingStatus`; entity `Booking` không cần sửa):
```java
/** UC24, BR-16: khách không đến sau thời gian ân hạn. */
public void markNoShow(Booking booking, LocalDateTime now) {
    requireStatus(booking, EnumSet.of(CONFIRMED), "đánh dấu không đến");
    if (!now.isAfter(booking.getPeriod().startAt().plus(rules.noShowAfter()))) {
        throw new DomainException("NOT_NO_SHOW_YET", "Chưa hết thời gian chờ khách");
    }
    transition(booking, BookingStatus.NO_SHOW, now);
}
```

**Bước 3 — Viết unit test** trong `src/test/java/com/cowork/business/service/BookingLifecycleTest.java` (chép mẫu từ các test có sẵn):
```java
@Test
void unpaidGuestBecomesNoShowAfterGracePeriod() {
    Booking booking = confirmedAt(9, 11);

    assertThatThrownBy(() -> lifecycle.markNoShow(booking, NOW.withHour(9).withMinute(20)))
            .hasFieldOrPropertyWithValue("code", "NOT_NO_SHOW_YET");

    lifecycle.markNoShow(booking, NOW.withHour(9).withMinute(31));
    assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
}
```
Chạy riêng test này: bấm ▶ cạnh tên hàm trong IntelliJ.

**Bước 4 — Thêm truy vấn database:**
- `dataaccess/repository/BookingRepository.java`: thêm `List<UUID> findConfirmedIdsStartedBefore(LocalDateTime threshold);`
- `dataaccess/repository/SpringDataBookingRepository.java`: thêm `@Query` tương tự `findIdsByStatusAndEndBefore` nhưng so sánh `b.period.startAt`.
- `dataaccess/repository/JpaBookingRepository.java`: cài đặt hàm mới.

**Bước 5 — Thêm hàm vào service** `business/service/BookingService.java` (bắt chước `expireHold` / `findExpiredHoldIds`):
```java
public void markNoShow(UUID bookingId) {
    Booking booking = load(bookingId);
    lifecycle.markNoShow(booking, now());
    bookings.save(booking);
    notifications.noShow(booking);   // bước 7, không bắt buộc
}
```

**Bước 6 — Cho job tự chạy** trong `business/job/BookingJobs.java` (bắt chước `flagOverstays`).

**Bước 7 — Gửi tin nhắn cho khách** (không bắt buộc): thêm hàm `noShow(Booking booking)` vào `business/service/NotificationService.java` (bắt chước `holdExpired`).

**Bước 8 —** `mvn test` → tất cả xanh → cập nhật mục 10 → commit.

Ví dụ này không cần sửa database vì trạng thái `NO_SHOW` đã có trong ràng buộc `ck_booking_status`.

### Khi chức năng cần thêm bảng hoặc cột

Ví dụ làm voucher (UC05) cần các bảng `voucher`, `voucher_resource_type`, `voucher_redemption`. Ba bảng này đã thiết kế sẵn ở Phần 2 của `db/schema.sql` ([docs/database.md mục 5](docs/database.md#5-phần-2--tóm-tắt-)).

1. Các bảng này đã có sẵn nếu bạn chạy cả `schema.sql`. Nếu cần thêm bảng mới hoặc thêm cột vào bảng cũ (`ALTER TABLE ... ADD COLUMN ...`), chạy trong Query Tool bằng tài khoản `postgres`; user `cowork` tự được cấp quyền trên bảng mới.
2. Chép câu SQL vừa chạy vào cuối `backend/db/schema.sql`, kèm một dòng chú thích ngày và lý do. Người khác (hoặc chính bạn trên máy khác) chạy lại file này là có database giống hệt.
3. Tạo entity Java khớp tên bảng và tên cột (Spring tự đổi `camelCase` trong Java thành `snake_case` trong DB: `discountValue` ↔ `discount_value`).
4. Chạy ứng dụng. Nếu entity và bảng không khớp, ứng dụng sẽ dừng ngay với lỗi `Schema-validation: missing column ...` — đọc lỗi, sửa cho khớp.
5. Thêm giá trị mới vào một enum Java (ví dụ trạng thái mới)? Phải sửa ràng buộc `CHECK` tương ứng trong pgAdmin, nếu không database sẽ từ chối lưu:
   ```sql
   ALTER TABLE booking DROP CONSTRAINT ck_booking_status;
   ALTER TABLE booking ADD CONSTRAINT ck_booking_status CHECK (status IN ('HELD', ..., 'TRANG_THAI_MOI'));
   ```

> Không chắc đặt code ở đâu? Tự hỏi: *"Đây là quy tắc nghiệp vụ hay là thao tác kỹ thuật?"* — HTTP, JSON → `presentation`. Xử lý nghiệp vụ, gọi nhiều thứ theo thứ tự, mở transaction → `business`. SQL, gọi hệ thống ngoài → `dataaccess`.

## 9. Quy ước code của dự án

- **Entity chỉ chứa dữ liệu** (field + getter/setter). Quy tắc nghiệp vụ viết ở `business/service`; đổi trạng thái đơn luôn đi qua `BookingLifecycle` (hàm `transition` ghi lịch sử), không gọi thẳng `booking.setStatus(...)` ở chỗ khác.
- **Service gọi thẳng service**, không dùng event. Gửi thông báo (`NotificationService`) luôn đặt **sau** lệnh lưu.
- **Lỗi nghiệp vụ** ném `DomainException("MA_LOI", "thông báo tiếng Việt")`. Dùng `ConflictException` khi xung đột dữ liệu (→ HTTP 409), `NotFoundException` khi không tìm thấy (→ 404). Không cần tự viết `try/catch` ở controller — `ApiExceptionHandler` lo hết.
- **Tiền** luôn dùng `Money` (VND, số nguyên), không dùng `double`.
- **Thời gian** luôn lấy từ `Clock` được inject (`LocalDateTime.now(clock)`), **không gọi** `LocalDateTime.now()` trực tiếp — để test có thể "tua" đồng hồ.
- **Ghi mã yêu cầu** trong chú thích khi cài đặt một quy tắc: `/** UC08: ... (BR-07) */`.
- **Mỗi chức năng mới phải có test.** Quy tắc nghiệp vụ → unit test trong `src/test/java/com/cowork/business/service/`. Luồng qua API → integration test (kế thừa `IntegrationTest`).
- Mỗi integration test dùng **ngày và vị trí riêng** (các test dùng chung một database). Đang dùng: 02/11 `MR-02`, 03/11 `HD-01`, 04/11 `HD-03`, 05/11 `HD-02`, 06/11 `FB-02`.
- **Đổi cấu hình** (thời gian giữ chỗ, giá...) trong `application.yml`, không sửa số cứng trong code.

## 10. Việc cần làm tiếp theo

Xếp theo thứ tự nên làm. Đánh dấu `[x]` khi xong.

**Ưu tiên cao — cần trước khi có người dùng thật**
- [x] Chuyển sang PostgreSQL, có exclusion constraint chống trùng chỗ (bảng tạo tay trong pgAdmin).
- [ ] Cân nhắc dùng công cụ migration (Flyway/Liquibase) khi có nhiều người cùng làm hoặc có server production, để mọi máy luôn có cùng cấu trúc bảng.
- [ ] **Integration test chạy trên PostgreSQL thật** (Testcontainers, cần Docker) — hiện test chạy trên H2 nên chưa kiểm tra tự động các ràng buộc riêng của PostgreSQL.
- [ ] **Đăng nhập & phân quyền** (UC01, UC23): Spring Security + JWT; phân quyền Khách / Lễ tân / Quản lý. Hiện API ai gọi cũng được.
- [ ] **Gửi thông báo sau khi commit** (hoặc qua bảng `notification_job`): hiện tin nhắn được gửi trong transaction, trước khi commit; cần sửa trước khi nối SMS / Zalo thật.
- [ ] **Tích hợp cổng thanh toán thật** (VNPay / MoMo / PayOS): viết lớp mới cài đặt interface `PaymentGateway`, thay `SandboxPaymentGateway`.

**Chức năng còn thiếu**
- [ ] No-show (UC24, BR-16) — có hướng dẫn chi tiết ở [mục 8](#8-làm-thêm-chức-năng-mới--ví-dụ-từng-bước), **nên làm đầu tiên để làm quen**.
- [ ] Nhắc lịch trước 30 phút, cảnh báo trước 15 phút hết giờ (UC26, UC12).
- [ ] Hoàn tiền thật qua cổng (UC25) — hiện đơn chỉ chuyển sang `REFUND_PENDING` và nhắn tin cho khách.
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
| `password authentication failed for user "cowork"` | Mật khẩu trong `application.yml` khác mật khẩu của user `cowork` (mặc định `cowork`, tạo trong Phần 3 của `schema.sql`). |
| `database "working-space" does not exist` | Chưa tạo database (bước B mục 4.2), hoặc tên database khác với `url` trong `application.yml`. |
| `Schema-validation: missing table [booking]` | Chưa tạo bảng đó, hoặc tạo sai database / sai tên. |
| `Schema-validation: missing column [paid_amount] in table [booking]` | Thiếu cột hoặc sai tên cột. Thêm: `ALTER TABLE booking ADD COLUMN paid_amount bigint NOT NULL DEFAULT 0;` |
| `Schema-validation: wrong column type encountered in column [...]` | Sai kiểu dữ liệu, ví dụ dùng `integer` cho cột tiền (phải là `bigint`), hoặc `varchar` cho id (phải là `uuid`). |
| `permission denied for table ...` | User `cowork` chưa được cấp quyền trên bảng đó. Chạy lại **Phần 3** ở cuối `schema.sql` (hoặc cả file). |
| `role "cowork" does not exist` | Chưa chạy `schema.sql` (Phần 3 tạo user này). |
| `type "tsrange"...` / `data type uuid has no default operator class for access method "gist"` | Chưa chạy `CREATE EXTENSION IF NOT EXISTS btree_gist;` trước khi tạo bảng. |
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
| pgAdmin 4 | Phần mềm giao diện để quản lý PostgreSQL: tạo database, bảng, xem dữ liệu, chạy SQL |
| Role / user | Tài khoản đăng nhập database. Ứng dụng dùng user `cowork` |
| GRANT / quyền | Cho phép một user đọc, ghi trên bảng. User `cowork` chỉ được đọc ghi dữ liệu, không được xóa hay sửa cấu trúc bảng |
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
| Layered architecture | Chia code thành 3 tầng: presentation → business → dataaccess. Xem [mục 6.2](#62-kiến-trúc-phân-tầng-layered-architecture) |
| Entity / kiểu giá trị | Xem [mục 6.3](#63-các-loại-lớp-gặp-trong-code) |
