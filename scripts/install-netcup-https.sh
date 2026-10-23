#!/usr/bin/env bash
set -euo pipefail
HOST="${SEA_BATTLE_DEPLOY_HOST:-bernd@159.195.63.236}"
ssh "$HOST" bash -s <<'REMOTE'
set -euo pipefail
config=/etc/apache2/sites-available/seabattle.conf
backup="$config.before-https-$(date +%Y%m%d-%H%M%S)"
sudo -n cp -p "$config" "$backup"
rollback() {
    sudo -n cp -p "$backup" "$config"
    sudo -n apache2ctl configtest
    sudo -n systemctl reload apache2
}
trap rollback ERR
sudo -n a2enmod ssl headers rewrite proxy proxy_http >/dev/null
sudo -n install -d -m 0755 /var/www/letsencrypt/.well-known/acme-challenge
# Keep the game available while the certificate is issued.
sudo -n tee "$config" >/dev/null <<'APACHE'
<VirtualHost *:80>
    ServerName kuestenkrieg.de
    ServerAlias www.kuestenkrieg.de 159.195.63.236
    Alias /.well-known/acme-challenge/ /var/www/letsencrypt/.well-known/acme-challenge/
    <Directory /var/www/letsencrypt/.well-known/acme-challenge/>
        Require all granted
    </Directory>
    ProxyRequests Off
    ProxyPreserveHost On
    ProxyTimeout 300
    ProxyPass /.well-known/acme-challenge/ !
    ProxyPass / http://127.0.0.1:9090/ connectiontimeout=5 timeout=300
    ProxyPassReverse / http://127.0.0.1:9090/
    ErrorLog ${APACHE_LOG_DIR}/seabattle-error.log
    CustomLog ${APACHE_LOG_DIR}/seabattle-access.log combined
</VirtualHost>
APACHE
sudo -n apache2ctl configtest
sudo -n systemctl reload apache2
sudo -n certbot certonly --webroot -w /var/www/letsencrypt \
    --cert-name kuestenkrieg.de -d kuestenkrieg.de -d www.kuestenkrieg.de \
    --non-interactive --agree-tos --register-unsafely-without-email --keep-until-expiring
sudo -n tee "$config" >/dev/null <<'APACHE'
<VirtualHost *:80>
    ServerName kuestenkrieg.de
    ServerAlias www.kuestenkrieg.de 159.195.63.236
    Alias /.well-known/acme-challenge/ /var/www/letsencrypt/.well-known/acme-challenge/
    <Directory /var/www/letsencrypt/.well-known/acme-challenge/>
        Require all granted
    </Directory>
    RewriteEngine On
    RewriteCond %{REQUEST_URI} !^/\.well-known/acme-challenge/
    RewriteRule ^ https://kuestenkrieg.de%{REQUEST_URI} [R=301,L]
    ErrorLog ${APACHE_LOG_DIR}/seabattle-error.log
    CustomLog ${APACHE_LOG_DIR}/seabattle-access.log combined
</VirtualHost>
<VirtualHost *:443>
    ServerName kuestenkrieg.de
    ServerAlias www.kuestenkrieg.de
    SSLEngine on
    SSLProtocol all -SSLv3 -TLSv1 -TLSv1.1
    SSLCertificateFile /etc/letsencrypt/live/kuestenkrieg.de/fullchain.pem
    SSLCertificateKeyFile /etc/letsencrypt/live/kuestenkrieg.de/privkey.pem
    ProxyRequests Off
    ProxyPreserveHost On
    ProxyTimeout 300
    RequestHeader set X-Forwarded-Proto "https"
    RequestHeader set X-Forwarded-Port "443"
    ProxyPass / http://127.0.0.1:9090/ connectiontimeout=5 timeout=300
    ProxyPassReverse / http://127.0.0.1:9090/
    ErrorLog ${APACHE_LOG_DIR}/seabattle-ssl-error.log
    CustomLog ${APACHE_LOG_DIR}/seabattle-ssl-access.log combined
</VirtualHost>
APACHE
sudo -n apache2ctl configtest
sudo -n systemctl reload apache2
sudo -n tee /etc/letsencrypt/renewal-hooks/deploy/reload-apache >/dev/null <<'HOOK'
#!/bin/sh
set -e
/usr/sbin/apache2ctl configtest
/bin/systemctl reload apache2
HOOK
sudo -n chmod 0755 /etc/letsencrypt/renewal-hooks/deploy/reload-apache
sudo -n systemctl enable --now certbot.timer
curl -fsS --resolve kuestenkrieg.de:443:127.0.0.1 https://kuestenkrieg.de/start.html >/dev/null
curl -fsS --resolve www.kuestenkrieg.de:443:127.0.0.1 https://www.kuestenkrieg.de/start.html >/dev/null
trap - ERR
printf 'HTTPS verified; Apache backup: %s\n' "$backup"
REMOTE
