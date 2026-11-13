#!/usr/bin/env bash
set -euo pipefail

# If anything fails, print what the runner and emulator looked like so a dead
# emulator (out of memory, crash) can be told apart from a real test failure.
report_state() {
  echo "---- diagnostics ----" >&2
  free -m >&2 || true
  adb devices >&2 || true
  pgrep -af "qemu|emulator" >&2 || echo "No emulator process is running." >&2
  sudo dmesg 2>/dev/null | grep -i -E "out of memory|oom-kill|killed process" | tail -5 >&2 || true
}
trap 'report_state' ERR

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

# The Pixel 2 emulator profile uses the three-button navigation bar (back, home, recents).
# Switch it to swipe-gesture navigation so the screenshots show the thin gesture bar instead.
# "enable-exclusive --category" turns the gestural overlay on and the other navigation
# overlays off. If this fails the screenshots are still taken, just with the button bar.
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
sleep 5
if adb shell cmd overlay list 2>/dev/null | tr -d '\r' | grep -q '^\[x\] com.android.internal.systemui.navbar.gestural'; then
  echo "Gesture navigation is enabled."
else
  echo "Warning: could not enable gesture navigation; screenshots will show the button bar." >&2
fi

APP_ID="com.aistudio.unitask.zrvqk"

# Run the instrumentation directly instead of through connectedDebugAndroidTest.
# Gradle's connected task uninstalls the app when it finishes, which deletes the
# app-specific storage folder holding the screenshots before we can pull them.
# The APKs were already built by the earlier "Build debug app" workflow step.
APP_APK="$(ls app/build/outputs/apk/debug/*.apk | head -n 1)"
TEST_APK="$(ls app/build/outputs/apk/androidTest/debug/*.apk | head -n 1)"
echo "Installing $APP_APK and $TEST_APK"
adb install -r -t "$APP_APK"
adb install -r -t "$TEST_APK"
adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

RUNNER="$(adb shell pm list instrumentation | tr -d '\r' | grep "target=$APP_ID" | head -n 1 | sed -E 's/^instrumentation:([^ ]+).*/\1/')"
if [[ -z "$RUNNER" ]]; then
  echo "Could not find an instrumentation runner for $APP_ID." >&2
  adb shell pm list instrumentation >&2
  exit 1
fi
echo "Running instrumentation: $RUNNER"

# -r prints the full result stream, so pass/fail and stack traces appear in the log.
adb shell am instrument -w -r \
  -e class com.example.ReadmeScreenshotTest \
  -e demoEmail "$SCREENSHOT_DEMO_OWNER_EMAIL" \
  -e demoPassword "$SCREENSHOT_DEMO_OWNER_PASSWORD" \
  -e groupId "$SCREENSHOT_GROUP_ID" \
  "$RUNNER" | tee /tmp/instrumentation-output.txt | grep -v -E "demoPassword|demoEmail" || true

if ! grep -q "^INSTRUMENTATION_CODE: -1" /tmp/instrumentation-output.txt; then
  echo "Instrumentation did not finish successfully. Output above has the reason." >&2
  exit 1
fi
if grep -q "^INSTRUMENTATION_STATUS_CODE: -[124]" /tmp/instrumentation-output.txt; then
  echo "At least one test failed or errored. Output above has the stack trace." >&2
  exit 1
fi

# The test saves PNGs in the app's internal files directory. The debug build is
# debuggable, so run-as can read them, and exec-out keeps the PNG bytes intact.
# (Android/data on external storage was not visible to adb on this emulator.)
SCREENSHOT_DIR="files/readme-screenshots"
expected=(deadlines timetable courses groups my_tasks timer global_search recently_deleted theme_dark theme_accent notes note_preview note_editor settings_menu settings_general settings_notifications settings_backup settings_about)

mkdir -p docs/screenshots
# Do not let old checked-in screenshots masquerade as a fresh capture.
find docs/screenshots -maxdepth 1 -type f -delete

echo "Files the test left in the app:"
timeout 60 adb shell run-as "$APP_ID" ls -la "$SCREENSHOT_DIR" || true

echo "Exporting ${#expected[@]} screenshots."
for name in "${expected[@]}"; do
  destination="docs/screenshots/$name.png"

  ok=0
  for try in 1 2 3; do
    if timeout 60 adb exec-out run-as "$APP_ID" cat "$SCREENSHOT_DIR/$name.png" > "$destination"; then
      ok=1
      break
    fi
    echo "Reading $name.png failed (try $try of 3); retrying." >&2
    adb wait-for-device || true
    sleep 3
  done
  if [[ "$ok" != "1" ]]; then
    echo "Could not read $SCREENSHOT_DIR/$name.png from the app." >&2
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

echo "Successfully exported and validated all ${#expected[@]} screenshots."
