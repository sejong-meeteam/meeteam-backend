-- Additive migration. Back up and review history before applying to an existing DB.
-- Run BEFORE deploying the entity change; never enable schema recreation or seed resets.
-- Existing members retain their current names and remain unverified until a separate approved process.
ALTER TABLE member
    ADD COLUMN department VARCHAR(200) NULL,
    ADD COLUMN school_profile_verified BOOLEAN NOT NULL DEFAULT FALSE;
