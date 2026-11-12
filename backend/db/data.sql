-- =====================================================================
-- Dữ liệu mẫu cho database Co-Working Space — chạy SAU db/schema.sql
--
-- CÁCH CHẠY (pgAdmin 4): chọn database working-space → Tools → Query Tool → dán TOÀN BỘ file → Execute (F5).
--
-- - Đăng nhập bằng tài khoản postgres, giống khi chạy schema.sql.
-- - Đầu file XÓA dữ liệu của mọi bảng (trừ resource_type) rồi thêm lại, nên chạy lại nhiều lần vẫn ra cùng một bộ dữ liệu.
-- - Id được ghi rõ để dễ đọc quan hệ; cuối file đặt lại bộ đếm tự tăng để ứng dụng thêm dòng mới không bị trùng id.
-- - Đã có dữ liệu space_resource nên DemoDataSeeder của ứng dụng sẽ không thêm dữ liệu nữa.
-- - Bảng giá giống DemoDataSeeder (block 30 phút): tiền trong booking_charge tính đúng theo bảng giá này.
--   Ngày 05/10/2026 là thứ Hai; cao điểm 09:00–18:00 ngày thường, cuối tuần cả ngày tính giá thấp điểm.
-- =====================================================================

BEGIN;

TRUNCATE otp_code, outbox_event, audit_log, notification_job, compensation, ticket_attachment, ticket,
    invoice_line, invoice, refund, service_order_item, service_order, menu_item, menu_category,
    booking_addon, addon, voucher_redemption, voucher_resource_type, voucher,
    membership_usage, membership_pass, membership_plan_resource_type, membership_plan,
    staff, resource_amenity, amenity, payment, booking_status_history, booking_charge, booking,
    customer, price_rule, space_resource, zone, branch
    RESTART IDENTITY CASCADE;

-- resource_type đã có 5 dòng do schema.sql thêm; các bảng dưới tìm id theo code cho chắc chắn.


-- #####################################################################
-- PHẦN 1 — CODE ĐANG DÙNG
-- #####################################################################

-- ---------------------------------------------------------------------
-- branch, zone
-- ---------------------------------------------------------------------
INSERT INTO branch (id, code, name, address, open_time, close_time, status, created_at)
VALUES (1, 'HCM-Q1', 'Cowork Nguyễn Huệ', '72 Nguyễn Huệ, phường Bến Nghé, Quận 1, TP. Hồ Chí Minh', '07:00', '22:00', 'ACTIVE', '2026-01-10 08:00'),
       (2, 'HN-HK', 'Cowork Hoàn Kiếm', '15 Tràng Tiền, quận Hoàn Kiếm, Hà Nội', '08:00', '21:00', 'ACTIVE', '2026-06-01 08:00');

INSERT INTO zone (id, branch_id, name, floor, floor_plan_url)
VALUES (1, 1, 'Khu làm việc mở', 1, '/floor-plans/hcm-q1-tang-1.png'),
       (2, 1, 'Khu yên tĩnh', 2, '/floor-plans/hcm-q1-tang-2.png'),
       (3, 1, 'Khu phòng họp và sự kiện', 3, '/floor-plans/hcm-q1-tang-3.png'),
       (4, 2, 'Khu làm việc chung', 1, NULL);

