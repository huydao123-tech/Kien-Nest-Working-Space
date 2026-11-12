# SPEC: <Tên tính năng / tên bug>

> **Cách dùng template này**
> - Chép file này ra `ticket-response/<tên file request>.md` (trùng tên với file trong `ticket-request/`, ví dụ `ticket-response/voucher-gioi-han-khach-cu.md`), rồi điền vào từng mục.
> - Dòng nào bắt đầu bằng `>` là hướng dẫn. Viết xong thì xóa hết các dòng đó.
> - Viết sao cho **người lạ (hoặc AI) đọc mỗi file này là code được**, không cần hỏi lại câu nào.
> - Mục nào không áp dụng thì ghi rõ "Không có", đừng xóa mục đó. Ghi "Không có" nghĩa là bạn đã nghĩ tới và quyết định như vậy.

| Mục | Nội dung |
|---|---|
| Loại | Tính năng mới / Sửa bug / Refactor |
| Liên kết | Issue #..., UC.., FR-..., BR-.. |
| Người viết | ... |
| Ngày | YYYY-MM-DD |

---

## 1. Mục tiêu (Goal)

> Viết 1–3 câu: ai được gì sau khi làm xong. Đừng mô tả cách code ở đây.
> Ví dụ: "Voucher có thể giới hạn chỉ cho khách đã đăng ký trên 30 ngày và có tổng đơn từ 500.000đ trở lên."
> Ghi thêm: đã xử lý prompt nào (tên file request + số prompt); câu đã hỏi lại người dùng và câu trả lời.

...

### 1.1. Bug (chỉ điền nếu là sửa lỗi)

| | |
|---|---|
| Các bước tái hiện | 1. ... 2. ... 3. ... |
| Kết quả hiện tại | ... |
| Kết quả mong muốn | ... |
| Nguyên nhân (đã tìm được) | File `...`, hàm `...`, dòng ... làm sai vì ... |

### 1.2. Ngoài phạm vi (Out of scope)

> Liệt kê những thứ KHÔNG làm lần này. Mục này giúp AI không tự ý "làm thêm cho đủ".

- Không làm giao diện.
- Không sửa luồng ...

---

## 2. Context — code hiện tại liên quan

> Điền từ kết quả bạn tự lần luồng (Bước 2). Ghi đúng tên file và tên hàm.

### 2.1. Luồng hiện tại

```
<METHOD> /api/...                 (presentation)
  → XxxController.method()
  → XxxService.method()           (business)  ← logic chính ở đây
      → YyyService.method()
  → XxxRepository.method()        (dataaccess)
  → bảng: xxx, yyy
```

### 2.2. File sẽ phải sửa / tạo

| File | Sửa hay tạo mới | Sửa cái gì |
|---|---|---|
| `src/.../XxxService` | Sửa | Thêm kiểm tra ... trong hàm `...` |
| `src/.../XxxRepository` | Sửa | Thêm truy vấn `...` |
| `db/schema.sql` | Sửa | Thêm cột ... |
| `test/.../XxxServiceTest` | Sửa | Thêm test cho các trường hợp ở mục 6 |

### 2.3. File KHÔNG được đụng vào

> Rất quan trọng khi giao cho AI.

- `...`

### 2.4. Quy ước code phải theo

> Lấy từ Phần B của CLAUDE.md, chỉ ghi những quy ước liên quan tới ticket này.
> Ví dụ: quy tắc nghiệp vụ viết trong service, không viết trong entity; lỗi nghiệp vụ ném `DomainException("MA_LOI", "thông báo")`.

- ...

---

## 3. Data Model Change — thay đổi database

> Không có thay đổi thì ghi "Không có".

### 3.1. Thay đổi bảng

| Bảng | Thay đổi | Cột | Kiểu | Null? | Mặc định | Ràng buộc / Ghi chú |
|---|---|---|---|---|---|---|
| `voucher` | Thêm cột | `min_account_age_days` | `integer` | Có | `NULL` | `CHECK (>= 0)`. NULL = không giới hạn |
| ... | | | | | | |

### 3.2. SQL

> SQL người dùng phải tự chạy, ghi rõ tài khoản database cần dùng. Chạy lại nhiều lần không lỗi.

```sql
ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...;
```

### 3.3. Dữ liệu cũ

> Các dòng đang có trong bảng sẽ nhận giá trị gì? Có cần cập nhật dữ liệu cũ không?

...

### 3.4. Entity / DTO cần cập nhật

| Lớp | Field mới | Kiểu |
|---|---|---|
| `Voucher` | `minAccountAgeDays` | `Integer` |

---

## 4. API Contract

> Mỗi endpoint mới hoặc bị thay đổi viết một khối, ghi rõ ai được gọi. Không đổi API thì ghi "Không có".

### `<METHOD> /api/...`

**Request**
```json
{
  "field": "kiểu — bắt buộc? — giới hạn"
}
```

**Response thành công** — `200 / 201`
```json
{
}
```

**Response lỗi**

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Thiếu field / sai định dạng |
| 404 | `..._NOT_FOUND` | ... |
| 409 | `...` | ... |

---

## 5. Logic Flow — các bước xử lý

> Viết theo thứ tự từ lúc nhận request tới lúc trả response. Mỗi bước ghi rõ nằm ở hàm nào.
> Chỗ nào rẽ nhánh thì ghi điều kiện và kết quả của nhánh đó.

