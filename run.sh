#!/bin/sh
cd "$(dirname "$0")"
exec java -jar dist/ssd_ip_cp4_2026f_224110_app.jar "$@"
