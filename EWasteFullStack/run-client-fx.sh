#!/usr/bin/env bash
# Starts the JavaFX desktop client. Optional argument: server URL (default http://localhost:8080)
cd "$(dirname "$0")"
[ -d out-fx/com ] || ./build-fx.sh
exec java --module-path lib-fx --add-modules javafx.controls -cp "out:out-fx" com.ewaste.fx.FxLauncher "$@"
