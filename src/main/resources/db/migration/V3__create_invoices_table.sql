-- Migration V3: Create INVOICES table
-- Purpose: Store ride invoices and settlements
-- Tracks billing information and payment status for completed rides

CREATE TABLE IF NOT EXISTS INVOICES (
    INVOICE_ID SERIAL PRIMARY KEY,
    RIDE_ID INTEGER NOT NULL UNIQUE,
    RIDER_ID INTEGER NOT NULL,
    USER_ID INTEGER NOT NULL,
    AMOUNT DECIMAL(10, 2) NOT NULL,
    CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    STATUS VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_invoices_ride FOREIGN KEY (RIDE_ID) REFERENCES RIDE_DETAILS(RIDE_ID) ON DELETE CASCADE
);

-- Index for fast lookup by ride_id
CREATE INDEX IF NOT EXISTS idx_invoices_ride_id ON INVOICES(RIDE_ID);

-- Index for lookup by rider_id
CREATE INDEX IF NOT EXISTS idx_invoices_rider_id ON INVOICES(RIDER_ID);

-- Index for lookup by user_id
CREATE INDEX IF NOT EXISTS idx_invoices_user_id ON INVOICES(USER_ID);

-- Index for status filtering
CREATE INDEX IF NOT EXISTS idx_invoices_status ON INVOICES(STATUS);

-- Index for date range queries
CREATE INDEX IF NOT EXISTS idx_invoices_created_at ON INVOICES(CREATED_AT);
