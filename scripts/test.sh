#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
if [[ -n "${JAVA_HOME:-}" ]]; then
    java_bin="$JAVA_HOME/bin/"
else
    java_bin=""
fi
"${java_bin}javac" -encoding UTF-8 --release 17 -d "$classes" \
    app/src/main/java/com/lynkracing/dashboard/Telemetry.java tests/TelemetryTest.java
"${java_bin}java" -cp "$classes" com.lynkracing.dashboard.TelemetryTest
