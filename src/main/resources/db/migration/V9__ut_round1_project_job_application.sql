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

-- C3. MARKETING and DATA job fields. Same rows as db/seed/job-seed.sql; existing tech stacks are reused by name.
ALTER TABLE job_field MODIFY COLUMN code VARCHAR(50) NOT NULL;
ALTER TABLE job_position MODIFY COLUMN code VARCHAR(70) NOT NULL;

INSERT INTO job_field (code, name) VALUES
    ('MARKETING', '마케팅'),
    ('DATA', '데이터')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO job_position (job_field_id, code, name) VALUES
    ((SELECT job_field_id FROM job_field WHERE code = 'MARKETING'), 'PERFORMANCE_MARKETER', '퍼포먼스 마케터'),
    ((SELECT job_field_id FROM job_field WHERE code = 'MARKETING'), 'CONTENT_MARKETER', '콘텐츠 마케터'),
    ((SELECT job_field_id FROM job_field WHERE code = 'MARKETING'), 'BRAND_MARKETER', '브랜드 마케터'),
    ((SELECT job_field_id FROM job_field WHERE code = 'DATA'), 'DATA_ANALYST', '데이터 분석가'),
    ((SELECT job_field_id FROM job_field WHERE code = 'DATA'), 'DATA_ENGINEER', '데이터 엔지니어'),
    ((SELECT job_field_id FROM job_field WHERE code = 'DATA'), 'DATA_SCIENTIST', '데이터 사이언티스트')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO tech_stack (name) VALUES
    ('Meta Ads'), ('Google Ads'), ('Canva'),
    ('R'), ('SQL'), ('Tableau'), ('Excel')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id)
SELECT jf.job_field_id, ts.tech_stack_id
FROM job_field jf, tech_stack ts
WHERE jf.code = 'MARKETING'
  AND ts.name IN ('Google Analytics', 'Meta Ads', 'Google Ads', 'Notion', 'Figma', 'Canva')
ON DUPLICATE KEY UPDATE job_field_id = VALUES(job_field_id);

INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id)
SELECT jf.job_field_id, ts.tech_stack_id
FROM job_field jf, tech_stack ts
WHERE jf.code = 'DATA'
  AND ts.name IN ('Python', 'R', 'SQL', 'Pandas', 'Tableau', 'Excel')
ON DUPLICATE KEY UPDATE job_field_id = VALUES(job_field_id);

-- C4. Decision time for the leader's processed-applicant history.
-- Existing decided rows are backfilled from edited_at: once ACCEPTED/REJECTED, nothing else updates the row.
ALTER TABLE project_member_application ADD COLUMN decided_at DATETIME(6) NULL;
UPDATE project_member_application SET decided_at = edited_at WHERE status IN ('ACCEPTED', 'REJECTED');
