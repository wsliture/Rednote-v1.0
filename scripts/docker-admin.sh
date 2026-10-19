#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/docker.sh"
read -r -p '管理员用户名（4–20 位字母、数字或下划线）: ' REDNOTE_ADMIN_USERNAME
read -r -s -p '管理员密码: ' REDNOTE_ADMIN_PASSWORD
printf '\n'
read -r -s -p '再次输入密码: ' confirm
printf '\n'
[[ -n "$REDNOTE_ADMIN_PASSWORD" && "$REDNOTE_ADMIN_PASSWORD" == "$confirm" ]] || { echo '密码为空或不一致' >&2; exit 1; }
unset confirm
export REDNOTE_ADMIN_USERNAME REDNOTE_ADMIN_PASSWORD
compose up -d --wait db
compose stop app
admin_container="rednote-admin-$(date +%s)-$$"
cleanup() {
  rc=$?
  trap - EXIT
  docker rm -f "$admin_container" >/dev/null 2>&1 || true
  unset REDNOTE_ADMIN_USERNAME REDNOTE_ADMIN_PASSWORD
  if ! compose up -d --wait --wait-timeout 300 app; then
    echo '应用恢复失败，请检查日志' >&2
    rc=1
  fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
compose run -d --no-deps --name "$admin_container" \
  -e REDNOTE_ADMIN_INITIALIZE=true -e REDNOTE_ADMIN_USERNAME -e REDNOTE_ADMIN_PASSWORD app >/dev/null
initialization_completed() { grep 'Rednote administrator initialization completed' >/dev/null; }
for ((i=0; i<150; i++)); do
  state=$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$admin_container")
  if [[ "$state" == 'running healthy' ]] && docker logs "$admin_container" 2>&1 | initialization_completed; then
    echo '管理员初始化完成；已有管理员不会重置密码。'
    exit 0
  fi
  if [[ "$state" == exited* || "$state" == *unhealthy ]]; then
    docker logs --tail 80 "$admin_container" >&2
    exit 1
  fi
  sleep 2
done
echo '管理员初始化超时' >&2
exit 1
