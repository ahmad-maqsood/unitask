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

status=0
./gradlew connectedDebugAndroidTest --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=com.example.SmokeFlowsTest || status=$?

# Gradle only says "There were failing tests". Print each failing test and its
# first stack-trace lines here so the cause is visible in the job log.
if [[ "$status" -ne 0 ]]; then
  echo "::group::Failing smoke tests"
  python3 - <<'PY'
import glob
import xml.etree.ElementTree as ET

files = glob.glob("app/build/outputs/androidTest-results/**/*.xml", recursive=True)
if not files:
    print("No test result XML files were found.")
for path in files:
    for case in ET.parse(path).getroot().iter("testcase"):
        for kind in ("failure", "error"):
            node = case.find(kind)
            if node is not None:
                text = (node.get("message") or "") + "\n" + (node.text or "")
                print(f"FAILED {case.get('classname')}.{case.get('name')}")
                print("\n".join(text.strip().splitlines()[:15]))
                print("---")
PY
  echo "::endgroup::"
fi
exit "$status"
