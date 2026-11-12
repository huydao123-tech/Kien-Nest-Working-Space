# SPEC: <Tên tính năng / tên bug>

> **Cách dùng template này**
> - Chép file này ra file mới, ví dụ `docs/specs/voucher-gioi-han-khach-cu.md`, rồi điền vào từng mục.
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

...

### 1.1. Bug (chỉ điền nếu là sửa lỗi)


### 1.2. Ngoài phạm vi (Out of scope)


## 2. Context — code hiện tại liên quan(Luồng hiện tại)




## 3. Data Model Change — thay đổi database

> Không có thay đổi thì ghi "Không có".

### 3.1. Thay đổi bảng



### 3.2. SQL


### 3.3. Dữ liệu cũ

> Các dòng đang có trong bảng sẽ nhận giá trị gì? Có cần cập nhật dữ liệu cũ không?

...

### 3.4. Entity / DTO cần cập nhật

| Lớp | Field mới | Kiểu Java |
|---|---|---|
| `Voucher` | `minAccountAgeDays` | `Integer` |

---

## 4. API Contract

> Mỗi endpoint mới hoặc bị thay đổi viết một khối. Không đổi API thì ghi "Không có".

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

1. `Controller` nhận request, kiểm tra dữ liệu đầu vào (`@Valid`).
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

