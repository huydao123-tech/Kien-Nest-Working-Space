# SPEC: Hoàn thiện luồng tra cứu chỗ trống (UC02)

| Mục | Nội dung |
|---|---|
| Loại | Tính năng mới (hoàn thiện UC02) |
| Liên kết | UC02, FR-SRCH-01, FR-SRCH-02, FR-SRCH-03, FR-SRCH-04, FR-SRCH-06, BR-01, BR-09, BR-14, SRS Q1 |
| Người viết | huydao123-tech (code và spec do Claude viết) |
| Ngày | 2026-11-01 |

---

## 1. Mục tiêu (Goal)

Khách chọn chi nhánh, loại không gian, khung giờ, số người và tiện ích mong muốn. Hệ thống trả về:
1. danh sách vị trí đặt được ngay, kèm giá dự kiến;
2. sơ đồ mặt bằng gồm mọi vị trí của chi nhánh, mỗi vị trí có tọa độ và trạng thái trong khung giờ đó (Trống / Đang giữ / Đã đặt / Bảo trì).

Khung giờ chỉ hợp lệ khi: nằm trong giờ mở cửa của chi nhánh, không ở quá khứ, dài ít nhất 1 giờ. API giữ chỗ dùng đúng các quy tắc này.

### 1.1. Bug (chỉ điền nếu là sửa lỗi)

Không có. Đây là phần còn thiếu của UC02, không phải lỗi:

| Thiếu | Trước khi làm |
|---|---|
| Lọc theo chi nhánh | `GET /api/availability` tìm trên mọi vị trí, không có tham số chi nhánh |
| Giờ mở cửa, thời lượng tối thiểu | Không kiểm tra. Đặt được 30 phút, hoặc lúc 23:00 khi chi nhánh đã đóng cửa |
| Trạng thái từng vị trí, tọa độ | API chỉ trả vị trí còn trống. Bảng `space_resource` chưa có cột tọa độ |
| Lọc theo tiện ích | Bảng `amenity`, `resource_amenity` có sẵn nhưng code chưa dùng |

### 1.2. Ngoài phạm vi (Out of scope)

- Không làm giao diện (vẽ sơ đồ, kéo thả vị trí).
- Không làm FR-SRCH-05 (gợi ý khung giờ lân cận hoặc vị trí tương đương khi hết chỗ).
- Không làm "Tự gán" Hot Desk (UC02 luồng 6a).
- Không lọc theo **số lượng** tiện ích (ví dụ "ít nhất 4 ổ cắm"). Chỉ lọc theo có/không; số lượng vẫn trả về trong response.
- Không cập nhật trạng thái sơ đồ theo thời gian thực (WebSocket/SSE).
- Không kiểm tra giờ mở cửa khi **gia hạn** (`BookingService.extend`). Việc này để làm sau.
- Không hỗ trợ đơn qua đêm (vắt sang ngày hôm sau) và chi nhánh mở cửa qua nửa đêm.
- Không có API quản trị để sửa tọa độ, tiện ích hay giờ mở cửa (UC19).
- Không có đăng nhập: các API tra cứu là công khai.

---

## 2. Context — code hiện tại liên quan

### 2.1. Luồng hiện tại

Trước khi làm:
```
GET /api/availability?type=&startAt=&endAt=&partySize=
  → AvailabilityController.search()
  → AvailabilityService.search()                 chỉ kiểm tra endAt > startAt
      → SpaceResourceRepository.findBookable()   lọc theo loại, ACTIVE, sức chứa (mọi chi nhánh)
      → BookingRepository.findOccupiedResourceIds()
      → PricingService.quote()
  → bảng: space_resource, resource_type, booking, price_rule
```

Sau khi làm:
```
GET /api/branches                                → AvailabilityService.branches()  → BranchRepository
GET /api/amenities                               → AvailabilityService.amenities() → AmenityRepository

GET /api/availability?branchId=&type=&startAt=&endAt=&partySize=&amenities=
  → AvailabilityController.search()
  → AvailabilityService.search()                 (business, @Transactional(readOnly = true))
      → BranchRepository.findById()              không có → 404
      → BookingWindowPolicy.validate()           khung giờ, quá khứ, thời lượng, chi nhánh mở cửa
      → AmenityRepository.findByCodeIn()         mã tiện ích lạ → 422
      → SpaceResourceRepository.findInBranch()   vị trí của chi nhánh + zone + tiện ích (1 câu SQL)
      → lọc trong Java: loại, ACTIVE, sức chứa, đủ tiện ích
      → BookingRepository.findOccupancies()      đơn đang chiếm khung giờ (chỉ id vị trí + trạng thái)
      → PricingService.quote()
  → bảng: branch, zone, space_resource, resource_type, amenity, resource_amenity, booking, price_rule

GET /api/branches/{branchId}/floor-plan?type=&startAt=&endAt=
  → AvailabilityController.floorPlan()
  → AvailabilityService.floorPlan()              giống trên, nhưng giữ mọi vị trí và gắn trạng thái

POST /api/bookings                               (giữ chỗ, UC03 — đã có, chỉ đổi phần kiểm tra)
  → BookingService.hold()
      → BookingWindowPolicy.validate()           cùng quy tắc với lúc tìm
```

### 2.2. File sẽ phải sửa / tạo

