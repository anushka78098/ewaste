#!/usr/bin/env bash
# Builds runnable jars into ./dist:
#   ewaste-server.jar      server (needs lib/sqlite-jdbc-*.jar next to it for SQLite; works without, using the file store)
#   ewaste-client.jar      Swing desktop client (double-click or: java -jar ewaste-client.jar [server-url])
#   ewaste-client-fx.jar   JavaFX client (only if lib-fx/ exists; run with the java --module-path command printed below)
set -e
cd "$(dirname "$0")/.."
rm -rf dist build-tmp && mkdir -p dist/lib build-tmp/server build-tmp/client build-tmp/fx

javac -d build-tmp/server $(find src/com/ewaste/common src/com/ewaste/model src/com/ewaste/server -name '*.java')
SQLITE=$(ls lib/sqlite-jdbc-*.jar 2>/dev/null | head -1 || true)
CP=""
if [ -n "$SQLITE" ]; then cp "$SQLITE" dist/lib/; CP="lib/$(basename "$SQLITE")"; fi
printf 'Main-Class: com.ewaste.server.ServerMain\nClass-Path: %s\n' "$CP" > build-tmp/server.mf
jar cfm dist/ewaste-server.jar build-tmp/server.mf -C build-tmp/server .

javac -d build-tmp/client $(find src/com/ewaste/common src/com/ewaste/client -name '*.java')
printf 'Main-Class: com.ewaste.client.ClientMain\n' > build-tmp/client.mf
jar cfm dist/ewaste-client.jar build-tmp/client.mf -C build-tmp/client .

if [ -d lib-fx ]; then
  javac -d build-tmp/fx --module-path lib-fx --add-modules javafx.controls -cp build-tmp/client $(find src-fx -name '*.java')
  printf 'Main-Class: com.ewaste.fx.FxLauncher\n' > build-tmp/fx.mf
  # the FX client reuses the shared client classes, so ship them in the same jar
  (cd build-tmp/client && cp -r . ../fx/)
  jar cfm dist/ewaste-client-fx.jar build-tmp/fx.mf -C build-tmp/fx .
  cp -r lib-fx dist/
  echo "JavaFX client: java --module-path lib-fx --add-modules javafx.controls -jar ewaste-client-fx.jar"
fi
rm -rf build-tmp
echo "Built:"; ls -la dist
