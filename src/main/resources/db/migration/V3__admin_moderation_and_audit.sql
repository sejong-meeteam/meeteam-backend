-- REVIEWED APPLICATION PLAN ONLY: do not auto-enable Flyway on an existing DB without history/baseline review.
-- MySQL 8.0. Additive migration: no record/table/column deletion. Back up and validate before deployment.
ALTER TABLE member
    ADD COLUMN suspended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN suspended_until DATETIME(6) NULL,
    ADD COLUMN suspend_reason VARCHAR(200) NULL,
    ADD COLUMN last_login_at DATETIME(6) NULL,
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE project
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE project SET visibility = CASE WHEN is_deleted = TRUE THEN 'DELETED' ELSE 'VISIBLE' END;

CREATE TABLE admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    admin_name VARCHAR(255) NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    target_name VARCHAR(255) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    metadata JSON NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_admin_audit_target_created (target_type, target_id, created_at),
    INDEX idx_admin_audit_created (created_at)
);