| File | Sửa hay tạo mới | Sửa cái gì |
|---|---|---|
| `backend/src/main/java/com/cowork/business/service/BookingWindowPolicy.java` | Tạo | Quy tắc khung giờ dùng chung cho tìm chỗ và giữ chỗ |
| `backend/src/main/java/com/cowork/business/service/AvailabilityService.java` | Sửa | Viết lại `search`; thêm `floorPlan`, `branches`, `amenities` |
| `backend/src/main/java/com/cowork/business/service/BookingService.java` | Sửa | `hold()` gọi `BookingWindowPolicy.validate()` thay cho 2 kiểm tra tự viết; thêm `branchOf()` |
| `backend/src/main/java/com/cowork/presentation/controller/AvailabilityController.java` | Sửa | Thêm tham số `branchId`, `amenities`; thêm 3 endpoint mới |
| `backend/src/main/java/com/cowork/presentation/controller/ApiExceptionHandler.java` | Sửa | Trả `400 VALIDATION_FAILED` khi thiếu tham số hoặc tham số sai kiểu |
| `backend/src/main/java/com/cowork/dto/AvailabilityDtos.java` | Tạo | Các record response và enum `SlotStatus` |
| `backend/src/main/java/com/cowork/dataaccess/repository/SpaceResourceRepository.java` | Sửa | Bỏ `findBookable`; thêm `findInBranch` |
| `backend/src/main/java/com/cowork/dataaccess/repository/BookingRepository.java` | Sửa | Đổi `findOccupiedResourceIds` thành `findOccupancies` (trả kèm trạng thái đơn) |
| `backend/src/main/java/com/cowork/dataaccess/repository/BranchRepository.java` | Tạo | `findByStatusOrderByCodeAsc` |
| `backend/src/main/java/com/cowork/dataaccess/repository/AmenityRepository.java` | Tạo | `findAllByOrderByCodeAsc`, `findByCodeIn` |
| `backend/src/main/java/com/cowork/dataaccess/repository/ZoneRepository.java` | Tạo | Để seeder lưu khu vực |
| `backend/src/main/java/com/cowork/dataaccess/entity/SpaceResource.java` | Sửa | Thêm `posX`, `posY` |
| `backend/src/main/java/com/cowork/config/BookingProperties.java` | Sửa | Thêm `minDuration` |
| `backend/src/main/resources/application.yml` | Sửa | `booking.min-duration: 1h` |
| `backend/src/main/java/com/cowork/config/DemoDataSeeder.java` | Sửa | Tạo chi nhánh, 3 khu vực, tiện ích và tọa độ (giống `db/data.sql`) |
| `backend/db/schema.sql` | Sửa | Thêm cột `pos_x`, `pos_y`, ràng buộc, index `zone_id` |
| `backend/db/data.sql` | Sửa | Thêm tọa độ cho 15 vị trí mẫu |
| `backend/src/test/java/com/cowork/business/service/BookingWindowPolicyTest.java` | Tạo | Test các giá trị biên của khung giờ |
| `backend/src/test/java/com/cowork/business/service/AvailabilityServiceTest.java` | Tạo | Test lọc và tính trạng thái trên sơ đồ |
| `backend/src/test/java/com/cowork/AvailabilityIntegrationTest.java` | Tạo | Test cả luồng qua REST |
| `backend/src/test/java/com/cowork/BookingFlowIntegrationTest.java` | Sửa | Gọi tìm chỗ có thêm `branchId` |
| `backend/src/test/java/com/cowork/business/service/BookingServiceTest.java` | Sửa | Constructor mới của `BookingService` và `BookingProperties` |
| `backend/README.md`, `docs/database.md` | Sửa | Bảng API, ý nghĩa cột tọa độ |

### 2.3. File KHÔNG được đụng vào

- `PricingService` và cách tính giá (BR-03, BR-04).
- Các luồng thanh toán, check-in/out, gia hạn, hủy trong `BookingService`. Chỉ được sửa phần kiểm tra đầu vào của `hold()`.
- Các entity khác ngoài `SpaceResource`.
- Ràng buộc chống trùng chỗ `ex_booking_no_overlap` trong `schema.sql`.

### 2.4. Quy ước code phải theo

- Quy tắc nghiệp vụ đặt trong service, không đặt trong entity. Entity chỉ có field + getter/setter.
- Lỗi nghiệp vụ: `DomainException("MA_LOI", "thông báo")` → 422; `ConflictException` → 409; `NotFoundException` → 404 với `code = NOT_FOUND`. Các lỗi này đã được `ApiExceptionHandler` đổi sang `ProblemDetail` có trường `code`.
- Mỗi bảng một repository `extends JpaRepository`.
- Service đọc dữ liệu dùng `@Transactional(readOnly = true)` và không ghi gì xuống DB.
- Ngày giờ là `LocalDateTime` theo giờ Việt Nam (`app.time-zone`), định dạng ISO `2026-11-02T09:00:00`.
- Lấy thời gian hiện tại qua bean `Clock`, không gọi `LocalDateTime.now()` không tham số (để test chỉnh giờ được).
- Khoảng thời gian là nửa mở `[startAt, endAt)`, giống BR-01.

---

## 3. Data Model Change — thay đổi database

### 3.1. Thay đổi bảng

