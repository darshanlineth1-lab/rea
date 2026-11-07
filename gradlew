#!/usr/bin/env bash
set -euo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -x "/tmp/toolchain/gradle-8.11.1/bin/gradle" ]; then
    ulimit -f unlimited 2>/dev/null || true
    export JAVA_HOME="/tmp/toolchain/extract/jdk-17.0.20.1+1"
    export ANDROID_HOME="/tmp/toolchain/android-sdk"
    export ANDROID_SDK_ROOT="/tmp/toolchain/android-sdk"
    export GRADLE_USER_HOME="/tmp/toolchain/gradle-home"
    export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.1.0:$PATH"
    exec "/tmp/toolchain/gradle-8.11.1/bin/gradle" \
        -p "$APP_HOME" \
        -Pandroid.aapt2FromMavenOverride=/tmp/toolchain/android-sdk/build-tools/36.1.0/aapt2 \
        --offline \
        "$@"
else
    exec java -Xmx1536m -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
