#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon :telematics-core:publishReleasePublicationToProjectRepository \
  :telematics-sdk:publishReleasePublicationToProjectRepository \
  :cmt-bridge:publishReleasePublicationToProjectRepository
./gradlew --no-daemon -PconsumePublishedSdk=true :distribution-consumer:clean \
  :distribution-consumer:assembleDebug :distribution-consumer:assembleRelease \
  :distribution-consumer:verifyPublishedDependencies
python3 scripts/package_distribution.py
