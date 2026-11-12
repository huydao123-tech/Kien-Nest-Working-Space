# Tiến độ yêu cầu chức năng — Co-Working Space

Đối chiếu từng yêu cầu FR trong `docs/SRS.md` (mục 3) với code backend hiện tại. Dự án chưa có giao diện: phần nào backend đã trả đủ dữ liệu thì tính là xong, phần còn thiếu ghi ở cột **Còn thiếu**.

- Trạng thái: ✅ Hoàn thành · 🟡 Làm một phần · ⬜ Chưa làm
- Ưu tiên: M = bắt buộc, S = nên có, C = có thì tốt.
- **Cập nhật lần cuối: 2026-11-10**, theo code sau ticket `notification.md` (Prompt 1). Lịch sử ở cuối file.

## Tổng quan

| Chỉ số | Giá trị |
|---|---|
| Yêu cầu chức năng | 76 |
| ✅ Hoàn thành | 31 |
| 🟡 Làm một phần | 25 |
| ⬜ Chưa làm | 20 |
| Bảng database code đang dùng | 27 / 36 |

| Nhóm | ✅ | 🟡 | ⬜ | Tổng |
|---|---:|---:|---:|---:|
| [AUTH · Tài khoản & định danh](#auth--tài-khoản--định-danh) | 4 | 1 | 0 | 5 |
| [SRCH · Tra cứu chỗ trống](#srch--tra-cứu-chỗ-trống) | 4 | 1 | 1 | 6 |
| [BOOK · Đặt chỗ](#book--đặt-chỗ) | 7 | 2 | 0 | 9 |
| [PROMO · Khuyến mãi & Membership](#promo--khuyến-mãi--membership) | 3 | 0 | 0 | 3 |
| [PAY · Thanh toán](#pay--thanh-toán) | 3 | 6 | 0 | 9 |
| [CHK · Check-in / Sử dụng / Check-out](#chk--check-in--sử-dụng--check-out) | 1 | 5 | 3 | 9 |
| [SVC · Dịch vụ tại chỗ & hỗ trợ](#svc--dịch-vụ-tại-chỗ--hỗ-trợ) | 0 | 0 | 4 | 4 |
| [OPS · Vận hành tại quầy](#ops--vận-hành-tại-quầy) | 0 | 4 | 5 | 9 |
| [ADM · Quản trị](#adm--quản-trị) | 5 | 3 | 0 | 8 |
| [RPT · Báo cáo](#rpt--báo-cáo) | 0 | 0 | 5 | 5 |
| [NOTI · Thông báo & tác vụ tự động](#noti--thông-báo--tác-vụ-tự-động) | 4 | 3 | 2 | 9 |

---

## AUTH · Tài khoản & định danh

Bảng: `customer` ✔, `staff` ✔, `otp_code` ✔

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-AUTH-01 | M | UC03 | ✅ | Khách vãng lai đặt chỗ bằng họ tên, SĐT, email, không cần tài khoản. | Nhập liên hệ khi giữ chỗ; tìm khách theo SĐT, chưa có thì tạo mới (`CustomerService`). `POST /api/bookings` vẫn công khai. | |
| FR-AUTH-02 | M | UC01 | ✅ | Đăng ký Member bằng SĐT (OTP) hoặc email. | `POST /api/auth/otp`, `/register/phone`, `/register/email`, `/login`, `/login/otp`; mật khẩu và OTP lưu BCrypt; JWT. | Gửi SMS thật (OTP hiện chỉ ghi log). |
| FR-AUTH-03 | S | UC01 | ✅ | Tự liên kết đơn vãng lai vào tài khoản khi đăng ký cùng SĐT. | Đăng ký SĐT + OTP dùng lại dòng `customer` cũ → mọi đơn cũ thuộc tài khoản. Đăng ký email không được nhận dòng vãng lai. | |
| FR-AUTH-04 | M | UC23 | 🟡 | Nhân viên đăng nhập nội bộ, phân quyền theo vai trò và chi nhánh. | `POST /api/auth/staff/login`; RBAC 4 vai trò bằng `@PreAuthorize`; tài khoản bị khóa không đăng nhập được. | Phân quyền theo **chi nhánh** (token có `branchId` nhưng API chưa lọc); thu hồi token. |
| FR-AUTH-05 | M | UC01 | ✅ | Member xem/sửa hồ sơ, xem số giờ Membership còn lại. | `GET /api/auth/me` kèm `membership` (tổng phút còn dùng được + từng gói); sửa hồ sơ `PUT /api/auth/me`; đổi mật khẩu `POST /api/auth/me/password`; quên mật khẩu bằng OTP `POST /api/auth/password/reset`. Test `AuthServiceTest`, `MembershipServiceTest`, `AuthIntegrationTest`. | Đổi SĐT của tài khoản (cần OTP SĐT mới); thu hồi token cũ sau khi đổi mật khẩu. |

## SRCH · Tra cứu chỗ trống

Bảng: `space_resource` ✔, `resource_type` ✔, `price_rule` ✔, `booking` ✔, `branch` ✔, `zone` ✔, `amenity` ✔, `resource_amenity` ✔

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-SRCH-01 | M | UC02 | 🟡 | Tìm theo chi nhánh, loại không gian, ngày, giờ bắt đầu/kết thúc hoặc tổng số giờ, số người. | `GET /api/availability` theo chi nhánh, loại, giờ bắt đầu/kết thúc, số người; kiểm tra giờ mở cửa, quá khứ, thời lượng tối thiểu (`BookingWindowPolicy`). | Nhập theo tổng số giờ. |
| FR-SRCH-02 | M | UC02 | ✅ | Sơ đồ mặt bằng với trạng thái từng vị trí: Trống / Đang giữ / Đã đặt / Bảo trì. | `GET /api/branches/{id}/floor-plan`: tọa độ, ảnh sơ đồ khu vực, trạng thái từng vị trí. | Giao diện vẽ sơ đồ (frontend). |
| FR-SRCH-03 | S | UC02 | ✅ | Lọc theo tiện ích: yên tĩnh, ổ cắm, sức chứa, màn hình/máy chiếu. | Tham số `amenities` (vị trí phải có đủ), `partySize`; `GET /api/amenities`. | Lọc theo số lượng tiện ích. |
| FR-SRCH-04 | M | UC02 | ✅ | Hiện giá dự kiến theo bảng giá hiệu lực (cao điểm/thấp điểm) trước khi đặt. | Mỗi chỗ trống kèm giá theo block 30 phút (`PricingService`). | |
| FR-SRCH-05 | S | UC02 | ⬜ | Hết chỗ thì gợi ý khung giờ lân cận hoặc vị trí tương đương. | | Hiện chỉ trả danh sách rỗng. |
| FR-SRCH-06 | M | UC02 | ✅ | Chỉ trả vị trí có sức chứa ≥ số người (BR-14). | Lọc capacity ≥ số người. | |

## BOOK · Đặt chỗ

Bảng: `booking` ✔, `booking_charge` ✔, `booking_status_history` ✔, `customer` ✔, `space_resource` ✔, `addon` ✔, `booking_addon` ✔

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-BOOK-01 | M | UC03 | 🟡 | Chọn vị trí cụ thể, hoặc để hệ thống tự gán với Hot Desk. | Khách gửi `resourceId`. | Tự gán chỗ cho Hot Desk. | X
| FR-BOOK-02 | M | UC03 | ✅ | Chọn add-on kèm số lượng; kiểm tra tồn kho add-on theo khung giờ. | `addons` trong `POST /api/bookings`; khóa dòng add-on, tính lúc dùng nhiều nhất trong khung giờ (`AddonService`); `GET /api/addons?branchId=`. | Thêm / bớt add-on sau khi đặt. |
| FR-BOOK-03 | M | UC03 | ✅ | Tạo đơn Held và soft lock vị trí trong thời gian giữ chỗ (BR-02). | Đơn HELD giữ 10 phút (`booking.hold-duration`). | |
| FR-BOOK-04 | M | UC03 | ✅ | Không có hai đơn hiệu lực cùng giữ một vị trí ở khung giờ giao nhau (BR-01). | `SELECT … FOR UPDATE` + `ex_booking_no_overlap`; test đặt đồng thời. | |
| FR-BOOK-05 | S | UC03 | 🟡 | Đồng hồ đếm ngược thời gian giữ chỗ. | API trả `holdExpiresAt`. | Hiển thị (frontend). |
| FR-BOOK-06 | M | UC03, UC05 | ✅ | Tổng tiền = thuê + add-on − voucher − khấu trừ membership; hiện từng dòng. | Mọi khoản là dòng `booking_charge` (`RENTAL`, `ADDON`, `VOUCHER_DISCOUNT`, `MEMBERSHIP_DEDUCTION`...); `Booking.getTotal()`. | |
| FR-BOOK-07 | M | UC06 | ✅ | Xem danh sách đơn của mình, chi tiết đơn, mã QR. | `GET /api/me/bookings`, `GET /api/bookings/{id}` (khách chỉ thấy đơn của mình); nhân viên `GET /api/bookings` lọc theo khách. | Khách vãng lai (không tài khoản) chưa có cách xem đơn. |
| FR-BOOK-08 | M | UC06 | ✅ | Hủy đơn Confirmed theo chính sách hủy, tính tiền hoàn. | Hủy trước ≥ 2 giờ (`booking.change-notice`), hoàn 100%, trả lượt voucher / phút membership, ghi `refund` REQUESTED. Chính sách thay BR-15 theo ticket `booking.md`. | Hoàn tiền thật qua cổng (xem FR-PAY-08); `docs/SRS.md` BR-15 chưa cập nhật. |
| FR-BOOK-09 | C | UC06 | ✅ | Đổi khung giờ đơn chưa check-in, tính chênh lệch. | `POST /api/bookings/{id}/reschedule`: trước ≥ 2 giờ, đổi được vị trí / khung giờ / thời lượng; rẻ hơn → đổi ngay, hoàn chênh lệch; đắt hơn → giữ chỗ mới 10 phút chờ trả phần chênh (`POST /api/bookings/{id}/payments`), quá hạn / lỗi thì giữ chỗ cũ (job). Test `BookingServiceTest`, `BookingExtrasIntegrationTest`. | Hoàn tiền thật qua cổng (FR-PAY-08). |

## PROMO · Khuyến mãi & Membership

Bảng: `voucher` ✔, `voucher_resource_type` ✔, `voucher_redemption` ✔, `membership_plan` ✔, `membership_plan_resource_type` ✔, `membership_pass` ✔, `membership_usage` ✔

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-PROMO-01 | M | UC05 | ✅ | Nhập voucher; kiểm tra hiệu lực, thời hạn, điều kiện, số lượt (BR-12). | `voucherCode` khi giữ chỗ; kiểm trạng thái, hạn, tổng lượt, lượt / khách, loại chỗ, giá trị tối thiểu; giảm %, số tiền, phút miễn phí (`VoucherService`). | |
| FR-PROMO-02 | M | UC05 | ✅ | Khấu trừ giờ Membership, phần vượt trả giá thường (BR-13). | `useMembership` (khách đăng nhập); trừ theo block, gói hết hạn sớm nhất trước; phủ hết thì xác nhận ngay (`MembershipService`). Không dùng chung với voucher (Q8). | Khách mua gói qua API. |
| FR-PROMO-03 | M | UC05 | ✅ | Giữ lượt voucher theo soft lock, trả lại khi đơn hết hạn/thất bại. | HELD khi giữ chỗ, USED khi xác nhận, RELEASED khi hết hạn / thất bại / hủy. | |

## PAY · Thanh toán

Bảng: `payment` ✔, `booking` ✔, `booking_charge` ✔, `booking_status_history` ✔, `refund` ✔, `invoice`, `invoice_line`

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-PAY-01 | M | UC04 | 🟡 | Hỗ trợ VietQR, ví điện tử, thẻ qua cổng thanh toán. | Interface `PaymentGateway` + cổng sandbox. | Cổng thật (VNPay / MoMo / PayOS). |
| FR-PAY-02 | M | UC04, UC25 | ✅ | Idempotency key; webhook trùng không gây hiệu ứng kép. | Idempotency key unique; dùng lại giao dịch đang mở; bỏ qua webhook đã xử lý. | |
| FR-PAY-03 | M | UC04 | ✅ | Xác thực chữ ký webhook trước khi cập nhật. | HMAC-SHA256 header `X-Signature`, sai → 401. | (Nên có) log cảnh báo bảo mật. |
| FR-PAY-04 | M | UC04 | 🟡 | Webhook thành công: Confirmed, khóa vị trí, mã đơn + QR, hóa đơn, lên lịch thông báo. | CONFIRMED, giữ chỗ, mã đơn + QR, nhắn tin, kiểm số tiền; voucher / membership chuyển USED. | Hóa đơn (`invoice`); lịch nhắc (`notification_job`). |
| FR-PAY-05 | M | UC04, UC24 | ✅ | Thất bại / hết hạn: Failed/Expired, nhả vị trí và lượt voucher. | FAILED / EXPIRED, nhả chỗ, trả lượt voucher và phút membership, nhắn tin. | |
| FR-PAY-06 | M | UC25 | 🟡 | Đối soát: chủ động hỏi cổng và nhận webhook trễ (BR-11). | Nhận webhook trễ. | Job hỏi lại cổng; trạng thái UNCONFIRMED. |
| FR-PAY-07 | M | UC25 | 🟡 | Webhook trễ cho đơn Expired: còn chỗ thì Confirmed, hết chỗ thì đề xuất chỗ khác hoặc chờ hoàn tiền. | Còn trống → CONFIRMED; bị chiếm hoặc đơn có voucher / membership → REFUND_PENDING. | Đề xuất vị trí tương đương. |
| FR-PAY-08 | M | UC25 | 🟡 | Gọi API hoàn tiền, theo dõi tới khi xong, cảnh báo nếu lỗi. | Hủy / đổi giờ rẻ hơn ghi `refund` REQUESTED vào giao dịch thành công (`RefundService`); quản lý xem `GET /api/refunds` và xác nhận hoàn tay xong `POST /api/refunds/{id}/complete` → REFUNDED + nhắn khách. | Gọi API hoàn tiền của cổng; đơn REFUND_PENDING chưa ghi `refund` và chưa chuyển REFUNDED. |
| FR-PAY-09 | M | UC13, UC16 | 🟡 | Lễ tân ghi nhận thanh toán tại quầy. | `POST /api/bookings/{id}/settle` (lễ tân, quản lý, admin). | Chọn hình thức thu; ghi `payment`; walk-in. |

## CHK · Check-in / Sử dụng / Check-out

Bảng: `booking` ✔, `booking_charge` ✔, `booking_status_history` ✔, `space_resource` ✔, `service_order`

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-CHK-01 | M | UC07 | 🟡 | Check-in bằng QR; mã hợp lệ, đúng chi nhánh, trong cửa sổ cho phép (BR-17). | Tìm theo QR, đơn CONFIRMED, sớm nhất 15 phút; chỉ nhân viên quầy gọi. | Kiểm tra đúng chi nhánh. |
| FR-CHK-02 | M | UC07 | 🟡 | Ghi giờ thực, vị trí Occupied, mở cửa, cấp Wi-Fi. | Ghi giờ check-in, CHECKED_IN. | Mở cửa, tài khoản Wi-Fi. |
| FR-CHK-03 | S | UC07, UC10 | ⬜ | Cốc nước miễn phí đầu tiên (BR-10). | | Chưa làm. |
| FR-CHK-04 | M | UC08 | 🟡 | Gia hạn: kiểm tra chỗ trống, báo giá, thu tiền, cập nhật giờ kết thúc (BR-07). | Kiểm trống, tính giá, khoản EXTENSION. | Báo giá để xác nhận; thu tiền ngay. |
| FR-CHK-05 | S | UC08 | ⬜ | Không gia hạn được thì đề xuất chỗ tương đương. | Trả lỗi `EXTENSION_UNAVAILABLE`. | Gợi ý chỗ khác. |
| FR-CHK-06 | M | UC09 | 🟡 | Check-out bằng QR; phụ phí overstay (BR-06) và dịch vụ phát sinh. | Ghi giờ check-out, phụ phí sau 5 phút ân hạn. | Tiền dịch vụ phát sinh. |
| FR-CHK-07 | M | UC09, UC16 | 🟡 | Có khoản phát sinh thì trả online hoặc tại quầy trước khi Completed. | Còn nợ → CHECKED_OUT, thu tại quầy → COMPLETED. | Trả online. |
| FR-CHK-08 | S | UC09 | ⬜ | Sau check-out: vị trí Cần dọn dẹp, thu hồi Wi-Fi. | | Chưa làm. |
| FR-CHK-09 | S | UC24 | ✅ | Không check-in sau ân hạn → No-show. | `BookingService.markNoShow` + job 60 giây: quá min(30 phút, 50% thời lượng) → NO_SHOW, nhả chỗ, không hoàn tiền, nhắn khách (`booking.no-show-grace`, `no-show-grace-percent`). | Check-in sau mốc chờ nhưng trước lượt quét job vẫn được nhận. |

## SVC · Dịch vụ tại chỗ & hỗ trợ

Bảng: `menu_category` ✔, `menu_item` ✔, `customer` ✔, `service_order`, `service_order_item`, `ticket`, `ticket_attachment`

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-SVC-01 | S | UC10 | ⬜ | Gọi đồ ăn/uống trên app, chuyển tới quầy, cộng vào hóa đơn. | Thực đơn quản lý được qua API (FR-ADM-07). | Gọi món (`service_order`). |
| FR-SVC-02 | M | UC11 | ⬜ | Gửi yêu cầu hỗ trợ/báo sự cố kèm mô tả, ảnh; tạo ticket. | | Toàn bộ. |
| FR-SVC-03 | S | UC11, UC17 | ⬜ | Ticket có mức ưu tiên; khách theo dõi trạng thái. | | Phụ thuộc SVC-02. |
| FR-SVC-04 | S | UC12 | ⬜ | Member chọn mốc cảnh báo 10/15/30 phút và kênh nhận. | Nhân viên sửa được qua `PUT /api/admin/customers/{id}`. | API cho chính khách cấu hình. |

## OPS · Vận hành tại quầy

Bảng: `booking` ✔, `booking_charge` ✔, `space_resource` ✔, `payment` ✔, `service_order`, `ticket`, `compensation`, `audit_log`

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-OPS-01 | M | UC13 | ⬜ | Lễ tân tạo đơn walk-in, cùng quy tắc chống trùng. | Tái dùng được `BookingService.hold`; lễ tân tạo hồ sơ khách được. | API walk-in + thu tiền tại quầy. |
| FR-OPS-02 | M | UC14 | 🟡 | Lễ tân check-in/out hộ bằng QR hoặc tìm theo mã đơn/SĐT. | Quét QR; tìm đơn theo SĐT / id khách (`GET /api/bookings?phone=`). | Tìm theo mã đơn BK…. |
| FR-OPS-03 | M | UC14 | ⬜ | Màn hình vận hành: sơ đồ trạng thái, sắp đến, đang dùng, sắp hết giờ, overstay. | | API tổng hợp + giao diện. |
| FR-OPS-04 | M | UC15 | ⬜ | Đổi chỗ Swap/Upgrade; lỗi do trung tâm thì miễn chênh lệch (BR-08). | | Chưa làm. |
| FR-OPS-05 | M | UC16 | 🟡 | Thu phụ phí overstay và dịch vụ ngoài, ghi hóa đơn. | Thu số còn nợ. | Dịch vụ tại quầy; hóa đơn. |
| FR-OPS-06 | M | UC17 | 🟡 | Cập nhật ticket; đánh dấu Bảo trì chặn đặt mới (BR-09); mở lại. | Bảo trì / mở lại (kỹ thuật, quản lý, admin). | Ticket sự cố. |
| FR-OPS-07 | M | UC17 | 🟡 | Khi chuyển Bảo trì, liệt kê booking tương lai bị ảnh hưởng và báo khách. | `POST /api/resources/{id}/maintenance` nhắn khách của mọi đơn CONFIRMED chưa kết thúc trên vị trí; bấm lần hai không nhắn lại. | Trả danh sách đơn bị ảnh hưởng cho quầy; sắp xếp lại / hoàn tiền (UC15). |
| FR-OPS-08 | S | UC18 | ⬜ | Đền bù theo hạn mức; vượt hạn mức cần Quản lý duyệt. | `staff.compensation_limit` sửa được qua API. | Toàn bộ luồng đền bù. |
| FR-OPS-09 | M | UC15, UC16, UC18 | ⬜ | Ghi audit log thao tác điều chỉnh giá, đổi chỗ, miễn phí, đền bù. | Lịch sử trạng thái đơn; đã có đăng nhập nên biết ai làm. | Ghi bảng `audit_log`. |

## ADM · Quản trị

Bảng: `branch` ✔, `zone` ✔, `space_resource` ✔, `resource_type` ✔, `amenity` ✔, `resource_amenity` ✔, `price_rule` ✔, `addon` ✔, `menu_category` ✔, `menu_item` ✔, `voucher` ✔, `membership_plan` ✔, `staff` ✔

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-ADM-01 | M | UC19 | ✅ | Quản lý chi nhánh, khu vực, sơ đồ mặt bằng, thuộc tính vị trí. | CRUD `/api/admin/branches`, `/zones`, `/resources` (tọa độ, tiện ích), `/amenities`, `/resource-types`. | Giao diện kéo thả sơ đồ. |
| FR-ADM-02 | M | UC19 | 🟡 | Bật/tắt khai thác vị trí (Inactive), Bảo trì theo khoảng thời gian. | Đổi ACTIVE / MAINTENANCE / INACTIVE qua API. | Bảo trì theo khoảng thời gian. |
| FR-ADM-03 | M | UC20 | 🟡 | Bảng giá theo loại × khung giờ × ngày trong tuần/ngày lễ, có ngày hiệu lực. | CRUD `/api/admin/price-rules`, chặn khung giá chồng nhau. | Ngày lễ; ngày hiệu lực / phiên bản giá. |
| FR-ADM-04 | M | UC20 | 🟡 | Cấu hình block giờ, giữ chỗ, hệ số phụ phí, ân hạn, chính sách hủy. | Tham số trong `application.yml`, kể cả hạn hủy / đổi giờ `booking.change-notice`. | Sửa khi đang chạy (lưu DB + API). |
| FR-ADM-05 | M | UC21 | ✅ | Quản lý voucher: mã, loại giảm, điều kiện, thời hạn, lượt. | CRUD `/api/admin/vouchers` (loại chỗ áp dụng, tổng lượt, lượt / khách). | |
| FR-ADM-06 | S | UC21 | ✅ | Quản lý gói Membership. | CRUD `/api/admin/membership-plans`. | |
| FR-ADM-07 | M | UC19 | ✅ | Quản lý add-on, thực đơn và giá. | CRUD `/api/admin/addons`, `/menu-categories`, `/menu-items`. | |
| FR-ADM-08 | M | UC23 | ✅ | Quản lý tài khoản nhân viên, vai trò, chi nhánh. | CRUD `/api/admin/staff` (chỉ ADMIN), khóa / mở khóa. | |

## RPT · Báo cáo

Bảng: `booking` ✔, `booking_charge` ✔, `payment` ✔, `refund` ✔, `voucher_redemption` ✔, `membership_usage` ✔, `compensation`, `ticket`, `service_order`

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-RPT-01 | M | UC22 | ⬜ | Tỷ lệ lấp đầy theo chi nhánh/loại/khung giờ/ngày, heatmap. | | Chưa làm. |
| FR-RPT-02 | M | UC22 | ⬜ | Doanh thu theo nguồn, trừ giảm giá, hoàn tiền, đền bù. | Từng khoản (thuê, add-on, giảm giá) đã lưu ở `booking_charge`. | API báo cáo. |
| FR-RPT-03 | S | UC22 | ⬜ | Báo cáo vận hành: no-show, overstay, ticket, walk-in. | | Chưa làm. |
| FR-RPT-04 | S | UC22 | ⬜ | Hiệu quả voucher, tiêu thụ membership. | Dữ liệu ở `voucher_redemption`, `membership_usage`. | API báo cáo. |
| FR-RPT-05 | S | UC22 | ⬜ | Xuất Excel/PDF. | | Chưa làm. |

## NOTI · Thông báo & tác vụ tự động

Bảng: `booking` ✔, `customer` ✔, `staff` ✔, `refund` ✔, `notification_job` ✔

| Mã | Ưu tiên | UC | Trạng thái | Yêu cầu | Đã có | Còn thiếu |
|---|---|---|---|---|---|---|
| FR-NOTI-01 | M | UC26 | 🟡 | Gửi mã đơn + QR ngay khi Confirmed qua kênh khách chọn. | Gửi tin khi xác nhận (kể cả đơn membership tổng 0). | Kênh thật SMS / Zalo / Email / Push (đang ghi log). |
| FR-NOTI-02 | M | UC26 | ⬜ | Nhắc lịch trước 30 phút. | | Lịch thông báo + job. |
| FR-NOTI-03 | M | UC26 | ✅ | Cảnh báo sắp hết giờ trước 15 phút. | Job 30 giây: đơn CHECKED_IN còn ≤ `customer.notify_before_minutes` (10/15/30) phút → nhắn kèm lời nhắc gia hạn; mỗi mốc gửi một lần (ghi `notification_job` END_WARNING SENT). | Nút gia hạn nhanh (giao diện); kênh thật SMS / Zalo (đang ghi log). |
| FR-NOTI-04 | M | UC26 | 🟡 | Báo khi thất bại/hết hạn, đổi chỗ, hoàn tiền, bảo trì, đền bù. | Thất bại, hết hạn, hủy (kèm số tiền hoàn), đổi chỗ / khung giờ, hủy yêu cầu đổi chỗ, tiền về trễ, hoàn tiền xong, vị trí bảo trì, no-show. | Đền bù (chờ luồng UC18). |
| FR-NOTI-05 | M | UC26 | 🟡 | Cập nhật/hủy lịch thông báo khi đơn thay đổi. | Cảnh báo hết giờ tính lại từ giờ kết thúc hiện tại mỗi lượt quét: gia hạn → mốc mới; check-out / overstay → không gửi. | Nhắc lịch trước 30 phút (NOTI-02) chưa có. |
| FR-NOTI-06 | S | UC26 | ⬜ | Gửi lại qua kênh dự phòng khi kênh chính lỗi. | | Chưa làm. |
| FR-SYS-01 | M | UC24 | ✅ | Tự hết hạn đơn Held quá giờ giữ chỗ, nhả vị trí. | Job 30 giây: EXPIRED, nhả chỗ, trả lượt voucher / phút, nhắn tin. | |
| FR-SYS-02 | M | UC24 | ✅ | Tự chuyển Overstay khi quá giờ, báo quầy. | Job 60 giây chuyển OVERSTAY rồi nhắn mọi lễ tân ACTIVE của chi nhánh (theo email). | Kênh thật (đang ghi log). |
| FR-SYS-03 | S | UC24 | ✅ | Tự đánh dấu No-show (BR-16). | Như FR-CHK-09. | |

---

## Bảng database

✔ = code đang dùng (có entity + được service đọc / ghi). **27 / 36** bảng.

| Bảng | Dùng | Nội dung |
|---|---|---|
| `customer` | ✔ | Khách hàng (vãng lai + Member), tìm theo SĐT, mật khẩu |
| `staff` | ✔ | Nhân viên, vai trò, chi nhánh, đăng nhập |
| `otp_code` | ✔ | Mã OTP đã băm |
| `branch` | ✔ | Chi nhánh, giờ mở cửa |
| `zone` | ✔ | Khu vực trong chi nhánh, ảnh mặt bằng |
| `resource_type` | ✔ | Danh mục loại không gian |
| `space_resource` | ✔ | Chỗ ngồi / phòng: sức chứa, trạng thái, tọa độ |
| `amenity` | ✔ | Danh mục tiện ích |
| `resource_amenity` | ✔ | Chỗ nào có tiện ích nào |
| `price_rule` | ✔ | Giá mỗi block theo loại × khung giờ × loại ngày |
| `booking` | ✔ | Đơn đặt chỗ |
| `booking_charge` | ✔ | Từng khoản tiền của đơn (thuê, add-on, gia hạn, phụ phí, giảm trừ) |
| `booking_status_history` | ✔ | Lịch sử đổi trạng thái đơn |
| `booking_addon` | ✔ | Add-on khách chọn cho đơn |
| `addon` | ✔ | Danh mục add-on, tồn kho |
| `payment` | ✔ | Giao dịch thanh toán |
| `refund` | ✔ | Yêu cầu hoàn tiền: ghi REQUESTED, quản lý xác nhận REFUNDED |
| `voucher` | ✔ | Mã giảm giá, điều kiện, số lượt |
| `voucher_resource_type` | ✔ | Voucher áp dụng cho loại không gian nào |
| `voucher_redemption` | ✔ | Lượt dùng voucher (giữ / đã dùng / trả lại) |
| `membership_plan` | ✔ | Gói Membership |
| `membership_plan_resource_type` | ✔ | Gói dùng được cho loại không gian nào |
| `membership_pass` | ✔ | Gói khách đã mua, số phút còn lại |
| `membership_usage` | ✔ | Số phút đã trừ cho từng đơn |
| `menu_category` | ✔ | Nhóm thực đơn (chỉ CRUD) |
| `menu_item` | ✔ | Món ăn / đồ uống (chỉ CRUD) |
| `invoice` | | Hóa đơn / biên lai |
| `invoice_line` | | Các dòng của hóa đơn |
| `service_order` | | Đơn gọi đồ / dịch vụ |
| `service_order_item` | | Các món trong đơn gọi |
| `ticket` | | Ticket sự cố / hỗ trợ |
| `ticket_attachment` | | Ảnh đính kèm ticket |
| `compensation` | | Đền bù |
| `notification_job` | ✔ | Lịch gửi thông báo (đang dùng để chống gửi trùng cảnh báo sắp hết giờ) |
| `audit_log` | | Ai làm gì, khi nào |
| `outbox_event` | | Còn lại từ thời dùng domain event, code không cần — có thể xóa khỏi `schema.sql` |

---

## Lịch sử cập nhật

| Ngày | Ticket / prompt | Thay đổi trạng thái |
|---|---|---|
| 2026-10-31 | — | Bản đầu (dạng HTML `docs/checklist-use-case.html`) |
| 2026-11-01 | `tra-cuu-cho-trong.md` Prompt 1–2 | SRCH-02, SRCH-03 → ✅; SRCH-01 vẫn 🟡 (còn nhập theo tổng số giờ) |
| 2026-11-03 | `authentication.md` Prompt 1 | AUTH-02, AUTH-03 → ✅; AUTH-04, AUTH-05 → 🟡; ADM-01, ADM-05..08 → ✅; ADM-02, ADM-03 thêm CRUD (vẫn 🟡); OPS-02 thêm tìm theo SĐT |
| 2026-11-03 | `booking.md` Prompt 1 | BOOK-02, BOOK-06, BOOK-07, BOOK-08 → ✅; BOOK-09 → 🟡; PROMO-01..03 → ✅; PAY-05 → ✅; PAY-08 → 🟡; NOTI-04 thêm hủy, đổi giờ |
| 2026-11-03 | — | Chuyển sang Markdown ở thư mục gốc `checklist-use-case.md` |
| 2026-11-05 | `authentication.md` (yêu cầu qua chat, không có prompt) | Xóa 101 unit test cũ, viết lại `AuthServiceTest` (81 test). PROMO-01, PROMO-03, PAY-05 → 🟡 (code còn nguyên, thiếu test sau khi xóa) |
| 2026-11-05 | `authentication.md` (yêu cầu qua chat, không có prompt) | `AuthServiceTest` đổi sang assert của `org.junit.Assert`; không đổi trạng thái FR |
| 2026-11-08 | `authentication.md` Prompt 2, `booking.md` Prompt 2, yêu cầu chat "viết unit test cho tất cả các file service" | AUTH-05 → ✅ (sửa hồ sơ, đổi / quên mật khẩu, số phút gói); BOOK-09 → ✅ (đổi vị trí / thời lượng, chỗ đắt hơn giữ chờ trả phần chênh); PROMO-01, PROMO-03, PAY-05 → ✅ (có lại unit test: `VoucherServiceTest`, `BookingServiceTest`, `PaymentServiceTest`). `membership.md` Prompt 1 và `tra-cuu-cho-trong.md` Prompt 2 trống, chưa xử lý |
| 2026-11-10 | `notification.md` Prompt 1 | CHK-09, SYS-03 → ✅ (no-show); NOTI-03 → ✅ (cảnh báo sắp hết giờ); SYS-02 → ✅ (báo lễ tân khi quá giờ); NOTI-05 → 🟡; OPS-07 → 🟡 (nhắn khách khi vị trí bảo trì); NOTI-04 thêm hoàn tiền xong, bảo trì, no-show (vẫn 🟡, thiếu đền bù); PAY-08 thêm API xác nhận hoàn tiền (vẫn 🟡); `notification_job` → ✔ |
