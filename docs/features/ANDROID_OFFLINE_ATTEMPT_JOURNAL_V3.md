# Android offline attempt journal
Introduces Room-backed attempt events with unique session/prompt identity, pending queue queries, retry counts and sync acknowledgements.
- Local insert is idempotent at the database constraint; pending upload is bounded to 75 per worker pass.
- Worker requires an injected authenticated uploader and marks each event only after successful acknowledgement.
- Failed batches increment retry metadata and use WorkManager retry policy.
- Integration acceptance: register Room database migration/version with existing application database, supply WorkerFactory/uploader, schedule constrained unique work and add API contract tests.
