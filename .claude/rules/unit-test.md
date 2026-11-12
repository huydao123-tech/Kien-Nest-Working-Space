# Quy tắc viết unit test

Áp dụng khi prompt yêu cầu viết, sửa hoặc bổ sung **unit test**. Với unit test, quy tắc này thắng mục B3.7 của `CLAUDE.md` (tên hàm test, JUnit). Integration test vẫn theo B3.7.

## 1. Độ phủ

Mỗi hàm logic trong file được test phải có **ít nhất 1 testcase**.

- **Hàm logic:** hàm có rẽ nhánh, tính toán, kiểm tra quy tắc, đổi trạng thái hoặc ném lỗi.
- **Không cần test:** getter/setter, constructor chỉ gán field, record DTO không có logic.
- **Hàm `private`:** test qua hàm không `private` gọi tới nó, không dùng reflection. Testcase đặt tên theo hàm được gọi.

## 2. JUnit 4

Dùng **JUnit 4**, không dùng JUnit 5 cho unit test mới.

| Việc | Dùng | Không dùng |
|---|---|---|
| Đánh dấu test | `org.junit.Test` | `org.junit.jupiter.api.Test` |
| Chạy trước mỗi test | `org.junit.Before` | `@BeforeEach` |
| Chạy sau mỗi test | `org.junit.After` | `@AfterEach` |
| Mockito | `mock(...)` trong `@Before` | `@ExtendWith(MockitoExtension.class)` |
| Kiểm tra kết quả | Assert riêng của `org.junit.Assert` (bảng dưới) | `assertThat`, `assertThatThrownBy` (AssertJ, Hamcrest, JUnit) |

- Class test và hàm test phải là `public`, hàm test trả về `void`.
- Mock và đồng hồ `MutableClock` dựng trong `@Before`, service dựng bằng `new`, không cần Spring.

**Chọn đúng assert theo điều cần kiểm tra:**

| Kiểm tra | Dùng |
|---|---|
| Giá trị bằng nhau (`expected` đứng trước `actual`) | `assertEquals(expected, actual)` |
| Null / khác null | `assertNull(actual)` / `assertNotNull(actual)` |
| Đúng / sai | `assertTrue(actual)` / `assertFalse(actual)` |
| Cùng một object | `assertSame(expected, actual)` |
| Ném lỗi | `assertThrows(XxxException.class, () -> ...)`, cần kiểm tra mã lỗi thì `assertEquals` trên `thrown.code()` |

So sánh với kiểu `Long` thì viết `assertEquals(Long.valueOf(7L), actual)`, vì `assertEquals(7L, actual)` báo lỗi biên dịch do gọi hàm không rõ ràng.

**Điều kiện để JUnit 4 chạy được:** `backend/pom.xml` phải có `org.junit.vintage:junit-vintage-engine` (scope `test`). Thiếu dependency này thì test JUnit 4 **không chạy và cũng không báo lỗi**. Chưa có thì hỏi người dùng trước khi thêm (A5). Báo xong phải kiểm tra số test trong `Tests run` có tăng đúng số testcase mới.

## 3. Đặt tên testcase

```
test_<tên hàm cần test>_<điều cần test>_<số thứ tự testcase trong file>
```

- `<điều cần test>` viết tiếng Anh dạng PascalCase, mô tả ngắn tình huống của testcase, ví dụ `EmailNotExisted`, `NullRequest`.
- Số thứ tự gồm 3 chữ số, bắt đầu từ `001`, tăng dần và **không lặp trong cả file**, không đếm lại theo từng hàm.
- Thêm testcase mới thì lấy số tiếp theo, không đánh lại số của testcase cũ.

Ví dụ trong một file: `test_hold_NewBooking_001`, `test_hold_SlotUnavailable_002`, `test_cancel_NullRequest_003`, `test_checkIn_BookingNotPaid_004`.

## 4. Ghi viewpoint

Trên **mỗi** hàm test phải ghi viewpoint áp dụng cho hàm đó, ngay trước `@Test`.

```java
// Viewpoint: VP3, VP7
@Test
public void test_cancel_NullRequest_003() {
    // ...
}
```

