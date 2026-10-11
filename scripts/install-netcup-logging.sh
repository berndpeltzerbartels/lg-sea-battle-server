#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST=bernd@159.195.63.236
tmp="$(ssh "$HOST" 'mktemp -d /tmp/seabattle-logging.XXXXXX')"
trap 'ssh "$HOST" "rm -rf -- $tmp"' EXIT
scp "$ROOT/seabattle-journald.conf" "$ROOT/seabattle-logging.conf" "$ROOT/seabattle-log-cleanup.service" "$ROOT/seabattle-log-cleanup.timer" "$HOST:$tmp/"
ssh "$HOST" bash -s -- "$tmp" <<'REMOTE'
set -euo pipefail
tmp="$1"
sudo -n install -D -m 0644 "$tmp/seabattle-journald.conf" /etc/systemd/journald@seabattle.conf
sudo -n install -D -m 0644 "$tmp/seabattle-logging.conf" /etc/systemd/system/seabattle.service.d/logging.conf
sudo -n install -m 0644 "$tmp/seabattle-log-cleanup.service" "$tmp/seabattle-log-cleanup.timer" /etc/systemd/system/
sudo -n systemctl daemon-reload
sudo -n systemctl enable --now seabattle-log-cleanup.timer
sudo -n systemctl restart systemd-journald@seabattle.service
sudo -n systemctl start seabattle-log-cleanup.service
# The game adopts the namespace on its next deployment/restart.
REMOTE
