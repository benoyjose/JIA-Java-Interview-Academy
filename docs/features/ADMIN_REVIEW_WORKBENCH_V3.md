# Admin review workbench
Adds a reusable, deterministic review filter/ranking model and screen with pending, returned and reviewer summaries.
- Search is limited to title and record ID; status and sort are explicit.
- UI only displays server-provided rows and delegates opening a record to the parent.
- Existing backend permissions remain authoritative; this feature adds no privilege-bearing endpoint.
- Tests cover filtering, deterministic ordering and summary aggregation. Next acceptance: integrate into the current review route and add role-specific browser tests.
