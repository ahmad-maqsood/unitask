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

# Resolve the app-specific external-files directory from the emulator instead of
# assuming the app package path. Use shell globbing supported by Android's shell
# rather than relying on find options that vary between Android versions.
SCREENSHOT_DIR="$(adb shell 'for dir in /sdcard/Android/data/*/files/readme-screenshots; do if [ -d "$dir" ]; then printf "%s\\n" "$dir"; break; fi; done' | tr -d '\r')"
if [[ -z "$SCREENSHOT_DIR" ]]; then
  echo "Could not find the app's readme-screenshots directory on the emulator." >&2
  echo "Installed Unitask packages:" >&2
  adb shell pm list packages | grep -i unitask >&2 || true
  echo "Available app-specific external-data directories:" >&2
  adb shell ls -la /sdcard/Android/data >&2 || true
  exit 1
fi

mkdir -p docs/screenshots
# Do not let old checked-in screenshots masquerade as a fresh capture.
find docs/screenshots -maxdepth 1 -type f -delete
echo "Pulling fresh screenshots from: $SCREENSHOT_DIR"
adb pull "$SCREENSHOT_DIR/." docs/screenshots/

expected=(deadlines timetable courses groups group_chat my_tasks notes note_preview note_editor settings_menu settings_general settings_notifications settings_backup settings_about)
for name in "${expected[@]}"; do
  if [[ ! -s "docs/screenshots/$name.png" ]]; then
    echo "Screenshot capture is incomplete; missing $name.png" >&2
    exit 1
  fi
done