-- ---------------------------------------------------------------------
-- space_resource — mã chỗ giống DemoDataSeeder để test thủ công dễ đối chiếu
-- ---------------------------------------------------------------------
INSERT INTO space_resource (id, code, name, resource_type_id, capacity, status, zone_id, pos_x, pos_y, version)
SELECT v.id, v.code, v.name, rt.id, v.capacity, v.status, v.zone_id, v.pos_x, v.pos_y, 0
FROM (VALUES (1, 'HD-01', 'Hot Desk 1', 'HOT_DESK', 1, 'ACTIVE', 1, 15, 30),
             (2, 'HD-02', 'Hot Desk 2', 'HOT_DESK', 1, 'ACTIVE', 1, 30, 30),
             (3, 'HD-03', 'Hot Desk 3', 'HOT_DESK', 1, 'ACTIVE', 1, 45, 30),
             (4, 'HD-04', 'Hot Desk 4', 'HOT_DESK', 1, 'ACTIVE', 1, 15, 60),
             (5, 'HD-05', 'Hot Desk 5', 'HOT_DESK', 1, 'MAINTENANCE', 1, 30, 60),   -- đang hỏng mạng, xem ticket 2
             (6, 'HD-06', 'Hot Desk 6', 'HOT_DESK', 1, 'ACTIVE', 1, 45, 60),
             (7, 'FD-01', 'Bàn cố định 1', 'FIXED_DESK', 1, 'ACTIVE', 2, 20, 25),
             (8, 'FD-02', 'Bàn cố định 2', 'FIXED_DESK', 1, 'ACTIVE', 2, 40, 25),
             (9, 'FD-03', 'Bàn cố định 3', 'FIXED_DESK', 1, 'INACTIVE', 2, 60, 25),
             (10, 'FB-01', 'Focus Booth 1', 'FOCUS_BOOTH', 1, 'ACTIVE', 2, 25, 70),
             (11, 'FB-02', 'Focus Booth 2', 'FOCUS_BOOTH', 1, 'ACTIVE', 2, 50, 70),
             (12, 'MR-01', 'Phòng họp nhỏ', 'MEETING_ROOM', 6, 'ACTIVE', 3, 25, 30),
             (13, 'MR-02', 'Phòng họp lớn', 'MEETING_ROOM', 12, 'ACTIVE', 3, 70, 30),
             (14, 'EV-01', 'Không gian sự kiện', 'EVENT_SPACE', 50, 'ACTIVE', 3, 50, 75),
             (15, 'HN-HD-01', 'Hot Desk Hà Nội 1', 'HOT_DESK', 1, 'ACTIVE', 4, NULL, NULL))
         AS v(id, code, name, type_code, capacity, status, zone_id, pos_x, pos_y)
JOIN resource_type rt ON rt.code = v.type_code;

-- ---------------------------------------------------------------------
-- price_rule — giá một block 30 phút: cao điểm / thấp điểm
-- ---------------------------------------------------------------------
INSERT INTO price_rule (resource_type_id, day_type, start_minute, end_minute, price_per_block, label)
SELECT rt.id, r.day_type, r.start_minute, r.end_minute,
       CASE WHEN r.peak THEN p.peak ELSE p.off_peak END, r.label
FROM (VALUES ('HOT_DESK', 25000, 18000),
             ('FIXED_DESK', 35000, 25000),
             ('FOCUS_BOOTH', 45000, 35000),
             ('MEETING_ROOM', 150000, 120000),
             ('EVENT_SPACE', 500000, 400000)) AS p(type_code, peak, off_peak)
JOIN resource_type rt ON rt.code = p.type_code
CROSS JOIN (VALUES ('WEEKDAY', 0, 540, false, 'Thấp điểm sáng'),
                   ('WEEKDAY', 540, 1080, true, 'Cao điểm'),
                   ('WEEKDAY', 1080, 1440, false, 'Thấp điểm tối'),
                   ('WEEKEND', 0, 1440, false, 'Cuối tuần')) AS r(day_type, start_minute, end_minute, peak, label)
ORDER BY rt.id, r.day_type, r.start_minute;

-- ---------------------------------------------------------------------
-- customer
-- ---------------------------------------------------------------------
-- Khách 1, 2, 4 đã đăng ký tài khoản (UC01, mật khẩu demo Cowork@123); khách 3, 5 là khách vãng lai (password_hash rỗng).
INSERT INTO customer (id, full_name, phone, email, created_at, version, phone_verified_at, notify_before_minutes, preferred_channel, password_hash)
VALUES (1, 'Nguyễn Văn An', '0912345678', 'an.nguyen@example.com', '2026-09-20 10:00', 0, '2026-09-20 10:01', 15, 'SMS', '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G'),
       (2, 'Trần Thị Bình', '0987654321', 'binh.tran@example.com', '2026-09-25 14:30', 0, '2026-09-25 14:31', 30, 'ZALO', '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G'),
       (3, 'Lê Hoàng Cường', '0901234567', NULL, '2026-10-01 09:55', 0, NULL, 15, 'SMS', NULL),
       (4, 'Phạm Minh Dung', '+84933445566', 'dung.pham@example.com', '2026-10-30 14:50', 0, '2026-10-30 14:51', 10, 'EMAIL', '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G'),
       (5, 'Võ Quốc Em', '0977889900', NULL, '2026-10-13 11:55', 0, NULL, 15, 'SMS', NULL);

