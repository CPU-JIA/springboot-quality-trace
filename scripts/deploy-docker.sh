#!/usr/bin/env bash
set -euo pipefail

APP_DOMAIN="${APP_DOMAIN:-}"
ACME_EMAIL="${ACME_EMAIL:-}"
ENV_FILE="${ENV_FILE:-.env}"
FORCE="${FORCE:-0}"

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令：$1"
    exit 1
  fi
}

rand_hex() {
  openssl rand -hex "$1"
}

need_cmd openssl
need_cmd docker

if [[ -z "$APP_DOMAIN" || -z "$ACME_EMAIL" ]]; then
  echo "用法：APP_DOMAIN=quality.example.com ACME_EMAIL=admin@example.com $0"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "缺少 Docker Compose v2"
  exit 1
fi

if [[ -f "$ENV_FILE" && "$FORCE" != "1" ]]; then
  echo "复用现有 $ENV_FILE。如需重建密钥，执行：FORCE=1 $0"
else
  cat >"$ENV_FILE" <<EOF
APP_DOMAIN=$APP_DOMAIN
ACME_EMAIL=$ACME_EMAIL

MYSQL_ROOT_PASSWORD=$(rand_hex 32)

JWT_SECRET=$(rand_hex 64)
JWT_EXPIRE_MILLIS=86400000

DRUID_LOGIN_USERNAME=admin
DRUID_LOGIN_PASSWORD=$(rand_hex 32)

LOG_LEVEL_QUALITY_TRACE=info
JAVA_OPTS=-Xms256m -Xmx512m
EOF
  chmod 600 "$ENV_FILE" 2>/dev/null || true
  echo "已生成 $ENV_FILE"
fi

docker compose up -d --build
docker compose ps

echo "部署完成：https://$APP_DOMAIN"
