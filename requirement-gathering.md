# Tài Liệu Thu Thập Yêu Cầu (Requirements Gathering)
**Dự án:** Hệ thống đặt chỗ Co-Working Space theo giờ

---

## 1. Xác định bài toán (Problem Statement)
- Mô hình **Co-Working Space** cho phép khách hàng đăng ký đặt chỗ làm việc linh hoạt theo khung giờ.
- Tiện ích cơ bản cung cấp: Ổ cắm điện, bàn ghế làm việc tiêu chuẩn, miễn phí cốc nước đầu tiên.

---

## 2. Danh sách Actor & Vai trò (Stakeholders & Actors)

| Phân loại | Actor | Vai trò trong quy trình |
| :--- | :--- | :--- |
| **Người dùng chính (Primary Actors)** | **Khách hàng** *(Member / Khách vãng lai)* | - Tìm kiếm chỗ trống, chọn loại vị trí (bàn cá nhân, phòng họp, ghế tự do).<br>- Đặt chỗ theo khung giờ, thực hiện thanh toán.<br>- Check-in/Check-out và yêu cầu gia hạn thời gian sử dụng. |
| | **Lễ tân / Nhân viên vận hành** *(Receptionist / Staff)* | - Hỗ trợ khách walk-in (đặt trực tiếp tại quầy).<br>- Kiểm soát quy trình check-in / check-out.<br>- Xử lý đổi chỗ, giải quyết sự cố tại chỗ.<br>- Thu phụ phí (phát sinh quá giờ, đồ uống/dịch vụ ngoài). |
| | **Quản lý trung tâm** *(Branch Manager / Admin)* | - Cấu hình sơ đồ không gian và trạng thái chỗ ngồi.<br>- Thiết lập bảng giá linh hoạt theo khung giờ (cao điểm / thấp điểm).<br>- Quản lý chương trình khuyến mãi, voucher.<br>- Giám sát tỷ lệ lấp đầy (*occupancy rate*) và báo cáo doanh thu. |
| **Hệ thống liên quan (System Actors)** | **Cổng thanh toán** *(Payment Gateway)* | - Xử lý giao dịch thanh toán trực tuyến (VietQR, ví điện tử, thẻ nội địa/quốc tế).<br>- Tự động kích hoạt luồng hoàn tiền khi phát sinh hủy/lỗi. |
| | **Hệ thống thông báo** *(Notification Service)* | - Gửi mã đặt chỗ (Booking ID / QR Code).<br>- Tự động gửi tin nhắc lịch check-in, cảnh báo sắp hết giờ qua SMS/Zalo/Email/Mobile App. |

---

## 3. Mục tiêu nghiệp vụ (Business Goals)

Hệ thống nhằm giải quyết các bài toán vận hành cốt lõi sau:

### 3.1. Đối với khách hàng
- **Đảm bảo tính sẵn sàng:** Đảm bảo có chỗ ngồi phù hợp (yên tĩnh, đủ ổ cắm, phòng họp riêng...) ngay khi đến nơi mà không sợ hết chỗ.
- **Tối ưu chi phí & thời gian:** Chủ động kiểm soát thời lượng và chi phí theo nhu cầu sử dụng thực tế.
- **Tiện ích tích hợp:** Dễ dàng gọi dịch vụ đồ ăn, thức uống hoặc yêu cầu hỗ trợ kỹ thuật trực tiếp khi cần.

### 3.2. Đối với trung tâm vận hành
- **Tối ưu hóa tỷ lệ lấp đầy (*Occupancy Rate*):** Tránh lãng phí bàn ghế trống hoặc quá tải cục bộ vào các khung giờ cao điểm.
- **Ngăn chặn trùng lặp (*Anti Double-booking*):** Cam kết tại một thời điểm, một vị trí cụ thể chỉ thuộc về một khách hàng duy nhất.
- **Tự động hóa & Minh bạch doanh thu:**
  - Cắt giảm thao tác ghi chép sổ sách thủ công của lễ tân.
  - Tính tiền tự động và chính xác theo từng block giờ.
  - Kiểm soát thực tế thời gian ra/vào để tránh thất thoát chi phí do khách ngồi quá giờ.

---

## 4. Input & Output của quy trình đặt chỗ

### 4.1. Input (Đầu vào)
* **Thông tin yêu cầu đặt chỗ:**
  - *Loại không gian / vị trí:* Chỗ ngồi linh hoạt (*Hot Desk*), Bàn cố định (*Fixed Desk*), Pod cá nhân (*Focus Booth*), Phòng họp (*Meeting Room*), Không gian sự kiện.
  - *Thời gian:* Ngày sử dụng, Giờ bắt đầu (*Start Time*), Giờ kết thúc (*End Time*) hoặc tổng số giờ thuê.
  - *Quy mô:* Số lượng người tham gia (đặc biệt với phòng họp/bàn nhóm).
  - *Dịch vụ bổ sung (Add-ons):* Thuê màn hình rời, máy chiếu, bảng trắng, suất đồ ăn/uống thêm.
* **Thông tin định danh & Thanh toán:**
  - Họ và tên, Số điện thoại, Email.
  - Mã ưu đãi (Voucher) hoặc Thẻ thành viên (Membership Pass).
  - Phương thức thanh toán lựa chọn.

