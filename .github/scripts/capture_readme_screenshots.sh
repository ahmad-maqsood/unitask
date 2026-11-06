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

# The package ID is defined by app/build.gradle.kts. Access the exact
# app-owned directory directly: Android 11 can deny listing /sdcard/Android/data,
# even when the app's own external-files directory is readable by ADB.
SCREENSHOT_DIR="/sdcard/Android/data/com.aistudio.unitask.zrvqk/files/readme-screenshots"

mkdir -p docs/screenshots
# Do not let old checked-in screenshots masquerade as a fresh capture.
find docs/screenshots -maxdepth 1 -type f -delete
echo "Pulling fresh screenshots from the app-owned directory: $SCREENSHOT_DIR"
if ! adb pull "$SCREENSHOT_DIR/." docs/screenshots/; then
  echo "Could not pull screenshots from the app-owned directory." >&2
  echo "The instrumentation test completed, but its output directory was not accessible at the expected package path." >&2
  exit 1
fi

expected=(deadlines timetable courses groups group_chat my_tasks notes note_preview note_editor settings_menu settings_general settings_notifications settings_backup settings_about)
for name in "${expected[@]}"; do
  if [[ ! -s "docs/screenshots/$name.png" ]]; then
    echo "Screenshot capture is incomplete; missing $name.png" >&2
    exit 1
  fi
done
