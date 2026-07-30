-- A rider profile belongs to exactly one authenticated USERS account.
ALTER TABLE RIDER_DETAILS ADD COLUMN IF NOT EXISTS USER_ID BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_rider_details_user_id
    ON RIDER_DETAILS(USER_ID) WHERE USER_ID IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_rider_details_user'
    ) THEN
        ALTER TABLE RIDER_DETAILS
            ADD CONSTRAINT fk_rider_details_user
            FOREIGN KEY (USER_ID) REFERENCES USERS(ID);
    END IF;
END $$;
