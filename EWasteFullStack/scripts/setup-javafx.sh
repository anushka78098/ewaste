#!/usr/bin/env bash
# Downloads the JavaFX 21 modules (base, graphics, controls) for THIS operating system from Maven Central into lib-fx/
# and verifies each jar against the SHA-1 that Maven Central publishes next to it.
set -e
cd "$(dirname "$0")/.."
V=21.0.5
case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)            C=linux ;;
  Linux-aarch64|Linux-arm64) C=linux-aarch64 ;;
  Darwin-x86_64)           C=mac ;;
  Darwin-arm64)            C=mac-aarch64 ;;
  *) echo "Unsupported platform $(uname -s)-$(uname -m). Download the JavaFX SDK from https://openjfx.io and unpack its lib/ folder to lib-fx/"; exit 1 ;;
esac
mkdir -p lib-fx
for M in base graphics controls; do
  F=javafx-$M-$V-$C.jar
  URL=https://repo1.maven.org/maven2/org/openjfx/javafx-$M/$V/$F
  curl -fL -o lib-fx/$F "$URL"
  EXPECTED=$(curl -fsL "$URL.sha1" | cut -d' ' -f1)
  ACTUAL=$(sha1sum lib-fx/$F 2>/dev/null | cut -d' ' -f1 || shasum lib-fx/$F | cut -d' ' -f1)
  if [ "$EXPECTED" != "$ACTUAL" ]; then echo "Checksum mismatch for $F - deleting download"; rm lib-fx/$F; exit 1; fi
  echo "ok  $F"
done
echo "JavaFX installed in lib-fx/. Next: ./build-fx.sh then ./run-client-fx.sh"
