-- Every account is either the booking admin or an operational rider.
-- Existing accounts are preserved as ADMIN; update known rider accounts to
-- RIDER before linking them from RIDER_DETAILS.USER_ID.
ALTER TABLE USERS
    ADD COLUMN IF NOT EXISTS ROLE VARCHAR(16) NOT NULL DEFAULT 'ADMIN';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_users_role'
    ) THEN
        ALTER TABLE USERS
            ADD CONSTRAINT ck_users_role CHECK (ROLE IN ('ADMIN', 'RIDER'));
    END IF;
END $$;
