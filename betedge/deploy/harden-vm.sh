#!/usr/bin/env bash
#
# NO EJECUTAR ESTO DE UNA SOLA VEZ. Es una referencia ordenada de los comandos de endurecimiento
# que van a correr por SSH contra la VM de Oracle una vez exista - pensado para revisarlo y
# ejecutarlo BLOQUE POR BLOQUE, verificando el resultado de cada uno antes de seguir con el
# siguiente, no como un solo `bash harden-vm.sh` a ciegas. Cada bloque está numerado y es
# independiente - podés copiar/pegar uno a la vez.
#
# ORDEN QUE IMPORTA DE VERDAD: el Bloque 3 (SSH sin password) es IRREVERSIBLE por SSH mismo si algo
# sale mal - una vez aplicado, si la llave no funciona, la única forma de recuperar acceso es la
# consola serial/VNC de Oracle Cloud (no otra sesión SSH). Por eso el Bloque 3 termina con una
# verificación EXPLÍCITA en una conexión SSH NUEVA (sin cerrar la que ya tenés abierta) antes de
# reiniciar sshd - si esa verificación falla, NO reinicies sshd, revisa qué pasó primero.
#
# set -e a propósito NO está en la cabecera de este archivo entero - cada bloque se corre suelto,
# no como script completo, así que un `set -e` global no protegería nada y daría falsa confianza.

set -uo pipefail

# ============================================================================
# BLOQUE 1 - Actualizar el sistema
# ============================================================================
# Antes de tocar firewall/SSH/fail2ban, dejar el SO al día - un ufw o fail2ban desactualizado
# puede tener bugs ya resueltos. Nada de esto es destructivo, seguro de correr sin revisión previa.

sudo apt-get update -y
sudo apt-get upgrade -y

# ============================================================================
# BLOQUE 2 - UFW: solo 22 (SSH), 80 (HTTP/ACME), 443 (HTTPS) entrantes
# ============================================================================
# default deny incoming + allow explícito de los 3 puertos que la VM realmente necesita expuestos.
# default allow outgoing - la VM necesita salir a internet (OddsPapi, The Odds API, apt, Certbot,
# Docker Hub) sin restricción, no hay lista de destinos que valga la pena mantener a mano acá.
#
# ADVERTENCIA REAL, no cosmética: Docker manipula iptables DIRECTAMENTE cuando publica puertos de
# un contenedor (`ports:` en docker-compose), y por defecto esas reglas se insertan ANTES que las
# de ufw en la cadena de netfilter - un contenedor que publique un puerto que ufw "bloquea" puede
# terminar expuesto igual, sin que ufw se entere ni lo reporte. docker-compose.prod.yml de este
# proyecto ya está diseñado para que esto no importe en la práctica (nginx publica 80/443, que ya
# están permitidos acá abajo; backend y postgres ya NO publican ningún puerto al host, ver los
# comentarios de esos dos servicios) - pero si en el futuro alguien agrega un `ports:` nuevo a
# cualquier servicio de ese compose, ESTA regla de ufw NO lo va a proteger automáticamente. Antes
# de confiar en ufw solo, correr `sudo iptables -L DOCKER -n` después de levantar docker-compose y
# confirmar que no hay ninguna regla ahí para un puerto que no sea 80/443.

sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp comment 'SSH'
sudo ufw allow 80/tcp comment 'HTTP + ACME challenge'
sudo ufw allow 443/tcp comment 'HTTPS'

# Revisar ANTES de habilitar - `ufw enable` puede cortar tu propia sesión SSH si la regla de 22
# quedó mal escrita arriba.
sudo ufw show added

# Recién acá se activa. `--force` evita el prompt interactivo de confirmación de ufw (necesario
# para correr por SSH sin una terminal atendida) - por eso la revisión de arriba (`ufw show added`)
# es el único momento real para atajar un error antes de que tenga efecto. NO uses `-y`: no es una
# flag válida de `ufw` (esa sintaxis es de `apt-get`) - confirmado contra una VM real
# (2026-09-09, Ubuntu 24.04/Oracle Cloud) que `ufw enable -y` no suprime el prompt; `--force` sí.
sudo ufw --force enable
sudo ufw status verbose

