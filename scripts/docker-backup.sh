#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/docker.sh"
umask 077
BACKUP_ROOT=${REDNOTE_BACKUP_ROOT:-$ROOT/backups}
mkdir -p "$BACKUP_ROOT"
BACKUP_ROOT=$(cd "$BACKUP_ROOT" && pwd)
backup_id=$(date -u +%Y%m%dT%H%M%SZ)-$$
work="$BACKUP_ROOT/.incomplete-$backup_id"
mkdir "$work"
# Backups are only accepted with the application initially healthy.
compose up -d --wait --wait-timeout 300 db app
resume() {
  rc=$?
  trap - EXIT
  if ! compose up -d --wait --wait-timeout 300 app; then
    echo '备份后应用恢复失败' >&2
    rc=1
  fi
  [[ $rc == 0 ]] || echo "操作失败；诊断/未完成备份请检查 $work（若已完成则检查 $BACKUP_ROOT/$backup_id）" >&2
  exit "$rc"
}
trap resume EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
compose stop app
compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --events --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > "$work/database.sql" 2> "$work/database.stderr"
compose run --rm --no-deps -T --entrypoint tar app -C /var/lib/rednote/uploads -czf - . > "$work/uploads.tar.gz" 2> "$work/uploads.stderr"
compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" -B -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank"' > "$work/flyway.tsv"
compose images > "$work/images.txt"
printf 'backup_id=%s\ncreated_utc=%s\n' "$backup_id" "$(date -u +%FT%TZ)" > "$work/metadata.txt"
(cd "$work"; shasum -a 256 database.sql uploads.tar.gz flyway.tsv images.txt metadata.txt > SHA256SUMS)
mv "$work" "$BACKUP_ROOT/$backup_id"
# Only complete, script-owned backup directories count toward retention.
count=0
while IFS= read -r dir; do
  [[ -f "$BACKUP_ROOT/$dir/SHA256SUMS" ]] || continue
  count=$((count + 1))
  if (( count > 7 )); then rm -rf -- "$BACKUP_ROOT/$dir"; fi
done < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '????????T??????Z-*' -exec basename {} \; | sort -r)
echo "备份完成: $BACKUP_ROOT/$backup_id"
