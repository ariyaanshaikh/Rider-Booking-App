ALTER TABLE ride_details ADD COLUMN IF NOT EXISTS booking_key VARCHAR(100);
CREATE TABLE IF NOT EXISTS confirmation_tokens (
    id BIGSERIAL PRIMARY KEY, token VARCHAR(255), created_at TIMESTAMP,
    expires_at TIMESTAMP, confirmed_at TIMESTAMP, user_id BIGINT NOT NULL REFERENCES users(id)
);
ALTER TABLE ride_details ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE UNIQUE INDEX IF NOT EXISTS uq_ride_booking_key ON ride_details(admin_id, booking_key) WHERE booking_key IS NOT NULL;
CREATE TABLE IF NOT EXISTS request_limits (
    purpose VARCHAR(32) NOT NULL, identity VARCHAR(255) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL, attempts INTEGER NOT NULL,
    PRIMARY KEY(purpose,identity)
);
CREATE INDEX IF NOT EXISTS idx_request_limits_window ON request_limits(window_start);
CREATE TABLE IF NOT EXISTS checkout_sessions (
    token_hash VARCHAR(64) PRIMARY KEY, ride_id INTEGER NOT NULL REFERENCES ride_details(ride_id),
    username VARCHAR(255) NOT NULL, expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_checkout_sessions_expiry ON checkout_sessions(expires_at);