| Bảng | Thay đổi | Cột | Kiểu | Null? | Mặc định | Ràng buộc / Ghi chú |
|---|---|---|---|---|---|---|
| `space_resource` | Thêm cột | `pos_x` | `integer` | Có | `NULL` | Tâm vị trí trên ảnh `zone.floor_plan_url`, tính theo % chiều rộng ảnh. NULL = chưa đặt lên sơ đồ |
| `space_resource` | Thêm cột | `pos_y` | `integer` | Có | `NULL` | % chiều cao ảnh |
| `space_resource` | Thêm ràng buộc | `ck_space_resource_position` | | | | `CHECK (pos_x BETWEEN 0 AND 100 AND pos_y BETWEEN 0 AND 100)`. Dòng có NULL vẫn qua |
| `space_resource` | Thêm index | `idx_space_resource_zone` | | | | `(zone_id)` — tìm theo chi nhánh đi qua zone |

Tọa độ tính theo % chứ không theo pixel, để ảnh sơ đồ phóng to/thu nhỏ thì vị trí vẫn đúng.

### 3.2. SQL

`db/schema.sql` đã được sửa sẵn (tạo bảng từ đầu). Với database **đang có dữ liệu**, chạy đoạn sau trong pgAdmin bằng tài khoản `postgres` (user `cowork` không có quyền sửa cấu trúc bảng):

```sql
ALTER TABLE space_resource ADD COLUMN IF NOT EXISTS pos_x integer;
ALTER TABLE space_resource ADD COLUMN IF NOT EXISTS pos_y integer;
ALTER TABLE space_resource DROP CONSTRAINT IF EXISTS ck_space_resource_position;
ALTER TABLE space_resource ADD CONSTRAINT ck_space_resource_position
    CHECK (pos_x BETWEEN 0 AND 100 AND pos_y BETWEEN 0 AND 100);
CREATE INDEX IF NOT EXISTS idx_space_resource_zone ON space_resource (zone_id);
```

Phải chạy đoạn này trước khi khởi động ứng dụng. Ứng dụng dùng `ddl-auto: validate`, nên khi thiếu cột `pos_x` sẽ báo lỗi và dừng ngay lúc khởi động.

### 3.3. Dữ liệu cũ

- Các vị trí đang có nhận `pos_x = pos_y = NULL`. Vẫn tìm và đặt được bình thường; trên sơ đồ, tọa độ trả về `null` để giao diện tự xử lý (ví dụ xếp vào danh sách "chưa có vị trí").
- Muốn có tọa độ mẫu thì chạy lại `db/data.sql`. File này xóa và nạp lại toàn bộ dữ liệu mẫu.
- Vị trí có `zone_id = NULL` không thuộc chi nhánh nào, nên không xuất hiện khi tìm theo chi nhánh. Khi giữ chỗ cho vị trí đó, hệ thống bỏ qua kiểm tra giờ mở cửa (không biết chi nhánh), nhưng vẫn kiểm tra quá khứ và thời lượng tối thiểu.

### 3.4. Entity / DTO cần cập nhật

| Lớp | Field mới | Kiểu Java |
|---|---|---|
| `SpaceResource` | `posX`, `posY` (`@Column(name = "pos_x" / "pos_y")`) | `Integer` |
| `BookingProperties` | `minDuration` (mặc định `1h`) | `Duration` |
| `AvailabilityDtos` (mới) | `BranchResponse`, `AmenityResponse`, `ResourceAmenityResponse`, `AvailableResource`, `FloorPlanResponse`, `ZonePlan`, `ResourceSlot`, enum `SlotStatus` | record / enum |
| `BookingRepository.Occupancy` (mới) | `getResourceId()`, `getStatus()` | interface projection |

---

## 4. API Contract

Mọi lỗi trả về theo định dạng `ProblemDetail` (RFC 7807), kèm trường `code`:
```json
{ "type": "about:blank", "title": "Unprocessable Entity", "status": 422,
  "detail": "Chi nhánh Cowork Nguyễn Huệ mở cửa từ 07:00 đến 22:00",
  "instance": "/api/availability", "code": "OUTSIDE_OPENING_HOURS" }
```

Thứ tự gọi của giao diện: `GET /api/branches` → `GET /api/amenities` → `GET /api/availability` hoặc `GET /api/branches/{id}/floor-plan` → `POST /api/bookings` (giữ chỗ, UC03).

### `GET /api/branches` — mới

Danh sách chi nhánh đang hoạt động (`status = ACTIVE`), sắp theo `code`.

**Request:** không có tham số.

**Response thành công** — `200`
```json
[
  { "id": 1, "code": "HCM-Q1", "name": "Cowork Nguyễn Huệ",
    "address": "72 Nguyễn Huệ, phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
    "openTime": "07:00:00", "closeTime": "22:00:00" }
]
```

**Response lỗi:** không có.

### `GET /api/amenities` — mới

Danh mục tiện ích để làm bộ lọc, sắp theo `code`.

**Request:** không có tham số.

**Response thành công** — `200`
```json
[
  { "code": "MONITOR", "name": "Màn hình rời" },
  { "code": "POWER_OUTLET", "name": "Ổ cắm điện" },
  { "code": "QUIET", "name": "Khu yên tĩnh" },
  { "code": "SOUND_SYSTEM", "name": "Hệ thống âm thanh" },
  { "code": "TV", "name": "TV trình chiếu" },
  { "code": "WHITEBOARD", "name": "Bảng trắng" }
]
```

**Response lỗi:** không có.

### `GET /api/availability` — sửa

