#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")/.."
destination=${1:?Usage: deploy/backup-database.sh /backup/directory}
mkdir -p "$destination"
file="$destination/penny-$(date -u +%Y%m%dT%H%M%SZ).dump"
trap 'rm -f "$file.partial"' EXIT HUP INT TERM
docker compose -f compose.production.yaml exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl -Fc' > "$file.partial"
docker compose -f compose.production.yaml exec -T db pg_restore --list < "$file.partial" > /dev/null
mv "$file.partial" "$file"
printf '%s\n' "$file"
