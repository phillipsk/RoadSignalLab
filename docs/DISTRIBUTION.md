# Distribution and compatibility

## Executable local publication

```sh
scripts/verify.sh
scripts/distribute.sh
```

The first script checks behavior, core ABI, Android/bridge public JVM signatures, shrinker builds, lint and local document links. The second publishes to `build/maven-repository`, then builds an independent app with `-PconsumePublishedSdk=true`. `verifyPublishedDependencies` asserts all three RoadSignal dependencies are external `ModuleComponentIdentifier` components at the requested version. A project dependency is a test failure in this mode.

Coordinates:

```text
io.github.phillipsk.roadsignal:telematics-core:0.1.1   JVM JAR
io.github.phillipsk.roadsignal:telematics-sdk:0.1.1    Android AAR
io.github.phillipsk.roadsignal:cmt-bridge:0.1.1        Android AAR; mock partner adapter
```

The core is exposed with `api` because its models are part of the Android facade. Coroutines `StateFlow` is also a public dependency. WorkManager and Android implementation details remain implementation dependencies. Publication includes POM/Gradle module metadata and source archives. The core's Javadoc task has no Java source API; Kotlin KDoc is supplied in sources and this guide. No claim of generated HTML API documentation is made.

The distribution script builds a deterministic ZIP entry order/timestamp, file checksums and an unsigned local build manifest. That does not prove that all compiler outputs are bit-for-bit reproducible across machines. The local manifest labels its provenance limitations. Full artifact reproducibility requires clean matched-toolchain rebuild comparisons and control of every nondeterministic input.

## Client consumption

Unpack the Maven ZIP into a directory; add its URI as a Maven repository in the consumer's `settings.gradle.kts`. Pin an explicit version and declare `implementation("io.github.phillipsk.roadsignal:telematics-sdk:0.1.1")`. Keep the POM and module metadata; copying only an AAR loses transitive dependency information. Sources are for debugging and review, not a substitute for published metadata.

The lab keeps distribution contained in the project: GitHub Actions artifacts carry the Maven ZIP and debug demo APK. The tag-only release job attaches its exact attested ZIP/APK, checksums and unsigned file manifest to the matching existing GitHub release after verification. It fails rather than replacing an existing asset. It does not publish into Maven Central or GitHub Packages, need registry secrets, or redistribute CMT software. A real team would choose a supported commercial registry/channel, availability policy, credential model and retention contract.

## Version and branch policy

The only artifact version source is `sdkVersion` in `gradle.properties`. The SDK's generated version constant follows it. The demonstration release uses version `0.1.1`. SemVer permits unstable 0.x APIs; this project voluntarily preserves 0.1.x source/binary contracts and reserves 0.2 for reviewed prototype API changes. A future stable 1.x line would adopt the documented public compatibility policy explicitly.

- `main`: reviewed integration for the next release; no production-branch promise is implied.
- `release/0.1`: maintenance line for the 0.1 family; changes are cherry-picked with test evidence.
- Short-lived feature/fix branches: review, merge and remove when recoverable through Git history.
- Immutable release tags/artifacts (annotated tags are recommended for a team-managed release): never replace a published version to conceal a mistake. Publish a new version and an advisory/migration note.

No branch-protection or registry policy is assumed merely because a document says it should exist. This repository's actual branches/tags and remote CI are recorded in verification evidence.

## Five compatibility surfaces

| Surface | Gate | Examples a signature diff misses |
| --- | --- | --- |
| Source | Kotlin/Java clients compile | Named arguments, overload ambiguity, exhaustive enums |
| Binary | Core ABI and Android JVM descriptor baselines; shrunk consumer | Generated constructors/copy methods and Kotlin metadata details |
| Behavior | Reducer, lifecycle, concurrency and host journeys | Callback threading, emission timing, permission/start semantics |
| Data | Non-destructive schema fixture and persistence tests | Downgrades, key loss, partial-trip recovery, future schema variants |
| Configuration | Revision/TTL/enable tests | Older binary understanding of new fields or defaults |

Baseline changes require human review. A newly compiled consumer is not a full old-binary compatibility test; release engineering should also run a frozen precompiled client against the new artifacts. Published WorkManager worker class names need migration treatment because queued jobs survive app upgrades.

## CI and supply chain

Actions are pinned to verified commit SHAs. Jobs use read-only repository permissions except the tag-only release job's scoped contents-write and OIDC/attestation permissions. Pull requests do not receive publishing secrets. The build script pins dependencies, the wrapper pins its distribution checksum, and Dependabot proposes reviewed updates.

The tag job verifies that tag/version match, waits for build/device checks, builds the contained package and requests a GitHub artifact attestation for the SDK ZIP and debug APK before attaching those exact binaries to the release. A local JSON manifest is not equivalent to that signed CI attestation. No SLSA level is claimed. The artifact uploaded by the attestation job is the attested build, and may differ from the separate ordinary build job's ZIP; compare the subject digest, not just filenames.

Still required before commercial release: dependency verification with reviewed trusted checksums/keys, a complete transitive SBOM, license/security review, signed public release policy, provenance verification instructions and retention/support commitments. Generating checksums from already compromised inputs would not establish trust. Archive timestamp normalization alone does not establish reproducibility.

## Manifest and footprint review

The release verification audits the merged manifest, including WorkManager's transitive wake-lock/boot-rescheduling permissions and components. This prototype's capture service never starts at boot. The host requests no background location and includes no internet permission. Compare the SDK AAR with its dependency closure and the final minified host, not just the small library file. The final artifact sizes are recorded as observations, not universal integration overhead.

## Verify a downloaded release

Download all assets from the same release. Run `sha256sum --check SHA256SUMS` on Linux or `shasum -a 256 -c SHA256SUMS` on macOS. Verify the CI provenance separately:

```sh
gh attestation verify roadsignal-maven-0.1.1.zip --repo phillipsk/RoadSignalLab
gh attestation verify RoadSignalLab-0.1.1-debug.apk --repo phillipsk/RoadSignalLab
```

A successful attestation verifies provenance and subject digest, not correctness, privacy, security or a SLSA maturity level. Inspect the workflow/source identity and known prototype limits. The debug APK is for demonstration and uses a disposable CI debug signing identity, not a production signing key. A locally built APK may have a different signing identity; uninstall this disposable lab package before switching between local and CI builds if Android rejects the signature. The Maven SDK ZIP has no APK signing identity. See [GitHub attestation guidance](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations) and [release upload semantics](https://cli.github.com/manual/gh_release_upload).
