# iOS offline attempt journal
Adds a Codable attempt-event contract and actor-isolated atomic JSON journal.
- Unique session/prompt identity prevents duplicate local submissions.
- Atomic file replacement protects against partial writes; elapsed duration is normalized non-negative.
- Acknowledgement removes only explicitly accepted event IDs.
- Integration acceptance: store under app-support URL, encrypt sensitive content if policy requires, add retention/cleanup, connect authenticated upload retry and tests for process restart.
