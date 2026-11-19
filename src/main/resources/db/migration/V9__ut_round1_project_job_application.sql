-- Additive migration for UT round 1. Back up and review history before applying to an existing DB.
-- MySQL 8.0. No record/table deletion; existing rows keep their values.

-- Hibernate generates native ENUM columns for @Enumerated(STRING) on MySQL, so a schema it created
-- rejects newly added enum values. VARCHAR passes `ddl-auto: validate` and keeps every stored value.
ALTER TABLE project MODIFY COLUMN project_category VARCHAR(255) NULL;

-- C1. Optional collaboration info. Existing projects stay NULL (not entered).
ALTER TABLE project
    ADD COLUMN meeting_mode VARCHAR(20) NULL,
    ADD COLUMN weekly_hours INT NULL,
    ADD COLUMN meeting_note VARCHAR(100) NULL;
