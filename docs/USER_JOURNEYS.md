# Demo journeys and significance

All replay journeys are local synthetic fixtures. There is no CMT account, SDK binary, network endpoint, emergency action or validated risk score. Start on an emulator; the instrumented suite resets only this lab's data.

| Journey | Actions / expected result | SDK contract demonstrated |
| --- | --- | --- |
| 1. Inspect before consent | Explore the lab; replay is gated; view Controls | SDK initialization does not imply permission to collect |
| 2. Enable local SDK | Controls → Enable local SDK | Durable consent, no automatic sensing |
| 3. Replay commute | Overview → Replay commute → Trips | Armed detection, finalized trip, encrypted persistence/outbox |
| 4. Inspect a trip | Open newest trip; view route and event timeline | Provenance, illustrative coaching, bounded event data |
| 5. Correct role | Select Passenger or Driver | Explicit user correction, no invented automatic classification |
| 6. Recover through a gap | Overview → Tunnel & bad GPS | Reject poor accuracy; split on long gap without inventing distance |
| 7. Impact candidate | Replay Impact candidate; inspect event | Synthetic signal extraction; no emergency dispatch |
| 8. Explore power requests | Lab → Power; apply economy/balanced/responsive | Actual listener policy, requested versus observed behavior |
| 9. Disable capture by policy | Start replay, then disable collection | Reversible admission/stop control; local fixture, not a remote service |
| 10. Queue offline work | Lab → Outbox → Offline → Schedule local sync | Durable constrained WorkManager job and finite retries |
| 11. Acknowledge/reject/recover | Select ACK or REJECT; retry dead letters explicitly | Local idempotency, error classification and recovery ownership |
| 12. Review publication | Lab → Pipeline; run distribute script | Real Maven artifacts and an external dependency consumer |
| 13. Partner boundary | Partners → Try mock integration / Check real SDK setup | Mock path works; unavailable vendor path fails explicitly |
| 14. Lifecycle recovery | Recreate Activity; cold-launch process after finalized trips | Persisted journal returns; recording does not auto-restart |
| 15. Device session | Controls → permission request → Start device session → Stop | Actual Android location FGS, notification and listener ownership |
| 16. Revoke and delete | Controls → revoke → confirmation | Producer stop, epoch/generation invalidation and local deletion |

## Device-session expectations

The device test verifies that an actual foreground service becomes foreground and stops. It does not claim a physical drive, power result or reliable screen-off sensor coverage. An emulator may supply no provider speed or poor fixes; remaining ARMED while stationary is expected. For a physical-device evaluation, obtain the user's explicit permission, follow applicable platform policies, and use a passenger/operator to run the test rather than interacting while driving.

The host's approximate-location choice can legitimately prevent valid detection under this prototype's accuracy gate. Missing linear/gyro hardware degrades features rather than justifying hidden additional permissions. The sample never asks for battery-optimization exemption.

## Automated capture

`ShowcaseJourneyTest` captures native screenshots while executing synthetic flows. `ColdStartTest` checks persisted fixtures after a separate process launch. `DeviceAndPrivacyTest` verifies FGS lifecycle and deletion. Run them using `scripts/instrumented.sh`, which preserves their intended setup order. The saved PNGs are real emulator output; no image generator or fabricated vendor UI is used.