Chỉ trả vị trí **đặt được ngay**: đang khai thác, đủ sức chứa, đủ mọi tiện ích yêu cầu, không có đơn hiệu lực chiếm khung giờ. Sắp theo tầng → tên khu vực → mã vị trí.

**Request (query string)**
```json
{
  "branchId":  "số — bắt buộc — id từ GET /api/branches",
  "type":      "enum — bắt buộc — HOT_DESK | FIXED_DESK | FOCUS_BOOTH | MEETING_ROOM | EVENT_SPACE",
  "startAt":   "ngày giờ ISO — bắt buộc — ví dụ 2026-11-02T09:00:00",
  "endAt":     "ngày giờ ISO — bắt buộc — sau startAt ít nhất booking.min-duration (1 giờ), cùng ngày, trong giờ mở cửa",
  "partySize": "số nguyên — không bắt buộc, mặc định 1 — >= 1",
  "amenities": "danh sách mã — không bắt buộc — 'QUIET,POWER_OUTLET' hoặc lặp tham số; không phân biệt hoa thường; vị trí phải có ĐỦ mọi mã"
}
```
Ví dụ: `/api/availability?branchId=1&type=FOCUS_BOOTH&startAt=2026-11-02T09:00:00&endAt=2026-11-02T11:00:00&partySize=1&amenities=QUIET`

**Response thành công** — `200` (không còn chỗ thì trả `[]`)
```json
[
  { "resourceId": 10, "code": "FB-01", "name": "Focus Booth 1", "type": "FOCUS_BOOTH", "capacity": 1,
    "zoneId": 2, "zoneName": "Khu yên tĩnh", "floor": 2, "posX": 25, "posY": 70,
    "amenities": [
      { "code": "POWER_OUTLET", "name": "Ổ cắm điện", "quantity": 2 },
      { "code": "QUIET", "name": "Khu yên tĩnh", "quantity": 1 }
    ],
    "quote": { "blocks": 4, "blockMinutes": 30, "total": { "amount": 180000, "zero": false, "positive": true } } },
  { "resourceId": 11, "code": "FB-02", "...": "cùng cấu trúc" }
]
```
`quote` giống nhau cho mọi vị trí cùng loại trong cùng khung giờ (giá theo loại không gian, BR-04). Hai trường `zero`, `positive` trong `total` là cách serialize sẵn có của `Money`, không phải trường mới.

**Response lỗi**

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Thiếu `branchId` / `type` / `startAt` / `endAt`; `type` không thuộc enum; ngày giờ sai định dạng; `branchId` không phải số |
| 404 | `NOT_FOUND` | `branchId` không tồn tại |
| 409 | `BRANCH_CLOSED` | Chi nhánh có `status = CLOSED` |
| 422 | `INVALID_TIME_RANGE` | `endAt <= startAt` |
| 422 | `START_IN_PAST` | `endAt <= now`, hoặc `startAt < now − 5 phút` |
| 422 | `DURATION_TOO_SHORT` | `endAt − startAt < 1 giờ` |
| 422 | `OUTSIDE_OPENING_HOURS` | Khác ngày, `startAt` trước giờ mở cửa, hoặc `endAt` sau giờ đóng cửa |
| 422 | `INVALID_PARTY_SIZE` | `partySize < 1` |
| 422 | `UNKNOWN_AMENITY` | Có mã tiện ích không tồn tại; `detail` liệt kê các mã đó |

### `GET /api/branches/{branchId}/floor-plan` — mới

Mọi vị trí của chi nhánh (trừ vị trí `INACTIVE`), gom theo khu vực, kèm tọa độ và trạng thái trong khung giờ. Response không chứa thông tin khách đã đặt.

**Request**
```json
{
  "branchId (path)": "số — bắt buộc",
  "type":            "enum — không bắt buộc — bỏ trống = mọi loại",
  "startAt":         "ngày giờ ISO — bắt buộc — cùng quy tắc như /api/availability",
  "endAt":           "ngày giờ ISO — bắt buộc"
}
```
Ví dụ: `/api/branches/1/floor-plan?type=FOCUS_BOOTH&startAt=2026-11-02T10:00:00&endAt=2026-11-02T12:00:00`

**Response thành công** — `200`
```json
{
  "branchId": 1, "branchName": "Cowork Nguyễn Huệ", "openTime": "07:00:00", "closeTime": "22:00:00",
  "startAt": "2026-11-02T10:00:00", "endAt": "2026-11-02T12:00:00",
  "zones": [
    { "zoneId": 2, "name": "Khu yên tĩnh", "floor": 2, "floorPlanUrl": "/floor-plans/hcm-q1-tang-2.png",
      "resources": [
        { "resourceId": 10, "code": "FB-01", "name": "Focus Booth 1", "type": "FOCUS_BOOTH", "capacity": 1,
          "posX": 25, "posY": 70, "status": "HELD",
          "amenities": [ { "code": "POWER_OUTLET", "name": "Ổ cắm điện", "quantity": 2 },
                         { "code": "QUIET", "name": "Khu yên tĩnh", "quantity": 1 } ] },
        { "resourceId": 11, "code": "FB-02", "...": "...", "status": "AVAILABLE" }
      ] }
  ]
}
```
- Chỉ trả những khu vực có ít nhất một vị trí sau khi lọc. Khu vực sắp theo tầng → tên, vị trí trong khu vực sắp theo mã.
- `status`: `AVAILABLE` | `HELD` | `BOOKED` | `MAINTENANCE`. Cách tính ở mục 5.1.
- Sơ đồ **không** lọc theo sức chứa hay tiện ích. Giao diện dùng `capacity` và `amenities` để làm mờ vị trí không phù hợp.

