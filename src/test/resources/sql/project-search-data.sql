-- ==================== 테스트 시드 데이터 ====================
-- 프로젝트 검색 통합 테스트용
-- @Nested + @Sql 조합에서 트랜잭션 경계 이슈로 DELETE 필요

DELETE FROM project;
DELETE FROM member;

-- ==================== 회원 데이터 ====================
INSERT INTO member (member_id, email, real_name, role, version, created_at) VALUES
(1, 'hong@example.com', '홍길동', 'USER', 0, '2024-01-01 09:00:00'),
(2, 'kim@example.com', '김철수', 'USER', 0, '2024-01-01 09:00:00'),
(3, 'lee@example.com', '이영희', 'USER', 0, '2024-01-01 09:00:00'),
(4, 'park@example.com', '박민수', 'USER', 0, '2024-01-01 09:00:00'),
(5, 'choi@example.com', '최지연', 'USER', 0, '2024-01-01 09:00:00');

-- ==================== 프로젝트 데이터 ====================
-- created_at 순서: 높은 ID = 더 최신
INSERT INTO project (project_id, creator_id, project_name, description, project_category, platform_category, recruitment_status, recruitment_deadline_type, start_date, end_date, is_deleted, like_count, created_at) VALUES
-- CAPSTONE 카테고리 (8개)
(1, 1, 'AI 챗봇 개발', 'GPT 기반 챗봇 서비스', 'CAPSTONE', 'WEB', 'RECRUITING', 'END_DATE', '2024-01-01', '2024-12-31', false, 15, '2024-01-01 10:00:00'),
(2, 2, 'AI 이미지 분석 플랫폼', '딥러닝 이미지 분류', 'CAPSTONE', 'WEB', 'RECRUITING', 'END_DATE', '2024-02-01', '2024-11-30', false, 8, '2024-01-02 10:00:00'),
(3, 3, 'AI 음성인식 앱', '음성 명령 인식 앱', 'CAPSTONE', 'IOS', 'CLOSED', 'RECRUITMENT_COMPLETED', '2024-01-15', '2024-06-30', false, 22, '2024-01-03 10:00:00'),

-- CREATIVE_SEMESTER 카테고리 (2개)
(4, 1, '헬스케어 모니터링', '건강 데이터 트래킹 앱', 'CREATIVE_SEMESTER', 'ANDROID', 'RECRUITING', 'END_DATE', '2024-03-01', '2024-10-15', false, 12, '2024-01-04 10:00:00'),
(5, 4, '의료 상담 플랫폼', '비대면 의료 상담', 'CREATIVE_SEMESTER', 'WEB', 'SUSPENDED', 'END_DATE', '2024-02-15', '2024-09-30', false, 5, '2024-01-05 10:00:00'),

-- CLUB 카테고리 (4개)
(6, 2, '온라인 학습 플랫폼', '인터랙티브 학습 서비스', 'CLUB', 'WEB', 'RECRUITING', 'END_DATE', '2024-04-01', '2024-08-31', false, 18, '2024-01-06 10:00:00'),
(7, 5, '코딩 교육 앱', '어린이 코딩 교육', 'CLUB', 'IOS', 'RECRUITING', 'END_DATE', '2024-03-15', '2024-12-15', false, 9, '2024-01-07 10:00:00'),

-- CAPSTONE 추가 (2개)
(8, 3, '가계부 앱 개발', '스마트 지출 관리', 'CAPSTONE', 'ANDROID', 'RECRUITING', 'END_DATE', '2024-05-01', '2024-07-15', false, 7, '2024-01-08 10:00:00'),
(9, 4, '핀테크 결제 시스템', '간편 결제 서비스', 'CAPSTONE', 'WEB', 'CLOSED', 'RECRUITMENT_COMPLETED', '2024-01-01', '2024-05-31', false, 30, '2024-01-09 10:00:00'),

-- CREATIVE_SEMESTER 추가 (1개)
(10, 5, '반려동물 케어 앱', '펫 건강 관리 서비스', 'CREATIVE_SEMESTER', 'ANDROID', 'RECRUITING', 'END_DATE', '2024-06-01', '2024-11-15', false, 11, '2024-01-10 10:00:00'),

-- CLUB 추가 (1개)
(11, 1, '패션 추천 서비스', 'AI 기반 스타일 추천', 'CLUB', 'IOS', 'RECRUITING', 'END_DATE', '2024-04-15', '2024-10-31', false, 14, '2024-01-11 10:00:00'),

-- CREATIVE_SEMESTER 추가 (1개)
(12, 2, '친환경 캠페인 플랫폼', '탄소 발자국 계산기', 'CREATIVE_SEMESTER', 'WEB', 'RECRUITING', 'END_DATE', '2024-05-15', '2024-09-15', false, 6, '2024-01-12 10:00:00'),

-- CLUB 추가 (1개)
(13, 3, '취미 공유 커뮤니티', '취미 매칭 서비스', 'CLUB', 'WEB', 'SUSPENDED', 'END_DATE', '2024-03-01', '2024-08-15', false, 3, '2024-01-13 10:00:00'),

-- CAPSTONE 추가 (마감임박순 + 키워드 검색 테스트용)
(14, 4, '긴급 프로젝트', '마감 3일 전', 'CAPSTONE', 'WEB', 'RECRUITING', 'END_DATE', '2024-06-01', '2024-06-10', false, 2, '2024-01-14 10:00:00'),
(15, 5, '여유 프로젝트', '마감 6개월 후', 'CAPSTONE', 'WEB', 'RECRUITING', 'END_DATE', '2024-06-01', '2025-01-31', false, 1, '2024-01-15 10:00:00'),
(16, 1, 'Spring Boot 백엔드 개발', '스프링 기반 API 서버', 'CAPSTONE', 'WEB', 'RECRUITING', 'END_DATE', '2024-06-01', '2024-12-01', false, 10, '2024-01-16 10:00:00'),
(17, 2, 'React 프론트엔드 개발', '리액트 SPA 개발', 'CAPSTONE', 'WEB', 'RECRUITING', 'END_DATE', '2024-06-01', '2024-11-01', false, 8, '2024-01-17 10:00:00'),
(18, 3, 'Flutter 크로스플랫폼 앱', '플러터 모바일 앱', 'CAPSTONE', 'ANDROID', 'RECRUITING', 'END_DATE', '2024-06-01', '2024-10-01', false, 5, '2024-01-18 10:00:00'),

-- CLUB 추가 (플랫폼별 테스트 강화)
(19, 4, 'iOS 네이티브 앱', 'Swift 기반 앱', 'CLUB', 'IOS', 'RECRUITING', 'END_DATE', '2024-06-01', '2024-09-30', false, 4, '2024-01-19 10:00:00'),
(20, 5, 'Android 네이티브 앱', 'Kotlin 기반 앱', 'CLUB', 'ANDROID', 'RECRUITING', 'END_DATE', '2024-06-01', '2024-09-15', false, 6, '2024-01-20 10:00:00');