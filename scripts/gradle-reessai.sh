#!/usr/bin/env bash
# Runs ./gradlew with the given arguments. If it fails because of the network only (a server that
# answers « 429 Too Many Requests », a download cut or refused), it waits one minute and runs once
# more. A real failure (a test, the lint, the compilation) is not run again: it fails at once.
set -uo pipefail

log="$(mktemp)"
./gradlew "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -eq 0 ]; then
  exit 0
fi

network='HTTP response code: 429|status code 429|Too Many Requests|Could not (GET|HEAD|download|resolve) |Connection reset|Read timed out|connect timed out|Remote host terminated the handshake|Failed to fetch maven artifact'
if grep -qE "$network" "$log"; then
  echo "::warning::Échec réseau (téléchargement), nouvel essai dans 60 s."
  sleep 60
  ./gradlew "$@"
  exit $?
fi
exit "$status"
