#!/usr/bin/env bash
# Export previews even when a test fails; preserve both failure statuses.
set +e
gradle :android:app:connectedDebugAndroidTest --no-daemon
test_status=$?
adb pull /sdcard/Download/partsdesk-preview android/app/build/reports/preview
preview_status=$?
if [ "$test_status" -ne 0 ]; then
  exit "$test_status"
fi
exit "$preview_status"

