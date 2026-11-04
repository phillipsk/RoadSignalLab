# Release, activation and partner adoption

The `v0.1.1` prototype includes contained CI publication, an API 31/35/36 matrix and a tag-gated attested ZIP/APK release job. The broader partner operating model below is proposed; no commercial partner rollout has occurred.

## Example release progression

1. Define product guardrails and supported host/OS/permission matrix before implementation.
2. Review the smallest change: sensor execution, public API, schema and config capability separately.
3. Run reducer tests, concurrency/privacy tests, device journeys, minified host, published consumer, API diff and migration checks.
4. Cut an immutable release candidate; exercise it in internal host apps with real device/drive evidence.
5. Publish a numbered artifact and migration guide. Record dependencies, known limits and matching source tag.
6. Partner teams integrate, test and release their host apps. Track installed app/SDK adoption, not just repository version changes.
7. Activate a compatible configuration cohort after binary adoption reaches the planned threshold.
8. Verify runtime and capture-quality metrics before broadening exposure. Keep an audited rollback path and an on-call owner.

## Capability/version matrix example

| Host cohort | SDK | Config contract | Next action |
| --- | --- | --- | --- |
| Internal preview | 0.2.0-rc.1 | v2 feature gated | Test API/storage migration before release |
| Supported maintenance | 0.1.x | v1 known fields | Backport compatible fixes |
| Older installed app | Older binary | Unknown future fields | Keep safe old defaults; request host upgrade |

These are hypothetical cohorts. The lab's implemented artifacts are listed in verification evidence; it does not fabricate historical customers or release adoption.

## Rollback is more than one switch

| Failure | Candidate action | Limit |
| --- | --- | --- |
| Policy-only regression | Restore a compatible last-known-good config | Only shipped code understands that control; apply/fetch latency exists |
| Detector/model regression | Disable new path or select prior compatible model | Validate model input/output schema and capture impact |
| SDK binary crash | Partner app rollback or patch release; safe kill switch if already shipped | SDK vendor cannot instantly replace all installed app binaries |
| Bad database migration | Stop rollout, investigate preserved fixture, forward-fix | Downgrades may not read newer schemas; do not promise destructive rollback |
| Security issue | Advisory + scoped mitigation + patched versions + partner escalation | Coordinate evidence and owner communications; avoid leaking data in diagnostics |

## Partner adoption tracker template

| Partner | Host version | SDK version | Config revision | Eligible installs | Installed share | Activated | Recording guardrails | Owner / next action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Example only | 1.2.0 | 0.1.1 | 7 | Unknown | Unknown | No | Not measured | Establish baseline before activation |

Never fill unknowns with optimistic estimates. Distinguish released, installed, activated and verified. Instrument the SDK version/config capability and aggregate outcome counters without raw route data where possible. Define support windows and escalation expectations before asking partners to upgrade.

## On-call checklist

Establish affected app/SDK/config/OS cohorts and user impact; stop further exposure if warranted. Compare capture quality, crash/ANR, queue growth and runtime-cost signals with a matched baseline. Check concurrent host/model/backend changes. Decide whether to roll back config, pause a model, or ship a binary patch. Preserve redacted evidence and ownership. After stabilization, write a concise incident review with tests, detection improvements and customer-adoption follow-through.

## Release commands

Run verification first. Create release tags only for a reviewed committed tree, then push the intended branch/tag and inspect the matching CI run. A tag is not evidence that a pipeline passed. Production signing/registry secrets belong in protected CI environments; none are stored in this repository.
