#!/usr/bin/env bash
set -euo pipefail

# android-emulator-runner splits its script input on newlines and executes
# each line separately. Keep shell control-flow in this standalone script.
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

mkdir -p "$HOME/.android"
if [[ ! -f "$HOME/.android/debug.keystore" ]]; then
  keytool -genkeypair -noprompt \
    -keystore "$HOME/.android/debug.keystore" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000
fi

adb shell pm grant com.aistudio.unitask.zrvqk android.permission.POST_NOTIFICATIONS || true

./gradlew connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.ReadmeScreenshotTest \
  -Pandroid.testInstrumentationRunnerArguments.demoEmail="$SCREENSHOT_DEMO_OWNER_EMAIL" \
  -Pandroid.testInstrumentationRunnerArguments.demoPassword="$SCREENSHOT_DEMO_OWNER_PASSWORD" \
  -Pandroid.testInstrumentationRunnerArguments.groupId="$SCREENSHOT_GROUP_ID"

mkdir -p docs/screenshots
adb pull /sdcard/Android/data/com.aistudio.unitask.zrvqk/files/readme-screenshots/. docs/screenshots/
