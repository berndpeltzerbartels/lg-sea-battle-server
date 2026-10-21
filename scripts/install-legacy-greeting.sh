#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST=peltzer-bartels.de
curl -fsS http://kuestenkrieg.de/start.html >/dev/null
tmp="$(ssh "$HOST" 'mktemp -d /tmp/kuestenkrieg-greeting.XXXXXX')"
trap 'ssh "$HOST" "rm -rf -- $tmp"' EXIT
scp "$ROOT/legacy-greeting.html" "$ROOT/peltzer-bartels-greeting.conf" "$HOST:$tmp/"
ssh "$HOST" bash -s -- "$tmp" <<'REMOTE'
set -euo pipefail
tmp="$1"
config=/etc/apache2/sites-available/peltzer-bartels.conf
backup="$config.before-migration-$(date +%Y%m%d-%H%M%S)"
sudo -n cp -p "$config" "$backup"
rollback() {
    sudo -n cp -p "$backup" "$config"
    sudo -n apache2ctl configtest
    sudo -n systemctl reload apache2
}
trap rollback ERR
sudo -n install -m 0644 "$tmp/legacy-greeting.html" /var/www/html/kuestenkrieg.html
sudo -n install -m 0644 "$tmp/peltzer-bartels-greeting.conf" "$config"
sudo -n apache2ctl configtest
sudo -n systemctl reload apache2
for path in / /sea-battle/ /sea-battle/start.html /app; do
    curl -fsS --resolve peltzer-bartels.de:443:127.0.0.1 "https://peltzer-bartels.de$path" | grep -q 'http://kuestenkrieg.de/start.html'
done
trap - ERR
sudo -n systemctl disable --now sea-battle
echo "Greeting verified; retired game stopped. Apache backup: $backup"
REMOTE
