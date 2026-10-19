#!/usr/bin/env bash
# Copy only project source/configuration; deployment credentials remain on the server.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
target=${1:?Usage: scripts/docker-remote.sh user@host [/opt/rednote] [up|update]}
remote_dir=${2:-/opt/rednote}
mode=${3:-up}
[[ "$mode" == up || "$mode" == update ]] || { echo 'Use up or update' >&2; exit 1; }
[[ "$target" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.@:-]*$ ]] || { echo 'Invalid SSH target' >&2; exit 1; }
[[ "$remote_dir" =~ ^/[a-zA-Z0-9_/-]+$ && "$remote_dir" != / ]] || { echo 'Use an absolute deployment directory without spaces' >&2; exit 1; }
SSH=(ssh)
SCP=(scp)
if [[ -n ${REDNOTE_SSH_CONTROL_PATH:-} ]]; then
  SSH+=(-o "ControlPath=$REDNOTE_SSH_CONTROL_PATH")
  SCP+=(-o "ControlPath=$REDNOTE_SSH_CONTROL_PATH")
fi
archive=$(mktemp "${TMPDIR:-/tmp}/rednote-source.XXXXXXXX")
trap 'rm -f "$archive"' EXIT
COPYFILE_DISABLE=1 tar --no-xattrs --exclude=deploy/docker/deploy.env \
  --exclude=.DS_Store --exclude='._*' --exclude=__pycache__ --exclude='*.pyc' -czf "$archive" -C "$ROOT" \
  Dockerfile .dockerignore compose.yaml pom.xml src scripts deploy docs README.md tests/deployment
remote_archive="/tmp/rednote-source-$(date +%s)-$$.tar.gz"
# Use SSH agent / keys, an existing ControlMaster, or interactive password prompts.
# Never place passwords in this script or arguments.
if [[ "$mode" == update ]]; then
  # Back up with the currently deployed scripts/config before uploading changes.
  "${SSH[@]}" "$target" "set -eu; cd '$remote_dir'; test -f deploy/docker/deploy.env; scripts/docker-backup.sh"
else
  "${SSH[@]}" "$target" "mkdir -p '$remote_dir'"
fi
"${SCP[@]}" "$archive" "$target:$remote_archive"
"${SSH[@]}" "$target" "set -eu; tar -tzf '$remote_archive' >/dev/null; rm -rf '$remote_dir/src'; tar -xzf '$remote_archive' -C '$remote_dir'; rm -f '$remote_archive'; cd '$remote_dir'; if [ ! -f deploy/docker/deploy.env ]; then scripts/docker.sh init; fi; scripts/docker.sh '$mode'"
