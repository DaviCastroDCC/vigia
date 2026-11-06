#!/usr/bin/env bash
#
# Deploy contínuo por "pull": roda no próprio servidor (systemd timer, a cada 2 minutos).
#
#   1. Busca a main no GitHub. Se não mudou, termina.
#   2. Só segue se o CI (GitHub Actions) passou naquele commit.
#   3. Sobe a versão nova e espera o /actuator/health responder.
#   4. Se a versão nova não ficar saudável, volta para a anterior (rollback).
#
# Por que "pull" e não um push do GitHub Actions via SSH: o servidor não precisa aceitar
# conexões de fora (a porta 22 continua liberada só para o IP do administrador) e nenhuma
# chave privada do servidor fica guardada no GitHub.
#
set -euo pipefail

APP_DIR="${APP_DIR:-/home/ec2-user/vigia}"
REPO="${REPO:-DaviCastroDCC/vigia}"
CI_CHECK_NAME="${CI_CHECK_NAME:-Testes e build}"
HEALTH_TIMEOUT_S="${HEALTH_TIMEOUT_S:-180}"
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)

log() { echo "[deploy] $*"; }

cd "$APP_DIR"
git fetch -q origin main
current=$(git rev-parse HEAD)
target=$(git rev-parse origin/main)
[[ "$current" == "$target" ]] && exit 0

short=${target:0:7}

# Resultado do CI para o commit (API pública do GitHub, sem token).
ci=$(curl -fsS -H "Accept: application/vnd.github+json" \
        "https://api.github.com/repos/$REPO/commits/$target/check-runs" |
     python3 -c '
import json, sys
name = sys.argv[1]
runs = [r for r in json.load(sys.stdin).get("check_runs", []) if r["name"] == name]
if not runs:
    print("pending")
elif runs[0]["status"] != "completed":
    print("pending")
else:
    print(runs[0]["conclusion"])
' "$CI_CHECK_NAME")

case "$ci" in
  success) ;;
  pending) log "$short: aguardando o CI terminar"; exit 0 ;;
  *)
    # Registra uma vez só por commit, para não encher o log a cada 2 minutos.
    if [[ "$(cat .deploy-skipped 2>/dev/null)" != "$target" ]]; then
      log "$short: CI terminou com '$ci' — deploy ignorado"
      echo "$target" > .deploy-skipped
    fi
    exit 0 ;;
esac

healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT_S))
  while (( SECONDS < deadline )); do
    if "${COMPOSE[@]}" exec -T api wget -qO- localhost:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then
      return 0
    fi
    sleep 5
  done
  return 1
}

log "$short: CI ok, publicando (versão anterior: ${current:0:7})"
git reset -q --hard "$target"
if "${COMPOSE[@]}" up -d --build --remove-orphans >/dev/null 2>&1 && healthy; then
  log "$short: no ar e saudável"
  docker image prune -f >/dev/null 2>&1 || true
  exit 0
fi

log "$short: não ficou saudável em ${HEALTH_TIMEOUT_S}s — voltando para ${current:0:7}"
git reset -q --hard "$current"
"${COMPOSE[@]}" up -d --build --remove-orphans >/dev/null 2>&1
if healthy; then
  log "rollback concluído: ${current:0:7} no ar"
else
  log "ATENÇÃO: rollback também não ficou saudável"
fi
echo "$target" > .deploy-skipped
exit 1
