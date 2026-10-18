#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then export JAVA_HOME="$(/usr/libexec/java_home -v 17)"; fi
export REDNOTE_TEST_DB_PASSWORD="${REDNOTE_TEST_DB_PASSWORD:-${REDNOTE_DB_PASSWORD:-}}"
export REDNOTE_TEST_DB_USER="${REDNOTE_TEST_DB_USER:-root}"
# Remote/custom URLs must point to a pre-provisioned isolated test database.
if [[ -z "${REDNOTE_TEST_DB_URL:-}" ]]; then
  mysql_bin="${MYSQL_BIN:-$(command -v mysql || true)}"
  if [[ -z "$mysql_bin" && -x /usr/local/mysql/bin/mysql ]]; then mysql_bin=/usr/local/mysql/bin/mysql; fi
  [[ -n "$mysql_bin" ]] || { echo 'Set MYSQL_BIN to your local MySQL 8 client' >&2; exit 1; }
  if [[ -z "$REDNOTE_TEST_DB_PASSWORD" ]]; then
    export REDNOTE_TEST_DB_PASSWORD="$(python3 - <<'PY'
from pathlib import Path
p=Path('.env.dev.properties')
for line in p.read_text().splitlines() if p.exists() else []:
    if line.startswith('rednote.dev.db.password='):
        print(line.split('=',1)[1]);break
PY
)"
  fi
  MYSQL_PWD="$REDNOTE_TEST_DB_PASSWORD" "$mysql_bin" --protocol=TCP -h 127.0.0.1 -P 3306 -u "$REDNOTE_TEST_DB_USER" \
    -e 'CREATE DATABASE IF NOT EXISTS rednote_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;'
fi
mvn test "$@"
