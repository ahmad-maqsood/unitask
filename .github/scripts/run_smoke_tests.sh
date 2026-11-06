#!/usr/bin/env bash
set -euo pipefail

# Wait for the Android emulator to finish booting before starting tests.
adb wait-for-device
for attempt in $(seq 1 30); do
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot_completed" == "1" ]]; then
    break
  fi
  sleep 5
done

boot_completed="$(adb shell getprop sys.boot_completed | tr -d '\r')"
if [[ "$boot_completed" != "1" ]]; then
  echo "Android emulator did not finish booting." >&2
  exit 1
fi

APP_ID="com.aistudio.unitask.zrvqk"

# Install pre-built debug APK if present so pm grant finds the package.
APP_APK="$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1 || true)"
if [[ -n "$APP_APK" && -f "$APP_APK" ]]; then
  adb install -r -t "$APP_APK" || true
fi

adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

./gradlew connectedDebugAndroidTest --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=com.example.SmokeFlowsTest