### 4.2. Output (Đầu ra)
* **Trạng thái đơn hàng (Booking Status):** Xác nhận thành công (*Confirmed*) hoặc Thất bại/Từ chối (*Failed/Rejected*).
* **Vé điện tử / Mã định danh:** Mã Booking ID kèm mã QR định danh (dùng để check-in mở cửa, nhận diện tài khoản Wi-Fi).
* **Cập nhật dữ liệu hệ thống:**
  - Tự động khóa (*Lock*) trạng thái chỗ ngồi trên sơ đồ theo khung giờ tương ứng.
  - Xuất hóa đơn điện tử / Biên lai giao dịch (hoặc khấu trừ số giờ từ gói Membership).
  - Tạo lịch trình gửi thông báo tự động (trước 30 phút khi đến và trước 15 phút khi hết giờ).

---

## 5. Các trường hợp ngoại lệ & Hướng xử lý (Exception & Edge Cases)

### 5.1. Xung đột thao tác đặt chỗ đồng thời (*Concurrency / Race Condition*)
* **Tình huống:** Hai khách hàng cùng thao tác chọn vị trí cuối cùng trong cùng khung giờ và nhấn "Thanh toán" tại cùng một thời điểm.
* **Hướng xử lý:**
  - Kích hoạt cơ chế **Giữ chỗ tạm thời (*Soft Lock/Hold*)** trong vòng 5–10 phút ngay khi người dùng chuyển sang bước thanh toán.
  - Nếu quá hạn 10 phút mà giao dịch chưa hoàn tất, hệ thống tự động hủy giữ chỗ và giải phóng trạng thái về kho chỗ trống.

### 5.2. Lỗi cổng thanh toán (*Payment Timeout / Deduction Error*)
* **Tình huống:** Tài khoản khách hàng đã bị trừ tiền thành công nhưng do lỗi kết nối mạng, hệ thống chưa nhận được webhook xác nhận, dẫn đến đơn bị tự động hủy do hết hạn giữ chỗ.
* **Hướng xử lý:**
  - Thiết lập cơ chế đối soát tự động (*Webhook Retry / Reconciliation*).
  - Nếu vị trí còn trống: Kích hoạt lại đơn đặt thành công ngay khi nhận được tín hiệu.
  - Nếu vị trí đã bị người khác đặt: Tự động chuyển đơn sang trạng thái *"Chờ hoàn tiền"* hoặc đề xuất đổi sang vị trí tương đương kèm thông báo cho khách.

### 5.3. Khách ngồi quá giờ đăng ký (*Overstay*) & Gia hạn tiếp nối
* **Tình huống:** Khách đặt chỗ khung giờ 13:00 - 15:00. Đến 15:00 khách muốn ngồi thêm, nhưng vị trí đó đã có người đặt trước từ 15:00 - 17:00.
* **Hướng xử lý:**
  - Kiểm soát chặt chẽ bằng cơ chế Check-in / Check-out qua mã QR tại quầy hoặc cửa kiểm soát.
  - Ghi nhận chính xác mốc thời gian khách hoàn tất check-out. Nếu vượt quá thời lượng đăng ký mà không thể gia hạn tại chỗ (do xung đột với lịch khách khác), hệ thống tự động tính toán khoản phụ phí phát sinh tương ứng vào hóa đơn thanh toán khi check-out.

### 5.4. Sự cố cơ sở vật chất tại chỗ (*Facility Issue*)
* **Tình huống:** Khách đến nhận bàn đúng giờ nhưng gặp sự cố kỹ thuật (ổ cắm hỏng, điều hòa rò rỉ nước, hoặc chưa được dọn dẹp vệ sinh).
* **Hướng xử lý:**
  - Cấp quyền cho Lễ tân thao tác chuyển bàn (*Swap / Upgrade Seat*) trực tiếp trên phần mềm điều hành.
  - Miễn phí phí nâng cấp hạng bàn nếu đổi sang vị trí có giá trị cao hơn do sự cố từ phía trung tâm.
  - Đánh dấu vị trí gặp sự cố sang trạng thái **Bảo trì (*Maintenance*)** để tạm ngưng nhận đặt chỗ mới.

---

## 6. Trải nghiệm & Hỗ trợ khách hàng phát sinh

### 6.1. Sự cố gián đoạn kết nối mạng trong quá trình làm việc
* **Tình huống:** Đường truyền Internet bị chập chờn, rớt mạng hoặc băng thông suy giảm, làm gián đoạn tiến độ làm việc của khách.
* **Hướng xử lý:**
  - Khách hàng có thể phản ánh ngay qua nút hỗ trợ kỹ thuật trên ứng dụng hoặc thông báo trực tiếp tại quầy CSKH.
  - Nhân viên kỹ thuật kiểm tra và xử lý đường truyền ưu tiên.
  - Trường hợp lỗi diện rộng kéo dài: Chủ động gửi lời xin lỗi và áp dụng chính sách đền bù (tặng đồ uống miễn phí hoặc hoàn trả voucher/giờ sử dụng cho lần sau).

### 6.2. Cảnh báo sắp hết thời lượng sử dụng
* **Tình huống:** Khách hàng tập trung làm việc nên quên theo dõi thời gian, dẫn đến việc ngồi quá giờ ngoài ý muốn và phải trả thêm phụ thu khi ra về.
* **Hướng xử lý:**
  - Hệ thống tự động kích hoạt gửi tin nhắn/thông báo nhắc nhở khi gần hết hạn sử dụng (ví dụ: trước 15 phút).
  - Cung cấp tùy chọn cho phép khách hàng cá nhân hóa mốc thời gian nhận cảnh báo (trước 10, 15 hoặc 30 phút) ngay trên ứng dụng đặt chỗ.