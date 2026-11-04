#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon :telematics-core:test :telematics-core:checkLegacyAbi \
  :sample-app:assembleDebug :sample-app:assembleRelease \
  :telematics-sdk:assembleRelease :cmt-bridge:assembleRelease \
  :telematics-sdk:lintRelease :cmt-bridge:lintRelease :sample-app:lintRelease \
  :distribution-consumer:assembleRelease
python3 scripts/check_manifest.py
python3 scripts/check_api.py
python3 scripts/check_docs.py
