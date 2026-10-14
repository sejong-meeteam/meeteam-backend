-- =====================================================
-- Job 초기 데이터 (JobField, JobPosition, TechStack)
-- =====================================================

-- JobField (직군)
INSERT INTO job_field (code, name) VALUES
    ('PLANNING', '기획'),
    ('DESIGN', '디자인'),
    ('FRONTEND', '프론트'),
    ('BACKEND', '백엔드'),
    ('AI', 'AI'),
    ('INFRA_OPERATION', '인프라/운영')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- JobPosition (직무)
-- 기획
INSERT INTO job_position (job_field_id, code, name) VALUES
    ((SELECT job_field_id FROM job_field WHERE code = 'PLANNING'), 'PRODUCT_MANAGER', 'PM 프로덕트 매니저'),
    ((SELECT job_field_id FROM job_field WHERE code = 'PLANNING'), 'PRODUCT_OWNER', 'PO 프로덕트 오너'),
    ((SELECT job_field_id FROM job_field WHERE code = 'PLANNING'), 'SERVICE_PLANNER', '서비스 기획')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 디자인
INSERT INTO job_position (job_field_id, code, name) VALUES
    ((SELECT job_field_id FROM job_field WHERE code = 'DESIGN'), 'UI_UX_DESIGNER', 'UI/UX 디자이너'),
    ((SELECT job_field_id FROM job_field WHERE code = 'DESIGN'), 'MOTION_DESIGNER', '모션 디자이너'),
    ((SELECT job_field_id FROM job_field WHERE code = 'DESIGN'), 'BX_BRAND_DESIGNER', 'BX 브랜드 디자이너')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 프론트
INSERT INTO job_position (job_field_id, code, name) VALUES
    ((SELECT job_field_id FROM job_field WHERE code = 'FRONTEND'), 'WEB_FRONTEND', '웹 프론트엔드'),
    ((SELECT job_field_id FROM job_field WHERE code = 'FRONTEND'), 'IOS', 'iOS'),
    ((SELECT job_field_id FROM job_field WHERE code = 'FRONTEND'), 'ANDROID', 'Android'),
    ((SELECT job_field_id FROM job_field WHERE code = 'FRONTEND'), 'CROSS_PLATFORM', '크로스 플랫폼')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 백엔드
INSERT INTO job_position (job_field_id, code, name) VALUES
    ((SELECT job_field_id FROM job_field WHERE code = 'BACKEND'), 'JAVA_SPRING', 'Java/Spring'),
    ((SELECT job_field_id FROM job_field WHERE code = 'BACKEND'), 'KOTLIN_SPRING', 'Kotlin/Spring'),
    ((SELECT job_field_id FROM job_field WHERE code = 'BACKEND'), 'NODE_NESTJS', 'Node.js/NestJS'),
    ((SELECT job_field_id FROM job_field WHERE code = 'BACKEND'), 'PYTHON_BACKEND', 'Python Backend')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- AI
INSERT INTO job_position (job_field_id, code, name) VALUES
    ((SELECT job_field_id FROM job_field WHERE code = 'AI'), 'MACHINE_LEARNING', '머신 러닝'),
    ((SELECT job_field_id FROM job_field WHERE code = 'AI'), 'DEEP_LEARNING', '딥러닝'),
    ((SELECT job_field_id FROM job_field WHERE code = 'AI'), 'LLM', 'LLM'),
    ((SELECT job_field_id FROM job_field WHERE code = 'AI'), 'MLOPS', 'MLOps')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 인프라/운영
INSERT INTO job_position (job_field_id, code, name) VALUES
    ((SELECT job_field_id FROM job_field WHERE code = 'INFRA_OPERATION'), 'DEVOPS_ARCHITECT', 'DevOps 엔지니어/아키텍처'),
    ((SELECT job_field_id FROM job_field WHERE code = 'INFRA_OPERATION'), 'QA', 'QA'),
    ((SELECT job_field_id FROM job_field WHERE code = 'INFRA_OPERATION'), 'CLOUD_ENGINEER', 'Cloud 엔지니어')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- =====================================================
