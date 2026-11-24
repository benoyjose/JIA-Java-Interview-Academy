# iOS native practice-session flow
- Adds a MainActor observable session state model and SwiftUI question/check/explanation/next flow.
- Invalid answers are ignored; submitted answers are immutable; the final prompt transitions to a completed summary.
- Empty queue and completion states are accessible and have stable accessibility identifiers.
- The runner is intentionally persistence-agnostic: integration must inject learner-scoped prompts, record attempts through the repository, support offline queueing, and connect navigation. No direct networking or account identifiers are stored in UI state.
