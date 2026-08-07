-- Vehicle details captured when an admin creates a ride request.
-- Nullable first keeps the migration safe for existing historical rides.
ALTER TABLE RIDE_DETAILS
    ADD COLUMN IF NOT EXISTS CHASSIS_NO VARCHAR(100),
    ADD COLUMN IF NOT EXISTS VEHICLE_NAME VARCHAR(150);
