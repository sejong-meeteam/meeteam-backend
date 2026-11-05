-- Local additive migration. Do not apply live without schema/history/backup review and approval.

-- Project-scoped delegation. Tokens/private keys are never stored in these tables.
CREATE TABLE github_project_link (
 project_id BIGINT NOT NULL,
 installation_id BIGINT,
 installation_active BOOLEAN NOT NULL DEFAULT FALSE,
 installed_by_member_id BIGINT,
 installed_github_user_id BIGINT,
 installed_at DATETIME(6),
 authorized_repository_ids LONGTEXT NOT NULL,
 github_repository_id BIGINT,
 full_name VARCHAR(200), html_url VARCHAR(500), description VARCHAR(500),
 repository_private BOOLEAN NOT NULL DEFAULT FALSE,
 default_branch VARCHAR(1024), stars INT NOT NULL DEFAULT 0, forks INT NOT NULL DEFAULT 0,
 auto_invite_on_approval BOOLEAN NOT NULL DEFAULT TRUE,
 auto_revoke_on_removal BOOLEAN NOT NULL DEFAULT TRUE,
 linked_at DATETIME(6), binding_version BIGINT NOT NULL DEFAULT 0, row_version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(project_id),
 CONSTRAINT uk_github_link_repository UNIQUE(github_repository_id),
 CONSTRAINT fk_github_link_project FOREIGN KEY(project_id) REFERENCES project(project_id) ON DELETE CASCADE
);
CREATE TABLE github_work (
 id BIGINT NOT NULL AUTO_INCREMENT,
 kind VARCHAR(24) NOT NULL, project_id BIGINT, member_id BIGINT,
 binding_version BIGINT NOT NULL, payload LONGTEXT NOT NULL,
 dedup_key VARCHAR(190), status VARCHAR(16) NOT NULL,
 attempts INT NOT NULL, next_attempt_at DATETIME(6) NOT NULL, locked_until DATETIME(6),
 created_at DATETIME(6) NOT NULL, failure_reason VARCHAR(300),
 PRIMARY KEY(id), CONSTRAINT uk_github_work_dedup UNIQUE(dedup_key),
 INDEX idx_github_work_pending(status,next_attempt_at,id),
 INDEX idx_github_work_lease(status,locked_until,id)
);
CREATE TABLE github_repository_snapshot (
 repository_id BIGINT PRIMARY KEY, last_pushed_at DATETIME(6), synced_at DATETIME(6),
 open_issue_count INT NOT NULL DEFAULT 0, open_pull_request_count INT NOT NULL DEFAULT 0,
 weekly_counts LONGTEXT NOT NULL, languages LONGTEXT NOT NULL, contributors LONGTEXT NOT NULL
);
CREATE TABLE github_activity_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, repository_id BIGINT NOT NULL,
 dedup_key VARCHAR(190) NOT NULL, type VARCHAR(32) NOT NULL, github_user_id BIGINT,
 number INT, title VARCHAR(1024), url VARCHAR(1000), occurred_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_github_event_dedup UNIQUE (dedup_key)
);
CREATE INDEX ix_github_event_repository_time ON github_activity_event(repository_id,occurred_at);
CREATE TABLE github_pull_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, repository_id BIGINT NOT NULL, number INT NOT NULL,
 github_user_id BIGINT, author_login VARCHAR(100), title VARCHAR(1024), url VARCHAR(1000),
 open BOOLEAN NOT NULL, draft BOOLEAN NOT NULL, review_decision VARCHAR(32),
 created_at DATETIME(6), updated_at DATETIME(6), merged_at DATETIME(6),
 CONSTRAINT uk_github_pr_number UNIQUE(repository_id,number)
);
CREATE TABLE project_github_collaborator (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 github_user_id BIGINT NOT NULL DEFAULT 0, login VARCHAR(39), invitation_id BIGINT,
 status VARCHAR(24) NOT NULL DEFAULT 'NOT_INVITED', invited_at DATETIME(6), failure_reason VARCHAR(300),
 binding_version BIGINT NOT NULL DEFAULT 0, operation_token VARCHAR(36), lease_until DATETIME(6), last_completed_work_id BIGINT, row_version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_github_collaborator_member UNIQUE(project_id,member_id)
);
CREATE TABLE github_collaborator_grant (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL, member_id BIGINT NOT NULL, binding_version BIGINT NOT NULL,
 github_user_id BIGINT NOT NULL, installation_id BIGINT NOT NULL, repository_id BIGINT, repository VARCHAR(200) NOT NULL,
 login VARCHAR(39) NOT NULL, invitation_id BIGINT, status VARCHAR(24) NOT NULL DEFAULT 'NOT_INVITED',
 invited_at DATETIME(6), failure_reason VARCHAR(300), last_completed_work_id BIGINT, row_version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_github_grant_identity UNIQUE(project_id,member_id,binding_version,github_user_id)
);
CREATE TABLE github_webhook_receipt (
 work_id BIGINT NOT NULL PRIMARY KEY,
 processed_at TIMESTAMP(6) NOT NULL
);
