#!/usr/bin/env bash
# Runs the pure-JVM tests for the DNS packet engine, the IP/UDP helpers and
# the domain matcher. No Android device or emulator needed.
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLINC=${KOTLINC:-kotlinc}
JAVA=${JAVA:-java}
mkdir -p build/tests
"$KOTLINC" -nowarn \
  tools/DnsSelfTest.kt \
  app/src/main/java/com/ultimateguard/core/DnsPacket.kt \
  app/src/main/java/com/ultimateguard/core/IpPacket.kt \
  app/src/main/java/com/ultimateguard/core/DomainMatcher.kt \
  -include-runtime -d build/tests/selftest.jar
"$JAVA" -jar build/tests/selftest.jar

"$KOTLINC" -nowarn tools/ListSelfTest.kt \
  app/src/main/java/com/ultimateguard/core/DomainMatcher.kt \
  -include-runtime -d build/tests/listtest.jar
"$JAVA" -jar build/tests/listtest.jar
