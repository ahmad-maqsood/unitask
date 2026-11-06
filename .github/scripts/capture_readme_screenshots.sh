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

APP_ID="com.aistudio.unitask.zrvqk"
adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS || true

./gradlew connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.ReadmeScreenshotTest \
  -Pandroid.testInstrumentationRunnerArguments.demoEmail="$SCREENSHOT_DEMO_OWNER_EMAIL" \
  -Pandroid.testInstrumentationRunnerArguments.demoPassword="$SCREENSHOT_DEMO_OWNER_PASSWORD" \
  -Pandroid.testInstrumentationRunnerArguments.groupId="$SCREENSHOT_GROUP_ID"

# Instrumentation writes to app-specific external storage so ADB can pull
# the PNGs as ordinary files without binary streaming through run-as.
SCREENSHOT_DIR="/sdcard/Android/data/$APP_ID/files/readme-screenshots"
expected=(deadlines timetable courses groups group_chat my_tasks notes note_preview note_editor settings_menu settings_general settings_notifications settings_backup settings_about)

mkdir -p docs/screenshots
# Do not let old checked-in screenshots masquerade as a fresh capture.
find docs/screenshots -maxdepth 1 -type f -delete

echo "Checking the app-specific screenshot directory."
if ! adb shell test -d "$SCREENSHOT_DIR"; then
  echo "Cannot access $SCREENSHOT_DIR on the emulator." >&2
  echo "The instrumentation test may not have created its screenshot files." >&2
  exit 1
fi

echo "Exporting \${#expected[@]} screenshots with adb pull."
for name in "\${expected[@]}"; do
  source_file="$SCREENSHOT_DIR/$name.png"
  destination="docs/screenshots/$name.png"

  if ! adb pull "$source_file" "$destination" >/dev/null; then
    echo "Could not pull $source_file from the emulator." >&2
    exit 1
  fi

  if [[ ! -s "$destination" ]]; then
    echo "Exported screenshot is empty: $name.png" >&2
    exit 1
  fi

  signature="$(od -An -tx1 -N8 "$destination" | tr -d ' \n')"
  if [[ "$signature" != "89504e470d0a1a0a" ]]; then
    echo "Exported file is not a valid PNG: $name.png" >&2
    exit 1
  fi
done

echo "Successfully exported and validated all \${#expected[@]} screenshots."