-- ---------------------------------------------------------------------
-- booking — mỗi đơn minh họa một trạng thái
--   1 COMPLETED   HD-01  T2 05/10 09:00–11:00  4 block × 25.000              = 100.000
--   2 COMPLETED   MR-02  T3 06/10 09:00–11:30  thuê 600.000 + máy chiếu 1 × 30.000 × 4 block + gia hạn 150.000 + quá giờ 150.000 = 1.020.000
--   3 CANCELLED   FD-01  T7 10/10 13:00–17:00  8 block × 25.000 (cuối tuần)  = 200.000, hủy trước ≥ 2h → hoàn 100%
--   4 CONFIRMED   FB-01  T2 07/12 14:00–16:00  4 block × 45.000              = 180.000
--   5 CONFIRMED   MR-01  T5 10/12 08:00–10:00  2 × 120.000 + 2 × 150.000 = 540.000, máy chiếu 120.000, 2 micro 120.000 = 780.000
--   6 NO_SHOW     HD-02  T4 14/10 18:00–20:00  4 block × 18.000              =  72.000
--   7 FAILED      HD-03  T3 20/10 10:00–11:00  2 block × 25.000 − voucher WELCOME10 5.000 =  45.000, thanh toán thất bại
--   8 EXPIRED     HD-04  T4 21/10 15:00–16:00  2 block × 25.000 − membership 30 phút 25.000 =  25.000, hết 10 phút giữ chỗ
-- ---------------------------------------------------------------------
INSERT INTO booking (id, code, qr_token, customer_id, resource_id, start_at, end_at, party_size, status, paid_amount,
                     hold_expires_at, checked_in_at, checked_out_at, created_at, version)
VALUES (1, 'BKHD2N5Q7R', 'demo-qr-7Gm2Kp9xLq4Vt8Rz1Nw6Yb3Hc5Jd0Fs', 1, 1, '2026-10-05 09:00', '2026-10-05 11:00', 1, 'COMPLETED', 100000,
        '2026-10-04 20:10', '2026-10-05 08:55', '2026-10-05 10:58', '2026-10-04 20:00', 3),
       (2, 'BKMR8T3W6Y', 'demo-qr-Qx4Lm8Np2Rt6Vz9Kc3Hb7Jd1Fs5Gw0Ye', 2, 13, '2026-10-06 09:00', '2026-10-06 11:30', 8, 'COMPLETED', 1020000,
        '2026-10-05 19:10', '2026-10-06 08:50', '2026-10-06 11:50', '2026-10-05 19:00', 5),
       (3, 'BKFD4K9P2L', 'demo-qr-Zt3Wq7Ex1Rb5Yn9Um2Ic6Op4As8Dk0Fj', 3, 7, '2026-10-10 13:00', '2026-10-10 17:00', 1, 'CANCELLED', 200000,
        '2026-10-01 10:10', NULL, NULL, '2026-10-01 10:00', 2),
       (4, 'BKFB7X3M5N', 'demo-qr-Lk2Jh6Gf0Ds4Aq8Wz3Xc7Vb1Nm5Qp9Ry', 1, 10, '2026-12-07 14:00', '2026-12-07 16:00', 1, 'CONFIRMED', 180000,
        '2026-11-01 09:10', NULL, NULL, '2026-11-01 09:00', 1),
       (5, 'BKMR2C6V8S', 'demo-qr-Pn5Bm9Vc3Xz7Lk1Jh4Gf8Ds2Aq6Wt0Ey', 4, 12, '2026-12-10 08:00', '2026-12-10 10:00', 5, 'CONFIRMED', 780000,
        '2026-10-30 15:10', NULL, NULL, '2026-10-30 15:00', 1),
       (6, 'BKHD9G4J3T', 'demo-qr-Ry8Tu2Io6Pa4Sd0Fg3Hj7Kl1Zx5Cv9Bn', 5, 2, '2026-10-14 18:00', '2026-10-14 20:00', 1, 'NO_SHOW', 72000,
        '2026-10-13 12:10', NULL, NULL, '2026-10-13 12:00', 2),
       (7, 'BKHD5E7U2W', 'demo-qr-Mw3Ne7Rb1Ty5Ui9Op2As6Df0Gh4Jk8Lz', 2, 3, '2026-10-20 10:00', '2026-10-20 11:00', 1, 'FAILED', 0,
        '2026-10-19 21:10', NULL, NULL, '2026-10-19 21:00', 1),
       (8, 'BKHD3Z8F6H', 'demo-qr-Xc6Vb0Nm4Qw8Er2Ty7Ui1Op5As9Df3Gh', 3, 4, '2026-10-21 15:00', '2026-10-21 16:00', 1, 'EXPIRED', 0,
        '2026-10-20 08:10', NULL, NULL, '2026-10-20 08:00', 1);

