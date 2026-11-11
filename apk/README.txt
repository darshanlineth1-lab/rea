SleepOff APKs were not pre-built in this delivery because the sandbox
lacks a reachable JDK and Android SDK (the network allowlist blocks the
hosts that host OpenJDK, Gradle, and Android build-tools).

The project is fully configured for one-click APK generation in Android
Studio. Open the sleepoff/ directory and press ▶ Run. Gradle will
produce:

  app-debug.apk   → app/build/outputs/apk/debug/app-debug.apk
  app-release.apk → app/build/outputs/apk/release/app-release.apk
                    (after you create/sign with a keystore)

See TECHNICAL_REPORT.md §1 for full build instructions.
