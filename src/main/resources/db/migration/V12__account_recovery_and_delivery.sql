ALTER TABLE users ADD COLUMN IF NOT EXISTS credential_version INTEGER NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS password_resets (
    user_id BIGINT PRIMARY KEY REFERENCES users(id), otp VARCHAR(6) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL, verified_at TIMESTAMPTZ, token_hash VARCHAR(64),
    used_at TIMESTAMPTZ, requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS reset_mail_jobs (
    id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
    email VARCHAR(255) NOT NULL, otp VARCHAR(6) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(), lease_until TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL, last_error VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS reset_mail_due ON reset_mail_jobs(state,available_at);
CREATE TABLE IF NOT EXISTS push_devices (
    app VARCHAR(10) NOT NULL CHECK (app IN ('admin','rider')),
    installation_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL REFERENCES users(id),
    token VARCHAR(200) NOT NULL UNIQUE, platform VARCHAR(10) NOT NULL CHECK (platform IN ('android','ios')),
    active BOOLEAN NOT NULL DEFAULT true, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(app,installation_id)
);
CREATE INDEX IF NOT EXISTS push_devices_owner ON push_devices(user_id) WHERE active;
CREATE TABLE IF NOT EXISTS notification_inbox (
    id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), event_key VARCHAR(200) NOT NULL,
    type VARCHAR(64) NOT NULL, ride_id INTEGER NOT NULL REFERENCES ride_details(ride_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(user_id,event_key)
);
CREATE INDEX IF NOT EXISTS notification_inbox_owner ON notification_inbox(user_id,created_at DESC);
CREATE TABLE IF NOT EXISTS push_jobs (
    id UUID PRIMARY KEY, notification_id UUID NOT NULL REFERENCES notification_inbox(id) ON DELETE CASCADE,
    app VARCHAR(10) NOT NULL, installation_id VARCHAR(64) NOT NULL, token VARCHAR(200) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(), lease_until TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL, receipt_id VARCHAR(200), last_error VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(notification_id,app,installation_id,token)
);
CREATE INDEX IF NOT EXISTS push_jobs_due ON push_jobs(state,available_at);
CREATE TABLE IF NOT EXISTS socket_events (
    id UUID PRIMARY KEY, destination VARCHAR(300) NOT NULL, payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS socket_events_created ON socket_events(created_at);
CREATE TABLE IF NOT EXISTS socket_deliveries (
    node_id UUID NOT NULL, event_id UUID NOT NULL REFERENCES socket_events(id) ON DELETE CASCADE,
    delivered BOOLEAN NOT NULL DEFAULT false, lease_until TIMESTAMPTZ,
    PRIMARY KEY(node_id,event_id)
);
CREATE TABLE IF NOT EXISTS socket_nodes (id UUID PRIMARY KEY, heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT now());
