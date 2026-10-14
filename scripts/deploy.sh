#!/bin/bash
set -e

# 설정
DEPLOY_DIR="/home/ubuntu/meeteam"
ACTIVE_ENV_FILE="$DEPLOY_DIR/active_env"
UPSTREAM_CONF="/etc/nginx/conf.d/meeteam-upstream.conf"
MAX_RETRY=60
RETRY_INTERVAL=5

# 색상 출력
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 현재 활성 환경 확인
get_active_env() {
    if [ -f "$ACTIVE_ENV_FILE" ]; then
        cat "$ACTIVE_ENV_FILE" #active_env 는 "green" 이 존재함.
    else
        echo "blue"
    fi
}

# 반대 환경 반환
get_inactive_env() {
    if [ "$(get_active_env)" == "blue" ]; then
        echo "green"
    else
        echo "blue"
    fi
}

# 포트 반환
get_port() {
    if [ "$1" == "blue" ]; then
        echo "8080"
    else
        echo "8081"
    fi
}

# Health check 수행
health_check() {
    local port=$1
    local retry=0

    log_info "Health check 시작: localhost:$port"

    while [ $retry -lt $MAX_RETRY ]; do
        if curl -sf http://localhost:$port/actuator/health > /dev/null 2>&1; then
            log_info "Health check 성공!"
            return 0
        fi
        retry=$((retry + 1))
        log_warn "Health check 재시도 ($retry/$MAX_RETRY)..."
        sleep $RETRY_INTERVAL
    done

    log_error "Health check 실패"
    return 1
}

# Nginx upstream 전환
switch_upstream() {
    local target_port=$1

    log_info "Nginx upstream 전환: 127.0.0.1:$target_port"

    sudo tee "$UPSTREAM_CONF" > /dev/null << EOF
upstream meeteam_backend {
    server 127.0.0.1:$target_port;
}
EOF

    # Nginx 설정 테스트
    if ! sudo nginx -t > /dev/null 2>&1; then
        log_error "Nginx 설정 오류"
        return 1
    fi

    # Nginx 리로드 (무중단)
    sudo systemctl reload nginx

    log_info "Upstream 전환 완료"
}

# 메인 배포 로직
deploy() {
    local active_env=$(get_active_env)
    local target_env=$(get_inactive_env)
    local target_port=$(get_port $target_env)
    local target_container="meeteam-$target_env"

    log_info "=========================================="
    log_info "Blue/Green 배포 시작"
    log_info "현재 활성: $active_env"
    log_info "배포 대상: $target_env (port: $target_port)"
    log_info "=========================================="

    cd "$DEPLOY_DIR"

    # 1. 새 이미지 Pull
    log_info "새 이미지 Pull 중..."
    docker compose pull $target_container

    # 2. 대상 컨테이너 시작
    log_info "컨테이너 시작: $target_container"
    docker compose up -d $target_container

    # 3. Health check
    if ! health_check "$target_port"; then
        log_error "배포 실패 - 컨테이너 중지"
        docker compose stop $target_container
        exit 1
    fi

    # 4. Nginx upstream 전환
    switch_upstream "$target_port"

    # 5. 상태 파일 업데이트
    echo "$target_env" > "$ACTIVE_ENV_FILE"
    log_info "활성 환경 업데이트: $target_env"

    # 6. 이전 컨테이너 종료
    local old_container="meeteam-$active_env"
    log_info "이전 컨테이너 종료: $old_container"
    docker compose stop $old_container

    # 7. 미사용 Docker 리소스 정리
    log_info "미사용 Docker 이미지 정리 중..."
    docker image prune -f

    log_info "=========================================="
    log_info "배포 완료!"
    log_info "활성 환경: $target_env"
    log_info "=========================================="
}

# 스크립트 실행
deploy