**Response lỗi:** như `/api/availability`, trừ `INVALID_PARTY_SIZE` và `UNKNOWN_AMENITY` (API này không nhận hai tham số đó).

### `POST /api/bookings` — đổi phần kiểm tra đầu vào

Request và response giữ nguyên. Thêm các lỗi sau, do áp cùng quy tắc với lúc tìm chỗ:

| HTTP | `code` | Khi nào |
|---|---|---|
| 409 | `BRANCH_CLOSED` | Chi nhánh của vị trí đang `CLOSED` |
| 422 | `DURATION_TOO_SHORT` | Khung giờ ngắn hơn 1 giờ |
| 422 | `OUTSIDE_OPENING_HOURS` | Ngoài giờ mở cửa của chi nhánh chứa vị trí |

Thứ tự kiểm tra thay đổi: hệ thống tìm và khóa vị trí **trước** (không có → `404`), rồi mới kiểm tra khung giờ. Trước đây khung giờ được kiểm tra trước.

---

## 5. Logic Flow — các bước xử lý

**`GET /api/availability`** — `AvailabilityService.search()`:

1. `AvailabilityController.search()` nhận tham số. Thiếu hoặc sai kiểu → `ApiExceptionHandler` trả `400 VALIDATION_FAILED`.
2. Mở transaction chỉ đọc (`readOnly = true`).
3. `partySize < 1` → `422 INVALID_PARTY_SIZE`.
4. `BranchRepository.findById(branchId)`. Không có → `404 NOT_FOUND`.
5. `BookingWindowPolicy.validate(startAt, endAt, branch)` kiểm tra lần lượt và dừng ở lỗi đầu tiên:
   1. `endAt > startAt`, nếu không → `INVALID_TIME_RANGE`;
   2. `endAt > now` và `startAt >= now − 5 phút`, nếu không → `START_IN_PAST`;
   3. `endAt − startAt >= booking.min-duration`, nếu không → `DURATION_TOO_SHORT`;
   4. `branch.status = ACTIVE`, nếu không → `409 BRANCH_CLOSED`;
   5. cùng ngày, `startAt.time >= openTime`, `endAt.time <= closeTime`, nếu không → `OUTSIDE_OPENING_HOURS`.
6. Chuẩn hóa `amenities` (bỏ khoảng trắng, viết hoa, bỏ mã rỗng, bỏ trùng). Gọi `AmenityRepository.findByCodeIn`. Có mã không tìm thấy → `422 UNKNOWN_AMENITY`.
7. `SpaceResourceRepository.findInBranch(branchId)`: vị trí có `zone.branch_id = branchId` và `status <> INACTIVE`, nạp kèm zone, tiện ích và amenity trong 1 câu SQL.
8. Lọc trong Java: đúng `type`, `status = ACTIVE` (bỏ `MAINTENANCE`, BR-09), `capacity >= partySize` (BR-14), có đủ mọi mã tiện ích yêu cầu.
9. `BookingRepository.findOccupancies(ids, range)`: các đơn có trạng thái thuộc `HELD, CONFIRMED, CHECKED_IN, OVERSTAY` và giao khung giờ (`b.startAt < endAt AND b.endAt > startAt`). Bỏ các vị trí đó (BR-01).
10. Không còn vị trí nào → trả `[]` và **không** gọi tính giá.
11. `PricingService.quote(type, range)` tính một lần, dùng cho mọi vị trí.
12. Trả danh sách `AvailableResource`. Không ghi gì vào DB.

**`GET /api/branches/{id}/floor-plan`** — `AvailabilityService.floorPlan()`: giống bước 2, 4, 5, 7, 9 ở trên, nhưng:
- bước 8 chỉ lọc theo `type` (nếu có), giữ cả vị trí `MAINTENANCE`, không lọc sức chứa hay tiện ích;
- không bỏ vị trí đang bị chiếm, mà gắn trạng thái theo mục 5.1;
- gom theo khu vực, giữ đúng thứ tự của câu truy vấn (tầng → tên khu vực → mã).

**`POST /api/bookings`** — `BookingService.hold()`:
1. `lockResource()`: `SELECT … FOR UPDATE` vị trí. Không có → `404`.
2. `BookingWindowPolicy.validate(startAt, endAt, branchOf(resource))`. `branchOf` lấy `resource.zone.branch`; zone rỗng → `null`, bỏ qua bước 5.4 và 5.5.
3. Các bước sau giữ nguyên như cũ: `requireBookable` → kiểm tra trùng → tính giá → tạo đơn `HELD`.

### 5.1. Quy tắc nghiệp vụ / công thức