-- TechStack (기술 스택)
-- =====================================================
INSERT INTO tech_stack (name) VALUES
    -- 기획
    ('Notion'), ('Jira'), ('Figma'), ('Google Analytics'),
    -- 디자인
    ('Zeplin'), ('After Effects'), ('Premiere'), ('Illustrator'), ('Photoshop'),
    -- 프론트 (웹)
    ('React.js'), ('TypeScript'), ('Next.js'), ('Vue.js'), ('Angular'),
    ('Zustand'), ('Redux'), ('TailwindCSS'),
    -- 프론트 (iOS)
    ('Swift'), ('SwiftUI'), ('UIKit'), ('Firebase'),
    -- 프론트 (Android)
    ('Kotlin'), ('Jetpack Compose'), ('Coroutine'), ('Hilt'),
    -- 프론트 (크로스플랫폼)
    ('React Native'), ('Expo'), ('Dart'), ('Flutter'),
    -- 백엔드
    ('Java'), ('Python'), ('Ruby on Rails'),
    ('Spring Boot'), ('Spring WebFlux'), ('NestJs'), ('Node.js'),
    ('JPA'), ('QueryDSL'),
    ('MySQL'), ('PostgreSQL'), ('MariaDB'), ('MongoDB'), ('Redis'), ('ElasticSearch'),
    ('Docker'), ('AWS'), ('Nginx'), ('Linux'),
    ('GitHub Actions'), ('GitLab CI/CD'), ('Jenkins'),
    ('Prometheus'), ('Grafana'), ('WebSocket'), ('Kafka'), ('RabbitMQ'),
    -- AI
    ('NumPy'), ('Pandas'), ('Scikit-learn'), ('PyTorch'), ('TensorFlow'), ('CUDA'),
    ('Hugging Face'), ('OpenAI API'), ('LangChain'), ('RAG'), ('Vector DB'),
    ('MLflow'), ('Airflow'), ('Kubernetes'),
    -- 인프라/운영
    ('Prometheus/Grafana'), ('Postman'), ('Playwright'), ('Selenium'), ('Cypress')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- =====================================================
-- JobFieldTechStack (직군-기술스택 연결)
-- =====================================================

-- 기획
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id)
SELECT jf.job_field_id, ts.tech_stack_id
FROM job_field jf, tech_stack ts
WHERE jf.code = 'PLANNING'
  AND ts.name IN ('Notion', 'Jira', 'Figma', 'Google Analytics')
ON DUPLICATE KEY UPDATE job_field_id = VALUES(job_field_id);

-- 디자인
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id)
SELECT jf.job_field_id, ts.tech_stack_id
FROM job_field jf, tech_stack ts
WHERE jf.code = 'DESIGN'
  AND ts.name IN ('Figma', 'Zeplin', 'After Effects', 'Premiere', 'Illustrator', 'Photoshop')
ON DUPLICATE KEY UPDATE job_field_id = VALUES(job_field_id);

-- 프론트
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id)
SELECT jf.job_field_id, ts.tech_stack_id
FROM job_field jf, tech_stack ts
WHERE jf.code = 'FRONTEND'
  AND ts.name IN (
    'React.js', 'TypeScript', 'Next.js', 'Vue.js', 'Angular', 'Zustand', 'Redux', 'TailwindCSS',
    'Swift', 'SwiftUI', 'UIKit', 'Firebase',
    'Kotlin', 'Jetpack Compose', 'Coroutine', 'Hilt',
    'React Native', 'Expo', 'Dart', 'Flutter'
  )
ON DUPLICATE KEY UPDATE job_field_id = VALUES(job_field_id);

-- 백엔드
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id)
SELECT jf.job_field_id, ts.tech_stack_id
FROM job_field jf, tech_stack ts
WHERE jf.code = 'BACKEND'
  AND ts.name IN (
    'Java', 'Kotlin', 'TypeScript', 'Python', 'Ruby on Rails',
    'Spring Boot', 'Spring WebFlux', 'NestJs', 'Node.js',
    'JPA', 'QueryDSL',
    'MySQL', 'PostgreSQL', 'MariaDB', 'MongoDB', 'Redis', 'ElasticSearch',
    'Docker', 'AWS', 'Nginx', 'Linux',
    'GitHub Actions', 'GitLab CI/CD', 'Jenkins',
    'Prometheus', 'Grafana', 'WebSocket', 'Kafka', 'RabbitMQ'
  )
ON DUPLICATE KEY UPDATE job_field_id = VALUES(job_field_id);

-- AI
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id)
SELECT jf.job_field_id, ts.tech_stack_id
FROM job_field jf, tech_stack ts
WHERE jf.code = 'AI'
  AND ts.name IN (
    'Python', 'NumPy', 'Pandas', 'Scikit-learn', 'PyTorch', 'TensorFlow', 'CUDA',
    'Hugging Face', 'OpenAI API', 'LangChain', 'RAG', 'Vector DB',
    'MLflow', 'Airflow', 'Docker', 'Kubernetes', 'AWS'
  )
ON DUPLICATE KEY UPDATE job_field_id = VALUES(job_field_id);

-- 인프라/운영
INSERT INTO job_field_tech_stack (job_field_id, tech_stack_id)
SELECT jf.job_field_id, ts.tech_stack_id
FROM job_field jf, tech_stack ts
WHERE jf.code = 'INFRA_OPERATION'
  AND ts.name IN (
    'AWS', 'Docker', 'Kubernetes', 'Nginx',
    'GitHub Actions', 'Prometheus/Grafana', 'Postman',
    'Jira', 'Playwright', 'Selenium', 'Cypress'
  )
ON DUPLICATE KEY UPDATE job_field_id = VALUES(job_field_id);
