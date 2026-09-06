#!/usr/bin/env bash
# Run on a disposable Linux CI runner; never reuse a developer's AVD.
set -euo pipefail

: "${ANDROID_HOME:?Android SDK must be configured}"
: "${API_LEVEL:?Set the system image API level}"
case "$API_LEVEL" in
  26|37.0) ;;
  *) echo "Unsupported CI API level: $API_LEVEL" >&2; exit 1 ;;
esac

serial=emulator-5554
avd=better-french-tts-ci
diagnostics=build/emulator-diagnostics
mkdir -p "$diagnostics"

cleanup() {
  result=$?
  trap - EXIT
  timeout 15 adb -s "$serial" logcat -d > "$diagnostics/logcat.txt" 2>&1 || true
  timeout 10 adb -s "$serial" shell getprop > "$diagnostics/device.txt" 2>&1 || true
  timeout 10 adb -s "$serial" emu kill > /dev/null 2>&1 || true
  exit "$result"
}
trap cleanup EXIT

sdkmanager --install "system-images;android-$API_LEVEL;google_apis;x86_64" emulator platform-tools
printf 'no\n' | avdmanager create avd --force --name "$avd" \
  --package "system-images;android-$API_LEVEL;google_apis;x86_64" --device pixel_2
adb start-server
"$ANDROID_HOME/emulator/emulator" -avd "$avd" -port 5554 \
  -no-window -no-snapshot -no-audio -no-boot-anim -no-metrics \
  -gpu swiftshader -memory 3072 -cores 2 > "$diagnostics/emulator.txt" 2>&1 &
emulator_pid=$!

# sys.boot_completed alone can precede working binder services on newer images.
# Retry readiness checks, not tests, and retain a hard deadline.
deadline=$((SECONDS + 600))
ready=false
while (( SECONDS < deadline )); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    echo "Emulator exited before becoming ready" >&2
    exit 1
  fi
  if [[ "$(timeout 5 adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] &&
    timeout 10 adb -s "$serial" shell cmd package list packages > /dev/null 2>&1 &&
    timeout 10 adb -s "$serial" shell input keyevent 82 > /dev/null 2>&1 &&
    timeout 10 adb -s "$serial" shell settings put global window_animation_scale 0 &&
    timeout 10 adb -s "$serial" shell settings put global transition_animation_scale 0 &&
    timeout 10 adb -s "$serial" shell settings put global animator_duration_scale 0; then
    ready=true
    break
  fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  echo "Android services did not become ready within the boot deadline" >&2
  exit 1
fi

expected_api=${API_LEVEL%%.*}
actual_api=$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
if [[ "$actual_api" != "$expected_api" ]]; then
  echo "Expected API $expected_api, got $actual_api" >&2
  exit 1
fi
echo "Running instrumented tests on API $actual_api"
./gradlew connectedDebugAndroidTest --console=plain --max-workers=2
