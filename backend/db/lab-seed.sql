-- =====================================================================
-- Dữ liệu lớn để luyện EXPLAIN ANALYZE và index — CHỈ dùng cho database working-space-lab
--
-- CÁCH CHẠY (pgAdmin 4, đăng nhập bằng postgres):
--   1. Tạo database mới tên đúng là working-space-lab.
--   2. Chọn database đó → Tools → Query Tool, chạy lần lượt: db/schema.sql → db/data.sql → file này.
--   3. Muốn chạy lại: chạy lại schema.sql và data.sql trước (làm mới toàn bộ database).
--
-- ⚠ DUNG LƯỢNG: đã đo với 1 triệu đơn, database khoảng 414 MB, cả thư mục dữ liệu 1,3 GB (có WAL) và hết khoảng 40 giây.
--   Chuẩn bị ít nhất 15 GB trống cho 10 triệu đơn, trên ổ chứa thư mục dữ liệu của PostgreSQL.
--   Ổ đầy thì server PostgreSQL sẽ dừng đột ngột, ảnh hưởng mọi database trên server đó.
-- - Chạy nhầm database khác hoặc chạy lần thứ hai thì dừng ngay, không đổi gì.
-- - Cả file là một transaction: lỗi ở đâu thì không có gì được thêm.
-- - Muốn chạy thử nhỏ trước: sửa số trong lab_config, ví dụ bookings = 100000.
--
-- Dữ liệu tạo ra: khách (SĐT 07xxxxxxxx), chỗ LAB-xxxx, đơn LB... và khoản tiền RENTAL.
-- - Đơn lịch sử trải đều 3 năm, id càng lớn thì càng gần hiện tại, giống bảng chạy thật.
-- - Một số ít khách đặt rất nhiều, phần lớn đặt ít, để truy vấn theo khách có độ lệch thật.
-- - Không tạo booking_status_history và payment, vì bài tập chỉ truy vấn booking và booking_charge.
-- =====================================================================

BEGIN;

DO $$
BEGIN
    IF current_database() <> 'working-space-lab' THEN
        RAISE EXCEPTION 'lab-seed.sql chỉ chạy trên database working-space-lab, đang ở %', current_database();
    END IF;
    IF EXISTS (SELECT FROM space_resource WHERE code = 'LAB-0001') THEN
        RAISE EXCEPTION 'Đã seed rồi. Chạy lại schema.sql và data.sql trước khi seed lần nữa';
    END IF;
    IF NOT EXISTS (SELECT FROM zone) THEN
        RAISE EXCEPTION 'Chưa có dữ liệu mẫu. Chạy db/data.sql trước';
    END IF;
END $$;

-- Chỉnh quy mô ở đây.
CREATE TEMP TABLE lab_config ON COMMIT DROP AS
SELECT 500000   AS customers,
       200      AS resources,
       10000000 AS bookings,
       1095     AS history_days;

SET LOCAL synchronous_commit = off;
SET LOCAL maintenance_work_mem = '512MB';
SELECT setseed(0.42); -- cùng một bộ dữ liệu ở mọi lần chạy

-- ---------------------------------------------------------------------
-- Tạm gỡ khóa ngoại, unique và index thường để nạp nhanh; cuối file tạo lại từ đúng định nghĩa này.
-- Giữ khóa chính, CHECK, EXCLUDE và index từng phần (chỉ phủ đơn đang hiệu lực).
-- ---------------------------------------------------------------------
CREATE TEMP TABLE lab_saved_ddl ON COMMIT DROP AS
SELECT format('ALTER TABLE %I ADD CONSTRAINT %I %s', c.conrelid::regclass, c.conname, pg_get_constraintdef(c.oid)) AS restore_sql,
       format('ALTER TABLE %I DROP CONSTRAINT %I', c.conrelid::regclass, c.conname) AS drop_sql
FROM pg_constraint c
WHERE c.conrelid IN ('booking'::regclass, 'booking_charge'::regclass)
  AND c.contype IN ('f', 'u')
UNION ALL
SELECT pg_get_indexdef(i.indexrelid),
       format('DROP INDEX %s', i.indexrelid::regclass)
FROM pg_index i
WHERE i.indrelid IN ('booking'::regclass, 'booking_charge'::regclass)
  AND i.indpred IS NULL
  AND NOT i.indisprimary
  AND NOT EXISTS (SELECT FROM pg_constraint c WHERE c.conindid = i.indexrelid);

DO $$
DECLARE
    statement text;
BEGIN
    FOR statement IN SELECT drop_sql FROM lab_saved_ddl LOOP
        EXECUTE statement;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- Khách và chỗ
-- ---------------------------------------------------------------------
INSERT INTO customer (full_name, phone, email, created_at, version)
SELECT 'Khách lab ' || n,
       '07' || lpad(n::text, 8, '0'),
       'lab' || n || '@example.test',
       now()::timestamp - make_interval(days => (random() * 1200)::int),
       0
FROM lab_config, generate_series(1, customers) AS n;

