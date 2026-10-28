# Đặc Tả Yêu Cầu Phần Mềm (SRS)
**Dự án:** Hệ thống đặt chỗ Co-Working Space theo giờ
**Phiên bản:** 1.0 — 28/10/2026
**Tài liệu nguồn:** [requirement-gathering.md](../requirement-gathering.md)
**Tài liệu liên quan:** [Đặc tả Use Case](use-cases.md) · [Các sơ đồ](diagrams.md)

> Cấu trúc tài liệu tham khảo theo IEEE 830 / ISO/IEC/IEEE 29148.
> Các nội dung không có trong tài liệu thu thập yêu cầu mà được bổ sung để hệ thống vận hành đầy đủ đều được đánh dấu **[Giả định]** và liệt kê lại ở mục 9 để khách hàng xác nhận.

---

## 1. Giới thiệu

### 1.1. Mục đích
Tài liệu mô tả đầy đủ yêu cầu chức năng, phi chức năng, quy tắc nghiệp vụ và ràng buộc của hệ thống đặt chỗ Co-Working Space theo giờ. Đối tượng đọc: chủ đầu tư, BA, đội phát triển, kiểm thử và vận hành.

### 1.2. Phạm vi sản phẩm
Hệ thống (sau đây gọi là **CWS-Booking**) gồm:
- **Ứng dụng khách hàng** (Web responsive + Mobile App): tìm chỗ, đặt chỗ, thanh toán, check-in/out bằng QR, gia hạn, gọi dịch vụ, báo sự cố.
- **Ứng dụng vận hành tại quầy** (Web cho Lễ tân): đặt chỗ walk-in, hỗ trợ check-in/out, đổi chỗ, thu phụ phí, xử lý sự cố.
- **Trang quản trị** (Web cho Quản lý): cấu hình sơ đồ không gian, bảng giá, khuyến mãi, báo cáo.
- **Dịch vụ nền** (Backend): chống trùng chỗ, giữ chỗ tạm, đối soát thanh toán, lịch thông báo tự động, tính phí theo block giờ.

**Ngoài phạm vi:** kế toán tổng hợp, quản lý nhân sự/chấm công, phần cứng cửa kiểm soát và hạ tầng Wi-Fi (chỉ tích hợp qua API).

### 1.3. Thuật ngữ & viết tắt

| Thuật ngữ | Giải thích |
| :--- | :--- |
| Vị trí (Resource) | Đơn vị cho thuê: một ghế Hot Desk, một bàn Fixed Desk, một Focus Booth, một phòng họp, một không gian sự kiện |
| Hot Desk | Chỗ ngồi linh hoạt, không cố định |
| Fixed Desk | Bàn cố định |
| Focus Booth | Pod cá nhân yên tĩnh |
| Khung giờ (Slot) | Khoảng thời gian [Start, End) gắn với một vị trí |
| Block giờ | Đơn vị tính tiền nhỏ nhất (ví dụ 30 hoặc 60 phút) |
| Soft Lock / Hold | Giữ chỗ tạm trong khi khách thanh toán |
| Double-booking | Một vị trí bị đặt bởi hai đơn trùng khung giờ |
| Overstay | Khách sử dụng quá giờ kết thúc đã đăng ký |
| Walk-in | Khách đến trực tiếp quầy đặt chỗ |
| Webhook | Lời gọi từ cổng thanh toán báo kết quả giao dịch |
| Reconciliation | Đối soát giao dịch giữa hệ thống và cổng thanh toán |
| Occupancy Rate | Tỷ lệ lấp đầy = giờ đã sử dụng / tổng giờ khả dụng |
| Membership Pass | Gói thành viên chứa số giờ trả trước |
| Add-on | Dịch vụ bổ sung: màn hình rời, máy chiếu, bảng trắng, suất ăn/uống |
| FR / NFR / BR | Yêu cầu chức năng / phi chức năng / quy tắc nghiệp vụ |

### 1.4. Tổng quan tài liệu
Mục 2 mô tả tổng quan; mục 3 yêu cầu chức năng; mục 4 quy tắc nghiệp vụ; mục 5 dữ liệu; mục 6 giao diện ngoài; mục 7 phi chức năng; mục 8 ma trận truy vết; mục 9 giả định & câu hỏi mở.

---

## 2. Mô tả tổng quan

