#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST="bernd@159.195.63.236"
JAR="$ROOT_DIR/build/libs/lg-sea-battle-server-0.1.0-SNAPSHOT.jar"
cd "$ROOT_DIR"
./gradlew xisJar
remote_tmp="$(ssh "$HOST" 'mktemp -d /tmp/seabattle-deploy.XXXXXX')"
trap 'ssh "$HOST" "rm -rf -- $remote_tmp"' EXIT
scp "$JAR" "$HOST:$remote_tmp/"
local_sum="$(shasum -a 256 "$JAR" | awk '{print $1}')"
ssh "$HOST" bash -s -- "$remote_tmp" "$local_sum" <<'REMOTE'
set -euo pipefail
tmp="$1"
expected="$2"
jar=/opt/seabattle/seabattle.jar
unit_before="$(sudo -n systemctl cat seabattle | sha256sum | awk '{print $1}')"
backup="$jar.before-deploy-$(date +%Y%m%d-%H%M%S)"
test "$(sha256sum "$tmp/lg-sea-battle-server-0.1.0-SNAPSHOT.jar" | awk '{print $1}')" = "$expected"
sudo -n cp -p "$jar" "$backup"
rollback() {
  sudo -n cp -p "$backup" "$jar"
  sudo -n systemctl restart seabattle
}
trap rollback ERR
sudo -n install -o root -g seabattle -m 0640 "$tmp/lg-sea-battle-server-0.1.0-SNAPSHOT.jar" "$jar"
sudo -n systemctl restart seabattle
ready=false
for attempt in {1..30}; do
  if curl -fsS http://127.0.0.1:9090/game/state >/dev/null; then
    ready=true
    break
  fi
  sleep 2
done
$ready
sudo -n systemctl is-active seabattle
test "$(sudo -n sha256sum "$jar" | awk '{print $1}')" = "$expected"
test "$(sudo -n systemctl cat seabattle | sha256sum | awk '{print $1}')" = "$unit_before"
trap - ERR
echo "Netcup deployment verified; backup: $backup"
REMOTE