INSERT INTO space_resource (code, name, resource_type_id, capacity, status, zone_id, version)
SELECT 'LAB-' || lpad(n::text, 4, '0'),
       'Chỗ lab ' || n,
       (SELECT id FROM resource_type WHERE code = 'HOT_DESK'),
       1 + n % 4,
       'ACTIVE',
       (SELECT min(id) FROM zone),
       0
FROM lab_config, generate_series(1, resources) AS n;

CREATE TEMP TABLE lab_range ON COMMIT DROP AS
SELECT (SELECT min(id) FROM customer WHERE phone LIKE '07%')             AS first_customer,
       (SELECT min(id) FROM space_resource WHERE code LIKE 'LAB-%')      AS first_resource,
       date_trunc('day', now())::timestamp - make_interval(days => history_days) AS history_start
FROM lab_config;

-- ---------------------------------------------------------------------
-- Đơn lịch sử: COMPLETED là chủ yếu; các trạng thái này không bị ràng buộc chống trùng chỉ.
-- ---------------------------------------------------------------------
INSERT INTO booking (code, qr_token, customer_id, resource_id, start_at, end_at, party_size, status, paid_amount,
                     hold_expires_at, checked_in_at, checked_out_at, created_at, version)
SELECT 'LB' || lpad(n::text, 9, '0'),
       md5('lab-' || n),
       first_customer + floor(customers * customer_pick * customer_pick)::bigint,
       first_resource + floor(resources * resource_pick)::bigint,
       start_at,
       start_at + make_interval(mins => blocks * 30),
       1,
       status,
       CASE WHEN status IN ('COMPLETED', 'NO_SHOW') THEN blocks * 25000 ELSE 0 END,
       CASE WHEN status IN ('EXPIRED', 'FAILED') THEN start_at - interval '1 day' + interval '10 minutes' END,
       CASE WHEN status = 'COMPLETED' THEN start_at END,
       CASE WHEN status = 'COMPLETED' THEN start_at + make_interval(mins => blocks * 30) END,
       start_at - interval '1 day',
       0
FROM (SELECT n, customers, resources, first_customer, first_resource,
             random() AS customer_pick,
             random() AS resource_pick,
             1 + floor(random() * 8)::int AS blocks,
             -- ngày tăng dần theo n; giờ bắt đầu từ 07:00 đến 19:30, bước 30 phút
             history_start
                 + make_interval(days => floor(n::numeric * history_days / bookings)::int)
                 + make_interval(mins => 420 + 30 * floor(random() * 26)::int) AS start_at,
             CASE
                 WHEN status_pick < 0.92  THEN 'COMPLETED'
                 WHEN status_pick < 0.96  THEN 'CANCELLED'
                 WHEN status_pick < 0.98  THEN 'EXPIRED'
                 WHEN status_pick < 0.995 THEN 'NO_SHOW'
                 ELSE 'FAILED'
             END AS status
      FROM lab_config, lab_range, generate_series(1, bookings) AS n, LATERAL (SELECT random() + n * 0 AS status_pick) AS pick
     ) AS generated;

-- ---------------------------------------------------------------------
-- Đơn đang hiệu lực: 30 ngày tới, mỗi chỗ 4 khung 2 giờ không chồng nhau (qua được ex_booking_no_overlap).
-- ---------------------------------------------------------------------
INSERT INTO booking (code, qr_token, customer_id, resource_id, start_at, end_at, party_size, status, paid_amount,
                     created_at, version)
SELECT 'LC' || lpad(row_number() OVER ()::text, 9, '0'),
       md5('lab-active-' || r.id || '-' || d || '-' || h),
       first_customer + floor(customers * random())::bigint,
       r.id,
       date_trunc('day', now())::timestamp + make_interval(days => d, hours => h),
       date_trunc('day', now())::timestamp + make_interval(days => d, hours => h + 2),
       1,
       'CONFIRMED',
       100000,
       now()::timestamp,
       0
FROM lab_config, lab_range,
     space_resource r,
     generate_series(1, 30) AS d,
     unnest(ARRAY[8, 10, 14, 16]) AS h
WHERE r.code LIKE 'LAB-%';

-- ---------------------------------------------------------------------
-- Khoản tiền thuê của các đơn đã thu tiền
-- ---------------------------------------------------------------------
INSERT INTO booking_charge (booking_id, line_no, charge_type, amount, start_at, end_at, created_at)
SELECT id, 0, 'RENTAL', paid_amount, start_at, end_at, created_at
FROM booking
WHERE code LIKE 'L%' AND paid_amount > 0
ORDER BY id;

-- ---------------------------------------------------------------------
-- Tạo lại những gì đã gỡ (kiểm tra luôn khóa ngoại và unique trên toàn bộ dữ liệu)
-- ---------------------------------------------------------------------
DO $$
DECLARE
    statement text;
BEGIN
    FOR statement IN SELECT restore_sql FROM lab_saved_ddl LOOP
        EXECUTE statement;
    END LOOP;
END $$;

ANALYZE customer;
ANALYZE space_resource;
ANALYZE booking;
ANALYZE booking_charge;

COMMIT;

-- Kết quả
SELECT status, count(*) AS bookings FROM booking GROUP BY status ORDER BY bookings DESC;
