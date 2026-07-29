-- Ride-start OTPs no longer expire by time. They remain valid only while the
-- ride is in ACCEPTED status; service-layer lifecycle checks still apply.

DROP INDEX IF EXISTS idx_ride_details_otp_expires_at;
ALTER TABLE RIDE_DETAILS DROP COLUMN IF EXISTS OTP_EXPIRES_AT;
