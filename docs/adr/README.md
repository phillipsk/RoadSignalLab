# Architecture decisions

| Decision | Choice and reason | Tradeoff |
| --- | --- | --- |
| ADR-001 | Pure JVM engine + Android facade + separate host | More boundaries; deterministic reducer tests and reusable acquisition seams |
| ADR-002 | Explicitly armed, user-started capture | Does not demonstrate always-on discovery; avoids pretending policy/permissions are solved |
| ADR-003 | No explicit SDK wake lock | Hardware/screen-off completeness remains a measurement problem |
| ADR-004 | SQLite transaction for trip/outbox/consent epoch | Manual schema/serialization work; atomic transitions are easy to inspect |
| ADR-005 | Keystore/AES-GCM payload columns | Metadata remains plaintext; recovery/key lifecycle still needs product design |
| ADR-006 | Mock CMT port, no invented vendor dependency | Cannot claim real integration; honest substitution seam remains reviewable |
| ADR-007 | Bounded sample queue and retention | Quality loss under overload/caps must be visible and measured |
| ADR-008 | WorkManager for deferrable delivery | No exact execution time; inappropriate for continuous realtime sensor capture |
| ADR-009 | Local Maven repository + independently resolved consumer | Extra build step; verifies actual packaging and transitive metadata |
| ADR-010 | Core ABI + JVM descriptor baselines | Not a replacement for frozen old binaries, Kotlin source checks or behavioral tests |
| ADR-011 | One active collection and one process | Smaller ownership model; not suitable for multi-process/parallel recorders without a broker |
| ADR-012 | No partial-trip checkpoint | Keeps prototype tractable; process death can lose an active segment |

Changing one of these requires an explicit problem statement, alternatives, failure modes, tests, migration implications and rollout evidence. Do not treat a document's proposed production extension as already implemented behavior.
