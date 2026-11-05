#!/usr/bin/env bash
# EC2-only, initial activation. Never source private env files or accept passwords.
# Run on the server: bash scripts/setup-admin-account.sh [--resume]
set +x
set -Eeuo pipefail
umask 077
fail() { printf '%s\n' "$1" >&2; exit 1; }
resume=false
case "${1:-}" in '') [[ $# == 0 ]] || fail 'Usage: setup-admin-account.sh [--resume]' ;; --resume) [[ $# == 1 ]] || fail 'Usage: setup-admin-account.sh [--resume]'; resume=true ;; *) fail 'Usage: setup-admin-account.sh [--resume]' ;; esac
for tool in docker curl python3 flock; do command -v "$tool" >/dev/null || fail 'Required server tool missing'; done
[[ "$(cat /sys/devices/virtual/dmi/id/sys_vendor 2>/dev/null)" == 'Amazon EC2' ]] || fail 'This script must run on the EC2 deployment host'
BASE=${MEETEAM_DEPLOY_DIR:-/home/ubuntu/meeteam}
[[ "$BASE" =~ ^/[A-Za-z0-9_./-]+$ && -d "$BASE" && ! -L "$BASE" ]] || fail 'Invalid deployment directory'
[[ "$(cd "$BASE" && pwd -P)" == "$BASE" ]] || fail 'Deployment directory must be canonical'
# Private configuration must remain readable by the existing deployment runner.
[[ "$(id -u)" != 0 && -z "${SUDO_USER:-}" && -z "${SUDO_UID:-}" ]] || fail 'Run directly as the deployment directory owner, not root or sudo'
python3 - "$BASE" <<'PY' || fail 'Run directly as the deployment directory owner, not root or sudo'
import os, sys
sys.exit(0 if os.stat(sys.argv[1]).st_uid == os.getuid() else 1)
PY
[[ -f "$BASE/compose-test.yml" && -s "$BASE/.env" && -s "$BASE/.env.test-account" ]] || fail 'Existing deployment/private test-account configuration missing'
[[ ! -L "$BASE/.deploy" && ! -L "$BASE/backups" ]] || fail 'Unsafe recovery directory'
mkdir -p "$BASE/.deploy" "$BASE/backups"
chmod 700 "$BASE/.deploy" "$BASE/backups"
[[ ! -L "$BASE/.deploy/deploy.lock" ]] || fail 'Unsafe deployment lock'
exec 9>"$BASE/.deploy/deploy.lock"
flock -n 9 || fail 'Another deployment is running; nothing changed'
cd "$BASE"
active="$BASE/.env.admin-account"
pending="$BASE/.env.admin-account.pending"
[[ ! -e "$active" && ! -L "$active" ]] || fail 'Admin activation already configured; password rotation is not supported'
if [[ "$resume" == true ]]; then
  [[ -f "$pending" && ! -L "$pending" ]] || fail 'No safe pending activation to resume'
else
  [[ ! -e "$pending" && ! -L "$pending" ]] || fail 'Pending activation exists; use --resume after reviewing recovery state'
fi
inspect() { docker inspect --format="$1" meeteam-test; }
[[ "$(inspect '{{.Name}}')" == /meeteam-test ]] || fail 'Unexpected target container'
project=$(inspect '{{index .Config.Labels "com.docker.compose.project"}}')
[[ "$project" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || fail 'Unexpected compose project'
[[ "$(inspect '{{index .Config.Labels "com.docker.compose.service"}}')" == meeteam-test ]] || fail 'Unexpected compose service'
[[ "$(inspect '{{index .Config.Labels "com.docker.compose.project.working_dir"}}')" == "$BASE" ]] || fail 'Unexpected compose working directory'
enabled_format='{{range .Config.Env}}{{if ge (len .) 22}}{{if eq (slice . 0 22) "ADMIN_ACCOUNT_ENABLED="}}{{if ne . "ADMIN_ACCOUNT_ENABLED=false"}}enabled{{end}}{{end}}{{end}}{{end}}'
[[ -z "$(inspect "$enabled_format")" ]] || fail 'Administrator is already enabled; refusing activation'
old_id=$(inspect '{{.Image}}')
[[ "$old_id" =~ ^sha256:[a-f0-9]{64}$ ]] || fail 'Invalid current app image'
config_files=$(inspect '{{index .Config.Labels "com.docker.compose.project.config_files"}}')
IFS=',' read -r -a previous_files <<< "$config_files"
[[ ${#previous_files[@]} -gt 0 ]] || fail 'Missing current compose files'
previous=(docker compose -p "$project")
found_base=false
for file in "${previous_files[@]}"; do
  [[ "$file" == "$BASE/"* && -f "$file" && ! -L "$file" ]] || fail 'Unsafe current compose file'
  python3 - "$BASE" "$file" <<'PY' || fail 'Unsafe current compose file'
import os, sys
base, file = sys.argv[1:]
if os.path.realpath(file) != file or not file.startswith(base + '/'):
    sys.exit(1)
# An earlier active admin override must not silently participate in rollback.
content = open(file).read()
if '.env.admin-account' in content or 'ADMIN_ACCOUNT_' in content:
    sys.exit(1)
PY
  [[ "$file" != "$BASE/compose-test.yml" ]] || found_base=true
  previous+=(-f "$file")
done
[[ "$found_base" == true ]] || fail 'Current app does not use the expected compose-test.yml'
release=$(mktemp -d "$BASE/.deploy/admin-account.XXXXXX")
scratch="$release/setup"
mkdir -m 700 "$scratch"
changed=false
promoted=false
cleanup() { rm -rf "$scratch"; }
trap cleanup EXIT
health() {
  local attempt
  for ((attempt=0; attempt<60; attempt++)); do
    if [[ "$(inspect '{{.Image}}')" == "$old_id" ]] &&
      curl -fsS --connect-timeout 2 --max-time 5 http://127.0.0.1:8080/actuator/health > "$scratch/health.json" 2>/dev/null &&
      python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])).get("status")=="UP" else 1)' "$scratch/health.json" 2>/dev/null; then return 0; fi
    sleep 3
  done
  return 1
}
printf 'services:\n  meeteam-test:\n    image: %s\n' "$old_id" > "$release/prior-image.yml"
rollback=("${previous[@]}" -f "$release/prior-image.yml")
recover() {
  local status=$?
  [[ "$status" -ne 0 ]] || status=1
  trap - ERR INT TERM
  set +e
  if [[ "$promoted" == true ]]; then
    mv "$active" "$pending" || printf '%s\n' 'RECOVERY ERROR: cannot retain pending configuration' >&2
  fi
  if [[ "$changed" == true ]]; then
    if "${rollback[@]}" up -d --no-deps --pull never meeteam-test > "$release/rollback.log" 2>&1 && health && [[ -z "$(inspect "$enabled_format")" ]]; then
      printf '%s\n' 'Previous app restored and healthy. Pending activation retained; use --resume, not a new password.' >&2
    else
      printf '%s\n' 'ROLLBACK UNHEALTHY: inspect private recovery files before any further action.' >&2
    fi
  else
    printf '%s\n' 'Activation stopped before app replacement. Any pending hash is retained.' >&2
  fi
  printf '%s\n' 'No database row was deleted or password overwritten. Private recovery files are retained.' >&2
  exit "$status"
}
trap recover ERR INT TERM
# Query via stdin: no passwords or hashes on argv, and root credential expansion
# takes place only inside MySQL's existing container. Never change a DB row here.
db_query() {
  docker exec -i meeteam-test-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE"' < "$scratch/query.sql" > "$scratch/result" 2> "$release/db-query.log"
}
# Count all reserved collisions, including deleted identities. A clean initial
# activation must have neither reserved email nor reserved student ID.
printf "%s\n" "SELECT COUNT(*) FROM member WHERE email='meeteam-admin@example.invalid' OR student_id='99990002';" > "$scratch/query.sql"
db_query
collisions=$(<"$scratch/result")
[[ "$collisions" =~ ^[0-9]+$ ]] || fail 'Cannot validate reserved identity'
if [[ "$resume" != true && "$collisions" != 0 ]]; then fail 'Reserved identity conflict; no member may be promoted or overwritten'; fi
if [[ "$resume" == true ]]; then
  python3 - "$pending" "$scratch/admin.hash" <<'PY'
import os, stat, sys, re
p, out = sys.argv[1:]
s = os.lstat(p)
assert stat.S_ISREG(s.st_mode) and s.st_uid == os.getuid() and stat.S_IMODE(s.st_mode) == 0o600 and s.st_nlink == 1
m = re.fullmatch(rb"ADMIN_ACCOUNT_ENABLED=true\nADMIN_ACCOUNT_USERNAME=99990002\nADMIN_ACCOUNT_PASSWORD_HASH='(\$2[aby]\$12\$[./A-Za-z0-9]{53})'\n", open(p,'rb').read())
assert m, 'Invalid pending configuration'
with open(out,'xb') as f: f.write(m[1] + b'\n')
PY
else
  # Ephemeral, disconnected process, not exec in the live app's memory cgroup.
  # No env_file, passwords, cloud credentials or DB connectivity are provided.
  docker run --rm -it --network none --read-only --cap-drop ALL \
    --security-opt no-new-privileges --memory 256m --pids-limit 128 \
    --tmpfs /tmp:rw,noexec,nosuid,size=16m --user "$(id -u):$(id -g)" \
    --mount "type=bind,src=$scratch,dst=/setup" --entrypoint java "$old_id" \
    -Xms16m -Xmx96m -XX:MaxMetaspaceSize=96m -XX:+UseSerialGC \
    -Dloader.main=com.wardk.meeteam_backend.global.auth.service.AdminPasswordSetupTool \
    -cp /app.jar org.springframework.boot.loader.launch.PropertiesLauncher /setup/admin.hash
fi
python3 - "$scratch/admin.hash" "$pending" "$resume" "$scratch/query.sql" <<'PY'
import os, re, stat, sys
p, pending, resume, query = sys.argv[1:]
s = os.lstat(p)
assert stat.S_ISREG(s.st_mode) and s.st_uid == os.getuid() and stat.S_IMODE(s.st_mode) == 0o600 and s.st_nlink == 1
raw = open(p,'rb').read()
assert re.fullmatch(rb'\$2[aby]\$12\$[./A-Za-z0-9]{53}\n', raw), 'Invalid password-tool output'
h = raw.decode().rstrip('\n')
if resume != 'true':
    # Compose single quotes are essential: bcrypt dollar signs must stay literal.
    with open(pending,'x') as f:
        f.write("ADMIN_ACCOUNT_ENABLED=true\nADMIN_ACCOUNT_USERNAME=99990002\nADMIN_ACCOUNT_PASSWORD_HASH='" + h + "'\n")
# Only a matching, unsuspended, undeleted reserved local ADMIN is resumable.
with open(query,'w') as f:
    f.write("SELECT COUNT(*), COALESCE(SUM(email='meeteam-admin@example.invalid' AND student_id IS NULL AND provider='local-admin' AND role='ADMIN' AND deleted_at IS NULL AND admin_withdrawn=0 AND suspended=0 AND BINARY password=BINARY '" + h + "'),0) FROM member WHERE email='meeteam-admin@example.invalid' OR student_id='99990002';\n")
PY
db_query
result=$(<"$scratch/result")
if [[ "$resume" == true ]]; then
  [[ "$result" == $'0\t0' || "$result" == $'1\t1' ]] || fail 'Pending hash does not match a valid reserved admin; no account was modified'
else
  [[ "$result" == $'0\t0' ]] || fail 'Reserved identity changed; activation refused'
fi
# Backup before any application bootstrap could commit. Never print DB output.
backup=$(mktemp "$BASE/backups/admin-account.XXXXXX")
docker exec meeteam-test-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces "$MYSQL_DATABASE"' > "$backup" 2> "$release/backup.log"
[[ -s "$backup" ]] || fail 'Private database backup failed'
printf 'services:\n  meeteam-test:\n    image: %s\n    env_file:\n      - %s/.env.admin-account\n' "$old_id" "$BASE" > "$release/activate.yml"
compose=("${previous[@]}" -f "$release/activate.yml")
mv "$pending" "$active"
promoted=true
"${compose[@]}" config --quiet > "$release/validate.log" 2>&1
changed=true
printf '%s\n' '앱을 재시작하고 관리자 설정을 확인하고 있습니다'
"${compose[@]}" up -d --no-deps --pull never meeteam-test > "$release/activate.log" 2>&1
health
[[ "$(inspect "$enabled_format")" == enabled ]] || false
# Recheck the exact stored hash/identity without exposing it. Startup may have
# committed even if health failed, hence rollback always retains the pending hash.
db_query
[[ "$(<"$scratch/result")" == $'1\t1' ]] || false
changed=false
promoted=false
trap - ERR INT TERM
printf '%s\n' 'Admin 99990002 activated and healthy. Existing test account and deployment files unchanged.'
