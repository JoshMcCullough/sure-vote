#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
port="${SUREVOTE_SMOKE_PORT:-8089}"
base="http://127.0.0.1:${port}"
log="$(mktemp)"

cd "$root/ui"
npm run build > /dev/null

cd "$root"
SUREVOTE_PORT="$port" \
SUREVOTE_STATIC="$root/ui/dist" \
SUREVOTE_ORIGINS="$base" \
SUREVOTE_RP_ID=127.0.0.1 \
SUREVOTE_DEMO_TENANT_ID="${SUREVOTE_DEMO_TENANT_ID:-6f1d2b8e-0c4a-4e7b-9a52-3d8f1c2e7b40}" \
  ./gradlew :api:run --console=plain -q > "$log" 2>&1 &
server=$!
trap 'kill "$server" 2> /dev/null || true; pkill -f "org.sure_vote.api.Main" 2> /dev/null || true' EXIT

for _ in $(seq 1 90); do
  if curl -fs "$base/health" > /dev/null; then
    break
  fi
  sleep 1
done
curl -fs "$base/health" > /dev/null || { cat "$log"; exit 1; }

cd "$root/ui"
SUREVOTE_SMOKE_URL="$base" node smoke/workspace.smoke.mjs
