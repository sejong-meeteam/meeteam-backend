# 서버 로그 모니터링 가이드

MeeTeam 서버의 로그를 확인하는 방법을 정리한 문서입니다.

## 1. EC2 접속

```bash
# SSH로 EC2 접속
ssh -i your-key.pem ec2-user@your-ec2-ip

# 또는 AWS Session Manager 사용 (키 없이)
aws ssm start-session --target i-xxxxxxxxxx
```

## 2. Docker 컨테이너 로그 확인

### 기본 명령어

```bash
# 실행 중인 컨테이너 확인
docker ps

# 전체 로그 보기
docker logs meeteam-backend

# 최근 100줄만 보기
docker logs --tail 100 meeteam-backend

# 실시간 로그 보기 (follow)
docker logs -f meeteam-backend

# 최근 100줄 + 실시간
docker logs --tail 100 -f meeteam-backend

# 타임스탬프 포함
docker logs -t meeteam-backend
```

### 시간 기반 필터링

```bash
# 최근 10분간 로그
docker logs --since 10m meeteam-backend

# 최근 1시간
docker logs --since 1h meeteam-backend

# 특정 시간 이후
docker logs --since 2024-01-15T10:00:00 meeteam-backend

# 특정 시간 범위
docker logs --since 2024-01-15T10:00:00 --until 2024-01-15T11:00:00 meeteam-backend
```

## 3. 로그 검색 (grep)

### 기본 검색

```bash
# 특정 키워드 검색
docker logs meeteam-backend 2>&1 | grep "ERROR"

# 대소문자 무시
docker logs meeteam-backend 2>&1 | grep -i "error"

# 여러 키워드 (OR)
docker logs meeteam-backend 2>&1 | grep -E "ERROR|WARN"

# 특정 키워드 제외
docker logs meeteam-backend 2>&1 | grep -v "DEBUG"
```

### 실시간 검색

```bash
# 실시간 + ERROR만
docker logs -f meeteam-backend 2>&1 | grep "ERROR"

# 실시간 + 세종대 로그인 관련
docker logs -f meeteam-backend 2>&1 | grep -i "sejong\|포털"

# 실시간 + 특정 API
docker logs -f meeteam-backend 2>&1 | grep "/api/v1/auth"
```

### 컨텍스트 포함 검색

```bash
# 매칭 라인 + 앞 3줄
docker logs meeteam-backend 2>&1 | grep -B 3 "ERROR"

# 매칭 라인 + 뒤 5줄
docker logs meeteam-backend 2>&1 | grep -A 5 "ERROR"

# 앞뒤 3줄씩
docker logs meeteam-backend 2>&1 | grep -C 3 "ERROR"
```

## 4. 자주 사용하는 검색 패턴

### 에러 로그 확인

```bash
# 모든 에러
docker logs --since 1h meeteam-backend 2>&1 | grep -i "error\|exception"

# 스택트레이스 포함
docker logs --since 1h meeteam-backend 2>&1 | grep -A 20 "Exception"
```

### 특정 API 요청 추적

```bash
# 로그인 API
docker logs --since 30m meeteam-backend 2>&1 | grep "/auth/login"

# 특정 사용자 (학번)
docker logs --since 1h meeteam-backend 2>&1 | grep "21013220"
```

### 성능 관련

```bash
# 느린 요청 (실행시간 로그)
docker logs --since 1h meeteam-backend 2>&1 | grep "실행시간"

# 커넥션 풀 상태
docker logs --since 1h meeteam-backend 2>&1 | grep "커넥션 풀"
```

### 세종대 포털 관련

```bash
# 세종대 로그인 전체
docker logs --since 1h meeteam-backend 2>&1 | grep -i "sejong\|세종"

# 세종대 응답 코드
docker logs --since 1h meeteam-backend 2>&1 | grep "세종대 포털 응답"

# 로그인 실패
docker logs --since 1h meeteam-backend 2>&1 | grep "로그인 실패"
```

## 5. 로그 파일로 저장

```bash
# 파일로 저장
docker logs meeteam-backend > logs.txt 2>&1

# 최근 1시간 로그 저장
docker logs --since 1h meeteam-backend > recent_logs.txt 2>&1

# 에러만 저장
docker logs meeteam-backend 2>&1 | grep "ERROR" > errors.txt
```

## 6. 로컬에서 원격 로그 보기

SSH 접속 없이 로컬에서 바로 확인:

```bash
# 실시간 로그 스트리밍
ssh -i your-key.pem ec2-user@your-ec2-ip "docker logs -f meeteam-backend"

# 최근 로그 + 검색
ssh -i your-key.pem ec2-user@your-ec2-ip "docker logs --since 10m meeteam-backend" | grep "ERROR"
```

## 7. Docker Compose 로그

```bash
# 모든 서비스 로그
docker-compose logs

# 특정 서비스
docker-compose logs meeteam-backend

# 실시간
docker-compose logs -f meeteam-backend

# 최근 100줄 + 실시간
docker-compose logs --tail 100 -f meeteam-backend
```

## 8. 유용한 Alias 설정

`~/.bashrc` 또는 `~/.zshrc`에 추가:

```bash
# EC2에서 사용할 alias
alias mlog='docker logs -f meeteam-backend'
alias mlog100='docker logs --tail 100 -f meeteam-backend'
alias merror='docker logs --since 1h meeteam-backend 2>&1 | grep -i "error\|exception"'
alias msejong='docker logs --since 1h meeteam-backend 2>&1 | grep -i "sejong\|세종\|포털"'

# 적용
source ~/.bashrc
```

## 9. 로그 레벨별 확인

```bash
# INFO 레벨
docker logs meeteam-backend 2>&1 | grep " INFO "

# WARN 레벨
docker logs meeteam-backend 2>&1 | grep " WARN "

# ERROR 레벨
docker logs meeteam-backend 2>&1 | grep " ERROR "

# DEBUG 레벨 (설정되어 있다면)
docker logs meeteam-backend 2>&1 | grep " DEBUG "
```

## 10. 트러블슈팅 체크리스트

### 서버가 응답하지 않을 때
```bash
# 1. 컨테이너 상태 확인
docker ps -a

# 2. 최근 로그 확인
docker logs --tail 200 meeteam-backend

# 3. 에러 확인
docker logs --since 5m meeteam-backend 2>&1 | grep -i "error\|exception\|failed"

# 4. 메모리/CPU 확인
docker stats meeteam-backend --no-stream
```

### 특정 API 오류 시
```bash
# 1. 해당 API 로그 검색
docker logs --since 30m meeteam-backend 2>&1 | grep "/api/v1/your-endpoint"

# 2. 관련 에러 확인
docker logs --since 30m meeteam-backend 2>&1 | grep -B 5 -A 10 "ERROR"
```

### 로그인 문제 시
```bash
# 세종대 포털 관련 전체 로그
docker logs --since 1h meeteam-backend 2>&1 | grep -E "Sejong|세종|포털|로그인|authenticate"
```