INSERT INTO booking_charge (booking_id, line_no, charge_type, amount, start_at, end_at, created_at)
VALUES (1, 0, 'RENTAL', 100000, '2026-10-05 09:00', '2026-10-05 11:00', '2026-10-04 20:00'),
       (2, 0, 'RENTAL', 600000, '2026-10-06 09:00', '2026-10-06 11:00', '2026-10-05 19:00'),
       (2, 1, 'ADDON', 120000, '2026-10-06 09:00', '2026-10-06 11:00', '2026-10-05 19:00'),
       (2, 2, 'EXTENSION', 150000, '2026-10-06 11:00', '2026-10-06 11:30', '2026-10-06 10:50'),
       (2, 3, 'OVERSTAY_SURCHARGE', 150000, '2026-10-06 11:30', '2026-10-06 11:50', '2026-10-06 11:50'),
       (3, 0, 'RENTAL', 200000, '2026-10-10 13:00', '2026-10-10 17:00', '2026-10-01 10:00'),
       (4, 0, 'RENTAL', 180000, '2026-12-07 14:00', '2026-12-07 16:00', '2026-11-01 09:00'),
       (5, 0, 'RENTAL', 540000, '2026-12-10 08:00', '2026-12-10 10:00', '2026-10-30 15:00'),
       (5, 1, 'ADDON', 120000, '2026-12-10 08:00', '2026-12-10 10:00', '2026-10-30 15:00'),
       (5, 2, 'ADDON', 120000, '2026-12-10 08:00', '2026-12-10 10:00', '2026-10-30 15:00'),
       (6, 0, 'RENTAL', 72000, '2026-10-14 18:00', '2026-10-14 20:00', '2026-10-13 12:00'),
       (7, 0, 'RENTAL', 50000, '2026-10-20 10:00', '2026-10-20 11:00', '2026-10-19 21:00'),
       (7, 1, 'VOUCHER_DISCOUNT', 5000, '2026-10-20 10:00', '2026-10-20 11:00', '2026-10-19 21:00'),
       (8, 0, 'RENTAL', 50000, '2026-10-21 15:00', '2026-10-21 16:00', '2026-10-20 08:00'),
       (8, 1, 'MEMBERSHIP_DEDUCTION', 25000, '2026-10-21 15:00', '2026-10-21 16:00', '2026-10-20 08:00');

INSERT INTO booking_status_history (booking_id, line_no, from_status, to_status, changed_at)
VALUES (1, 0, NULL, 'HELD', '2026-10-04 20:00'),
       (1, 1, 'HELD', 'CONFIRMED', '2026-10-04 20:03'),
       (1, 2, 'CONFIRMED', 'CHECKED_IN', '2026-10-05 08:55'),
       (1, 3, 'CHECKED_IN', 'COMPLETED', '2026-10-05 10:58'),
       (2, 0, NULL, 'HELD', '2026-10-05 19:00'),
       (2, 1, 'HELD', 'CONFIRMED', '2026-10-05 19:02'),
       (2, 2, 'CONFIRMED', 'CHECKED_IN', '2026-10-06 08:50'),
       (2, 3, 'CHECKED_IN', 'CHECKED_OUT', '2026-10-06 11:50'),
       (2, 4, 'CHECKED_OUT', 'COMPLETED', '2026-10-06 11:55'),
       (3, 0, NULL, 'HELD', '2026-10-01 10:00'),
       (3, 1, 'HELD', 'CONFIRMED', '2026-10-01 10:02'),
       (3, 2, 'CONFIRMED', 'CANCELLED', '2026-10-08 09:00'),
       (4, 0, NULL, 'HELD', '2026-11-01 09:00'),
       (4, 1, 'HELD', 'CONFIRMED', '2026-11-01 09:03'),
       (5, 0, NULL, 'HELD', '2026-10-30 15:00'),
       (5, 1, 'HELD', 'CONFIRMED', '2026-10-30 15:04'),
       (6, 0, NULL, 'HELD', '2026-10-13 12:00'),
       (6, 1, 'HELD', 'CONFIRMED', '2026-10-13 12:02'),
       (6, 2, 'CONFIRMED', 'NO_SHOW', '2026-10-14 18:30'),
       (7, 0, NULL, 'HELD', '2026-10-19 21:00'),
       (7, 1, 'HELD', 'FAILED', '2026-10-19 21:05'),
       (8, 0, NULL, 'HELD', '2026-10-20 08:00'),
       (8, 1, 'HELD', 'EXPIRED', '2026-10-20 08:10');

-- ---------------------------------------------------------------------
-- payment — đơn 2 thu nốt 300.000 tại quầy (settle) nên chỉ có một giao dịch qua cổng (thuê + add-on = 720.000)
-- ---------------------------------------------------------------------
INSERT INTO payment (id, booking_id, amount, method, status, idempotency_key, gateway_transaction_id, checkout_url,
                     created_at, completed_at, version)
