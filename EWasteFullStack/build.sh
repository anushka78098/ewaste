#!/usr/bin/env bash
# Compiles server + client + tests into ./out
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -d out -cp "lib/*" $(find src test -name '*.java')
echo "Build OK -> ./out"
