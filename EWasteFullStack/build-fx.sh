#!/usr/bin/env bash
# Compiles the JavaFX client (src-fx) into ./out-fx. Needs ./build.sh done first and JavaFX installed (scripts/setup-javafx.sh).
set -e
cd "$(dirname "$0")"
[ -d out/com ] || ./build.sh
[ -d lib-fx ] || { echo "JavaFX not installed yet - run scripts/setup-javafx.sh first"; exit 1; }
mkdir -p out-fx
javac -d out-fx --module-path lib-fx --add-modules javafx.controls -cp out $(find src-fx -name '*.java')
echo "JavaFX client built -> ./out-fx"
