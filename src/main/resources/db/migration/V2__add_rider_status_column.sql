-- Migration V2: Add status column to RIDER_DEMO table
-- Purpose: Track rider availability state
-- Values: AVAILABLE, ON_TRIP, OFFLINE

ALTER TABLE RIDER_DEMO 
ADD COLUMN IF NOT EXISTS STATUS VARCHAR(32) DEFAULT 'AVAILABLE';

-- Index for fast rider status filtering
CREATE INDEX IF NOT EXISTS idx_rider_demo_status ON RIDER_DEMO(STATUS);
