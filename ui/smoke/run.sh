#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
first="${SUREVOTE_SMOKE_PORT:-8089}"
demo_tenant="${SUREVOTE_DEMO_TENANT_ID:-6f1d2b8e-0c4a-4e7b-9a52-3d8f1c2e7b40}"
admin="${SUREVOTE_SMOKE_ADMIN_ID:-0b6c1f8a-52d4-4f0e-8d33-7a9e5c1b2d64}"
logs="$(mktemp -d)"
servers=()

cleanup() {
  for pid in "${servers[@]}"; do
    kill "$pid" 2> /dev/null || true
  done
}
trap cleanup EXIT

cd "$root/ui"
npm run build > /dev/null

cd "$root"
./gradlew :api:installDist --console=plain -q

start() {
  local surface="$1" port="$2"
  shift 2
  env "$@" \
    SUREVOTE_SURFACE="$surface" \
    SUREVOTE_PORT="$port" \
    SUREVOTE_STATIC="$root/ui/apps/workspace/dist" \
    SUREVOTE_STATIC_PUBLIC="$root/ui/apps/public/dist" \
    SUREVOTE_ORIGINS="http://127.0.0.1:${port}" \
    SUREVOTE_RP_ID=127.0.0.1 \
    "$root/api/build/install/api/bin/api" > "$logs/$surface.log" 2>&1 &
  servers+=("$!")
}

ready() {
  local surface="$1" port="$2"
  for _ in $(seq 1 "${SUREVOTE_SMOKE_WAIT:-120}"); do
    if curl -fs "http://127.0.0.1:${port}/health" > /dev/null; then
      return 0
    fi
    sleep 1
  done
  cat "$logs/$surface.log"
  return 1
}

start demo "$first" SUREVOTE_DEMO_TENANT_ID="$demo_tenant"
start public "$((first + 1))"
start workspace "$((first + 2))" SUREVOTE_DEV_SIGN_IN=true SUREVOTE_SYSTEM_ADMIN_ID="$admin" SUREVOTE_SYSTEM_ADMIN_NAME=Smoke
ready demo "$first"
ready public "$((first + 1))"
ready workspace "$((first + 2))"

cd "$root/ui"
SUREVOTE_SMOKE_URL="http://127.0.0.1:${first}" node smoke/demo.smoke.mjs
SUREVOTE_SMOKE_URL="http://127.0.0.1:$((first + 1))" node smoke/public.smoke.mjs
SUREVOTE_SMOKE_URL="http://127.0.0.1:$((first + 2))" SUREVOTE_SMOKE_ADMIN_ID="$admin" node smoke/workspace.smoke.mjs
