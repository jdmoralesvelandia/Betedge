#!/usr/bin/env bash
#
# NO EJECUTAR TODAVÍA - la VM de Oracle no existe. Este script queda listo para correr una vez:
#   1. La VM existe y el DNS de DuckDNS ya apunta a su IP pública (propagado, no solo configurado -
#      verifica con `dig ${DOMAIN_NAME}` antes de correr esto, Let's Encrypt lo va a verificar de
#      la misma forma y falla si el DNS todavía no resuelve).
#   2. `docker compose -f docker-compose.prod.yml up -d` ya está corriendo - este script necesita
#      que Nginx (Fase 1, nginx.conf.template, ver su propio comentario de cabecera) ya esté
#      sirviendo /.well-known/acme-challenge/ en el puerto 80 ANTES de pedir el certificado - el
#      método webroot de Certbot no levanta su propio servidor, escribe el archivo de challenge y
#      asume que algo más ya lo está sirviendo.
#
# Uso: DOMAIN_NAME=betedge-jdm.duckdns.org LETSENCRYPT_EMAIL=tu@correo.com ./setup-tls.sh
#
# Qué hace, en orden:
#   1. Instala Certbot (paquete nativo del SO, no Docker - corre en el host, no dentro de ningún
#      contenedor, para poder escribir directo en el webroot compartido con Nginx sin depender de
#      que la imagen de Nginx traiga Certbot).
#   2. Pide el certificado real vía el método webroot (usa el Nginx de Fase 1 que ya debe estar
#      corriendo - ver arriba).
#   3. Activa la Fase 2 (nginx-ssl.conf.template, con el bloque 443) sobre el contenedor de Nginx
#      YA corriendo, sin necesidad de recrearlo ni de bajar docker-compose.
#   4. Confirma que la renovación automática de Certbot (systemd timer, viene con el paquete deb en
#      Ubuntu) está activa, y deja configurado el deploy-hook para que cada renovación automática
#      recargue el Nginx dockerizado (que de otra forma no se enteraría de que el certificado
#      cambió, al vivir en un contenedor aparte).

set -euo pipefail

: "${DOMAIN_NAME:?Set DOMAIN_NAME primero, ej: DOMAIN_NAME=betedge-jdm.duckdns.org}"
: "${LETSENCRYPT_EMAIL:?Set LETSENCRYPT_EMAIL primero - se usa solo para avisos de expiracion del certificado, nada mas}"

NGINX_CONTAINER="${NGINX_CONTAINER:-betedge-nginx}"
WEBROOT="/var/www/certbot"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> [1/4] Instalando Certbot"
sudo apt-get update -y
sudo apt-get install -y certbot

echo "==> [2/4] Pidiendo certificado real para ${DOMAIN_NAME} (método webroot)"
echo "    Requiere que docker-compose.prod.yml ya esté arriba y sirviendo /.well-known/acme-challenge/"
echo "    en el puerto 80 - si esto falla con timeout/connection refused, confirma eso primero,"
echo "    no reintentes a ciegas (Let's Encrypt tiene rate limits por dominio/semana)."
sudo certbot certonly \
    --webroot -w "${WEBROOT}" \
    -d "${DOMAIN_NAME}" \
    --email "${LETSENCRYPT_EMAIL}" \
    --agree-tos \
    --no-eff-email \
    --non-interactive \
    --deploy-hook "docker exec ${NGINX_CONTAINER} nginx -s reload"
# --deploy-hook queda GUARDADO por Certbot en /etc/letsencrypt/renewal/${DOMAIN_NAME}.conf y se
# reusa automáticamente en cada renovación futura (manual o vía el timer de systemd) - no hace
# falta volver a pasarlo a mano en `certbot renew`.

echo "==> [3/4] Activando Fase 2 (HTTPS) sobre el Nginx ya corriendo"
docker exec "${NGINX_CONTAINER}" sh -c \
    "envsubst '\$DOMAIN_NAME' < /etc/nginx/templates-src/nginx-ssl.conf.template > /etc/nginx/conf.d/default.conf"
docker exec "${NGINX_CONTAINER}" nginx -t
docker exec "${NGINX_CONTAINER}" nginx -s reload
echo "    Nginx recargado en Fase 2. Verifica ahora: curl -I https://${DOMAIN_NAME}"

echo "==> [4/4] Confirmando renovación automática"
sudo systemctl enable --now certbot.timer
sudo systemctl status certbot.timer --no-pager || true
echo "    Simulacro de renovación (no renueva de verdad, solo valida que el proceso completo -"
echo "    incluido el deploy-hook - funcionaría cuando toque):"
sudo certbot renew --dry-run

echo "==> Listo. Certificado real en /etc/letsencrypt/live/${DOMAIN_NAME}/"
echo "    Recuerda: si DOMAIN_NAME cambia después de correr esto, hay que pedir un certificado"
echo "    nuevo para el dominio nuevo - un certificado de Let's Encrypt es específico de dominio,"
echo "    no se puede renombrar."