VALUES (1, 1, 100000, 'VIETQR', 'SUCCEEDED', '1:demo-payment-0001', 'SBX-DEMO-0001', '/api/payments/sandbox/SBX-DEMO-0001/complete', '2026-10-04 20:01', '2026-10-04 20:03', 1),
       (2, 2, 720000, 'CARD', 'SUCCEEDED', '2:demo-payment-0002', 'SBX-DEMO-0002', '/api/payments/sandbox/SBX-DEMO-0002/complete', '2026-10-05 19:01', '2026-10-05 19:02', 1),
       (3, 3, 200000, 'E_WALLET', 'SUCCEEDED', '3:demo-payment-0003', 'SBX-DEMO-0003', '/api/payments/sandbox/SBX-DEMO-0003/complete', '2026-10-01 10:01', '2026-10-01 10:02', 1),
       (4, 4, 180000, 'VIETQR', 'SUCCEEDED', '4:demo-payment-0004', 'SBX-DEMO-0004', '/api/payments/sandbox/SBX-DEMO-0004/complete', '2026-11-01 09:01', '2026-11-01 09:03', 1),
       (5, 5, 780000, 'VIETQR', 'SUCCEEDED', '5:demo-payment-0005', 'SBX-DEMO-0005', '/api/payments/sandbox/SBX-DEMO-0005/complete', '2026-10-30 15:01', '2026-10-30 15:04', 1),
       (6, 6, 72000, 'E_WALLET', 'SUCCEEDED', '6:demo-payment-0006', 'SBX-DEMO-0006', '/api/payments/sandbox/SBX-DEMO-0006/complete', '2026-10-13 12:01', '2026-10-13 12:02', 1),
       (7, 7, 45000, 'CARD', 'FAILED', '7:demo-payment-0007', 'SBX-DEMO-0007', '/api/payments/sandbox/SBX-DEMO-0007/complete', '2026-10-19 21:01', '2026-10-19 21:05', 1);


-- #####################################################################
-- PHẦN 2 — CHỨC NĂNG SẼ LÀM SAU
-- #####################################################################

-- ---------------------------------------------------------------------
-- amenity, resource_amenity
-- ---------------------------------------------------------------------
INSERT INTO amenity (id, code, name)
VALUES (1, 'POWER_OUTLET', 'Ổ cắm điện'),
       (2, 'MONITOR', 'Màn hình rời'),
       (3, 'WHITEBOARD', 'Bảng trắng'),
       (4, 'TV', 'TV trình chiếu'),
       (5, 'QUIET', 'Khu yên tĩnh'),
       (6, 'SOUND_SYSTEM', 'Hệ thống âm thanh');

INSERT INTO resource_amenity (id, resource_id, amenity_id, quantity)
VALUES (1, 1, 1, 2), (2, 2, 1, 2), (3, 3, 1, 2), (4, 4, 1, 2), (5, 5, 1, 2), (6, 6, 1, 2),
       (7, 7, 1, 4), (8, 7, 2, 1), (9, 8, 1, 4), (10, 8, 2, 1), (11, 9, 1, 4),
       (12, 10, 1, 2), (13, 10, 5, 1), (14, 11, 1, 2), (15, 11, 5, 1),
       (16, 12, 1, 6), (17, 12, 3, 1), (18, 12, 4, 1),
       (19, 13, 1, 12), (20, 13, 3, 2), (21, 13, 4, 1),
       (22, 14, 1, 20), (23, 14, 6, 1), (24, 14, 4, 2),
       (25, 15, 1, 2);

-- ---------------------------------------------------------------------
-- staff — UC23: đăng nhập POST /api/auth/staff/login, mật khẩu demo của mọi tài khoản: Cowork@123 (BCrypt).
--         Tài khoản 5 bị khóa (LOCKED) nên không đăng nhập được.
-- ---------------------------------------------------------------------
INSERT INTO staff (id, branch_id, full_name, email, password_hash, role, compensation_limit, status, created_at)
VALUES (1, NULL, 'Quản trị hệ thống', 'admin@cowork.example', '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G', 'ADMIN', 0, 'ACTIVE', '2026-01-10 08:00'),
       (2, 1, 'Đỗ Thu Hà', 'ha.do@cowork.example', '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G', 'MANAGER', 1000000, 'ACTIVE', '2026-01-10 08:00'),
       (3, 1, 'Ngô Gia Huy', 'huy.ngo@cowork.example', '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G', 'RECEPTIONIST', 100000, 'ACTIVE', '2026-02-01 08:00'),
       (4, 1, 'Bùi Văn Khoa', 'khoa.bui@cowork.example', '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G', 'TECHNICIAN', 0, 'ACTIVE', '2026-02-01 08:00'),
       (5, 2, 'Hoàng Mai Lan', 'lan.hoang@cowork.example', '$2a$10$ri9HZbdmbgrrVOrwGqkUDujJQr2qtfCuqzIb0cDJgL8PgeuMmSf1G', 'RECEPTIONIST', 100000, 'LOCKED', '2026-06-01 08:00');