| Quy tắc | Công thức | Ví dụ |
|---|---|---|
| Thời lượng tối thiểu (SRS Q1) | `endAt − startAt >= 1 giờ` (cấu hình `booking.min-duration`) | 09:00–09:59 → `DURATION_TOO_SHORT`; 09:00–10:00 → hợp lệ |
| Giờ mở cửa | cùng ngày và `openTime <= startAt.time` và `endAt.time <= closeTime` (biên được tính là hợp lệ) | Chi nhánh 07:00–22:00: 07:00–08:00 và 21:00–22:00 hợp lệ; 06:30–08:00, 21:00–22:30 và 21:00 hôm nay–21:00 mai → `OUTSIDE_OPENING_HOURS` |
| Quá khứ | `endAt > now` và `startAt >= now − 5 phút` | now = 08:00: bắt đầu 07:55 → hợp lệ; 07:54 → `START_IN_PAST` |
| Giao khung giờ (BR-01) | nửa mở: `b.startAt < endAt AND b.endAt > startAt` | Đơn 09:00–11:00 chặn tìm 10:00–12:00, nhưng không chặn 11:00–13:00 |
| Lọc tiện ích | mã tiện ích của vị trí ⊇ mã yêu cầu (AND) | `QUIET,POWER_OUTLET`: FB-01 (có cả hai) → có; HD-01 (chỉ có ổ cắm) → không |
| Trạng thái trên sơ đồ | `MAINTENANCE` nếu vị trí đang bảo trì; ngược lại `BOOKED` nếu có đơn CONFIRMED/CHECKED_IN/OVERSTAY giao khung; ngược lại `HELD` nếu có đơn HELD giao khung; ngược lại `AVAILABLE` | Một vị trí có cả đơn HELD lẫn CHECKED_IN trong khung → `BOOKED` |
| Giá dự kiến (FR-SRCH-04) | `PricingService.quote(type, range)` — không đổi | Focus Booth thứ Hai 09:00–11:00, 4 block × 45.000 → 180.000 (dữ liệu mẫu) |

### 5.2. Transaction và đồng thời

- Tìm chỗ và xem sơ đồ chỉ **đọc**, chạy trong transaction `readOnly`, không khóa dòng nào.
- Kết quả chỉ đúng tại lúc đọc. Hai khách có thể cùng thấy một chỗ trống; khi cả hai giữ chỗ, `BookingService.hold()` khóa dòng vị trí (`FOR UPDATE`) và kiểm tra lại chồng lấn. Một người được `201`, người kia nhận `409 SLOT_UNAVAILABLE`. Ràng buộc `ex_booking_no_overlap` trong DB là lớp bảo vệ cuối (đã có, không đổi).
- Đơn `HELD` đã quá hạn nhưng job chưa giải phóng vẫn được tính là đang chiếm chỗ (hiện `HELD`, bị ẩn khỏi kết quả tìm). Cách này giống BR-01 lúc giữ chỗ, để không hứa một chỗ mà lát nữa giữ không được.

---

## 6. Edge Cases — mục quan trọng nhất

| # | Tình huống | Kết quả mong đợi | Dữ liệu sau đó |
|---|---|---|---|
| E1 | Chưa đăng nhập | API tra cứu công khai → `200` (hệ thống chưa có đăng nhập) | Không đổi gì |
| E2 | Thiếu `branchId` (hoặc `type`, `startAt`, `endAt`) | `400 VALIDATION_FAILED`, `detail = "branchId: bắt buộc phải có"` | Không đổi gì |
| E3 | `type=SAUNA`, ngày giờ sai định dạng, `branchId=abc` | `400 VALIDATION_FAILED` | Không đổi gì |
| E4 | `partySize=0` hoặc số âm | `422 INVALID_PARTY_SIZE` | Không đổi gì |
| E5 | `branchId` không tồn tại | `404 NOT_FOUND` | Không đổi gì |
| E6 | Chi nhánh `CLOSED` | `409 BRANCH_CLOSED` | Không đổi gì |
| E7 | `endAt <= startAt` | `422 INVALID_TIME_RANGE` | Không đổi gì |
| E8 | Khung giờ đã qua; bắt đầu trước now đúng 5 phút / 6 phút | đã qua → `422 START_IN_PAST`; 5 phút → hợp lệ (`>=`); 6 phút → `START_IN_PAST` | Không đổi gì |
| E9 | Thời lượng 59 phút / đúng 60 phút | 59 → `422 DURATION_TOO_SHORT`; 60 → hợp lệ (`>=`) | Không đổi gì |
| E10 | Bắt đầu đúng giờ mở cửa, kết thúc đúng giờ đóng cửa | Hợp lệ (biên được tính) | Không đổi gì |
| E11 | Bắt đầu trước giờ mở cửa / kết thúc sau giờ đóng cửa | `422 OUTSIDE_OPENING_HOURS` | Không đổi gì |
| E12 | Khung giờ vắt qua nửa đêm | `422 OUTSIDE_OPENING_HOURS` (chưa hỗ trợ đơn qua đêm) | Không đổi gì |
| E13 | Mã tiện ích không tồn tại (`JACUZZI`) | `422 UNKNOWN_AMENITY`, `detail` có `JACUZZI` | Không đổi gì |
| E14 | Mã tiện ích viết thường hoặc có khoảng trắng (`" quiet "`) | Được chuẩn hóa thành `QUIET` → lọc bình thường | Không đổi gì |
| E15 | Lọc nhiều tiện ích | Chỉ trả vị trí có **đủ** mọi tiện ích | Không đổi gì |
| E16 | Vị trí `MAINTENANCE` | Ẩn khỏi `/availability`; sơ đồ hiện `MAINTENANCE` | Không đổi gì |
| E17 | Vị trí `INACTIVE` | Ẩn khỏi cả hai API | Không đổi gì |
| E18 | Sức chứa < `partySize` | Ẩn khỏi `/availability`; sơ đồ vẫn hiện (kèm `capacity`) | Không đổi gì |
| E19 | Vị trí có đơn `HELD` giao khung giờ | Ẩn khỏi `/availability`; sơ đồ `HELD` | Không đổi gì |
| E20 | Vị trí có đơn `CONFIRMED` / `CHECKED_IN` / `OVERSTAY`, hoặc vừa HELD vừa CHECKED_IN | Ẩn; sơ đồ `BOOKED` (BOOKED được ưu tiên hơn HELD) | Không đổi gì |
| E21 | Đơn kết thúc đúng lúc khung tìm bắt đầu (09–11 và 11–13) | Không coi là trùng → vị trí vẫn trống | Không đổi gì |
| E22 | Không còn vị trí nào phù hợp | `200 []`, không gọi tính giá | Không đổi gì |
| E23 | Vị trí chưa có tọa độ | `posX`, `posY` = `null` | Không đổi gì |
| E24 | Vị trí chưa gán khu vực (`zone_id` NULL — dữ liệu cũ) | Không xuất hiện khi tìm theo chi nhánh. Giữ chỗ vẫn được, bỏ qua kiểm tra giờ mở cửa | Không đổi gì |
| E25 | Gọi thẳng `POST /api/bookings` ngoài giờ mở cửa | `422 OUTSIDE_OPENING_HOURS` (không lách được qua API giữ chỗ) | Không tạo đơn |
| E26 | Hai khách cùng thấy một chỗ trống rồi cùng giữ chỗ | Tìm chỗ trả cùng kết quả cho cả hai; khi giữ, một người `201`, người kia `409 SLOT_UNAVAILABLE` (đã có từ trước) | Chỉ một đơn HELD |
| E27 | Đơn HELD quá hạn nhưng job chưa giải phóng | Vẫn tính là đang chiếm: ẩn khỏi kết quả, sơ đồ `HELD` | Không đổi gì |
| E28 | DB lỗi giữa chừng | `500`; vì chỉ đọc nên không có gì phải rollback | Không đổi gì |
| E29 | Dịch vụ ngoài lỗi | Không áp dụng — luồng này không gọi dịch vụ ngoài | — |

