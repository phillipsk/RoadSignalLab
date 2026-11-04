# RoadSignal Lab

Use [AGENTS.md](AGENTS.md) as the canonical agent instructions. Then read [implementation status](docs/IMPLEMENTATION_STATUS.md), [architecture](docs/ARCHITECTURE.md), [SDK API guide](docs/SDK_API.md), and [distribution](docs/DISTRIBUTION.md).

Keep CMT facts separate from this original prototype. No vendor API is implemented. Do not remove consent gates or disguise replay/model output as device measurements. Preserve bounded/off-main processing and epoch/generation fences. Run the documented verification and published-consumer checks before release. API baselines require explicit compatibility review, not automatic acceptance.
