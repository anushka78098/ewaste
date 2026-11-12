#!/usr/bin/env bash
# Downloads the official SQLite JDBC driver from Maven Central into lib/ and verifies its SHA-1.
set -e
cd "$(dirname "$0")/.."
V=3.36.0.3
BASE=https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/$V
mkdir -p lib
curl -fL -o lib/sqlite-jdbc-$V.jar "$BASE/sqlite-jdbc-$V.jar"
EXPECTED=$(curl -fsL "$BASE/sqlite-jdbc-$V.jar.sha1" | cut -d' ' -f1)
ACTUAL=$(sha1sum lib/sqlite-jdbc-$V.jar 2>/dev/null | cut -d' ' -f1 || shasum lib/sqlite-jdbc-$V.jar | cut -d' ' -f1)
if [ "$EXPECTED" != "$ACTUAL" ]; then echo "Checksum mismatch - deleting download"; rm lib/sqlite-jdbc-$V.jar; exit 1; fi
echo "SQLite driver installed in lib/ (checksum verified). Restart the server; it will use data/ewaste.db."
