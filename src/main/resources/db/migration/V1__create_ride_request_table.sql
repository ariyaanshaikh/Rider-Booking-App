-- Migration V1: Create RIDE_REQUEST table
-- Purpose: Stores per-rider request entries for ride matching
-- Each row represents a notification sent to a rider for a specific ride
-- Tracks status: SENT, ACCEPTED, CANCELLED

CREATE TABLE IF NOT EXISTS RIDE_REQUEST (
    ID SERIAL PRIMARY KEY,
    RIDE_ID INTEGER NOT NULL,
    RIDER_ID INTEGER NOT NULL,
    STATUS VARCHAR(32) NOT NULL DEFAULT 'SENT',
    CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ride_request_ride FOREIGN KEY (RIDE_ID) REFERENCES RIDE_DETAILS(RIDE_ID) ON DELETE CASCADE
);

-- Index for fast lookup by ride_id
CREATE INDEX IF NOT EXISTS idx_ride_request_ride_id ON RIDE_REQUEST(RIDE_ID);

-- Index for fast lookup by rider_id
CREATE INDEX IF NOT EXISTS idx_ride_request_rider_id ON RIDE_REQUEST(RIDER_ID);

-- Index for status filtering
CREATE INDEX IF NOT EXISTS idx_ride_request_status ON RIDE_REQUEST(STATUS);
