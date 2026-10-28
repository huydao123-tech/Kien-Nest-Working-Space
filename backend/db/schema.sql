-- =====================================================================
-- Cơ sở dữ liệu hệ thống đặt chỗ Co-Working Space — PostgreSQL 16+
-- Thiết kế và giải thích từng bảng: docs/database.md
--
-- CÁCH CHẠY (pgAdmin 4):
--   1. Cây bên trái: chọn database của dự án (ví dụ working-space) → menu Tools → Query Tool.
--   2. Dán TOÀN BỘ file này vào → bấm Execute (F5).
--   3. Tab Messages báo "Query returned successfully" là xong: 35 bảng + user cowork cho ứng dụng.
--
-- - Đăng nhập pgAdmin bằng tài khoản postgres (tài khoản quản trị) để chạy.
-- - Cả file chạy như một khối: lỗi ở đâu thì không có gì được tạo, sửa xong chạy lại cả file.
-- - Chạy lại nhiều lần không sao: bảng / dữ liệu đã có thì bỏ qua.
-- - Ứng dụng KHÔNG tự chạy file này.
--
-- PHẦN 1 (bảng 1–10): code đang dùng, bắt buộc phải có.
-- PHẦN 2 (bảng 11–35): cho các chức năng sẽ làm sau, code chưa dùng.
-- PHẦN 3: tạo user cowork và cấp quyền cho ứng dụng.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist; -- cần cho các ràng buộc EXCLUDE chống trùng thời gian


-- #####################################################################
-- PHẦN 1 — CODE ĐANG DÙNG
-- #####################################################################

-- ---------------------------------------------------------------------
-- 1. resource_type — danh mục loại không gian (dữ liệu cố định)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS resource_type
(
    code        varchar(20)  NOT NULL,
    name        varchar(100) NOT NULL,
    description text,
    CONSTRAINT pk_resource_type PRIMARY KEY (code)
);

