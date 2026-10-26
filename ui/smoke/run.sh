#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
first="${SUREVOTE_SMOKE_PORT:-8089}"
demo_tenant="${SUREVOTE_DEMO_TENANT_ID:-6f1d2b8e-0c4a-4e7b-9a52-3d8f1c2e7b40}"
admin="${SUREVOTE_SMOKE_ADMIN_ID:-0b6c1f8a-52d4-4f0e-8d33-7a9e5c1b2d64}"
logs="$(mktemp -d)"
servers=()
database=""

# The demo, public, workspace, and ingest surfaces share one Postgres, as in production, so a demonstration cast on
# the demo host can be looked up and disputed on the public lookup host. Set SUREVOTE_SMOKE_JDBC_URL (with
# SUREVOTE_SMOKE_JDBC_USER and SUREVOTE_SMOKE_JDBC_PASSWORD for an owner role) to use your own; otherwise a
# throwaway container is started. The writer, reader, and dispute roles are created either way.
db_port="${SUREVOTE_SMOKE_DB_PORT:-55432}"
jdbc="${SUREVOTE_SMOKE_JDBC_URL:-jdbc:postgresql://127.0.0.1:${db_port}/surevote}"
owner="${SUREVOTE_SMOKE_JDBC_USER:-surevote}"
owner_password="${SUREVOTE_SMOKE_JDBC_PASSWORD:-surevote}"
role_password=smoke

