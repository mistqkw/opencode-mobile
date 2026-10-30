#!/bin/bash
# Light build: 3 GB memory cap, 2 of 4 cores, low priority, so the rest of the machine stays usable.
# Usage: tools/lite-build.sh [gradle tasks]
cd "$(dirname "$0")/.."
export JAVA_HOME=/home/mista/toolchain/jdk
exec flock -n /tmp/ocm-build.lock \
  systemd-run --user --scope --quiet -p MemoryMax=3000M -p MemorySwapMax=0 -p CPUQuota=200% \
  nice -n 19 ionice -c3 ./gradlew --no-daemon --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1400m -XX:MaxMetaspaceSize=512m" \
    -Pkotlin.compiler.execution.strategy=in-process "${@:-:app:assembleDebug}"
