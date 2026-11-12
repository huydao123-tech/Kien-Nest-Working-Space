# Prompt 1(close)
-viết tính năng Chọn add-on kèm số lượng; kiểm tra tồn kho add-on theo khung giờ ngay khi đặt chỗ. Tính cả phí thuê addon vào giá tổng 
-tính tổng tiền thuê trong booking_charge, theo block. Tổng tiền = tiền thuê + add-on − voucher − khấu trừ membership
-mới chỉ đc xem đc chi tiết 1 đơn qua api GET /api/bookings/{id}. Viết chuc năng xem đơn của tôi với từng cusstomer và staff có thể xem được full 
booking và filter theo khách 
- viết chức năng hủy book cho khach đã thanh toán. Chỉ đc hủy 2 tiếng trc khi đến giờ. Được hoàn full tiền. Áp dụng rule tương tự cho khách đổi khung giờ check-in
# Prompt 2(close)
viết tính năng đổi chỗ dat trước khi check-in còn thiếu trường hợp Khung đắt hơn thì phải bù thêm tiền, cho phép đổi vị trí / thời lượng. Chỉ cho đổi khi thời gian 
đến lúc check-in >= 2 tiếng