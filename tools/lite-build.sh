#!/bin/bash
# Лёгкая сборка: потолок памяти 3 ГБ, 2 ядра из 4, низкий приоритет — рядом остаётся
# браузер с YouTube. Использование: tools/lite-build.sh [gradle-задачи]
cd "$(dirname "$0")/.."
export JAVA_HOME=/home/mista/toolchain/jdk
exec flock -n /tmp/ocm-build.lock \
  systemd-run --user --scope --quiet -p MemoryMax=3000M -p MemorySwapMax=0 -p CPUQuota=200% \
  nice -n 19 ionice -c3 ./gradlew --no-daemon --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1400m -XX:MaxMetaspaceSize=512m" \
    -Pkotlin.compiler.execution.strategy=in-process "${@:-:app:assembleDebug}"
