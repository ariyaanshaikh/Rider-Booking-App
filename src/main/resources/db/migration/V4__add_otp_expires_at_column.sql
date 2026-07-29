-- Migration V4: Add OTP_EXPIRES_AT column to RIDE_DETAILS table
-- Purpose: Store OTP expiry time for security
-- OTP becomes invalid after this timestamp

ALTER TABLE RIDE_DETAILS 
ADD COLUMN IF NOT EXISTS OTP_EXPIRES_AT TIMESTAMP;

-- Index for OTP expiry filtering (useful for cleanup queries)
CREATE INDEX IF NOT EXISTS idx_ride_details_otp_expires_at ON RIDE_DETAILS(OTP_EXPIRES_AT);
