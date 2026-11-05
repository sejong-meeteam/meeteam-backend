-- Additive migration. Review actual schema/Flyway history and backups before live application.
-- A separate revision cancels pending link attempts without revoking MeeTeam login tokens.
ALTER TABLE member ADD COLUMN github_link_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE member_github_account (
    member_id BIGINT NOT NULL,
    github_user_id BIGINT NOT NULL,
    login VARCHAR(39) NOT NULL,
    avatar_url VARCHAR(500),
    connected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT uk_member_github_user UNIQUE (github_user_id),
    CONSTRAINT fk_github_account_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE
);
