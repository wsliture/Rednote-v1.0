#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
ENV_FILE=${REDNOTE_DEPLOY_ENV:-$ROOT/deploy/docker/deploy.env}
[[ "$ENV_FILE" = /* ]] || ENV_FILE="$PWD/$ENV_FILE"
cd "$ROOT"
compose() { docker compose --env-file "$ENV_FILE" -f "$ROOT/compose.yaml" "$@"; }
if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
  case ${1:-help} in
    init)
      [[ ! -e "$ENV_FILE" ]] || { echo "配置已存在，不覆盖: $ENV_FILE" >&2; exit 1; }
      command -v openssl >/dev/null
      umask 077
      mkdir -p "$(dirname "$ENV_FILE")"
      # noclobber also protects against a concurrent initialization.
      (set -o noclobber; sed \
        -e "s/^REDNOTE_DB_PASSWORD=$/REDNOTE_DB_PASSWORD=$(openssl rand -hex 32)/" \
        -e "s/^MYSQL_ROOT_PASSWORD=$/MYSQL_ROOT_PASSWORD=$(openssl rand -hex 32)/" \
        deploy/docker/deploy.env.example > "$ENV_FILE")
      echo "已生成: $ENV_FILE"
      ;;
    up) compose up -d --build --wait --wait-timeout 300 ;;
    update)
      # Existing deployment only: build first so failures leave the running app alone.
      db_id=$(compose ps -q db)
      [[ -n "$db_id" && $(docker inspect --format '{{.State.Health.Status}}' "$db_id") == healthy ]] || {
        echo '数据库未健康运行，请先使用 up 初始化或修复数据库' >&2; exit 1;
      }
      old_image=$(compose images -q app)
      if [[ -n "$old_image" ]]; then
        rollback_tag="rednote-app:rollback-$(date -u +%Y%m%dT%H%M%SZ)-$$"
        docker image tag "$old_image" "$rollback_tag"
        echo "旧应用镜像已保留: ${rollback_tag}（仅兼容数据库结构时可直接回退）"
      fi
      compose build app
      compose up -d --no-deps --no-build --wait --wait-timeout 300 app
      ;;
    stop) compose stop ;;
    down) compose down ;;
    status) compose ps ;;
    logs) compose logs --tail 100 -f ;;
    compose) shift; compose "$@" ;;
    *) echo "用法: $0 {init|up|update|stop|down|status|logs|compose <参数...>}" ;;
  esac
fi
