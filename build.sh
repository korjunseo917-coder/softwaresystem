#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes dist docs/api
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -d build/classes src/irrigation/*.java
java -m jdk.jartool/sun.tools.jar.Main --create --file dist/ssd_ip_cp4_2026f_224110_app.jar --main-class irrigation.MainFrame -C build/classes .
java -m jdk.javadoc/jdk.javadoc.internal.tool.Main -quiet -Xdoclint:all,-missing -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -d docs/api src/irrigation/*.java
