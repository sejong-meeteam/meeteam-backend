-- V1: OAuth 관련 컬럼 삭제
-- 세종대 포털 로그인만 사용하므로 OAuth 관련 컬럼 제거

-- provider_id 컬럼 삭제 (존재하는 경우에만)
SET @exist_provider_id := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'member'
    AND COLUMN_NAME = 'provider_id'
);

SET @sql_provider_id = IF(@exist_provider_id > 0,
    'ALTER TABLE member DROP COLUMN provider_id',
    'SELECT "provider_id column does not exist"');
PREPARE stmt FROM @sql_provider_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- oauth_access_token 컬럼 삭제 (존재하는 경우에만)
SET @exist_oauth_token := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'member'
    AND COLUMN_NAME = 'oauth_access_token'
);

SET @sql_oauth_token = IF(@exist_oauth_token > 0,
    'ALTER TABLE member DROP COLUMN oauth_access_token',
    'SELECT "oauth_access_token column does not exist"');
PREPARE stmt FROM @sql_oauth_token;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;