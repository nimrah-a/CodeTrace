#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
find src/main/java src/test/java -name '*.java' | sort > out/sources.txt
javac --release 17 -d out @out/sources.txt
java -ea -cp out com.codetrace.EngineTest
