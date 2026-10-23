#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST=peltzer-bartels.de
curl -fsS https://kuestenkrieg.de/start.html >/dev/null
tmp="$(ssh "$HOST" 'mktemp -d /tmp/kuestenkrieg-greeting.XXXXXX')"
trap 'ssh "$HOST" "rm -rf -- $tmp"' EXIT
scp "$ROOT/legacy-greeting.html" "$ROOT/peltzer-bartels-greeting.conf" "$HOST:$tmp/"
ssh "$HOST" bash -s -- "$tmp" <<'REMOTE'
set -euo pipefail
tmp="$1"
config=/etc/apache2/sites-available/peltzer-bartels.conf
backup="$config.before-migration-$(date +%Y%m%d-%H%M%S)"
sudo -n cp -p "$config" "$backup"
html_backup="/var/backups/kuestenkrieg-greeting-$(date +%Y%m%d-%H%M%S)"
sudo -n mkdir -p "$html_backup"
sudo -n cp -p /var/www/html/*.html "$html_backup/"
rollback() {
    sudo -n cp -p "$backup" "$config"
    sudo -n cp -p "$html_backup/"*.html /var/www/html/
    sudo -n apache2ctl configtest
    sudo -n systemctl reload apache2
}
trap rollback ERR
sudo -n install -m 0644 "$tmp/legacy-greeting.html" /var/www/html/kuestenkrieg.html
sudo -n python3 - <<'PY'
from pathlib import Path
for path in Path('/var/www/html').glob('*.html'):
    text = path.read_text()
    text = text.replace('http://kuestenkrieg.de', 'https://kuestenkrieg.de')
    text = text.replace('href="/sea-battle/start.html"', 'href="https://kuestenkrieg.de/start.html"')
    text = text.replace('href="/sea-battle/contact.html"', 'href="https://kuestenkrieg.de/contact.html"')
    path.write_text(text)
PY
sudo -n install -m 0644 "$tmp/peltzer-bartels-greeting.conf" "$config"
sudo -n apache2ctl configtest
sudo -n systemctl reload apache2
for path in / /sea-battle/ /sea-battle/start.html /app; do
    curl -fsS --resolve peltzer-bartels.de:443:127.0.0.1 "https://peltzer-bartels.de$path" | grep -q 'https://kuestenkrieg.de/start.html'
done
trap - ERR
sudo -n systemctl disable --now sea-battle
echo "Greeting verified; retired game stopped. Apache backup: $backup"
REMOTE