INSERT INTO resource_type (code, name, description)
VALUES ('HOT_DESK', 'Chỗ ngồi linh hoạt', 'Ghế ngồi chung, không cố định'),
       ('FIXED_DESK', 'Bàn cố định', 'Bàn riêng cho một người'),
       ('FOCUS_BOOTH', 'Pod cá nhân', 'Buồng yên tĩnh cho một người'),
       ('MEETING_ROOM', 'Phòng họp', 'Phòng kín cho nhóm'),
       ('EVENT_SPACE', 'Không gian sự kiện', 'Khu vực tổ chức sự kiện')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. branch — chi nhánh
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS branch
(
    id         uuid         NOT NULL,
    code       varchar(20)  NOT NULL,
    name       varchar(255) NOT NULL,
    address    varchar(500) NOT NULL,
    open_time  time         NOT NULL DEFAULT '00:00',
    close_time time         NOT NULL DEFAULT '23:59',
    status     varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_branch PRIMARY KEY (id),
    CONSTRAINT uq_branch_code UNIQUE (code),
    CONSTRAINT ck_branch_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

-- ---------------------------------------------------------------------
-- 3. zone — khu vực / tầng trong chi nhánh
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zone
(
    id             uuid         NOT NULL,
    branch_id      uuid         NOT NULL,
    name           varchar(100) NOT NULL,
    floor          integer      NOT NULL DEFAULT 1,
    floor_plan_url varchar(500),
    CONSTRAINT pk_zone PRIMARY KEY (id),
    CONSTRAINT fk_zone_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT uq_zone_branch_name UNIQUE (branch_id, name)
);

-- ---------------------------------------------------------------------
-- 4. space_resource — vị trí cho thuê (ghế, bàn, pod, phòng)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS space_resource
(
    id       uuid         NOT NULL,
    code     varchar(20)  NOT NULL,
    name     varchar(255) NOT NULL,
    type     varchar(20)  NOT NULL,
    capacity integer      NOT NULL,
    status   varchar(20)  NOT NULL,
    zone_id  uuid,                      -- chưa dùng trong code, để trống được
    version  bigint,
    CONSTRAINT pk_space_resource PRIMARY KEY (id),
    CONSTRAINT uq_space_resource_code UNIQUE (code),
    CONSTRAINT fk_space_resource_type FOREIGN KEY (type) REFERENCES resource_type (code),
    CONSTRAINT fk_space_resource_zone FOREIGN KEY (zone_id) REFERENCES zone (id),
    CONSTRAINT ck_space_resource_capacity CHECK (capacity > 0),
    CONSTRAINT ck_space_resource_status CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_space_resource_type_status ON space_resource (type, status);

-- ---------------------------------------------------------------------
-- 5. price_rule — bảng giá theo loại không gian × loại ngày × khung giờ
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS price_rule
(
    id              uuid        NOT NULL,
    resource_type   varchar(20) NOT NULL,
    day_type        varchar(10) NOT NULL,
    start_minute    integer     NOT NULL,   -- phút trong ngày, 540 = 09:00
    end_minute      integer     NOT NULL,   -- không tính phút này, 1440 = hết ngày
    price_per_block bigint      NOT NULL,   -- VND cho một block (mặc định 30 phút)
    label           varchar(50) NOT NULL,
    CONSTRAINT pk_price_rule PRIMARY KEY (id),
    CONSTRAINT fk_price_rule_resource_type FOREIGN KEY (resource_type) REFERENCES resource_type (code),
    CONSTRAINT ck_price_rule_day_type CHECK (day_type IN ('WEEKDAY', 'WEEKEND')),
    CONSTRAINT ck_price_rule_window CHECK (start_minute >= 0 AND end_minute <= 1440 AND start_minute < end_minute),
    CONSTRAINT ck_price_rule_price CHECK (price_per_block >= 0),
    -- Mỗi thời điểm chỉ có đúng một mức giá cho một loại không gian.
    CONSTRAINT ex_price_rule_no_overlap EXCLUDE USING gist (
        resource_type WITH =,
        day_type WITH =,
        int4range(start_minute, end_minute) WITH &&
    )
);

-- ---------------------------------------------------------------------
-- 6. customer — khách hàng, định danh bằng số điện thoại
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer
(
    id                    uuid         NOT NULL,
    full_name             varchar(255) NOT NULL,
    phone                 varchar(15)  NOT NULL,
    email                 varchar(255),
    created_at            timestamp(6) NOT NULL,
    version               bigint,
    -- 3 cột dưới chưa dùng trong code (UC01, UC12), đã có giá trị mặc định
    phone_verified_at     timestamp(6),
    notify_before_minutes integer      NOT NULL DEFAULT 15,
    preferred_channel     varchar(10)  NOT NULL DEFAULT 'SMS',
    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT uq_customer_phone UNIQUE (phone),
    CONSTRAINT ck_customer_notify_before CHECK (notify_before_minutes IN (10, 15, 30)),
    CONSTRAINT ck_customer_channel CHECK (preferred_channel IN ('SMS', 'ZALO', 'EMAIL', 'PUSH'))
);

-- ---------------------------------------------------------------------
-- 7. booking — đơn đặt chỗ
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking
(
    id              uuid         NOT NULL,
    code            varchar(12)  NOT NULL,
    qr_token        varchar(64)  NOT NULL,
    customer_id     uuid         NOT NULL,
    resource_id     uuid         NOT NULL,
    start_at        timestamp(6) NOT NULL,
    end_at          timestamp(6) NOT NULL,
    party_size      integer      NOT NULL,
    status          varchar(20)  NOT NULL,
    paid_amount     bigint       NOT NULL DEFAULT 0,
    hold_expires_at timestamp(6),
    checked_in_at   timestamp(6),
    checked_out_at  timestamp(6),
    created_at      timestamp(6) NOT NULL,
    version         bigint,
    CONSTRAINT pk_booking PRIMARY KEY (id),
    CONSTRAINT uq_booking_code UNIQUE (code),
    CONSTRAINT uq_booking_qr_token UNIQUE (qr_token),
    CONSTRAINT fk_booking_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_booking_resource FOREIGN KEY (resource_id) REFERENCES space_resource (id),
    CONSTRAINT ck_booking_period CHECK (end_at > start_at),
    CONSTRAINT ck_booking_party_size CHECK (party_size > 0),
    CONSTRAINT ck_booking_paid CHECK (paid_amount >= 0),
    CONSTRAINT ck_booking_status CHECK (status IN (
        'HELD', 'CONFIRMED', 'FAILED', 'EXPIRED', 'CHECKED_IN', 'OVERSTAY',
        'CHECKED_OUT', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'REFUND_PENDING', 'REFUNDED')),
    -- BR-01: lớp bảo vệ cuối cùng chống double-booking. tsrange mặc định là nửa mở [start, end),
    -- nên đơn 09:00–11:00 và 11:00–13:00 không bị coi là trùng.
    CONSTRAINT ex_booking_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        tsrange(start_at, end_at) WITH &&
    ) WHERE (status IN ('HELD', 'CONFIRMED', 'CHECKED_IN', 'OVERSTAY'))
);

CREATE INDEX IF NOT EXISTS idx_booking_customer ON booking (customer_id);
CREATE INDEX IF NOT EXISTS idx_booking_hold_expiry ON booking (hold_expires_at) WHERE status = 'HELD';
CREATE INDEX IF NOT EXISTS idx_booking_checked_in_end ON booking (end_at) WHERE status = 'CHECKED_IN';

-- ---------------------------------------------------------------------
-- 8. booking_charge — các khoản phải thu của đơn (tổng tiền = tổng các dòng)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking_charge
(
    booking_id  uuid         NOT NULL,
    line_no     integer      NOT NULL,   -- thứ tự dòng trong đơn, bắt đầu từ 0
    charge_type varchar(30)  NOT NULL,
    amount      bigint       NOT NULL,
    start_at    timestamp(6) NOT NULL,   -- khoảng thời gian mà khoản này tính tiền
    end_at      timestamp(6) NOT NULL,
    created_at  timestamp(6) NOT NULL,
    CONSTRAINT pk_booking_charge PRIMARY KEY (booking_id, line_no),
    CONSTRAINT fk_booking_charge_booking FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT ck_booking_charge_type CHECK (charge_type IN ('RENTAL', 'EXTENSION', 'OVERSTAY_SURCHARGE')),
    CONSTRAINT ck_booking_charge_amount CHECK (amount >= 0),
    CONSTRAINT ck_booking_charge_period CHECK (end_at > start_at)
);

-- ---------------------------------------------------------------------
-- 9. booking_status_history — lịch sử đổi trạng thái của đơn
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking_status_history
(
    booking_id  uuid         NOT NULL,
    line_no     integer      NOT NULL,
    from_status varchar(20),             -- rỗng ở lần tạo đơn
    to_status   varchar(20)  NOT NULL,
    changed_at  timestamp(6) NOT NULL,
    CONSTRAINT pk_booking_status_history PRIMARY KEY (booking_id, line_no),
    CONSTRAINT fk_booking_status_history_booking FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 10. payment — giao dịch thanh toán qua cổng
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment
(
    id                     uuid         NOT NULL,
    booking_id             uuid         NOT NULL,
    amount                 bigint       NOT NULL,
    method                 varchar(20)  NOT NULL,
    status                 varchar(20)  NOT NULL,
    idempotency_key        varchar(80)  NOT NULL,
    gateway_transaction_id varchar(80),
    checkout_url           varchar(255),
    created_at             timestamp(6) NOT NULL,
    completed_at           timestamp(6),
    version                bigint,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT uq_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_payment_gateway_transaction_id UNIQUE (gateway_transaction_id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (method IN ('VIETQR', 'E_WALLET', 'CARD')),
    CONSTRAINT ck_payment_status CHECK (status IN ('INITIATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNCONFIRMED'))
);

CREATE INDEX IF NOT EXISTS idx_payment_booking ON payment (booking_id);
-- Mỗi đơn chỉ có một giao dịch đang mở tại một thời điểm.
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_open_per_booking ON payment (booking_id) WHERE status IN ('INITIATED', 'PENDING');


-- #####################################################################
-- PHẦN 2 — CHỨC NĂNG SẼ LÀM SAU (code chưa dùng)
-- #####################################################################

-- ---------------------------------------------------------------------
-- 11–12. Tiện ích của vị trí (ổ cắm, màn hình, yên tĩnh...) — FR-SRCH-03
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS amenity
(
    id   uuid         NOT NULL,
    code varchar(30)  NOT NULL,
    name varchar(100) NOT NULL,
    CONSTRAINT pk_amenity PRIMARY KEY (id),
    CONSTRAINT uq_amenity_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS resource_amenity
(
    resource_id uuid    NOT NULL,
    amenity_id  uuid    NOT NULL,
    quantity    integer NOT NULL DEFAULT 1,   -- ví dụ: 4 ổ cắm
    CONSTRAINT pk_resource_amenity PRIMARY KEY (resource_id, amenity_id),
    CONSTRAINT fk_resource_amenity_resource FOREIGN KEY (resource_id) REFERENCES space_resource (id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_amenity_amenity FOREIGN KEY (amenity_id) REFERENCES amenity (id),
    CONSTRAINT ck_resource_amenity_quantity CHECK (quantity > 0)
);

-- ---------------------------------------------------------------------
-- 13. staff — nhân viên, đăng nhập trang quầy / quản trị (UC23)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staff
(
    id                 uuid         NOT NULL,
    branch_id          uuid,                      -- rỗng = quản trị toàn hệ thống
    full_name          varchar(255) NOT NULL,
    email              varchar(255) NOT NULL,
    password_hash      varchar(255) NOT NULL,
    role               varchar(20)  NOT NULL,
    compensation_limit bigint       NOT NULL DEFAULT 0,   -- hạn mức đền bù tự duyệt (BR-18)
    status             varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_staff PRIMARY KEY (id),
    CONSTRAINT uq_staff_email UNIQUE (email),
    CONSTRAINT fk_staff_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT ck_staff_role CHECK (role IN ('RECEPTIONIST', 'TECHNICIAN', 'MANAGER', 'ADMIN')),
    CONSTRAINT ck_staff_status CHECK (status IN ('ACTIVE', 'LOCKED'))
);

-- ---------------------------------------------------------------------
-- 14–17. Gói thành viên (Membership) — UC05, UC21
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS membership_plan
(
    id            uuid         NOT NULL,
    name          varchar(100) NOT NULL,
    total_minutes integer      NOT NULL,
    valid_days    integer      NOT NULL,
    price         bigint       NOT NULL,
    status        varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_membership_plan PRIMARY KEY (id),
    CONSTRAINT ck_membership_plan_values CHECK (total_minutes > 0 AND valid_days > 0 AND price >= 0),
    CONSTRAINT ck_membership_plan_status CHECK (status IN ('ACTIVE', 'STOPPED'))
);

-- Gói được dùng cho loại không gian nào
CREATE TABLE IF NOT EXISTS membership_plan_resource_type
(
    plan_id       uuid        NOT NULL,
    resource_type varchar(20) NOT NULL,
    CONSTRAINT pk_membership_plan_resource_type PRIMARY KEY (plan_id, resource_type),
    CONSTRAINT fk_mprt_plan FOREIGN KEY (plan_id) REFERENCES membership_plan (id) ON DELETE CASCADE,
    CONSTRAINT fk_mprt_resource_type FOREIGN KEY (resource_type) REFERENCES resource_type (code)
);

CREATE TABLE IF NOT EXISTS membership_pass
(
    id                uuid         NOT NULL,
    customer_id       uuid         NOT NULL,
    plan_id           uuid         NOT NULL,
    remaining_minutes integer      NOT NULL,
    purchased_at      timestamp(6) NOT NULL,
    expires_at        timestamp(6) NOT NULL,
    status            varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    version           bigint,
    CONSTRAINT pk_membership_pass PRIMARY KEY (id),
    CONSTRAINT fk_membership_pass_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_membership_pass_plan FOREIGN KEY (plan_id) REFERENCES membership_plan (id),
    CONSTRAINT ck_membership_pass_remaining CHECK (remaining_minutes >= 0),
    CONSTRAINT ck_membership_pass_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'USED_UP'))
);

CREATE INDEX IF NOT EXISTS idx_membership_pass_customer ON membership_pass (customer_id);

-- Mỗi lần trừ giờ của gói cho một đơn
CREATE TABLE IF NOT EXISTS membership_usage
(
    id         uuid         NOT NULL,
    pass_id    uuid         NOT NULL,
    booking_id uuid         NOT NULL,
    minutes    integer      NOT NULL,
    status     varchar(20)  NOT NULL DEFAULT 'HELD',   -- HELD khi đang giữ chỗ, trả lại nếu đơn hết hạn
    used_at    timestamp(6) NOT NULL,
    CONSTRAINT pk_membership_usage PRIMARY KEY (id),
    CONSTRAINT fk_membership_usage_pass FOREIGN KEY (pass_id) REFERENCES membership_pass (id),
    CONSTRAINT fk_membership_usage_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT uq_membership_usage UNIQUE (pass_id, booking_id),
    CONSTRAINT ck_membership_usage_minutes CHECK (minutes > 0),
    CONSTRAINT ck_membership_usage_status CHECK (status IN ('HELD', 'USED', 'RELEASED'))
);

-- ---------------------------------------------------------------------
-- 18–20. Voucher khuyến mãi — UC05, UC21
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS voucher
(
    id                  uuid         NOT NULL,
    code                varchar(30)  NOT NULL,
    name                varchar(255) NOT NULL,
    discount_type       varchar(20)  NOT NULL,
    discount_value      bigint       NOT NULL,    -- % (1–100), số tiền VND hoặc số phút tùy discount_type
    min_order_amount    bigint       NOT NULL DEFAULT 0,
    max_discount_amount bigint,                   -- trần giảm khi giảm theo %
    valid_from          timestamp(6) NOT NULL,
    valid_to            timestamp(6) NOT NULL,
    total_limit         integer,                  -- rỗng = không giới hạn
    per_customer_limit  integer      NOT NULL DEFAULT 1,
    used_count          integer      NOT NULL DEFAULT 0,
    status              varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    version             bigint,
    CONSTRAINT pk_voucher PRIMARY KEY (id),
    CONSTRAINT uq_voucher_code UNIQUE (code),
    CONSTRAINT ck_voucher_type CHECK (discount_type IN ('PERCENT', 'AMOUNT', 'FREE_MINUTES')),
    CONSTRAINT ck_voucher_value CHECK (discount_value > 0 AND (discount_type <> 'PERCENT' OR discount_value <= 100)),
    CONSTRAINT ck_voucher_period CHECK (valid_to > valid_from),
    CONSTRAINT ck_voucher_usage CHECK (used_count >= 0 AND (total_limit IS NULL OR used_count <= total_limit)),
    CONSTRAINT ck_voucher_status CHECK (status IN ('ACTIVE', 'PAUSED'))
);

-- Voucher áp dụng cho loại không gian nào (không có dòng nào = áp dụng mọi loại)
CREATE TABLE IF NOT EXISTS voucher_resource_type
(
    voucher_id    uuid        NOT NULL,
    resource_type varchar(20) NOT NULL,
    CONSTRAINT pk_voucher_resource_type PRIMARY KEY (voucher_id, resource_type),
    CONSTRAINT fk_vrt_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id) ON DELETE CASCADE,
    CONSTRAINT fk_vrt_resource_type FOREIGN KEY (resource_type) REFERENCES resource_type (code)
);

CREATE TABLE IF NOT EXISTS voucher_redemption
(
    id              uuid         NOT NULL,
    voucher_id      uuid         NOT NULL,
    booking_id      uuid         NOT NULL,
    customer_id     uuid         NOT NULL,
    discount_amount bigint       NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'HELD',
    created_at      timestamp(6) NOT NULL,
    CONSTRAINT pk_voucher_redemption PRIMARY KEY (id),
    CONSTRAINT fk_voucher_redemption_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id),
    CONSTRAINT fk_voucher_redemption_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_voucher_redemption_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT uq_voucher_redemption_booking UNIQUE (booking_id),   -- BR-12: một voucher mỗi đơn
    CONSTRAINT ck_voucher_redemption_amount CHECK (discount_amount >= 0),
    CONSTRAINT ck_voucher_redemption_status CHECK (status IN ('HELD', 'USED', 'RELEASED'))
);

CREATE INDEX IF NOT EXISTS idx_voucher_redemption_voucher_customer ON voucher_redemption (voucher_id, customer_id);

-- ---------------------------------------------------------------------
-- 21–22. Dịch vụ bổ sung thuê kèm (màn hình, máy chiếu...) — FR-BOOK-02
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS addon
(
    id              uuid         NOT NULL,
    branch_id       uuid         NOT NULL,
    code            varchar(30)  NOT NULL,
    name            varchar(100) NOT NULL,
    stock           integer      NOT NULL,
    price_per_block bigint       NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_addon PRIMARY KEY (id),
    CONSTRAINT fk_addon_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT uq_addon_branch_code UNIQUE (branch_id, code),
    CONSTRAINT ck_addon_values CHECK (stock >= 0 AND price_per_block >= 0),
    CONSTRAINT ck_addon_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE IF NOT EXISTS booking_addon
(
    id         uuid    NOT NULL,
    booking_id uuid    NOT NULL,
    addon_id   uuid    NOT NULL,
    quantity   integer NOT NULL,
    unit_price bigint  NOT NULL,
    CONSTRAINT pk_booking_addon PRIMARY KEY (id),
    CONSTRAINT fk_booking_addon_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_booking_addon_addon FOREIGN KEY (addon_id) REFERENCES addon (id),
    CONSTRAINT uq_booking_addon UNIQUE (booking_id, addon_id),
    CONSTRAINT ck_booking_addon_values CHECK (quantity > 0 AND unit_price >= 0)
);

-- ---------------------------------------------------------------------
-- 23–26. Gọi đồ ăn uống tại chỗ — UC10
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS menu_category
(
    id         uuid         NOT NULL,
    name       varchar(100) NOT NULL,
    sort_order integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_menu_category PRIMARY KEY (id),
    CONSTRAINT uq_menu_category_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS menu_item
(
    id           uuid         NOT NULL,
    branch_id    uuid         NOT NULL,
    category_id  uuid         NOT NULL,
    name         varchar(100) NOT NULL,
    price        bigint       NOT NULL,
    is_available boolean      NOT NULL DEFAULT true,
    is_drink     boolean      NOT NULL DEFAULT false,   -- dùng để áp cốc nước miễn phí (BR-10)
    CONSTRAINT pk_menu_item PRIMARY KEY (id),
    CONSTRAINT fk_menu_item_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_menu_item_category FOREIGN KEY (category_id) REFERENCES menu_category (id),
    CONSTRAINT ck_menu_item_price CHECK (price >= 0)
);

CREATE TABLE IF NOT EXISTS service_order
(
    id         uuid         NOT NULL,
    booking_id uuid         NOT NULL,
    status     varchar(20)  NOT NULL DEFAULT 'NEW',
    note       varchar(500),
    handled_by uuid,
    created_at timestamp(6) NOT NULL,
    CONSTRAINT pk_service_order PRIMARY KEY (id),
    CONSTRAINT fk_service_order_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_service_order_staff FOREIGN KEY (handled_by) REFERENCES staff (id),
    CONSTRAINT ck_service_order_status CHECK (status IN ('NEW', 'PREPARING', 'DELIVERED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_service_order_booking ON service_order (booking_id);

CREATE TABLE IF NOT EXISTS service_order_item
(
    id            uuid    NOT NULL,
    order_id      uuid    NOT NULL,
    menu_item_id  uuid    NOT NULL,
    quantity      integer NOT NULL,
    unit_price    bigint  NOT NULL,
    is_free_drink boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_service_order_item PRIMARY KEY (id),
    CONSTRAINT fk_service_order_item_order FOREIGN KEY (order_id) REFERENCES service_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_service_order_item_menu FOREIGN KEY (menu_item_id) REFERENCES menu_item (id),
    CONSTRAINT ck_service_order_item_values CHECK (quantity > 0 AND unit_price >= 0)
);

-- ---------------------------------------------------------------------
-- 27–29. Hoàn tiền và hóa đơn — UC25, FR-PAY-08
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refund
(
    id                uuid         NOT NULL,
    payment_id        uuid         NOT NULL,
    amount            bigint       NOT NULL,
    reason            varchar(50)  NOT NULL,
    status            varchar(20)  NOT NULL DEFAULT 'REQUESTED',
    gateway_refund_id varchar(80),
    attempts          integer      NOT NULL DEFAULT 0,
    requested_at      timestamp(6) NOT NULL,
    completed_at      timestamp(6),
    CONSTRAINT pk_refund PRIMARY KEY (id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT uq_refund_gateway_id UNIQUE (gateway_refund_id),
    CONSTRAINT ck_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_refund_status CHECK (status IN ('REQUESTED', 'REFUNDED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS invoice
(
    id                    uuid         NOT NULL,
    booking_id            uuid         NOT NULL,
    invoice_no            varchar(30)  NOT NULL,
    total_amount          bigint       NOT NULL,
    e_invoice_lookup_code varchar(50),
    issued_at             timestamp(6) NOT NULL,
    CONSTRAINT pk_invoice PRIMARY KEY (id),
    CONSTRAINT fk_invoice_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT uq_invoice_booking UNIQUE (booking_id),
    CONSTRAINT uq_invoice_no UNIQUE (invoice_no),
    CONSTRAINT ck_invoice_total CHECK (total_amount >= 0)
);

CREATE TABLE IF NOT EXISTS invoice_line
(
    id          uuid         NOT NULL,
    invoice_id  uuid         NOT NULL,
    line_no     integer      NOT NULL,
    description varchar(255) NOT NULL,
    quantity    integer      NOT NULL DEFAULT 1,
    unit_price  bigint       NOT NULL,
    amount      bigint       NOT NULL,
    CONSTRAINT pk_invoice_line PRIMARY KEY (id),
    CONSTRAINT fk_invoice_line_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id) ON DELETE CASCADE,
    CONSTRAINT uq_invoice_line UNIQUE (invoice_id, line_no),
    CONSTRAINT ck_invoice_line_values CHECK (quantity > 0 AND unit_price >= 0 AND amount >= 0)
);

-- ---------------------------------------------------------------------
-- 30–32. Sự cố và đền bù — UC11, UC17, UC18
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ticket
(
    id                    uuid         NOT NULL,
    resource_id           uuid         NOT NULL,
    booking_id            uuid,
    reported_by_customer  uuid,
    category              varchar(20)  NOT NULL,
    priority              varchar(10)  NOT NULL DEFAULT 'NORMAL',
    description           text         NOT NULL,
    status                varchar(20)  NOT NULL DEFAULT 'OPEN',
    assignee_id           uuid,
    created_at            timestamp(6) NOT NULL,
    resolved_at           timestamp(6),
    CONSTRAINT pk_ticket PRIMARY KEY (id),
    CONSTRAINT fk_ticket_resource FOREIGN KEY (resource_id) REFERENCES space_resource (id),
    CONSTRAINT fk_ticket_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_ticket_customer FOREIGN KEY (reported_by_customer) REFERENCES customer (id),
    CONSTRAINT fk_ticket_assignee FOREIGN KEY (assignee_id) REFERENCES staff (id),
    CONSTRAINT ck_ticket_category CHECK (category IN ('NETWORK', 'POWER', 'AIR_CON', 'CLEANING', 'OTHER')),
    CONSTRAINT ck_ticket_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
    CONSTRAINT ck_ticket_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_ticket_open ON ticket (status, priority) WHERE status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD');

-- Ảnh đính kèm của ticket
CREATE TABLE IF NOT EXISTS ticket_attachment
(
    id          uuid         NOT NULL,
    ticket_id   uuid         NOT NULL,
    url         varchar(500) NOT NULL,
    uploaded_at timestamp(6) NOT NULL,
    CONSTRAINT pk_ticket_attachment PRIMARY KEY (id),
    CONSTRAINT fk_ticket_attachment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS compensation
(
    id          uuid         NOT NULL,
    booking_id  uuid         NOT NULL,
    ticket_id   uuid,
    type        varchar(20)  NOT NULL,
    value       bigint       NOT NULL,   -- số tiền VND, số phút, hoặc số đồ uống tùy type
    voucher_id  uuid,                    -- khi đền bù bằng voucher
    created_by  uuid         NOT NULL,
    approved_by uuid,                    -- quản lý duyệt khi vượt hạn mức
    created_at  timestamp(6) NOT NULL,
    CONSTRAINT pk_compensation PRIMARY KEY (id),
    CONSTRAINT fk_compensation_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_compensation_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id),
    CONSTRAINT fk_compensation_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id),
    CONSTRAINT fk_compensation_created_by FOREIGN KEY (created_by) REFERENCES staff (id),
    CONSTRAINT fk_compensation_approved_by FOREIGN KEY (approved_by) REFERENCES staff (id),
    CONSTRAINT ck_compensation_type CHECK (type IN ('FREE_DRINK', 'VOUCHER', 'FREE_MINUTES', 'REFUND')),
    CONSTRAINT ck_compensation_value CHECK (value > 0)
);

-- ---------------------------------------------------------------------
-- 33–35. Hạ tầng: lịch thông báo, nhật ký kiểm toán, outbox
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_job
(
    id          uuid         NOT NULL,
    customer_id uuid         NOT NULL,
    booking_id  uuid,
    type        varchar(30)  NOT NULL,
    channel     varchar(10)  NOT NULL,
    send_at     timestamp(6) NOT NULL,
    status      varchar(20)  NOT NULL DEFAULT 'SCHEDULED',
    attempts    integer      NOT NULL DEFAULT 0,
    sent_at     timestamp(6),
    last_error  varchar(500),
    CONSTRAINT pk_notification_job PRIMARY KEY (id),
    CONSTRAINT fk_notification_job_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_notification_job_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT ck_notification_job_type CHECK (type IN ('BOOKING_CONFIRMED', 'CHECK_IN_REMINDER', 'END_WARNING',
        'BOOKING_RELEASED', 'BOOKING_CANCELLED', 'REFUND', 'RESOURCE_MAINTENANCE', 'COMPENSATION')),
    CONSTRAINT ck_notification_job_channel CHECK (channel IN ('SMS', 'ZALO', 'EMAIL', 'PUSH')),
    CONSTRAINT ck_notification_job_status CHECK (status IN ('SCHEDULED', 'SENT', 'FAILED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_notification_job_due ON notification_job (send_at) WHERE status = 'SCHEDULED';

CREATE TABLE IF NOT EXISTS audit_log
(
    id          uuid         NOT NULL,
    actor_id    uuid,                    -- nhân viên thực hiện; rỗng = hệ thống tự động
    action      varchar(50)  NOT NULL,
    entity_type varchar(50)  NOT NULL,
    entity_id   uuid         NOT NULL,
    before_data jsonb,
    after_data  jsonb,
    reason      varchar(500),
    created_at  timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES staff (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log (entity_type, entity_id);

-- Transactional outbox: event ghi cùng giao dịch với dữ liệu, một job đọc và phát đi sau
CREATE TABLE IF NOT EXISTS outbox_event
(
    id             uuid         NOT NULL,
    aggregate_type varchar(50)  NOT NULL,
    aggregate_id   uuid         NOT NULL,
    event_type     varchar(100) NOT NULL,
    payload        jsonb        NOT NULL,
    created_at     timestamp(6) NOT NULL DEFAULT now(),
    published_at   timestamp(6),
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;


-- #####################################################################
-- PHẦN 3 — USER CHO ỨNG DỤNG
-- Ứng dụng đăng nhập bằng user riêng "cowork" (mật khẩu "cowork", chỉ dùng cho máy dev),
-- không dùng tài khoản quản trị postgres. Khớp với spring.datasource trong application.yml.
-- #####################################################################

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'cowork') THEN
        CREATE ROLE cowork WITH LOGIN PASSWORD 'cowork';
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO cowork', current_database());
END
$$;

GRANT USAGE ON SCHEMA public TO cowork;
-- Đọc / ghi dữ liệu trên mọi bảng hiện có (không được xóa bảng hay sửa cấu trúc bảng)
GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public TO cowork;
-- Bảng tạo thêm về sau cũng tự được cấp quyền như trên
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON TABLES TO cowork;
