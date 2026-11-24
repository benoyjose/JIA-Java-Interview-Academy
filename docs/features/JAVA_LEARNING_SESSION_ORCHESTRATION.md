# Java learning-session orchestration
Implements the service-level composition boundary on top of the adaptive scheduler:
1. validate bounded daily and per-session goals;
2. rank due reviews before new learning, with an explicit review budget;
3. rank topic candidates by learner weakness and deterministic ID tie-break;
4. return typed immutable session items with rationale and total budget.
The orchestration service accepts caller-supplied evidence only. Production API wiring must resolve the authenticated learner before loading evidence, persist the plan transactionally, and emit session-start/progress events. Tests cover budget and ranking invariants.
