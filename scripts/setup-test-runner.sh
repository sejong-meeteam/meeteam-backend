#!/usr/bin/env bash
# One-time Ubuntu EC2 setup. Obtain a short-lived token from this repository's
# Settings > Actions > Runners > New self-hosted runner. Never paste it into chat.
set -euo pipefail
umask 077
[[ "$(id -un)" == ubuntu ]] || { echo 'Run as the ubuntu user, not root.'; exit 1; }
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || { echo 'This installer is for Ubuntu/Linux x64 only.'; exit 1; }
docker info >/dev/null
DEST=/home/ubuntu/actions-runner
[[ ! -e "$DEST/.runner" ]] || { echo 'Runner already configured. Stop rather than overwrite it.'; exit 1; }
mkdir -p "$DEST"
chmod 700 "$DEST"
cd "$DEST"
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"; unset registration_token' EXIT

curl -fsSL --retry 3 https://api.github.com/repos/actions/runner/releases/latest > "$scratch/release.json"
python3 - "$scratch/release.json" > "$scratch/asset" <<'PY'
import json, sys
release = json.load(open(sys.argv[1]))
assets = [item for item in release.get('assets', []) if item['name'].startswith('actions-runner-linux-x64-') and item['name'].endswith('.tar.gz')]
if len(assets) != 1:
    raise SystemExit('Official Linux x64 runner asset not found')
asset = assets[0]
url, digest = asset['browser_download_url'], asset.get('digest') or ''
if not url.startswith('https://github.com/actions/runner/releases/download/') or not digest.startswith('sha256:'):
    raise SystemExit('Official URL/checksum unavailable. Use the GitHub New Runner screen instructions instead.')
print(url)
print(digest.removeprefix('sha256:'))
PY
mapfile -t asset < "$scratch/asset"
curl -fsSL --retry 3 "${asset[0]}" -o "$scratch/runner.tar.gz"
printf '%s  %s\n' "${asset[1]}" "$scratch/runner.tar.gz" | sha256sum -c -
tar -xzf "$scratch/runner.tar.gz" -C "$DEST"
sudo ./bin/installdependencies.sh

echo 'Repository: sejong-meeteam/meeteam-backend'
echo 'Paste ONLY the short-lived registration token from New self-hosted runner; it will not be displayed.'
read -r -s -p 'Runner registration token (hidden): ' registration_token
printf '\n'
[[ -n "$registration_token" ]] || { echo 'No token provided'; exit 1; }
./config.sh --unattended \
  --url https://github.com/sejong-meeteam/meeteam-backend \
  --token "$registration_token" \
  --name "meeteam-test-$(hostname)" \
  --labels meeteam-test --work _work
unset registration_token
sudo ./svc.sh install ubuntu
sudo ./svc.sh start
sudo ./svc.sh status
echo 'RUNNER READY. It will start automatically when this EC2 instance starts.'
echo 'Keep this runner repository-scoped. Never enable untrusted pull_request jobs on it.'