-- ---------------------------------------------------------------------
-- membership_plan, membership_plan_resource_type, membership_pass, membership_usage
-- ---------------------------------------------------------------------
INSERT INTO membership_plan (id, name, total_minutes, valid_days, price, status)
VALUES (1, 'Gói Hot Desk 20 giờ', 1200, 30, 900000, 'ACTIVE'),
       (2, 'Gói Linh hoạt 50 giờ', 3000, 60, 2000000, 'ACTIVE'),
       (3, 'Gói thử 5 giờ', 300, 7, 200000, 'STOPPED');

INSERT INTO membership_plan_resource_type (plan_id, resource_type_id)
SELECT v.plan_id, rt.id
FROM (VALUES (1, 'HOT_DESK'),
             (2, 'HOT_DESK'), (2, 'FIXED_DESK'), (2, 'FOCUS_BOOTH'),
             (3, 'HOT_DESK')) AS v(plan_id, type_code)
JOIN resource_type rt ON rt.code = v.type_code;

INSERT INTO membership_pass (id, customer_id, plan_id, remaining_minutes, purchased_at, expires_at, status, version)
VALUES (1, 3, 1, 1200, '2026-10-15 10:00', '2026-11-14 10:00', 'ACTIVE', 1),
       (2, 1, 2, 2400, '2026-09-20 10:30', '2026-11-19 10:30', 'ACTIVE', 1),
       (3, 5, 3, 0, '2026-09-01 09:00', '2026-09-08 09:00', 'USED_UP', 1);

-- Đơn 8 giữ chỗ dùng 30 phút của gói nhưng hết hạn giữ chỗ → số phút được trả lại cho gói (RELEASED)
INSERT INTO membership_usage (id, pass_id, booking_id, minutes, status, used_at)
VALUES (1, 1, 8, 30, 'RELEASED', '2026-10-20 08:00');

-- ---------------------------------------------------------------------
-- voucher, voucher_resource_type, voucher_redemption
-- ---------------------------------------------------------------------
INSERT INTO voucher (id, code, name, discount_type, discount_value, min_order_amount, max_discount_amount,
                     valid_from, valid_to, total_limit, per_customer_limit, used_count, status, version)
VALUES (1, 'WELCOME10', 'Giảm 10% cho khách mới', 'PERCENT', 10, 0, 50000, '2026-09-01 00:00', '2026-12-31 23:59', NULL, 1, 0, 'ACTIVE', 0),
       (2, 'HOP50K', 'Giảm 50.000đ khi đặt phòng họp', 'AMOUNT', 50000, 300000, NULL, '2026-10-01 00:00', '2026-12-31 23:59', 100, 2, 0, 'ACTIVE', 0),
       (3, 'XINLOI30', 'Tặng 30 phút đền bù sự cố', 'FREE_MINUTES', 30, 0, NULL, '2026-10-06 00:00', '2027-01-06 23:59', 1, 1, 0, 'ACTIVE', 0),
       (4, 'TET2026', 'Khuyến mãi Tết (tạm dừng)', 'PERCENT', 20, 100000, 100000, '2026-01-20 00:00', '2026-02-20 23:59', 500, 1, 0, 'PAUSED', 0);

-- Voucher 1, 3, 4 không có dòng nào → áp dụng mọi loại chỗ
INSERT INTO voucher_resource_type (voucher_id, resource_type_id)
SELECT 2, id FROM resource_type WHERE code IN ('MEETING_ROOM', 'EVENT_SPACE');

-- Đơn 7 thanh toán thất bại nên voucher đã giữ được trả lại (RELEASED), không tính vào used_count
INSERT INTO voucher_redemption (id, voucher_id, booking_id, customer_id, discount_amount, status, created_at)
VALUES (1, 1, 7, 2, 5000, 'RELEASED', '2026-10-19 21:00');

