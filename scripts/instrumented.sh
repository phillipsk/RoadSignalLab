#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon :telematics-sdk:assembleDebugAndroidTest \
  :sample-app:assembleDebug :sample-app:assembleDebugAndroidTest
python3 scripts/run_device_journeys.py
