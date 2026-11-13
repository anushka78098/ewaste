#!/usr/bin/env bash
# Starts the REST backend. Options: --port=8080 --host=127.0.0.1 --data=data --store=auto|sqlite|postgres|file
cd "$(dirname "$0")"
[ -d out/com ] || ./build.sh
exec java -cp "out:lib/*" com.ewaste.server.ServerMain "$@"