1. `Controller` nhận request, kiểm tra dữ liệu đầu vào.
2. `Service.xxx()` mở transaction.
3. Tải ... từ DB. Không có → ném `..._NOT_FOUND`.
4. Kiểm tra quy tắc:
   - Nếu ... → ném `...`.
   - Nếu ... → ...
5. Tính ... theo công thức: `...`
6. Lưu ... (bảng ...).
7. Gọi ... (thông báo, cổng thanh toán...).
8. Commit, trả về ...

### 5.1. Quy tắc nghiệp vụ / công thức

> Viết công thức và ví dụ có số cụ thể. Nhờ có số mà AI kiểm tra được mình tính đúng hay sai.

| Quy tắc | Công thức | Ví dụ |
|---|---|---|
| ... | ... | Đầu vào ... → kết quả ... |

### 5.2. Transaction và đồng thời

> Những bước nào phải nằm chung một transaction? Có cần khóa dòng (`FOR UPDATE`) không? Hai request cùng lúc thì chuyện gì xảy ra?

...

---

## 6. Edge Cases — mục quan trọng nhất

> Mỗi dòng là một tình huống, kèm kết quả mong đợi rõ ràng (mã lỗi, trạng thái, dữ liệu trong DB).
> Viết xong mục này thì nó chính là danh sách test case.

| # | Tình huống | Kết quả mong đợi | Dữ liệu sau đó |
|---|---|---|---|
| E1 | Chưa đăng nhập / không có quyền | `401` / `403` | Không đổi gì |
| E2 | Dữ liệu đầu vào thiếu, sai định dạng, số âm, chuỗi rỗng | `400 VALIDATION_ERROR` | Không đổi gì |
| E3 | Đối tượng không tồn tại | `404 ..._NOT_FOUND` | Không đổi gì |
| E4 | Đối tượng ở trạng thái không cho phép (đã hủy, đã hết hạn...) | `409 ...` | Không đổi gì |
| E5 | Giá trị nằm đúng ở biên (đúng 30 ngày, đúng 500.000đ, đúng giờ hết hạn) | ... (ghi rõ `>` hay `>=`) | ... |
| E6 | Hết hạn đúng lúc đang xử lý | ... | ... |
| E7 | Hai request cùng lúc cho cùng một tài nguyên | Một cái thành công, cái kia `409` | Chỉ một bản ghi |
| E8 | Gửi lặp cùng một request (bấm 2 lần, retry) | Không tạo trùng | ... |
| E9 | DB lỗi giữa chừng | Rollback toàn bộ, trả `500` | Như trước khi gọi |
| E10 | Dịch vụ ngoài (cổng thanh toán, SMS) lỗi hoặc timeout | ... | ... |
| E11 | Dữ liệu cũ (tạo trước khi có tính năng này) | ... | ... |
| ... | | | |

---

## 7. Tiêu chí hoàn thành (Acceptance Criteria)

- [ ] Mọi edge case ở mục 6 đều có test và test qua.
- [ ] Lệnh test của dự án qua toàn bộ, kể cả các test cũ (ghi dòng tổng kết thật, ví dụ `Tests run: N, Failures: 0`).
- [ ] Chạy lại SQL ở mục 3.2 nhiều lần không lỗi (dùng `IF NOT EXISTS`).
- [ ] Không sửa file nào ngoài danh sách ở mục 2.2.
- [ ] Đúng quy ước ở mục 2.4.
- [ ] Đã cập nhật `checklist-use-case.md`.
- [ ] ...

### 7.1. Test cần viết

| Test | Loại | Kiểm tra edge case |
|---|---|---|
| `XxxServiceTest.rejectsWhen...` | Unit | E4 |
| `XxxIntegrationTest....` | Integration | E7 |

### 7.2. Kiểm tra tay

```bash
curl -X POST http://localhost:8080/api/... -H "Content-Type: application/json" -d '{ }'
```

Kết quả mong đợi: ...

---

## 8. Sau khi AI làm xong — tự đánh giá spec

> Điền sau khi xem kết quả. Mục này giúp lần sau viết spec tốt hơn.

| Câu hỏi | Trả lời |
|---|---|
| Code chạy đúng ngay lần đầu? | Có / Không |
| Phải chat lại mấy lần để sửa? | ... |
| AI làm sai ở đâu? | ... |
| Spec bị hở ở mục nào (thiếu edge case, mơ hồ, thiếu tên file...)? | ... (kèm quyết định AI tự đưa ra; SRS / use case nào cần cập nhật) |
| Lần sau cần bổ sung gì vào template? | ... |

---

## Checklist

- [ ] Mục 2 có tên file và tên hàm thật, đã mở code ra kiểm tra.
- [ ] Mục 3 ghi đủ kiểu dữ liệu, null, mặc định, dữ liệu cũ.
- [ ] Mục 5 không có câu kiểu "xử lý cho phù hợp" hay "kiểm tra hợp lệ" mà không nói kiểm tra cái gì.
- [ ] Mục 6 có ít nhất: quyền, đầu vào sai, không tồn tại, sai trạng thái, giá trị ở biên, đồng thời, lỗi giữa chừng.
- [ ] Mỗi edge case có kết quả cụ thể: mã HTTP + mã lỗi + trạng thái dữ liệu.
- [ ] Đã ghi mục "Ngoài phạm vi" và "File không được đụng".
