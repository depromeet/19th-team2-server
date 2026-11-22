#!/bin/bash
set -Eeuo pipefail
umask 022

root="${TEAM2_ROOT:-/opt/team2}"
config="${TEAM2_CONFIG_FILE:-/etc/team2/deploy.json}"
operation="${SSM_Operation:-deploy}"
attempts="${TEAM2_HEALTH_ATTEMPTS:-24}"
interval="${TEAM2_HEALTH_INTERVAL:-5}"

fail() { echo "$*" >&2; exit 1; }
mkdir -p "$root/releases"
exec 9>"$root/deploy.lock"
flock -n 9 || fail 'Another deployment is running.'

staging=""
prune_required=false

prune_releases() {
  python3 - "$root" "$config" <<'PY'
import json, re, shutil, sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
with open(sys.argv[2], encoding='utf-8') as source:
    retain = json.load(source).get('release_retention_count', 5)
if type(retain) is not int or retain < 3:
    raise SystemExit('release_retention_count must be an integer of at least 3.')

release_root = root / 'releases'
releases = [
    path for path in release_root.glob('*/*')
    if not path.parent.is_symlink() and not path.is_symlink() and path.is_dir()
    and re.fullmatch(r'[0-9a-f]{40}/[0-9]+-[0-9]+', path.relative_to(release_root).as_posix())
]
protected = {
    (root / name).resolve()
    for name in ('current', 'previous', 'last-successful')
    if (root / name).is_symlink()
}
keep = {path for path in releases if path in protected}
for path in sorted(releases, key=lambda path: (path.stat().st_mtime_ns, str(path)), reverse=True):
    if len(keep) < retain:
        keep.add(path)
for path in releases:
    if path not in keep:
        shutil.rmtree(path)
        if not any(path.parent.iterdir()):
            path.parent.rmdir()
PY
}

cleanup() {
  local status=$?
  trap - EXIT
  if [[ -n "$staging" ]]; then
    rm -rf "$staging" || status=1
  fi
  if [[ "$prune_required" == true ]]; then
    prune_releases || { echo 'Release cleanup failed; check disk usage and permissions.' >&2; status=1; }
  fi
  exit "$status"
}
trap cleanup EXIT

atomic_link() {
  python3 - "$1" "$2" <<'PY'
import os, sys
target, link = sys.argv[1:]
temporary = link + '.next'
try:
    os.unlink(temporary)
except FileNotFoundError:
    pass
os.symlink(target, temporary)
os.replace(temporary, link)
PY
}

healthy() {
  local response
  response=$(curl --fail --silent --show-error --max-time 5 \
    http://127.0.0.1:8080/actuator/health) || return 1
  printf '%s' "$response" | python3 -c \
    'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status") == "UP" else 1)'
}

restart_and_check() {
  if systemctl restart team2-server; then
    for ((i=0; i<attempts; i++)); do
      if healthy; then return 0; fi
      sleep "$interval"
    done
  fi
  systemctl stop team2-server || true
  fail 'Application failed health checks. Last successful releases are preserved; check DB compatibility before rollback.'
}

last_successful=$(readlink "$root/last-successful" || true)
case "$operation" in
  deploy)
    key="${SSM_ReleaseKey:-}"
    digest="${SSM_ArtifactSha256:-}"
    [[ "$key" =~ ^releases/[0-9a-f]{40}/[0-9]+-[0-9]+$ ]] || fail 'Invalid release key.'
    [[ "$digest" =~ ^[0-9a-f]{64}$ ]] || fail 'Invalid artifact checksum.'
    region=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["region"])' "$config")
    bucket=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["artifact_bucket"])' "$config")
    staging=$(mktemp -d "$root/.incoming.XXXXXX")
    aws s3 cp "s3://$bucket/$key/app.jar" "$staging/app.jar" \
      --region "$region" --only-show-errors
    python3 - "$staging/app.jar" "$digest" <<'PY'
import hashlib, sys
digest = hashlib.sha256()
with open(sys.argv[1], 'rb') as source:
    for block in iter(lambda: source.read(1024 * 1024), b''):
        digest.update(block)
if digest.hexdigest() != sys.argv[2]:
    raise SystemExit('Artifact checksum mismatch; existing service was not changed.')
PY
    release="$root/$key"
    [[ ! -e "$release" ]] || fail 'Release directory already exists; use a new workflow attempt.'
    mkdir -p "$(dirname "$release")"
    mv "$staging" "$release"
    prune_required=true
    chmod 0755 "$release"
    chmod 0644 "$release/app.jar"
    ;;
  rollback)
    [[ "${SSM_ConfirmRollback:-no}" == yes ]] \
      || fail 'Check DB compatibility, then explicitly set ConfirmRollback=yes.'
    current=$(readlink "$root/current" || true)
    if [[ -n "$last_successful" && "$current" != "$last_successful" ]]; then
      release="$last_successful"
    else
      release=$(readlink "$root/previous" || true)
    fi
    [[ -n "$release" && "$release" == "$root/releases/"* && -f "$release/app.jar" ]] \
      || fail 'No previous successful release is available.'
    ;;
  *) fail 'Unsupported deployment operation.' ;;
esac

atomic_link "$release" "$root/current"
prune_required=true
restart_and_check
if [[ -n "$last_successful" && "$last_successful" != "$release" ]]; then
  atomic_link "$last_successful" "$root/previous"
fi
atomic_link "$release" "$root/last-successful"
echo "Operation $operation succeeded: $release"