### 2.1. Bối cảnh sản phẩm
CWS-Booking là hệ thống độc lập, trung tâm giữa các bên: Khách hàng, Lễ tân, Quản lý và các hệ thống ngoài (Cổng thanh toán, Dịch vụ thông báo, Hệ thống kiểm soát ra vào/Wi-Fi, Nhà cung cấp hóa đơn điện tử). Xem **Context Diagram** tại [diagrams.md](diagrams.md#1-context-diagram).

### 2.2. Chức năng chính
1. Quản lý tài khoản & định danh khách hàng (Member và khách vãng lai).
2. Tra cứu chỗ trống theo sơ đồ & khung giờ.
3. Đặt chỗ, giữ chỗ tạm, áp voucher/membership, chọn add-on.
4. Thanh toán trực tuyến, đối soát, hoàn tiền.
5. Check-in / check-out bằng QR, gia hạn, tính phụ phí overstay.
6. Dịch vụ tại chỗ: gọi đồ ăn uống, yêu cầu hỗ trợ kỹ thuật, báo sự cố.
7. Vận hành tại quầy: walk-in, đổi chỗ, thu phụ phí, bảo trì vị trí, đền bù.
8. Quản trị: sơ đồ không gian, bảng giá, khuyến mãi, báo cáo.
9. Thông báo tự động đa kênh.

### 2.3. Nhóm người dùng

| Actor | Đặc điểm | Quyền chính |
| :--- | :--- | :--- |
| Khách vãng lai (Guest) | Không có tài khoản, định danh bằng SĐT + Email | Tìm, đặt, thanh toán, check-in/out, gia hạn |
| Thành viên (Member) | Có tài khoản, có thể có Membership Pass | Như Guest + lịch sử đặt chỗ, khấu trừ giờ gói, cấu hình cảnh báo, lưu thông tin |
| Lễ tân / Nhân viên vận hành | Thao tác tại quầy, cần thao tác nhanh | Walk-in, check-in/out hộ, đổi chỗ, thu phụ phí, ticket sự cố, đánh dấu bảo trì, đền bù trong hạn mức |
| Nhân viên kỹ thuật **[Giả định]** | Xử lý ticket kỹ thuật/mạng | Nhận & cập nhật ticket |
| Quản lý trung tâm / Admin | Quản lý một hoặc nhiều chi nhánh | Cấu hình, bảng giá, khuyến mãi, báo cáo, quản lý tài khoản nhân viên |
| Cổng thanh toán (hệ thống) | VietQR, ví điện tử, thẻ nội địa/quốc tế | Xử lý giao dịch, gửi webhook, hoàn tiền |
| Dịch vụ thông báo (hệ thống) | SMS, Zalo ZNS, Email, Push | Gửi mã đặt chỗ, nhắc lịch, cảnh báo |
| Bộ lập lịch (Scheduler/Timer) | Tác nhân thời gian nội bộ | Hết hạn giữ chỗ, kích hoạt nhắc lịch, đối soát định kỳ |

### 2.4. Môi trường vận hành
- Ứng dụng khách: trình duyệt hiện đại (Chrome, Safari, Edge — 2 phiên bản mới nhất), Android 9+, iOS 15+.
- Ứng dụng quầy & quản trị: trình duyệt máy tính, màn hình ≥ 1366px; máy quét QR / camera tại quầy.
- Backend triển khai trên cloud, múi giờ chuẩn **Asia/Ho_Chi_Minh (UTC+7)**, tiền tệ **VND**.

### 2.5. Ràng buộc thiết kế & triển khai
- Không lưu trữ số thẻ thanh toán; mọi dữ liệu thẻ do cổng thanh toán xử lý (chuẩn PCI-DSS).
- Tuân thủ Nghị định 13/2023/NĐ-CP về bảo vệ dữ liệu cá nhân.
- Hóa đơn điện tử tuân thủ Nghị định 123/2020/NĐ-CP, xuất qua nhà cung cấp được cấp phép.
- Giao diện mặc định tiếng Việt, hỗ trợ tiếng Anh.

### 2.6. Giả định & phụ thuộc
- Cổng thanh toán hỗ trợ webhook, API truy vấn trạng thái giao dịch và API hoàn tiền.
- Hệ thống kiểm soát cửa / Wi-Fi có API nhận mã QR / cấp tài khoản Wi-Fi tạm **[Giả định]**.
- Đồng hồ máy chủ được đồng bộ NTP; mọi mốc thời gian check-in/out lấy theo giờ máy chủ.

---

## 3. Yêu cầu chức năng

Mức ưu tiên: **M** = Must (bắt buộc cho bản phát hành đầu), **S** = Should, **C** = Could.

### 3.1. Tài khoản & định danh (AUTH)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-AUTH-01 | Khách có thể đặt chỗ không cần tài khoản bằng cách cung cấp Họ tên, SĐT, Email (khách vãng lai). | M | UC03 |
| FR-AUTH-02 | Khách có thể đăng ký tài khoản Member bằng SĐT (xác thực OTP) hoặc Email. | M | UC01 |
| FR-AUTH-03 | Hệ thống tự động liên kết các đơn của khách vãng lai vào tài khoản khi khách đăng ký bằng cùng SĐT đã xác thực. | S | UC01 |
| FR-AUTH-04 | Nhân viên và Quản lý đăng nhập bằng tài khoản nội bộ; phân quyền theo vai trò (RBAC) và theo chi nhánh. | M | UC23 |
| FR-AUTH-05 | Member xem/sửa hồ sơ, xem số giờ còn lại của Membership Pass. | M | UC01 |

### 3.2. Tra cứu chỗ trống (SRCH)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-SRCH-01 | Khách tìm chỗ theo: chi nhánh, loại không gian (Hot Desk, Fixed Desk, Focus Booth, Meeting Room, Event Space), ngày, giờ bắt đầu, giờ kết thúc hoặc tổng số giờ, số người. | M | UC02 |
| FR-SRCH-02 | Hiển thị sơ đồ mặt bằng trực quan với trạng thái từng vị trí trong khung giờ tìm kiếm (Trống / Đang giữ / Đã đặt / Bảo trì). | M | UC02 |
| FR-SRCH-03 | Lọc theo tiện ích: yên tĩnh, số ổ cắm, sức chứa, có màn hình/máy chiếu. | S | UC02 |
| FR-SRCH-04 | Hiển thị giá dự kiến theo bảng giá hiệu lực (cao điểm/thấp điểm) trước khi đặt. | M | UC02 |
| FR-SRCH-05 | Khi không còn chỗ phù hợp, gợi ý khung giờ lân cận hoặc vị trí tương đương còn trống. | S | UC02 |
| FR-SRCH-06 | Chỉ trả về vị trí có sức chứa ≥ số người yêu cầu (BR-14). | M | UC02 |

### 3.3. Đặt chỗ (BOOK)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-BOOK-01 | Khách chọn vị trí cụ thể (hoặc để hệ thống tự gán với Hot Desk) cho khung giờ đã chọn. | M | UC03 |
| FR-BOOK-02 | Khách chọn add-on (màn hình rời, máy chiếu, bảng trắng, suất ăn/uống) kèm số lượng; hệ thống kiểm tra tồn kho add-on theo khung giờ. | M | UC03 |
| FR-BOOK-03 | Khi khách chuyển sang bước thanh toán, hệ thống tạo đơn trạng thái **Held** và **soft lock** vị trí trong thời gian giữ chỗ (BR-02). | M | UC03 |
| FR-BOOK-04 | Hệ thống bảo đảm không có hai đơn hiệu lực cùng giữ một vị trí ở khung giờ giao nhau (BR-01), kể cả khi nhiều yêu cầu đến đồng thời. | M | UC03 |
| FR-BOOK-05 | Hiển thị đồng hồ đếm ngược thời gian giữ chỗ trên màn hình thanh toán. | S | UC03 |
| FR-BOOK-06 | Tính tổng tiền = tiền thuê theo block giờ (BR-03, BR-04) + add-on − voucher − khấu trừ membership; hiển thị chi tiết từng dòng. | M | UC03, UC05 |
| FR-BOOK-07 | Khách xem danh sách đơn của mình, chi tiết đơn, mã QR. | M | UC06 |
| FR-BOOK-08 | Khách hủy đơn Confirmed theo chính sách hủy (BR-15); hệ thống tính tiền hoàn tương ứng. | M | UC06 |
| FR-BOOK-09 | Khách đổi khung giờ của đơn chưa check-in nếu vị trí còn trống, tính chênh lệch. | C | UC06 |

### 3.4. Khuyến mãi & Membership (PROMO)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-PROMO-01 | Khách nhập mã voucher; hệ thống kiểm tra hiệu lực, thời hạn, điều kiện (loại không gian, khung giờ, giá trị tối thiểu), số lượt còn lại (BR-12). | M | UC05 |
| FR-PROMO-02 | Member có Membership Pass được khấu trừ số giờ từ gói trước, phần vượt thanh toán theo giá thường (BR-13). | M | UC05 |
| FR-PROMO-03 | Lượt dùng voucher được giữ cùng thời gian soft lock; trả lại lượt nếu đơn hết hạn/thất bại. | M | UC05 |

### 3.5. Thanh toán (PAY)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-PAY-01 | Hỗ trợ VietQR, ví điện tử, thẻ nội địa/quốc tế qua cổng thanh toán. | M | UC04 |
| FR-PAY-02 | Mỗi giao dịch có mã idempotency; xử lý webhook trùng lặp không tạo hiệu ứng kép. | M | UC04, UC25 |
| FR-PAY-03 | Xác thực chữ ký webhook trước khi cập nhật trạng thái. | M | UC04 |
| FR-PAY-04 | Khi nhận webhook thành công trong thời gian giữ chỗ: chuyển đơn **Confirmed**, khóa cứng vị trí, sinh Booking ID + QR, xuất hóa đơn/biên lai, lên lịch thông báo. | M | UC04 |
| FR-PAY-05 | Khi thanh toán thất bại hoặc hết thời gian giữ chỗ: chuyển đơn **Failed/Expired**, giải phóng vị trí và lượt voucher. | M | UC04, UC24 |
| FR-PAY-06 | Đối soát tự động: truy vấn chủ động trạng thái các giao dịch chưa xác định và nhận webhook trễ (BR-11). | M | UC25 |
| FR-PAY-07 | Webhook trễ cho đơn đã Expired: nếu vị trí còn trống → kích hoạt lại đơn Confirmed; nếu đã bị chiếm → đề xuất vị trí tương đương hoặc chuyển **Chờ hoàn tiền**, thông báo cho khách. | M | UC25 |
| FR-PAY-08 | Gọi API hoàn tiền của cổng; theo dõi trạng thái hoàn tiền tới khi hoàn tất; cảnh báo nhân viên nếu hoàn tiền thất bại. | M | UC25 |
| FR-PAY-09 | Lễ tân ghi nhận thanh toán tại quầy (tiền mặt, VietQR tại quầy, POS thẻ). | M | UC13, UC16 |

### 3.6. Check-in / Sử dụng / Check-out (CHK)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-CHK-01 | Khách check-in bằng QR tại cửa kiểm soát hoặc quầy; hệ thống kiểm tra mã hợp lệ, đúng chi nhánh, trong cửa sổ cho phép (BR-17). | M | UC07 |
| FR-CHK-02 | Khi check-in thành công: ghi nhận mốc thời gian thực, chuyển vị trí **Occupied**, gửi lệnh mở cửa và cấp tài khoản Wi-Fi gắn với booking. | M | UC07 |
| FR-CHK-03 | Ghi nhận quyền miễn phí cốc nước đầu tiên cho mỗi booking (BR-10). | S | UC07, UC10 |
| FR-CHK-04 | Khách yêu cầu gia hạn trên ứng dụng; hệ thống kiểm tra vị trí trống khung kế tiếp, báo giá, thu tiền và cập nhật giờ kết thúc (BR-07). | M | UC08 |
| FR-CHK-05 | Nếu không gia hạn được tại vị trí hiện tại, đề xuất chuyển sang vị trí tương đương còn trống. | S | UC08 |
| FR-CHK-06 | Khách check-out bằng QR; hệ thống ghi nhận mốc thời gian, tính phụ phí overstay (BR-06) và các dịch vụ phát sinh chưa thanh toán. | M | UC09 |
| FR-CHK-07 | Nếu có khoản phát sinh, khách thanh toán online hoặc tại quầy trước khi đơn chuyển **Completed**. | M | UC09, UC16 |
| FR-CHK-08 | Sau check-out, vị trí chuyển **Cần dọn dẹp**, tài khoản Wi-Fi gắn với booking bị thu hồi. | S | UC09 |
| FR-CHK-09 | Đơn Confirmed không check-in sau thời gian ân hạn → **No-show**, giải phóng phần còn lại của khung giờ **[Giả định]**. | S | UC24 |

### 3.7. Dịch vụ tại chỗ & hỗ trợ (SVC)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-SVC-01 | Khách đang check-in gọi đồ ăn/uống từ thực đơn trên app; đơn chuyển tới màn hình quầy; tiền cộng vào hóa đơn booking hoặc thanh toán ngay. | S | UC10 |
| FR-SVC-02 | Khách gửi yêu cầu hỗ trợ kỹ thuật/báo sự cố (mạng, ổ cắm, điều hòa, vệ sinh…) kèm mô tả, ảnh; hệ thống tạo ticket gắn với vị trí. | M | UC11 |
| FR-SVC-03 | Ticket có mức ưu tiên; sự cố mạng được ưu tiên cao. Khách theo dõi trạng thái ticket. | S | UC11, UC17 |
| FR-SVC-04 | Member tùy chỉnh mốc nhận cảnh báo sắp hết giờ: 10, 15 hoặc 30 phút (mặc định 15) và kênh nhận thông báo. | S | UC12 |

### 3.8. Vận hành tại quầy (OPS)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-OPS-01 | Lễ tân tạo đơn walk-in cho khách tại quầy với cùng quy tắc chống trùng chỗ như đặt online. | M | UC13 |
| FR-OPS-02 | Lễ tân check-in/check-out hộ khách bằng quét QR hoặc tìm theo Booking ID/SĐT. | M | UC14 |
| FR-OPS-03 | Màn hình vận hành thời gian thực: sơ đồ trạng thái vị trí, danh sách sắp đến, đang sử dụng, sắp hết giờ, đang overstay. | M | UC14 |
| FR-OPS-04 | Lễ tân đổi chỗ (Swap/Upgrade) cho booking đang hiệu lực; nếu nguyên nhân do trung tâm thì miễn chênh lệch nâng hạng (BR-08), ngược lại thu chênh lệch. | M | UC15 |
| FR-OPS-05 | Lễ tân thu phụ phí overstay và dịch vụ ngoài (đồ uống, dịch vụ) và ghi vào hóa đơn. | M | UC16 |
| FR-OPS-06 | Lễ tân/kỹ thuật cập nhật ticket sự cố; đánh dấu vị trí **Bảo trì** để chặn đặt mới (BR-09); mở lại khi khắc phục xong. | M | UC17 |
| FR-OPS-07 | Khi vị trí chuyển Bảo trì, hệ thống liệt kê các booking tương lai bị ảnh hưởng để sắp xếp lại và thông báo cho khách. | M | UC17 |
| FR-OPS-08 | Lễ tân áp dụng đền bù (đồ uống miễn phí, voucher, cộng giờ) theo hạn mức được cấu hình; vượt hạn mức cần Quản lý duyệt. | S | UC18 |
| FR-OPS-09 | Mọi thao tác điều chỉnh giá, đổi chỗ, miễn phí, đền bù được ghi audit log (ai, khi nào, lý do). | M | UC15, UC16, UC18 |

### 3.9. Quản trị (ADM)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-ADM-01 | Quản lý chi nhánh, khu vực, sơ đồ mặt bằng (kéo thả vị trí), thuộc tính vị trí (loại, sức chứa, tiện ích, giờ mở cửa). | M | UC19 |
| FR-ADM-02 | Bật/tắt khai thác vị trí (Inactive) và đặt trạng thái Bảo trì theo khoảng thời gian. | M | UC19 |
| FR-ADM-03 | Thiết lập bảng giá theo loại không gian × khung giờ (cao điểm/thấp điểm) × ngày trong tuần/ngày lễ; có ngày hiệu lực; không ảnh hưởng đơn đã xác nhận. | M | UC20 |
| FR-ADM-04 | Cấu hình tham số: kích thước block giờ, thời gian giữ chỗ (5–10 phút), hệ số phụ phí overstay, thời gian ân hạn, chính sách hủy. | M | UC20 |
| FR-ADM-05 | Quản lý voucher/khuyến mãi: mã, loại giảm (%/số tiền/giờ miễn phí), điều kiện, thời hạn, tổng lượt, lượt/khách. | M | UC21 |
| FR-ADM-06 | Quản lý gói Membership: số giờ, thời hạn, loại không gian áp dụng, giá. | S | UC21 |
| FR-ADM-07 | Quản lý danh mục add-on, thực đơn đồ ăn/uống và giá. | M | UC19 |
| FR-ADM-08 | Quản lý tài khoản nhân viên, vai trò, chi nhánh được phân công. | M | UC23 |

### 3.10. Báo cáo (RPT)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-RPT-01 | Báo cáo tỷ lệ lấp đầy theo chi nhánh / loại không gian / khung giờ / ngày, dạng heatmap. | M | UC22 |
| FR-RPT-02 | Báo cáo doanh thu theo nguồn: tiền thuê, add-on, dịch vụ, phụ phí overstay; trừ giảm giá, hoàn tiền, đền bù. | M | UC22 |
| FR-RPT-03 | Báo cáo vận hành: số no-show, số overstay, số ticket sự cố & thời gian xử lý, tỷ lệ walk-in. | S | UC22 |
| FR-RPT-04 | Báo cáo hiệu quả voucher và tiêu thụ membership. | S | UC22 |
| FR-RPT-05 | Xuất báo cáo Excel/PDF. | S | UC22 |

### 3.11. Thông báo & tác vụ tự động (NOTI / SYS)

| ID | Yêu cầu | Ưu tiên | UC |
| :--- | :--- | :---: | :--- |
| FR-NOTI-01 | Gửi Booking ID + QR ngay khi đơn Confirmed qua kênh khách chọn (SMS/Zalo/Email/Push). | M | UC26 |
| FR-NOTI-02 | Gửi nhắc lịch **trước 30 phút** giờ bắt đầu. | M | UC26 |
| FR-NOTI-03 | Gửi cảnh báo sắp hết giờ **trước 15 phút** (hoặc mốc khách cấu hình), kèm nút gia hạn nhanh. | M | UC26 |
| FR-NOTI-04 | Gửi thông báo khi: đơn thất bại/hết hạn, đổi chỗ, hoàn tiền, vị trí bị bảo trì ảnh hưởng đơn, đền bù. | M | UC26 |
| FR-NOTI-05 | Khi cập nhật/hủy đơn, các lịch thông báo liên quan được cập nhật/hủy tương ứng. | M | UC26 |
| FR-NOTI-06 | Gửi lại qua kênh dự phòng nếu kênh chính thất bại (ví dụ Zalo → SMS). | S | UC26 |
| FR-SYS-01 | Tự động hết hạn đơn Held quá thời gian giữ chỗ và giải phóng vị trí. | M | UC24 |
| FR-SYS-02 | Tự động chuyển đơn sang Overstay khi quá giờ kết thúc mà chưa check-out, thông báo cho quầy. | M | UC24 |
| FR-SYS-03 | Tự động đánh dấu No-show theo BR-16 **[Giả định]**. | S | UC24 |

---

## 4. Quy tắc nghiệp vụ

| ID | Quy tắc | Nguồn |
| :--- | :--- | :--- |
| BR-01 | Tại một thời điểm, một vị trí chỉ thuộc về **một** đơn hiệu lực (Held, Confirmed, CheckedIn, Overstay). Hai khoảng [Start, End) giao nhau là xung đột. | §3.2 |
| BR-02 | Thời gian soft lock cấu hình 5–10 phút, mặc định **10 phút**, bắt đầu khi khách chuyển sang bước thanh toán. Hết hạn → tự hủy giữ chỗ. | §5.1 |
| BR-03 | Tiền thuê tính theo block giờ; thời lượng làm tròn **lên** block gần nhất. Kích thước block mặc định **30 phút** **[Giả định]**. | §3.2 |
| BR-04 | Đơn giá lấy theo bảng giá hiệu lực tại thời điểm đặt. Đơn vắt qua nhiều khung giá (ví dụ thấp điểm → cao điểm) được tính riêng từng phần. | §2 |
| BR-05 | Nhắc lịch trước 30 phút giờ bắt đầu; cảnh báo hết giờ trước 15 phút (khách chọn 10/15/30). | §4.2, §6.2 |
| BR-06 | Phụ phí overstay = số block vượt (làm tròn lên, sau thời gian ân hạn 5 phút **[Giả định]**) × đơn giá khung giờ tương ứng × hệ số phụ thu (mặc định 1.0 **[Giả định]**). | §5.3 |
| BR-07 | Gia hạn tại chỗ chỉ thực hiện được khi vị trí trống trong khoảng gia hạn; ngược lại đề xuất vị trí tương đương. Không được gia hạn chồng lên đơn khác. | §5.3 |
| BR-08 | Đổi chỗ do sự cố từ phía trung tâm: miễn phí chênh lệch nâng hạng. Đổi chỗ theo yêu cầu khách: thu chênh lệch (không hoàn phần chênh khi hạ hạng **[Giả định]**). | §5.4 |
| BR-09 | Vị trí ở trạng thái Bảo trì hoặc Ngưng khai thác không xuất hiện trong kết quả tìm kiếm và không nhận đặt mới. | §5.4 |
| BR-10 | Mỗi booking được miễn phí cốc nước đầu tiên. | §1 |
| BR-11 | Khách đã bị trừ tiền nhưng không nhận được chỗ (do lỗi hệ thống/cổng) được hoàn **100%** hoặc chọn vị trí tương đương. | §5.2 |
| BR-12 | Mỗi đơn áp dụng tối đa **một** voucher **[Giả định]**; voucher phải còn hạn, còn lượt, thỏa điều kiện. | §2 |
| BR-13 | Membership được khấu trừ trước; phần thiếu thanh toán bằng phương thức khác. Giờ membership chỉ áp cho loại không gian gói cho phép. | §4.2 |
| BR-14 | Số người tham gia không vượt sức chứa của vị trí. | §4.1 |
| BR-15 | Chính sách hủy **[Giả định — cần xác nhận]**: hủy trước ≥ 24h hoàn 100%; 2h–24h hoàn 50%; < 2h không hoàn. Membership: trả lại giờ theo cùng tỷ lệ. | — |
| BR-16 | No-show **[Giả định]**: không check-in sau 30 phút kể từ giờ bắt đầu (hoặc 50% thời lượng, lấy giá trị nhỏ hơn) → đơn No-show, không hoàn tiền, giải phóng phần thời gian còn lại. | — |
| BR-17 | Cửa sổ check-in: sớm nhất 15 phút trước giờ bắt đầu **[Giả định]**, nếu vị trí không có người sử dụng trước đó. Check-in sớm không làm thay đổi giờ kết thúc. | §5.3 |
| BR-18 | Đền bù do lỗi diện rộng kéo dài (mạng): đồ uống miễn phí hoặc voucher/giờ sử dụng cho lần sau; Lễ tân được duyệt trong hạn mức, vượt hạn mức cần Quản lý. | §6.1 |
| BR-19 | Thay đổi bảng giá không ảnh hưởng đơn đã Confirmed. | — |

---

## 5. Yêu cầu dữ liệu

### 5.1. Thực thể chính

| Thực thể | Thuộc tính chính |
| :--- | :--- |
| Branch (Chi nhánh) | id, tên, địa chỉ, giờ mở cửa, múi giờ |
| Zone / Floor plan | id, branch_id, tên, ảnh/tọa độ sơ đồ |
| ResourceType | Hot Desk, Fixed Desk, Focus Booth, Meeting Room, Event Space |
| Resource (Vị trí) | id, zone_id, type, mã hiển thị, sức chứa, tiện ích, trạng thái khai thác, tọa độ trên sơ đồ |
| PriceRule | id, resource_type, branch_id, ngày trong tuần/ngày lễ, giờ từ–đến, đơn giá/block, hiệu lực từ–đến |
| Customer | id, họ tên, SĐT, email, loại (Guest/Member), cấu hình thông báo |
| MembershipPass | id, customer_id, gói, số giờ còn lại, hạn dùng |
| Voucher | mã, loại giảm, giá trị, điều kiện, hạn dùng, tổng lượt, lượt/khách, lượt đã dùng |
| Booking | id (Booking ID), customer_id, resource_id, start, end, số người, trạng thái, hold_expires_at, kênh (online/walk-in), qr_token, tổng tiền, voucher, check_in_at, check_out_at |
| BookingAddon | booking_id, addon_id, số lượng, đơn giá |
| ServiceOrder | id, booking_id, món, số lượng, giá, trạng thái |
| Payment | id, booking_id, cổng, phương thức, số tiền, idempotency_key, mã giao dịch cổng, trạng thái |
| Refund | id, payment_id, số tiền, lý do, trạng thái |
| Invoice | id, booking_id, số hóa đơn, các dòng, tổng, mã tra cứu HĐĐT |
| Ticket (Sự cố) | id, resource_id, booking_id, loại, mức ưu tiên, mô tả, ảnh, trạng thái, người xử lý |
| Compensation | id, booking_id/ticket_id, loại, giá trị, người duyệt |
| NotificationJob | id, booking_id, loại, kênh, thời điểm gửi, trạng thái |
| Staff | id, họ tên, vai trò, chi nhánh |
| AuditLog | id, actor, hành động, đối tượng, giá trị trước/sau, lý do, thời điểm |

### 5.2. Ràng buộc dữ liệu
- Ràng buộc cấp CSDL ngăn hai Booking hiệu lực trùng `resource_id` và giao nhau `[start, end)` (ví dụ PostgreSQL exclusion constraint với `tstzrange`) — lớp bảo vệ cuối cùng cho BR-01.
- `qr_token` ngẫu nhiên, không đoán được, có thời hạn theo booking.
- Dữ liệu cá nhân lưu tối thiểu, xóa/ẩn danh theo yêu cầu của chủ thể dữ liệu; lưu dữ liệu giao dịch tối thiểu 10 năm theo quy định kế toán **[Giả định]**.

---

## 6. Yêu cầu giao diện ngoài

### 6.1. Giao diện người dùng
- **App khách:** luồng đặt chỗ ≤ 4 bước (Tìm → Chọn vị trí → Xác nhận → Thanh toán); màn hình "Booking hiện tại" có QR, đồng hồ thời gian còn lại, nút Gia hạn / Gọi dịch vụ / Báo sự cố.
- **App quầy:** dashboard sơ đồ thời gian thực với mã màu trạng thái; tìm nhanh theo SĐT/Booking ID; thao tác check-in/out ≤ 2 lần bấm sau khi quét QR.
- **Trang quản trị:** trình chỉnh sơ đồ kéo thả, bảng giá dạng lưới (ngày × giờ), dashboard báo cáo.

### 6.2. Giao diện phần cứng
- Máy quét QR / camera tại quầy và cửa kiểm soát.
- Máy in hóa đơn nhiệt tại quầy (tùy chọn).

### 6.3. Giao diện phần mềm

| Hệ thống | Hướng | Nội dung |
| :--- | :--- | :--- |
| Cổng thanh toán | 2 chiều | Tạo giao dịch, redirect/QR, webhook kết quả, API truy vấn trạng thái, API hoàn tiền |
| Dịch vụ thông báo | Ra | SMS brandname, Zalo ZNS, Email, Push (FCM/APNs); nhận trạng thái gửi |
| Kiểm soát ra vào / Wi-Fi | 2 chiều | Xác thực QR → lệnh mở cửa; cấp/thu hồi tài khoản Wi-Fi theo booking; sự kiện quẹt vào/ra |
| Hóa đơn điện tử | 2 chiều | Phát hành hóa đơn, nhận số hóa đơn & mã tra cứu |

### 6.4. Giao tiếp
- Mọi API qua HTTPS (TLS 1.2+); cập nhật trạng thái sơ đồ thời gian thực qua WebSocket/SSE.

---

## 7. Yêu cầu phi chức năng

| ID | Nhóm | Yêu cầu |
| :--- | :--- | :--- |
| NFR-01 | Hiệu năng | Tìm chỗ trống & hiển thị sơ đồ: p95 ≤ 2 giây với 500 người dùng đồng thời. |
| NFR-02 | Hiệu năng | Tạo soft lock: p95 ≤ 500 ms. Check-in bằng QR từ lúc quét tới mở cửa ≤ 2 giây. |
| NFR-03 | Toàn vẹn | **0 double-booking**: kiểm thử tải với 100 yêu cầu đồng thời cho cùng một vị trí/khung giờ chỉ được 1 đơn thành công. |
| NFR-04 | Thời gian thực | Thay đổi trạng thái vị trí hiển thị trên các màn hình trong ≤ 3 giây. |
| NFR-05 | Sẵn sàng | Uptime ≥ 99.5%/tháng; luồng check-in tại quầy có chế độ dự phòng (tra cứu offline danh sách booking trong ngày). |
| NFR-06 | Tin cậy | Webhook xử lý idempotent; tác vụ hết hạn giữ chỗ, đối soát, thông báo có cơ chế thử lại và không mất khi khởi động lại dịch vụ. |
| NFR-07 | Chính xác thời gian | Thông báo gửi lệch không quá ±1 phút so với lịch. |
| NFR-08 | Bảo mật | RBAC theo vai trò & chi nhánh; mật khẩu băm (bcrypt/argon2); OTP giới hạn tần suất; xác thực chữ ký webhook; không lưu dữ liệu thẻ. |
| NFR-09 | Riêng tư | Tuân thủ NĐ 13/2023: thu thập đồng ý, mục đích rõ ràng, quyền xem/xóa dữ liệu; che SĐT/Email trên màn hình quầy khi không cần thiết. |
| NFR-10 | Kiểm toán | Audit log bất biến cho thao tác tài chính, đổi chỗ, đền bù, cấu hình giá; lưu ≥ 2 năm. |
| NFR-11 | Khả dụng | Khách lần đầu hoàn tất đặt chỗ trong ≤ 3 phút; giao diện responsive từ 360px. |
| NFR-12 | Mở rộng | Hỗ trợ nhiều chi nhánh; thêm chi nhánh không cần thay đổi mã nguồn. |
| NFR-13 | Bản địa hóa | Tiếng Việt/Tiếng Anh; định dạng tiền VND, ngày dd/MM/yyyy, giờ 24h. |
| NFR-14 | Bảo trì | Log tập trung, giám sát (metrics, alert) cho tỷ lệ lỗi thanh toán, độ trễ webhook, hàng đợi thông báo. |
| NFR-15 | Sao lưu | Sao lưu CSDL hàng ngày; RPO ≤ 15 phút, RTO ≤ 2 giờ. |

---

## 8. Ma trận truy vết

| Mục trong tài liệu thu thập | Yêu cầu SRS | Use Case |
| :--- | :--- | :--- |
| §1 Bài toán, tiện ích cơ bản | FR-SRCH-03, FR-CHK-03, BR-10 | UC02, UC07 |
| §2 Khách hàng: tìm, chọn loại vị trí | FR-SRCH-01..06 | UC02 |
| §2 Khách hàng: đặt theo khung giờ, thanh toán | FR-BOOK-*, FR-PAY-01..05 | UC03, UC04 |
| §2 Khách hàng: check-in/out, gia hạn | FR-CHK-01..08 | UC07, UC08, UC09 |
| §2 Lễ tân: walk-in | FR-OPS-01 | UC13 |
| §2 Lễ tân: check-in/out | FR-OPS-02, FR-OPS-03 | UC14 |
| §2 Lễ tân: đổi chỗ, sự cố | FR-OPS-04, FR-OPS-06, FR-OPS-07 | UC15, UC17 |
| §2 Lễ tân: thu phụ phí | FR-OPS-05 | UC16 |
| §2 Quản lý: sơ đồ, trạng thái | FR-ADM-01, FR-ADM-02 | UC19 |
| §2 Quản lý: bảng giá | FR-ADM-03, FR-ADM-04 | UC20 |
| §2 Quản lý: khuyến mãi | FR-ADM-05, FR-ADM-06 | UC21 |
| §2 Quản lý: occupancy, doanh thu | FR-RPT-01..05 | UC22 |
| §2 Cổng thanh toán | FR-PAY-01..08 | UC04, UC25 |
| §2 Hệ thống thông báo | FR-NOTI-01..06 | UC26 |
| §3.1 Mục tiêu khách hàng | FR-SRCH-*, FR-SVC-01..02 | UC02, UC10, UC11 |
| §3.2 Anti double-booking | FR-BOOK-03, FR-BOOK-04, BR-01, NFR-03 | UC03 |
| §3.2 Tính tiền theo block, kiểm soát ra/vào | BR-03, BR-06, FR-CHK-06 | UC09 |
| §4.1 Input | FR-SRCH-01, FR-BOOK-01..02, FR-AUTH-01, FR-PROMO-* | UC02, UC03, UC05 |
| §4.2 Output | FR-PAY-04, FR-NOTI-01..03 | UC04, UC26 |
| §5.1 Race condition | FR-BOOK-03..05, FR-SYS-01, BR-02 | UC03, UC24 |
| §5.2 Lỗi cổng thanh toán | FR-PAY-06..08, BR-11 | UC25 |
| §5.3 Overstay & gia hạn | FR-CHK-04..07, FR-SYS-02, BR-06, BR-07 | UC08, UC09, UC16 |
| §5.4 Sự cố cơ sở vật chất | FR-OPS-04, FR-OPS-06, FR-OPS-07, BR-08, BR-09 | UC11, UC15, UC17 |
| §6.1 Sự cố mạng | FR-SVC-02, FR-SVC-03, FR-OPS-08, BR-18 | UC11, UC17, UC18 |
| §6.2 Cảnh báo hết giờ | FR-NOTI-03, FR-SVC-04 | UC12, UC26 |

---

## 9. Giả định & câu hỏi mở (cần khách hàng xác nhận)

| # | Câu hỏi | Giả định đang dùng |
| :--- | :--- | :--- |
| Q1 | Kích thước block tính tiền? Thời lượng đặt tối thiểu? | Block 30 phút, tối thiểu 1 giờ |
| Q2 | Chính sách hủy và hoàn tiền? | BR-15 |
| Q3 | Xử lý khách không đến (no-show)? | BR-16 |
| Q4 | Thời gian ân hạn overstay và hệ số phụ thu? | Ân hạn 5 phút, hệ số 1.0 |
| Q5 | Cho phép check-in sớm bao nhiêu phút? | 15 phút nếu vị trí trống |
| Q6 | Hot Desk: khách chọn ghế cụ thể hay hệ thống tự gán? | Cả hai, mặc định tự gán |
| Q7 | Một đơn có được đặt nhiều vị trí (nhóm) hay không? | Bản 1: mỗi đơn một vị trí; phòng họp cho nhóm |
| Q8 | Một đơn được dùng nhiều voucher? Voucher kết hợp membership? | Một voucher; không kết hợp với membership |
| Q9 | Hạn mức đền bù của Lễ tân? | Tối đa 1 đồ uống hoặc voucher ≤ 50.000đ / lượt |
| Q10 | Hệ thống cửa kiểm soát/Wi-Fi hiện có hỗ trợ API? | Có API; nếu không, check-in bằng quét QR tại quầy |
| Q11 | Đặt chỗ dài hạn (Fixed Desk theo tháng) có trong phạm vi? | Không trong bản 1 |
| Q12 | Không gian sự kiện có cần quy trình báo giá/duyệt riêng? | Đặt như phòng họp, có thể cần duyệt tay |
