-- Keep real_name unchanged. Existing members remain public until they opt in.
ALTER TABLE member
    ADD COLUMN name_private BIT NOT NULL DEFAULT 0;
