#!/usr/bin/env bash
# Starts the Swing desktop client. Optional argument: server URL (default http://localhost:8080)
cd "$(dirname "$0")"
[ -d out/com ] || ./build.sh
exec java -cp "out:lib/*" com.ewaste.client.ClientMain "$@"
