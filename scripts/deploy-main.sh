#!/usr/bin/env bash
# Called only by the trusted main-branch GitHub Actions job on the test EC2 runner.
set -Eeuo pipefail
umask 077

PAYLOAD=$(cd "${1:?payload directory required}" && pwd -P)
SHA=${2:?full commit SHA required}
RUN=${3:-manual-$(date +%s)}
BASE=${MEETEAM_DEPLOY_DIR:-/home/ubuntu/meeteam}
[[ "$SHA" =~ ^[a-f0-9]{40}$ && "$RUN" =~ ^[A-Za-z0-9._-]+$ ]] || { echo 'Invalid deployment identifier'; exit 1; }
[[ -f "$PAYLOAD/REVISION" && "$(tr -d '\r\n' < "$PAYLOAD/REVISION")" == "$SHA" ]] || { echo 'Artifact revision mismatch'; exit 1; }
[[ -f "$BASE/compose-test.yml" && -s "$BASE/.env" && -s "$BASE/.env.test-account" ]] || { echo 'Existing server/private test-account configuration missing'; exit 1; }
grep -q '^TEST_ACCOUNT_ENABLED=true$' "$BASE/.env.test-account" || { echo 'Test login must remain enabled for this test-server deployment'; exit 1; }
[[ -f "$PAYLOAD/SHA256SUMS" ]] || { echo 'Artifact checksum manifest missing'; exit 1; }
(cd "$PAYLOAD" && sha256sum -c SHA256SUMS)
for required in Dockerfile custom.java.security; do
  [[ -s "$PAYLOAD/$required" ]] || { echo "Missing artifact file: $required"; exit 1; }
done
command -v docker >/dev/null
command -v curl >/dev/null
command -v python3 >/dev/null
command -v flock >/dev/null

mkdir -p "$BASE/.deploy" "$BASE/backups"
chmod 700 "$BASE/.deploy" "$BASE/backups"
exec 9>"$BASE/.deploy/deploy.lock"
flock -n 9 || { echo 'Another deployment is running; no containers changed'; exit 1; }
cd "$BASE"
project=$(docker inspect --format='{{index .Config.Labels "com.docker.compose.project"}}' meeteam-test)
service=$(docker inspect --format='{{index .Config.Labels "com.docker.compose.service"}}' meeteam-test)
[[ -n "$project" && "$service" == meeteam-test ]] || { echo 'Unexpected container/project; stopped'; exit 1; }
config_files=$(docker inspect --format='{{index .Config.Labels "com.docker.compose.project.config_files"}}' meeteam-test)
IFS=',' read -r -a previous_files <<< "$config_files"
previous_compose=(docker compose -p "$project")
for file in "${previous_files[@]}"; do
  [[ "$file" == "$BASE/"* && -f "$file" ]] || { echo 'Cannot preserve the previous compose configuration; stopped'; exit 1; }
  previous_compose+=(-f "$file")
done
[[ ${#previous_files[@]} -gt 0 ]] || { echo 'Previous deployment configuration unavailable'; exit 1; }

stamp=$(date +%Y%m%d-%H%M%S)
release="$BASE/.deploy/$SHA-$RUN-$stamp"
mkdir "$release"
rollback_file="$release/rollback-image.yml"
old_id=$(docker inspect --format='{{.Image}}' meeteam-test)
old_tag="meeteam-server:rollback-$RUN-$stamp"
new_tag="meeteam-server:main-$SHA-$RUN"
docker tag "$old_id" "$old_tag"
printf 'services:\n  meeteam-test:\n    image: %s\n' "$old_tag" > "$rollback_file"
rollback=("${previous_compose[@]}" -f "$rollback_file")
changed=false
scratch=$(mktemp -d "$release/check.XXXXXX")
cleanup() { rm -rf "$scratch"; }
trap cleanup EXIT

fail_deployment() {
  local status=$?
  [[ "$status" -ne 0 ]] || status=1
  trap - ERR INT TERM
  set +e
  if [[ "$changed" == true ]]; then
    echo 'Deployment verification failed. Restoring the previous app only.'
    "${rollback[@]}" up -d --no-deps --pull never meeteam-test
    restored=$?
    if [[ "$restored" -eq 0 ]]; then
      restored_health=false
      for retry in $(seq 1 24); do
        if [[ "$(docker inspect --format='{{.Image}}' meeteam-test)" == "$old_id" ]] \
          && curl -fsS --connect-timeout 2 --max-time 5 http://127.0.0.1:8080/actuator/health > "$scratch/rollback-health.json" 2>/dev/null \
          && python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])).get("status")=="UP" else 1)' "$scratch/rollback-health.json"; then
          restored_health=true
          break
        fi
        sleep "${MEETEAM_HEALTH_INTERVAL:-3}"
      done
      if [[ "$restored_health" == true ]]; then
        echo 'Previous app restored and healthy. DB/Redis/private configuration/Nginx unchanged.'
      else
        echo 'ROLLBACK UNHEALTHY: administrator intervention required.'
      fi
    else
      echo 'ROLLBACK FAILED: administrator intervention required; private backups are retained.'
    fi
  else
    echo 'Deployment failed before app replacement. Existing app was left running.'
  fi
  exit "$status"
}
trap fail_deployment ERR INT TERM

