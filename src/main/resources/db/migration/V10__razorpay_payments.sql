-- Additive migration: existing rides are not repriced or marked paid.
ALTER TABLE ride_details ADD COLUMN IF NOT EXISTS distance_meters BIGINT;
ALTER TABLE ride_details ADD COLUMN IF NOT EXISTS payment_completed VARCHAR(32);
ALTER TABLE ride_details ADD COLUMN IF NOT EXISTS payment_json JSONB;
ALTER TABLE ride_details ALTER COLUMN payment_json TYPE JSONB USING payment_json::jsonb;
ALTER TABLE ride_details DROP CONSTRAINT IF EXISTS ck_ride_status;
ALTER TABLE ride_details ADD CONSTRAINT ck_ride_status CHECK
    (ride_status IN ('REQUESTED','ACCEPTED','AWAITING_PAYMENT','STARTED','COMPLETED','RIDER_CANCELLED','ADMIN_CANCELLED')) NOT VALID;
-- NOT VALID preserves legacy rows (for example 'Requested'). New writes are checked.
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS ck_invoice_status;
ALTER TABLE invoices ADD CONSTRAINT ck_invoice_status CHECK
    (status IN ('PENDING','PAID','SETTLED','REFUNDED')) NOT VALID;

CREATE TABLE IF NOT EXISTS ride_payments (
    ride_id INTEGER PRIMARY KEY REFERENCES ride_details(ride_id),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    business_paise BIGINT NOT NULL CHECK (business_paise >= 0),
    rider_paise BIGINT NOT NULL CHECK (rider_paise > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
    receipt VARCHAR(40) NOT NULL UNIQUE,
    order_id VARCHAR(64) UNIQUE,
    payment_id VARCHAR(64) UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATING'
        CHECK (status IN ('CREATING','ORDER_CREATED','CAPTURED','REFUND_REQUIRED','REFUNDED')),
    transfer_status VARCHAR(32) NOT NULL DEFAULT 'NOT_ELIGIBLE'
        CHECK (transfer_status IN ('NOT_ELIGIBLE','WAITING_ROUTE','READY','SUBMITTING','RECONCILE','PROCESSED','SETTLED','FAILED')),
    transfer_id VARCHAR(64) UNIQUE,
    transfer_account_id VARCHAR(64),
    last_error VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (business_paise + rider_paise = amount_paise)
);

-- Populate only through a trusted operations connection after Razorpay onboarding.
-- Application ADMIN is a paying customer, not a privileged finance operator.
CREATE TABLE IF NOT EXISTS rider_route_accounts (
    rider_id BIGINT PRIMARY KEY REFERENCES rider_details(id),
    account_id VARCHAR(64) NOT NULL UNIQUE CHECK (account_id LIKE 'acc_%'),
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS razorpay_webhook_events (
    event_id VARCHAR(256) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ride_payments_transfer ON ride_payments(transfer_status);
