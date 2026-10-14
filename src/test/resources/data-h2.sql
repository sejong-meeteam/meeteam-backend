-- =====================================================
-- Job 초기 데이터 (JobField, JobPosition, TechStack)
-- =====================================================

-- JobField (직군)
INSERT INTO job_field (job_field_id, code, name) VALUES
    (1, 'PLANNING', '기획'),
    (2, 'DESIGN', '디자인'),
    (3, 'FRONTEND', '프론트'),
    (4, 'BACKEND', '백엔드'),
    (5, 'AI', 'AI'),
    (6, 'INFRA_OPERATION', '인프라/운영');

-- JobPosition (직무)
-- 기획
INSERT INTO job_position (job_position_id, job_field_id, code, name) VALUES
    (1, 1, 'PRODUCT_MANAGER', 'PM 프로덕트 매니저'),
    (2, 1, 'PRODUCT_OWNER', 'PO 프로덕트 오너'),
    (3, 1, 'SERVICE_PLANNER', '서비스 기획');

-- 디자인
INSERT INTO job_position (job_position_id, job_field_id, code, name) VALUES
    (4, 2, 'UI_UX_DESIGNER', 'UI/UX 디자이너'),
    (5, 2, 'MOTION_DESIGNER', '모션 디자이너'),
    (6, 2, 'BX_BRAND_DESIGNER', 'BX 브랜드 디자이너');

-- 프론트
INSERT INTO job_position (job_position_id, job_field_id, code, name) VALUES
    (7, 3, 'WEB_FRONTEND', '웹 프론트엔드'),
    (8, 3, 'IOS', 'iOS'),
    (9, 3, 'ANDROID', 'Android'),
    (10, 3, 'CROSS_PLATFORM', '크로스 플랫폼');

-- 백엔드
INSERT INTO job_position (job_position_id, job_field_id, code, name) VALUES
    (11, 4, 'JAVA_SPRING', 'Java/Spring'),
    (12, 4, 'KOTLIN_SPRING', 'Kotlin/Spring'),
    (13, 4, 'NODE_NESTJS', 'Node.js/NestJS'),
    (14, 4, 'PYTHON_BACKEND', 'Python Backend');

-- AI
INSERT INTO job_position (job_position_id, job_field_id, code, name) VALUES
    (15, 5, 'MACHINE_LEARNING', '머신 러닝'),
    (16, 5, 'DEEP_LEARNING', '딥러닝'),
    (17, 5, 'LLM', 'LLM'),
    (18, 5, 'MLOPS', 'MLOps');

-- 인프라/운영
INSERT INTO job_position (job_position_id, job_field_id, code, name) VALUES
    (19, 6, 'DEVOPS_ARCHITECT', 'DevOps 엔지니어/아키텍처'),
    (20, 6, 'QA', 'QA'),
    (21, 6, 'CLOUD_ENGINEER', 'Cloud 엔지니어');

-- =====================================================
-- TechStack (기술 스택)
-- =====================================================
INSERT INTO tech_stack (tech_stack_id, name) VALUES
    -- 기획
    (1, 'Notion'), (2, 'Jira'), (3, 'Figma'), (4, 'Google Analytics'),
    -- 디자인
    (5, 'Zeplin'), (6, 'After Effects'), (7, 'Premiere'), (8, 'Illustrator'), (9, 'Photoshop'),
    -- 프론트 (웹)
    (10, 'React.js'), (11, 'TypeScript'), (12, 'Next.js'), (13, 'Vue.js'), (14, 'Angular'),
    (15, 'Zustand'), (16, 'Redux'), (17, 'TailwindCSS'),
    -- 프론트 (iOS)
    (18, 'Swift'), (19, 'SwiftUI'), (20, 'UIKit'), (21, 'Firebase'),
    -- 프론트 (Android)
    (22, 'Kotlin'), (23, 'Jetpack Compose'), (24, 'Coroutine'), (25, 'Hilt'),
    -- 프론트 (크로스플랫폼)
    (26, 'React Native'), (27, 'Expo'), (28, 'Dart'), (29, 'Flutter'),
    -- 백엔드
    (30, 'Java'), (31, 'Python'), (32, 'Ruby on Rails'),
    (33, 'Spring Boot'), (34, 'Spring WebFlux'), (35, 'NestJs'), (36, 'Node.js'),
    (37, 'JPA'), (38, 'QueryDSL'),
    (39, 'MySQL'), (40, 'PostgreSQL'), (41, 'MariaDB'), (42, 'MongoDB'), (43, 'Redis'), (44, 'ElasticSearch'),
    (45, 'Docker'), (46, 'AWS'), (47, 'Nginx'), (48, 'Linux'),
    (49, 'GitHub Actions'), (50, 'GitLab CI/CD'), (51, 'Jenkins'),
    (52, 'Prometheus'), (53, 'Grafana'), (54, 'WebSocket'), (55, 'Kafka'), (56, 'RabbitMQ'),
    -- AI
    (57, 'NumPy'), (58, 'Pandas'), (59, 'Scikit-learn'), (60, 'PyTorch'), (61, 'TensorFlow'), (62, 'CUDA'),
    (63, 'Hugging Face'), (64, 'OpenAI API'), (65, 'LangChain'), (66, 'RAG'), (67, 'Vector DB'),
    (68, 'MLflow'), (69, 'Airflow'), (70, 'Kubernetes'),
    -- 인프라/운영
    (71, 'Prometheus/Grafana'), (72, 'Postman'), (73, 'Playwright'), (74, 'Selenium'), (75, 'Cypress');

-- =====================================================
-- JobFieldTechStack (직군-기술스택 연결)
-- =====================================================

-- 기획 (job_field_id = 1)
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4);

-- 디자인 (job_field_id = 2)
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id) VALUES
    (2, 3), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9);

-- 프론트 (job_field_id = 3)
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id) VALUES
    (3, 10), (3, 11), (3, 12), (3, 13), (3, 14), (3, 15), (3, 16), (3, 17),
    (3, 18), (3, 19), (3, 20), (3, 21),
    (3, 22), (3, 23), (3, 24), (3, 25),
    (3, 26), (3, 27), (3, 28), (3, 29);

-- 백엔드 (job_field_id = 4)
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id) VALUES
    (4, 11), (4, 22), (4, 30), (4, 31), (4, 32),
    (4, 33), (4, 34), (4, 35), (4, 36),
    (4, 37), (4, 38),
    (4, 39), (4, 40), (4, 41), (4, 42), (4, 43), (4, 44),
    (4, 45), (4, 46), (4, 47), (4, 48),
    (4, 49), (4, 50), (4, 51),
    (4, 52), (4, 53), (4, 54), (4, 55), (4, 56);

-- AI (job_field_id = 5)
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id) VALUES
    (5, 31), (5, 57), (5, 58), (5, 59), (5, 60), (5, 61), (5, 62),
    (5, 63), (5, 64), (5, 65), (5, 66), (5, 67),
    (5, 68), (5, 69), (5, 45), (5, 70), (5, 46);

-- 인프라/운영 (job_field_id = 6)
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id) VALUES
    (6, 46), (6, 45), (6, 70), (6, 47),
    (6, 49), (6, 71), (6, 72),
    (6, 2), (6, 73), (6, 74), (6, 75);