-- ---------------------------------------------------------------------
-- addon, booking_addon — FR-BOOK-02: tiền add-on (đơn giá block × số block × số lượng) là dòng ADDON trong booking_charge
-- ---------------------------------------------------------------------
INSERT INTO addon (id, branch_id, code, name, stock, price_per_block, status)
VALUES (1, 1, 'PROJECTOR', 'Máy chiếu', 2, 30000, 'ACTIVE'),
       (2, 1, 'EXTRA_MONITOR', 'Màn hình phụ 27 inch', 5, 10000, 'ACTIVE'),
       (3, 1, 'MIC', 'Micro không dây', 4, 15000, 'ACTIVE'),
       (4, 2, 'PROJECTOR', 'Máy chiếu', 1, 30000, 'INACTIVE');

INSERT INTO booking_addon (id, booking_id, addon_id, quantity, unit_price)
VALUES (1, 2, 1, 1, 30000),
       (2, 5, 1, 1, 30000),
       (3, 5, 3, 2, 15000);

-- ---------------------------------------------------------------------
-- menu_category, menu_item, service_order, service_order_item
-- ---------------------------------------------------------------------
INSERT INTO menu_category (id, name, sort_order)
VALUES (1, 'Cà phê', 1),
       (2, 'Trà và nước ép', 2),
       (3, 'Đồ ăn nhẹ', 3);

INSERT INTO menu_item (id, branch_id, category_id, name, price, is_available, is_drink)
VALUES (1, 1, 1, 'Cà phê sữa đá', 29000, true, true),
       (2, 1, 1, 'Bạc xỉu', 32000, true, true),
       (3, 1, 2, 'Trà đào cam sả', 39000, true, true),
       (4, 1, 2, 'Nước cam ép', 35000, false, true),
       (5, 1, 3, 'Bánh mì thịt', 25000, true, false),
       (6, 1, 3, 'Bánh croissant', 30000, true, false),
       (7, 2, 1, 'Cà phê đen', 25000, true, true);

INSERT INTO service_order (id, booking_id, status, note, handled_by, created_at)
VALUES (1, 1, 'DELIVERED', NULL, 3, '2026-10-05 09:15'),
       (2, 2, 'DELIVERED', 'Mang vào phòng họp lúc 10 giờ', 3, '2026-10-06 09:05'),
       (3, 2, 'REJECTED', 'Hết nước cam', 3, '2026-10-06 10:20');

-- Cốc nước đầu tiên của mỗi đơn miễn phí (BR-10) nên unit_price = 0
INSERT INTO service_order_item (id, order_id, menu_item_id, quantity, unit_price, is_free_drink)
VALUES (1, 1, 1, 1, 0, true),
       (2, 1, 5, 1, 25000, false),
       (3, 2, 2, 1, 0, true),
       (4, 2, 3, 7, 39000, false),
       (5, 2, 6, 8, 30000, false),
       (6, 3, 4, 2, 35000, false);

-- ---------------------------------------------------------------------
-- refund, invoice, invoice_line
-- ---------------------------------------------------------------------
INSERT INTO refund (id, payment_id, amount, reason, status, gateway_refund_id, attempts, requested_at, completed_at)
VALUES (1, 3, 200000, 'CUSTOMER_CANCELLED', 'REFUNDED', 'SBX-RF-DEMO-0001', 1, '2026-10-08 09:00', '2026-10-08 09:02');

INSERT INTO invoice (id, booking_id, invoice_no, total_amount, e_invoice_lookup_code, issued_at)
VALUES (1, 1, 'INV-2026-000001', 100000, 'TRACUU-7KQ2M9', '2026-10-05 10:58'),
       (2, 2, 'INV-2026-000002', 1020000, 'TRACUU-3XW8P5', '2026-10-06 11:55');

INSERT INTO invoice_line (id, invoice_id, line_no, description, quantity, unit_price, amount)
VALUES (1, 1, 1, 'Thuê Hot Desk HD-01, 05/10/2026 09:00–11:00', 4, 25000, 100000),
       (2, 2, 1, 'Thuê phòng họp MR-02, 06/10/2026 09:00–11:00', 4, 150000, 600000),
       (3, 2, 2, 'Gia hạn phòng họp MR-02, 11:00–11:30', 1, 150000, 150000),
       (5, 2, 4, 'Thuê kèm máy chiếu, 09:00–11:00 (4 block)', 4, 30000, 120000),
       (4, 2, 3, 'Phụ phí quá giờ MR-02, 11:30–11:50', 1, 150000, 150000);

-- ---------------------------------------------------------------------
-- ticket, ticket_attachment, compensation
-- ---------------------------------------------------------------------
INSERT INTO ticket (id, resource_id, booking_id, reported_by_customer, category, priority, description, status,
                    assignee_id, created_at, resolved_at)
