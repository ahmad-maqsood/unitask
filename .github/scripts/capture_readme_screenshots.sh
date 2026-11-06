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

# Instrumentation writes into the app's private files directory. Retrieve it
# through run-as (the debug build is debuggable); external-storage paths are
# not guaranteed to exist on every emulator/API level.
APP_ID="com.aistudio.unitask.zrvqk"
SCREENSHOT_DIR="files/readme-screenshots"

mkdir -p docs/screenshots
# Do not let old checked-in screenshots masquerade as a fresh capture.
find docs/screenshots -maxdepth 1 -type f -delete
echo "Exporting fresh screenshots from the app's private files directory."
if ! adb exec-out run-as "$APP_ID" tar -C "$SCREENSHOT_DIR" -cf - . | tar -xf - -C docs/screenshots; then
  echo "Could not export screenshots from the app's private files directory." >&2
  echo "Confirm that the debug app is debuggable and that the instrumentation test created its output directory." >&2
  exit 1
fi

expected=(deadlines timetable courses groups group_chat my_tasks notes note_preview note_editor settings_menu settings_general settings_notifications settings_backup settings_about)
for name in "${expected[@]}"; do
  if [[ ! -s "docs/screenshots/$name.png" ]]; then
    echo "Screenshot capture is incomplete; missing $name.png" >&2
    exit 1
  fi
done