# ============================================================================
# BLOQUE 3 - SSH solo por llave, nunca por password
# ============================================================================
# PARAR ACÁ SI TODAVÍA NO CONFIRMASTE que tu llave pública ya está en
# ~/.ssh/authorized_keys de esta VM y que conectás con ella hoy mismo, en la sesión que estás
# usando ahora mismo, ANTES de tocar sshd_config. Si no estás seguro, confirmalo primero:
#   ssh -o PreferredAuthentications=publickey -o PasswordAuthentication=no <user>@<host> echo ok
# Si ese comando no imprime "ok" sin pedir password, NO sigas con este bloque.

sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak-pre-harden

# PasswordAuthentication/PermitRootLogin: grep-y-agrega en vez de un sed ciego. Motivo real,
# confirmado contra una VM real (2026-09-09, imagen cloud-init de Ubuntu/Oracle): esa imagen ya
# trae PasswordAuthentication no vía un drop-in aparte (/etc/ssh/sshd_config.d/*.conf) y
# PermitRootLogin sin ninguna línea explícita en sshd_config (default compilado de OpenSSH) - un
# sed que solo reemplaza líneas YA EXISTENTES no encuentra nada que reemplazar en ninguno de los
# dos casos y no hace nada, dejando la política dependiente de un detalle de la imagen en vez de
# explícita acá. Si la línea ya existe (imagen distinta, instalación desde ISO estándar), esto no
# duplica nada.
sudo grep -q '^PasswordAuthentication' /etc/ssh/sshd_config || \
    echo 'PasswordAuthentication no' | sudo tee -a /etc/ssh/sshd_config
sudo grep -q '^PermitRootLogin' /etc/ssh/sshd_config || \
    echo 'PermitRootLogin prohibit-password' | sudo tee -a /etc/ssh/sshd_config

sudo sed -i 's/^#\?KbdInteractiveAuthentication.*/KbdInteractiveAuthentication no/' /etc/ssh/sshd_config

# Valida la sintaxis ANTES de reiniciar sshd - un sshd_config roto que se recarga te puede dejar
# sin poder conectar de nuevo.
sudo sshd -t

# NO REINICIAR TODAVÍA. Desde una terminal DISTINTA (dejando esta sesión SSH abierta como red de
# seguridad), abrí una conexión SSH NUEVA contra la misma VM y confirmá que entra con la llave. Si
# esa conexión nueva funciona, entonces sí:
sudo systemctl restart ssh

# Verificación final, otra vez desde una terminal nueva (no la que hizo el restart):
#   ssh <user>@<host>   -> debe entrar sin pedir password
#   ssh -o PubkeyAuthentication=no <user>@<host>  -> debe RECHAZAR la conexión (confirma que
#                                                     password quedó realmente deshabilitado)

# ============================================================================
# BLOQUE 4 - fail2ban contra intentos de fuerza bruta
# ============================================================================
# jail de sshd activado por default en la mayoría de instalaciones deb - se confirma explícito acá
# en vez de asumirlo.

sudo apt-get install -y fail2ban

sudo tee /etc/fail2ban/jail.local > /dev/null <<'EOF'
[sshd]
enabled = true
port = 22
maxretry = 5
bantime = 1h
findtime = 10m
# El filtro por defecto del propio paquete (filter.d/sshd.conf, seccion [Init]) trae hardcodeado
# journalmatch = _SYSTEMD_UNIT=sshd.service - pero en Ubuntu la unidad real es ssh.service. Sin
# este override el jail queda "activo" pero ciego: nunca matchea ningun evento real. Confirmado
# contra una VM real (2026-09-09): journalctl _COMM=sshd muestra _SYSTEMD_UNIT=ssh.service, nunca
# sshd.service; probado end-to-end generando un intento SSH real con usuario inexistente - antes
# de este override, fail2ban-client status sshd se quedaba en 0 intentos pase lo que pasara.
journalmatch = _SYSTEMD_UNIT=ssh.service + _COMM=sshd
EOF

sudo systemctl enable --now fail2ban
sudo systemctl status fail2ban --no-pager
sudo fail2ban-client status sshd

# ============================================================================
# Verificación final del bloque completo
# ============================================================================
echo "--- ufw ---"
sudo ufw status verbose
echo "--- sshd: password auth debe decir 'no' ---"
sudo sshd -T | grep -i passwordauthentication
echo "--- fail2ban ---"
sudo fail2ban-client status