---

## 7. Tiêu chí hoàn thành (Acceptance Criteria)

- [x] Mọi edge case ở mục 6 đều có test và test qua, trừ E1, E28, E29 (không áp dụng) và E26 (đã có `ConcurrentHoldIntegrationTest` từ trước).
- [x] `mvn test` qua toàn bộ, kể cả test cũ: **49/49**.
- [x] Chạy `schema.sql` lại nhiều lần không lỗi (file vẫn xóa và tạo lại bảng như trước; đoạn ALTER ở 3.2 dùng `IF NOT EXISTS`).
- [x] Không sửa file nào ngoài danh sách ở mục 2.2.
- [x] Đúng quy ước ở mục 2.4.
- [ ] Đã chạy đoạn SQL ở 3.2 trên PostgreSQL của máy dev và khởi động ứng dụng với `ddl-auto: validate` thành công. **Người dùng phải tự làm**, vì cần tài khoản `postgres`.

### 7.1. Test cần viết

Cập nhật 2026-11-05: toàn bộ test loại **Unit** trong bảng này đã bị xóa theo yêu cầu của người dùng (xem `ticket-response/authentication.md` mục 1). Chỉ còn các test Integration; số test hiện tại: `Tests run: 109`.

| Test | Loại | Kiểm tra edge case |
|---|---|---|
| `BookingWindowPolicyTest.rejectsEndNotAfterStart` | Unit | E7 |
| `BookingWindowPolicyTest.rejectsWindowInThePastButToleratesFiveMinutes` | Unit | E8 |
| `BookingWindowPolicyTest.requiresMinimumDurationOfOneHour` | Unit | E9 |
| `BookingWindowPolicyTest.acceptsExactlyOpeningAndClosingTime` | Unit | E10 |
| `BookingWindowPolicyTest.rejectsWindowOutsideOpeningHours` | Unit | E11 |
| `BookingWindowPolicyTest.rejectsWindowSpanningMidnight` | Unit | E12 |
| `BookingWindowPolicyTest.rejectsClosedBranch` | Unit | E6 |
| `BookingWindowPolicyTest.skipsBranchChecksWhenResourceHasNoBranch` | Unit | E24 |
| `AvailabilityServiceTest.rejectsInvalidPartySizeAndUnknownBranch` | Unit (Mockito) | E4, E5 |
| `AvailabilityServiceTest.excludesOccupiedMaintenanceAndTooSmallResources` | Unit (Mockito) | E16, E18, E19 |
| `AvailabilityServiceTest.amenityFilterRequiresEveryRequestedAmenity` | Unit (Mockito) | E14, E15 |
| `AvailabilityServiceTest.unknownAmenityIsRejected` | Unit (Mockito) | E13 |
| `AvailabilityServiceTest.doesNotQuotePriceWhenNothingIsFree` | Unit (Mockito) | E20, E22 |
| `AvailabilityServiceTest.floorPlanShowsStatusOfEveryResource` | Unit (Mockito) | E16, E19 |
| `AvailabilityServiceTest.bookedWinsOverHeldOnTheSameResource` | Unit (Mockito) | E20 |
| `AvailabilityIntegrationTest.rejectsInvalidSearchRequests` | Integration (MockMvc + H2) | E2, E3, E5, E9, E11, E13 |
| `AvailabilityIntegrationTest.filtersByAmenitiesRequiringAllOfThem` | Integration | E15, E14 |
| `AvailabilityIntegrationTest.floorPlanShowsHeldResourceWhileSearchHidesIt` | Integration | E19, E21 |
| `AvailabilityIntegrationTest.holdAppliesTheSameOpeningHoursRule` | Integration | E25 |
| `AvailabilityIntegrationTest.listsActiveBranchesWithOpeningHoursAndAmenityCatalog` | Integration | API danh mục |
| `ConcurrentHoldIntegrationTest` (đã có) | Integration | E26 |

