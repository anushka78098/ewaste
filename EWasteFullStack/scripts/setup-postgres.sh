#!/usr/bin/env bash
# Downloads the PostgreSQL JDBC driver into lib/ and verifies its SHA-256 checksum.
set -e
cd "$(dirname "$0")/.."
V=42.7.14
EXPECTED=73914527305A40CCE504B0D3D90B23CAF565136912D607AE8AE7C5895512332C
FILE="lib/postgresql-$V.jar"
URL="https://repo.maven.apache.org/maven2/org/postgresql/postgresql/$V/postgresql-$V.jar"
mkdir -p lib

file_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

if [ -f "$FILE" ]; then
  ACTUAL=$(file_sha256 "$FILE")
  if [ "$EXPECTED" = "$ACTUAL" ]; then
    echo "PostgreSQL JDBC driver already installed and checksum verified."
    exit 0
  fi
fi

curl -fL -o "$FILE" "$URL"
ACTUAL=$(file_sha256 "$FILE")
if [ "$EXPECTED" != "$ACTUAL" ]; then
  rm -f "$FILE"
  echo "Checksum mismatch - downloaded driver deleted"
  exit 1
fi
echo "PostgreSQL JDBC driver installed in lib/ (checksum verified). Restart the server."