Ghi mã viewpoint ở mục 5. Testcase dùng nhiều viewpoint thì ghi tất cả, cách nhau bằng dấu phẩy.

## 5. Danh sách viewpoint

Phải tuân thủ các viewpoint dưới đây **nếu gặp** tình huống tương ứng trong hàm được test.

| Mã | Khi gặp | Testcase phải có |
|---|---|---|
| VP1 | Biến có kiểu dữ liệu **không phải** object | Đủ dữ liệu: Max Digits, Min Digits, Null, Empty |
| VP2 | Dữ liệu chuỗi ký tự | Đủ ký tự 1 byte (chữ cái, số, ký hiệu), ký tự 2 byte, ký tự 3 byte |
| VP3 | Rẽ nhánh | Test đủ các nhánh liên quan và đủ trường hợp: cận trên, cận dưới, khoảng giữa cận trên và cận dưới |
| VP4 | Vòng lặp | Thỏa điều kiện vòng lặp với: 0 record, 1 record, > 1 record |
| VP5 | Sửa thuộc tính / column / method có sẵn | Test thứ bị chỉnh sửa, không viết testcase cho tất cả thuộc tính / column / method trong object |
| VP6 | Performance, security, volume, stress | **Không** viết testcase cho các loại test này |
| VP7 | Biến có kiểu dữ liệu object | Dữ liệu có giá trị Null và khác Null |
| VP8 | Sửa code ảnh hưởng tới vị trí khác | Test các vị trí khác bị ảnh hưởng bởi vị trí chỉnh sửa |
| VP9 | Có `try/catch` | Test xử lý bên trong `try/catch` được thực hiện |
| VP10 | Xử lý đa luồng | Testcase multithread |

VP6 là điều cấm nên không ghi lên hàm test. Mỗi hàm test ghi ít nhất một mã trong các mã còn lại, không tự đặt thêm viewpoint.

## 6. Ghi log testcase

Mỗi class unit test khai báo `support/TestCaseLogRule`, và **mỗi** hàm test tự log input và output của nó. Không dùng spy hay helper để log tự động, vì mỗi hàm test có input và output khác nhau.

```java
@Rule
public final TestCaseLogRule log = new TestCaseLogRule();

// Viewpoint: VP3, VP7
@Test
public void test_registerByPhone_GuestWithoutEmail_003() {
    RegisterPhoneRequest request = phoneRequest(PHONE, "Lê C", PASSWORD, null);
    log.input("request = {}", request);

    service.registerByPhone(request);

    log.output("email = {}", guest.getContact().email());
    assertNull(guest.getContact().email());
}
```

- **File log:** mỗi file test có đúng một file log, trùng tên với file test: `UTLogs/<tên class test>.log` ở thư mục gốc repo (ví dụ `AuthServiceTest.java` → `UTLogs/AuthServiceTest.log`). `UTLogs/` đã có trong `.gitignore`. File được ghi mới ở mỗi lần chạy. Mỗi testcase đúng 5 dòng, theo thứ tự: tên hàm test, phiên bản JDK, input, output, result (`PASS` / `FAIL`).
- **Log input:** gồm các input parameter của hàm được test, gọi trước khi gọi hàm. Tạo biến rồi truyền cùng biến đó vào log và vào hàm, để log đúng giá trị thật.
- **Log output:** gồm các giá trị testcase muốn thể hiện, viết dạng `<tên biến> = {}`, gọi **trước** assert tương ứng. Ví dụ test object bằng null: `log.output("email = {}", email);` với `email` là null, rồi `assertNull(email);`.
- **Số biến trong log output phải ít hơn hoặc bằng số assert** của hàm test. Mỗi `{}` là một biến. Mỗi lời gọi `assert...` tính là một assert, kể cả `assertThrows`; `verify` của Mockito không tính.
- **Test ném lỗi:** dùng `assertThrows`, log `code = {}` với `thrown.code()`, rồi `assertEquals` trên mã lỗi. Lỗi không có mã thì log `exception = {}` với tên class của lỗi.