VALUES (1, 13, 2, 2, 'AIR_CON', 'HIGH', 'Điều hòa phòng họp lớn không mát, phòng rất nóng.', 'RESOLVED', 4, '2026-10-06 09:20', '2026-10-06 09:50'),
       (2, 5, NULL, NULL, 'NETWORK', 'NORMAL', 'Cổng mạng LAN ở HD-05 không nhận cáp.', 'IN_PROGRESS', 4, '2026-10-28 08:30', NULL),
       (3, 10, NULL, 1, 'CLEANING', 'LOW', 'Bàn trong Focus Booth 1 còn vết cà phê.', 'CLOSED', 3, '2026-10-15 14:00', '2026-10-15 14:20');

INSERT INTO ticket_attachment (id, ticket_id, url, uploaded_at)
VALUES (1, 1, '/uploads/tickets/1/dieu-hoa.jpg', '2026-10-06 09:21'),
       (2, 2, '/uploads/tickets/2/cong-lan.jpg', '2026-10-28 08:31');

-- Đền bù cho sự cố điều hòa ở đơn 2: một đồ uống (lễ tân tự duyệt) và voucher 30 phút (quản lý duyệt)
INSERT INTO compensation (id, booking_id, ticket_id, type, value, voucher_id, created_by, approved_by, created_at)
VALUES (1, 2, 1, 'FREE_DRINK', 1, NULL, 3, NULL, '2026-10-06 09:55'),
       (2, 2, 1, 'VOUCHER', 30, 3, 3, 2, '2026-10-06 10:00');

-- ---------------------------------------------------------------------
-- notification_job, audit_log, outbox_event
-- ---------------------------------------------------------------------
INSERT INTO notification_job (id, customer_id, booking_id, type, channel, send_at, status, attempts, sent_at, last_error)
VALUES (1, 1, 1, 'BOOKING_CONFIRMED', 'SMS', '2026-10-04 20:03', 'SENT', 1, '2026-10-04 20:03', NULL),
       (2, 1, 4, 'BOOKING_CONFIRMED', 'SMS', '2026-11-01 09:03', 'SENT', 1, '2026-11-01 09:03', NULL),
       (3, 1, 4, 'CHECK_IN_REMINDER', 'SMS', '2026-12-07 13:45', 'SCHEDULED', 0, NULL, NULL),
       (4, 4, 5, 'CHECK_IN_REMINDER', 'EMAIL', '2026-12-10 07:50', 'SCHEDULED', 0, NULL, NULL),
       (5, 3, 3, 'REFUND', 'SMS', '2026-10-08 09:02', 'FAILED', 3, NULL, 'Nhà mạng từ chối: số điện thoại tạm khóa'),
       (6, 2, 2, 'COMPENSATION', 'ZALO', '2026-10-06 10:00', 'SENT', 1, '2026-10-06 10:00', NULL);

INSERT INTO audit_log (id, actor_id, action, entity_type, entity_id, before_data, after_data, reason, created_at)
VALUES (1, 4, 'RESOURCE_MAINTENANCE', 'space_resource', 5,
        '{"status": "ACTIVE"}', '{"status": "MAINTENANCE"}', 'Cổng mạng hỏng, xem ticket 2', '2026-10-28 08:35'),
       (2, 2, 'COMPENSATION_APPROVED', 'compensation', 2,
        NULL, '{"type": "VOUCHER", "value": 30, "voucherCode": "XINLOI30"}', 'Vượt hạn mức của lễ tân', '2026-10-06 10:00'),
       (3, NULL, 'BOOKING_EXPIRED', 'booking', 8,
        '{"status": "HELD"}', '{"status": "EXPIRED"}', 'Job tự động: quá 10 phút giữ chỗ', '2026-10-20 08:10');

INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at)
VALUES (1, 'booking', 4, 'BookingConfirmed', '{"bookingId": 4, "code": "BKFB7X3M5N", "customerId": 1}', '2026-11-01 09:03', '2026-11-01 09:03'),
       (2, 'booking', 5, 'BookingConfirmed', '{"bookingId": 5, "code": "BKMR2C6V8S", "customerId": 4}', '2026-10-30 15:04', NULL);


-- #####################################################################
-- Đặt lại bộ đếm id tự tăng = id lớn nhất đang có, để dòng ứng dụng thêm sau lấy id tiếp theo.
-- #####################################################################
DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT table_name FROM information_schema.columns
             WHERE table_schema = 'public' AND column_name = 'id' AND is_identity = 'YES'
    LOOP
        EXECUTE format('SELECT setval(pg_get_serial_sequence(%L, ''id''), COALESCE((SELECT max(id) FROM %I), 0) + 1, false)', t, t);
    END LOOP;
END
$$;

COMMIT;
