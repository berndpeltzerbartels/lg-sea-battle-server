#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="$ROOT_DIR/build/libs/lg-sea-battle-server-0.1.0-SNAPSHOT.jar"
REMOTE_HOST="${SEA_BATTLE_DEPLOY_HOST:-peltzer-bartels.de}"
REMOTE_TMP="/home/bernd/sea-battle.jar.new"
REMOTE_JAR="/opt/sea-battle/sea-battle.jar"

cd "$ROOT_DIR"

echo "Building Sea Battle server with xisJar..."
./gradlew xisJar

echo "Uploading $(basename "$JAR_PATH") to $REMOTE_HOST..."
scp "$JAR_PATH" "$REMOTE_HOST:$REMOTE_TMP"

echo "Installing and restarting sea-battle.service..."
ssh "$REMOTE_HOST" 'set -euo pipefail
backup_ts=$(date +%Y%m%d-%H%M%S)
sudo cp /opt/sea-battle/sea-battle.jar /opt/sea-battle/sea-battle.jar.bak-$backup_ts
sudo install -o bernd -g bernd -m 0644 /home/bernd/sea-battle.jar.new /opt/sea-battle/sea-battle.jar
rm -f /home/bernd/sea-battle.jar.new
sudo systemctl restart sea-battle.service
sleep 4
systemctl is-active sea-battle.service
ps -eo pid,args | grep "[j]ava .*sea-battle.jar 9090"
'

local_sum="$(shasum -a 256 "$JAR_PATH" | awk "{print \$1}")"
remote_sum="$(ssh "$REMOTE_HOST" "sha256sum $REMOTE_JAR | awk '{print \$1}'")"

if [[ "$local_sum" != "$remote_sum" ]]; then
  echo "Checksum mismatch after deploy." >&2
  echo "local:  $local_sum" >&2
  echo "remote: $remote_sum" >&2
  exit 1
fi

echo "Deploy complete. Local and server JAR checksums match."
