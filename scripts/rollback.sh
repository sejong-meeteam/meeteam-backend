#!/bin/bash
set -e

# 설정
DEPLOY_DIR="/home/ubuntu/meeteam"
ACTIVE_ENV_FILE="$DEPLOY_DIR/active_env"
UPSTREAM_CONF="/etc/nginx/conf.d/meeteam-upstream.conf"

# 색상 출력
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 포트 반환
get_port() {
    if [ "$1" == "blue" ]; then
        echo "8080"
    else
        echo "8081"
    fi
}

# 현재 활성 환경 확인
get_active_env() {
    if [ -f "$ACTIVE_ENV_FILE" ]; then
        cat "$ACTIVE_ENV_FILE"
    else
        echo "blue"
    fi
}

# 롤백 수행
rollback() {
    local active_env=$(get_active_env)
    local rollback_env

    if [ "$active_env" == "blue" ]; then
        rollback_env="green"
    else
        rollback_env="blue"
    fi

    local rollback_port=$(get_port $rollback_env)
    local rollback_container="meeteam-$rollback_env"

    log_info "=========================================="
    log_info "롤백 시작"
    log_info "현재 활성: $active_env"
    log_info "롤백 대상: $rollback_env"
    log_info "=========================================="

    cd "$DEPLOY_DIR"

    # 1. 롤백 대상 컨테이너가 실행 중인지 확인
    if ! docker ps --format '{{.Names}}' | grep -q "^$rollback_container$"; then
        log_info "롤백 대상 컨테이너 시작: $rollback_container"
        docker compose up -d $rollback_container
        sleep 10
    fi

    # 2. Health check
    log_info "Health check 수행 중..."
    if ! curl -sf http://localhost:$rollback_port/actuator/health > /dev/null 2>&1; then
        log_error "롤백 대상 컨테이너가 정상이 아닙니다"
        exit 1
    fi

    # 3. Nginx upstream 전환
    log_info "Nginx upstream 전환: 127.0.0.1:$rollback_port"
    sudo tee "$UPSTREAM_CONF" > /dev/null << EOF
upstream meeteam_backend {
    server 127.0.0.1:$rollback_port;
}
EOF
    sudo systemctl reload nginx

    # 4. 상태 파일 업데이트
    echo "$rollback_env" > "$ACTIVE_ENV_FILE"

    # 5. 이전 활성 컨테이너 종료
    log_info "이전 컨테이너 종료: meeteam-$active_env"
    docker compose stop "meeteam-$active_env"

    log_info "=========================================="
    log_info "롤백 완료!"
    log_info "활성 환경: $rollback_env"
    log_info "=========================================="
}

rollback