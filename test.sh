#!/bin/sh
set -eu
cd "$(dirname "$0")"
sh build.sh
mkdir -p build/test
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -cp build/classes -d build/test test/irrigation/*.java
java -cp build/classes:build/test irrigation.ServiceTest
java -cp build/classes:build/test irrigation.RevisionTest
java -cp build/classes:build/test irrigation.CompletionRecordTest