cleanup() {
  for pid in "${servers[@]}"; do
    kill "$pid" 2> /dev/null || true
  done
  if [ -n "$database" ]; then
    docker rm -f "$database" > /dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

cd "$root/ui"
npm run build > /dev/null

cd "$root"
./gradlew :api:installDist --console=plain -q

sql() {
  if [ -n "$database" ]; then
    docker exec -i "$database" psql -v ON_ERROR_STOP=1 -q -U "$owner" -d surevote -c "$1" > /dev/null
  else
    PGPASSWORD="$owner_password" psql "${jdbc#jdbc:}" -U "$owner" -v ON_ERROR_STOP=1 -q -c "$1" > /dev/null
  fi
}

if [ -z "${SUREVOTE_SMOKE_JDBC_URL:-}" ]; then
  database="surevote-smoke-$$"
  docker run -d --rm --name "$database" -p "127.0.0.1:${db_port}:5432" \
    -e POSTGRES_DB=surevote -e POSTGRES_USER="$owner" -e POSTGRES_PASSWORD="$owner_password" \
    postgres:16-alpine > /dev/null
  for _ in $(seq 1 60); do
    if docker exec "$database" pg_isready -q -U "$owner" -d surevote -h 127.0.0.1; then
      break
    fi
    sleep 1
  done
fi
for role in surevote_app surevote_public surevote_dispute; do
  sql "do \$\$ begin if not exists (select from pg_roles where rolname = '$role') then
    create role $role login password '$role_password'; end if; end \$\$"
done

start() {
  local surface="$1" port="$2"
  shift 2
  env "$@" \
    SUREVOTE_JDBC_URL="$jdbc" \
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

writer=(SUREVOTE_JDBC_APP_USER=surevote_app SUREVOTE_JDBC_APP_PASSWORD="$role_password")
lookup="http://127.0.0.1:$((first + 1))"
# Only the workspace migrates and grants. The others start first and must wait for it without serving.
start ingest "$((first + 3))" "${writer[@]}"
start demo "$first" "${writer[@]}" SUREVOTE_DEMO_TENANT_ID="$demo_tenant" SUREVOTE_LOOKUP_URL="$lookup"
start public "$((first + 1))" \
  SUREVOTE_JDBC_PUBLIC_USER=surevote_public SUREVOTE_JDBC_PUBLIC_PASSWORD="$role_password" \
  SUREVOTE_JDBC_DISPUTE_USER=surevote_dispute SUREVOTE_JDBC_DISPUTE_PASSWORD="$role_password"
# On a fresh database (the default container) the gate has something to wait for.
if [ -n "$database" ]; then
  for _ in $(seq 1 60); do
    if grep -q "could not read the database schema version\|Waiting for the workspace surface to migrate" \
      "$logs/ingest.log"; then
      break
    fi
    sleep 1
  done
  if curl -fs "http://127.0.0.1:$((first + 3))/health" > /dev/null; then
    echo "the ingest surface served before the workspace migrated"
    exit 1
  elif ! grep -q "could not read the database schema version\|Waiting for the workspace surface to migrate" \
    "$logs/ingest.log"; then
    cat "$logs/ingest.log"
    exit 1
  fi
  echo "ok the other surfaces wait for the workspace to migrate"
fi
start workspace "$((first + 2))" "${writer[@]}" \
  SUREVOTE_JDBC_USER="$owner" SUREVOTE_JDBC_PASSWORD="$owner_password" \
  SUREVOTE_GRANT_PUBLIC_ROLE=surevote_public SUREVOTE_GRANT_DISPUTE_ROLE=surevote_dispute \
  SUREVOTE_DEV_SIGN_IN=true SUREVOTE_SYSTEM_ADMIN_ID="$admin" SUREVOTE_SYSTEM_ADMIN_NAME=Smoke
ready workspace "$((first + 2))"
ready demo "$first"
ready public "$((first + 1))"
ready ingest "$((first + 3))"

refused() {
  local surface="$1" port="$2" status
  status="$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d '{}' \
    "http://127.0.0.1:${port}/ballots")"
  echo "$status"
}

# The public surface must refuse to start with the writer role.
if env SUREVOTE_SURFACE=public SUREVOTE_PORT="$((first + 4))" SUREVOTE_JDBC_URL="$jdbc" "${writer[@]}" \
  SUREVOTE_JDBC_PUBLIC_USER=surevote_public SUREVOTE_JDBC_DISPUTE_USER=surevote_dispute \
  timeout 60 "$root/api/build/install/api/bin/api" > "$logs/public-writer.log" 2>&1; then
  echo "the public surface started with the writer role"
  exit 1
elif ! grep -q "must not use the writer role" "$logs/public-writer.log"; then
  cat "$logs/public-writer.log"
  exit 1
fi
echo "ok the public surface refuses the writer role"

# The demo, like every surface but the workspace, must refuse the owner credential.
if env SUREVOTE_SURFACE=demo SUREVOTE_PORT="$((first + 4))" SUREVOTE_JDBC_URL="$jdbc" "${writer[@]}" \
  SUREVOTE_DEMO_TENANT_ID="$demo_tenant" SUREVOTE_JDBC_USER="$owner" SUREVOTE_JDBC_PASSWORD="$owner_password" \
  timeout 60 "$root/api/build/install/api/bin/api" > "$logs/demo-owner.log" 2>&1; then
  echo "the demo surface started with the owner credential"
  exit 1
elif ! grep -q "demo surface does not migrate" "$logs/demo-owner.log"; then
  cat "$logs/demo-owner.log"
  exit 1
fi
echo "ok the demo surface refuses the owner credential"

ingest="http://127.0.0.1:$((first + 3))"
for check in "POST /ballots 40[01]" "POST /elections/00000000-0000-4000-8000-000000000000/validations 40[01]" \
  "GET /health 200" "GET /app/ 404" "GET /voter/ 404" "POST /lookup 404" "GET /auth/session 404" \
  "GET /workspace 404" "GET /showcase/tenant 404"; do
  read -r method path expected <<< "$check"
  got="$(curl -s -o /dev/null -w '%{http_code}' -X "$method" -H 'Content-Type: application/json' \
    $([ "$method" = POST ] && echo "-d {}") "$ingest$path")"
  if ! [[ "$got" =~ ^${expected}$ ]]; then
    echo "ingest $method $path returned $got, expected $expected"
    exit 1
  fi
done
echo "ok ingest serves only signed machine routes"
workspace_ballots="$(refused workspace "$((first + 2))")"
if [ "$workspace_ballots" != "404" ]; then
  echo "workspace POST /ballots returned $workspace_ballots, expected 404"
  exit 1
fi
echo "ok the workspace no longer takes machine requests"

cd "$root/ui"
SUREVOTE_SMOKE_URL="http://127.0.0.1:${first}" SUREVOTE_SMOKE_LOOKUP_URL="$lookup" node smoke/demo.smoke.mjs
SUREVOTE_SMOKE_URL="http://127.0.0.1:$((first + 1))" node smoke/public.smoke.mjs
SUREVOTE_SMOKE_URL="http://127.0.0.1:$((first + 2))" SUREVOTE_SMOKE_ADMIN_ID="$admin" node smoke/workspace.smoke.mjs
