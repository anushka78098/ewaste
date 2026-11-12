#!/usr/bin/env bash
# Makes a double-click installer / app folder for the Swing client with jpackage (JDK 14+). Run it ON the OS you want to target.
#   ./scripts/make-installer.sh              app folder (no extra tools needed)
#   ./scripts/make-installer.sh deb|rpm|dmg|pkg   native installer (deb/rpm need dpkg/rpmbuild, Windows needs the WiX toolset: use make-installer.bat)
set -e
cd "$(dirname "$0")/.."
./scripts/package.sh >/dev/null
TYPE=${1:-app-image}
rm -rf installers && mkdir installers
jpackage --type "$TYPE" --name EWasteClient --app-version 1.0 --vendor "E-Waste Manager" \
  --input dist --main-jar ewaste-client.jar --main-class com.ewaste.client.ClientMain --dest installers
echo "Done -> ./installers"; ls installers