# Build a small, precompiled JAR artifact. No AWS/ECR/SSH credentials are needed.
docker build --label "org.opencontainers.image.revision=$SHA" \
  --label 'com.meeteam.managed=main-deploy' -t "$new_tag" "$PAYLOAD"
new_id=$(docker image inspect --format='{{.Id}}' "$new_tag")

# Database backup must succeed BEFORE replacing the app. The dump is private and never uploaded.
backup="$BASE/backups/main-$RUN-$stamp.sql"
docker exec meeteam-test-mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces "$MYSQL_DATABASE"' > "$backup"
test -s "$backup"

override="$release/app.yml"
{
  printf 'services:\n  meeteam-test:\n    image: %s\n' "$new_tag"
  if [[ -s "$BASE/.env.test-account" ]]; then
    printf '    env_file:\n      - %s/.env.test-account\n' "$BASE"
  fi
  printf '    environment:\n'
  printf '      SPRING_PROFILES_ACTIVE: test\n'
  printf '      SPRING_JPA_HIBERNATE_DDL_AUTO: validate\n'
  printf '      SPRING_SQL_INIT_MODE: never\n'
  printf '      SPRING_FLYWAY_ENABLED: "false"\n'
  printf '      COOKIE_DOMAIN: meeteam.fignal.me\n'
  printf '      COOKIE_SECURE: "true"\n'
  printf '      COOKIE_SAME_SITE: None\n'
  if [[ ! -s "$BASE/.env.test-account" ]]; then
    printf '      TEST_ACCOUNT_ENABLED: "false"\n'
  fi
} > "$override"
compose=(docker compose -p "$project" -f "$BASE/compose-test.yml" -f "$override")
"${compose[@]}" config --quiet
changed=true
"${compose[@]}" up -d --no-deps --pull never meeteam-test

ready=false
for attempt in $(seq 1 "${MEETEAM_HEALTH_RETRIES:-60}"); do
  if [[ "$(docker inspect --format='{{.Image}}' meeteam-test)" == "$new_id" ]] \
    && curl -fsS --connect-timeout 2 --max-time 5 http://127.0.0.1:8080/actuator/health > "$scratch/health.json" 2>/dev/null \
    && python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])).get("status")=="UP" else 1)' "$scratch/health.json"; then
    ready=true
    break
  fi
  sleep "${MEETEAM_HEALTH_INTERVAL:-3}"
done
if [[ "$ready" != true ]]; then
  echo 'New app health check failed.'
  fail_deployment
fi

# Ensure a future main deployment does not silently remove the existing web-compatible test login.
if [[ -s "$BASE/.env.test-account" ]] && grep -q '^TEST_ACCOUNT_ENABLED=true$' "$BASE/.env.test-account"; then
  python3 - "$BASE/.env.test-account" "$scratch/request.json" <<'PY'
import json, sys
settings = dict(line.rstrip('\n').split('=', 1) for line in open(sys.argv[1]) if '=' in line)
with open(sys.argv[2], 'w') as request:
    json.dump({'studentId': '99990001', 'password': settings['TEST_ACCOUNT_PASSWORD']}, request)
PY
  login_status=$(curl -sS --connect-timeout 10 --max-time 20 \
    --resolve meeteam.fignal.me:443:13.125.120.72 \
    -c "$scratch/cookies" -o "$scratch/login.json" -w '%{http_code}' \
    -H 'Content-Type: application/json' --data-binary "@$scratch/request.json" \
    https://meeteam.fignal.me/api/v1/auth/login/sejong)
  if [[ "$login_status" != 200 ]]; then
    echo 'Existing numeric test login verification failed.'
    fail_deployment
  fi
  me_status=$(curl -sS --connect-timeout 10 --max-time 20 \
    --resolve meeteam.fignal.me:443:13.125.120.72 \
    -b "$scratch/cookies" -o "$scratch/me.json" -w '%{http_code}' \
    https://meeteam.fignal.me/api/v1/members/me)
  if [[ "$me_status" != 200 ]]; then
    echo 'Authenticated profile verification failed.'
    fail_deployment
  fi
  python3 - "$scratch/me.json" <<'PY'
import json, sys
profile = json.load(open(sys.argv[1])).get('result') or {}
sys.exit(0 if profile.get('email') == 'meeteam-test@example.invalid' else 1)
PY
  echo 'Existing test login and authenticated profile verified.'
fi
printf '%s\n' "$SHA" > "$BASE/.deploy/current-revision"
changed=false
trap - ERR INT TERM
echo "DEPLOY SUCCESS: $SHA"
echo 'App replaced; DB/Redis/Nginx/HTTPS/private configuration preserved.'
# Deliberately retain previous image tags and private DB backups for recovery. Monitor disk usage.