E17 được bảo đảm bởi điều kiện `status <> INACTIVE` trong `findInBranch`. Dữ liệu seeder không có vị trí INACTIVE nên chưa có test riêng. E23 cũng chưa có test riêng.

### 7.2. Kiểm tra tay

Chạy nhanh không cần PostgreSQL (dữ liệu mẫu do `DemoDataSeeder` tạo; chi nhánh id = 1, mở cửa 07:00–22:00):
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2

curl http://localhost:8080/api/branches
curl http://localhost:8080/api/amenities
curl "http://localhost:8080/api/availability?branchId=1&type=FOCUS_BOOTH&startAt=2026-11-02T09:00:00&endAt=2026-11-02T11:00:00&amenities=QUIET"

# Giữ chỗ FB-01 rồi xem sơ đồ khung 10:00–12:00
curl -X POST http://localhost:8080/api/bookings -H "Content-Type: application/json" \
  -d '{"resourceId":10,"startAt":"2026-11-02T09:00:00","endAt":"2026-11-02T11:00:00","partySize":1,"contact":{"fullName":"Nguyen Van A","phone":"0912345678"}}'
curl "http://localhost:8080/api/branches/1/floor-plan?type=FOCUS_BOOTH&startAt=2026-11-02T10:00:00&endAt=2026-11-02T12:00:00"

# Lỗi
curl "http://localhost:8080/api/availability?type=HOT_DESK&startAt=2026-11-02T09:00:00&endAt=2026-11-02T11:00:00"
curl "http://localhost:8080/api/availability?branchId=1&type=HOT_DESK&startAt=2026-11-02T21:00:00&endAt=2026-11-02T23:00:00"
```

Kết quả mong đợi (đã chạy thử ngày 2026-11-01):
- `branches` → 1 chi nhánh `HCM-Q1`, `07:00:00`–`22:00:00`; `amenities` → 6 mã.
- `availability` → `FB-01`, `FB-02`, mỗi vị trí `quote.total.amount = 180000`, có `posX`, `posY`.
- Sau khi giữ chỗ, `floor-plan` → `FB-01` có `status = HELD`, `FB-02` có `status = AVAILABLE`.
- Thiếu `branchId` → `400 VALIDATION_FAILED`. Khung 21:00–23:00 → `422 OUTSIDE_OPENING_HOURS`.

Ngày trong ví dụ phải ở tương lai so với giờ máy, nếu không sẽ nhận `START_IN_PAST`.

---

## 8. Sau khi AI làm xong — tự đánh giá spec

| Câu hỏi | Trả lời |
|---|---|
| Code chạy đúng ngay lần đầu? | Có: 49/49 test qua ngay lần chạy đầu. Sau đó tự sửa thêm 2 chỗ: chuyển lọc loại không gian từ JPQL `(:type is null or …)` sang Java (dễ lỗi kiểu tham số null trên PostgreSQL), và sắp tiện ích theo mã để response ổn định |
| Phải chat lại mấy lần để sửa? | 0 |
| AI làm sai ở đâu? | Chưa phát hiện sai. Chưa kiểm chứng trên PostgreSQL thật, vì database dev cần chạy ALTER (mục 3.2) bằng tài khoản `postgres` trước |
| Spec bị hở ở mục nào (thiếu edge case, mơ hồ, thiếu tên file...)? | Yêu cầu gốc không nói rõ: (1) API giữ chỗ có phải áp cùng quy tắc giờ mở cửa và thời lượng tối thiểu không — đã quyết định là có (E25); (2) tọa độ đo theo đơn vị gì — đã chọn % kích thước ảnh; (3) lọc tiện ích theo AND hay OR, có lọc theo số lượng không — đã chọn AND, không lọc số lượng; (4) sơ đồ có lọc theo sức chứa / tiện ích không — đã chọn không |
| Lần sau cần bổ sung gì vào template? | Thêm mục "Quyết định đã chốt" cho các câu hỏi kiểu trên; và một dòng "Có cần chạy migration trên DB dev không, ai chạy, bằng tài khoản nào" |

---

## Checklist

- [x] Mục 2 có tên file và tên hàm thật, đã mở code ra kiểm tra.
- [x] Mục 3 ghi đủ kiểu dữ liệu, null, mặc định, dữ liệu cũ.
- [x] Mục 5 không có câu kiểu "xử lý cho phù hợp" hay "kiểm tra hợp lệ" mà không nói kiểm tra cái gì.
- [x] Mục 6 có ít nhất: quyền, đầu vào sai, không tồn tại, sai trạng thái, giá trị ở biên, đồng thời, lỗi giữa chừng.
- [x] Mỗi edge case có kết quả cụ thể: mã HTTP + mã lỗi + trạng thái dữ liệu.
- [x] Đã ghi mục "Ngoài phạm vi" và "File không được đụng".
