# Engineering guide

Start with [the setup and walkthrough guide](START_HERE.md), then use these documents to explore the SDK implementation.

| Document | Purpose |
| --- | --- |
| [Implementation status](IMPLEMENTATION_STATUS.md) | Executable behavior, mocked boundaries, production gaps |
| [Research and source ledger](RESEARCH.md) | Verified CMT material, Android platform sources, industry comparisons |
| [Architecture](ARCHITECTURE.md) | State ownership, concurrency, timing, capture, persistence |
| [SDK API guide](SDK_API.md) | Host integration, Kotlin/Java contracts, error and cancellation behavior |
| [Power and reliability](POWER_AND_RELIABILITY.md) | Wake locks, batching, Doze, measurement and rollout design |
| [Data, privacy and threats](DATA_AND_PRIVACY.md) | Consent, encryption scope, retention, deletion and host responsibilities |
| [Distribution](DISTRIBUTION.md) | Maven publication, dependency verification, API compatibility and supply chain |
| [Release playbook](RELEASE_PLAYBOOK.md) | Branches, releases, customer adoption, activation and rollback |
| [User journeys](USER_JOURNEYS.md) | A reproducible demonstration with expected results |
| [Verification evidence](VERIFICATION.md) | Actual checks and known limits; updated after execution |
| [Architecture decisions](adr/README.md) | Why this project made its particular tradeoffs |

Markdown is the canonical report because GitHub renders it directly. An HTML file in a GitHub repository is source text unless served through Pages; the optional local HTML reading copy is a portable companion, not a hosted website.

A [portable HTML reading copy](engineering-report.html) is included for download and local viewing. GitHub displays HTML source; use the Markdown documents for native web rendering.
