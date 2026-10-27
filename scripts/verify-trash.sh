#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Build in a disposable copy, never in the active development server's mounts.
verify_dir=$(mktemp -d "${TMPDIR:-/tmp}/penny-trash-verify.XXXXXX")
verify_project="penny-trash-verify-$$"
cleanup() {
 docker compose -p "$verify_project" -f "$verify_dir/compose.m106-test.yaml" down -v --remove-orphans >/dev/null 2>&1 || true
 rm -rf "$verify_dir"
}
trap cleanup EXIT
COPYFILE_DISABLE=1 tar --exclude=.git --exclude=.env --exclude='.penny-*' --exclude=.superpowers --exclude=node_modules --exclude=target --exclude=dist -cf - backend frontend compose.m106-test.yaml | tar -xf - -C "$verify_dir"
docker compose -p "$verify_project" -f "$verify_dir/compose.m106-test.yaml" run --rm backend-test
docker compose -p "$verify_project" -f "$verify_dir/compose.m106-test.yaml" run --rm frontend-test
