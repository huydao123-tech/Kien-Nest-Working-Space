-- =====================================================================
-- V1: Schema lõi — Space, Pricing, Booking, Payment
-- Thiết kế: docs/database.md
-- KHÔNG sửa file này sau khi đã chạy; thay đổi schema bằng file V2__..., V3__...
-- =====================================================================

-- Cho phép dùng toán tử "=" trên uuid/varchar trong index GiST (cần cho exclusion constraint).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- Space: vị trí cho thuê
-- ---------------------------------------------------------------------
CREATE TABLE space_resource
(
    id       uuid         NOT NULL,
    code     varchar(20)  NOT NULL,
    name     varchar(255) NOT NULL,
    type     varchar(20)  NOT NULL,
    capacity integer      NOT NULL,
    status   varchar(20)  NOT NULL,
    version  bigint,
    CONSTRAINT pk_space_resource PRIMARY KEY (id),
    CONSTRAINT uq_space_resource_code UNIQUE (code),
    CONSTRAINT ck_space_resource_type CHECK (type IN ('HOT_DESK', 'FIXED_DESK', 'FOCUS_BOOTH', 'MEETING_ROOM', 'EVENT_SPACE')),
    CONSTRAINT ck_space_resource_capacity CHECK (capacity > 0),
    CONSTRAINT ck_space_resource_status CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'INACTIVE'))
);

CREATE INDEX idx_space_resource_type_status ON space_resource (type, status);

-- ---------------------------------------------------------------------
-- Pricing: bảng giá theo loại không gian × loại ngày × khung giờ
-- ---------------------------------------------------------------------
CREATE TABLE price_rule
(
    id              uuid        NOT NULL,
    resource_type   varchar(20) NOT NULL,
    day_type        varchar(10) NOT NULL,
    start_minute    integer     NOT NULL,
    end_minute      integer     NOT NULL,
    price_per_block bigint      NOT NULL,
    label           varchar(50) NOT NULL,
    CONSTRAINT pk_price_rule PRIMARY KEY (id),
    CONSTRAINT ck_price_rule_resource_type CHECK (resource_type IN ('HOT_DESK', 'FIXED_DESK', 'FOCUS_BOOTH', 'MEETING_ROOM', 'EVENT_SPACE')),
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
-- Booking: đơn đặt chỗ
-- ---------------------------------------------------------------------
CREATE TABLE booking
(
    id               uuid         NOT NULL,
    code             varchar(12)  NOT NULL,
    qr_token         varchar(64)  NOT NULL,
    resource_id      uuid         NOT NULL,
    resource_type    varchar(20)  NOT NULL,
    contact_name     varchar(255) NOT NULL,
    contact_phone    varchar(15)  NOT NULL,
    contact_email    varchar(255),
    start_at         timestamp(6) NOT NULL,
    end_at           timestamp(6) NOT NULL,
    party_size       integer      NOT NULL,
    status           varchar(20)  NOT NULL,
    rental_amount    bigint       NOT NULL DEFAULT 0,
    extension_amount bigint       NOT NULL DEFAULT 0,
    surcharge_amount bigint       NOT NULL DEFAULT 0,
    paid_amount      bigint       NOT NULL DEFAULT 0,
    hold_expires_at  timestamp(6),
    checked_in_at    timestamp(6),
    checked_out_at   timestamp(6),
    created_at       timestamp(6) NOT NULL,
    version          bigint,
    CONSTRAINT pk_booking PRIMARY KEY (id),
    CONSTRAINT uq_booking_code UNIQUE (code),
    CONSTRAINT uq_booking_qr_token UNIQUE (qr_token),
    CONSTRAINT fk_booking_resource FOREIGN KEY (resource_id) REFERENCES space_resource (id),
    CONSTRAINT ck_booking_resource_type CHECK (resource_type IN ('HOT_DESK', 'FIXED_DESK', 'FOCUS_BOOTH', 'MEETING_ROOM', 'EVENT_SPACE')),
    CONSTRAINT ck_booking_period CHECK (end_at > start_at),
    CONSTRAINT ck_booking_party_size CHECK (party_size > 0),
    CONSTRAINT ck_booking_status CHECK (status IN (
        'HELD', 'CONFIRMED', 'FAILED', 'EXPIRED', 'CHECKED_IN', 'OVERSTAY',
        'CHECKED_OUT', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'REFUND_PENDING', 'REFUNDED')),
    CONSTRAINT ck_booking_amounts CHECK (rental_amount >= 0 AND extension_amount >= 0
        AND surcharge_amount >= 0 AND paid_amount >= 0),
    -- BR-01: lớp bảo vệ cuối cùng chống double-booking. tsrange mặc định là nửa mở [start, end),
    -- khớp với TimeRange trong code: đơn 09:00–11:00 và 11:00–13:00 không bị coi là trùng.
    CONSTRAINT ex_booking_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        tsrange(start_at, end_at) WITH &&
    ) WHERE (status IN ('HELD', 'CONFIRMED', 'CHECKED_IN', 'OVERSTAY'))
);

-- Job giải phóng chỗ hết hạn giữ (UC24).
CREATE INDEX idx_booking_hold_expiry ON booking (hold_expires_at) WHERE status = 'HELD';
-- Job đánh dấu khách ngồi quá giờ (UC24).
CREATE INDEX idx_booking_checked_in_end ON booking (end_at) WHERE status = 'CHECKED_IN';
-- Lễ tân tra cứu theo số điện thoại (UC14).
CREATE INDEX idx_booking_contact_phone ON booking (contact_phone);

-- ---------------------------------------------------------------------
-- Payment: giao dịch thanh toán
-- ---------------------------------------------------------------------
CREATE TABLE payment
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

CREATE INDEX idx_payment_booking ON payment (booking_id);
-- Mỗi đơn chỉ có một giao dịch đang mở tại một thời điểm.
CREATE UNIQUE INDEX ux_payment_open_per_booking ON payment (booking_id) WHERE status IN ('INITIATED', 'PENDING');
