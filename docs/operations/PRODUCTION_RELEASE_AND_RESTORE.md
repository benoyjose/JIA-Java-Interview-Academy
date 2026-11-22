# Production release, backup and restore runbook

This document is a release gate and operational procedure template. It does not claim that production infrastructure, backup schedules or restore drills are already configured. Complete the environment-specific fields and record evidence before production launch.

## Release checklist

- [ ] Confirm the release tag points to the reviewed commit and CI is green for Android, Spring Boot, Laravel compatibility, admin web and iOS package.
- [ ] Confirm the selected backend is the single active writer. Do not run Spring and Laravel as dual writers.
- [ ] Verify production secrets are supplied by the deployment secret store, not repository files or build logs.
- [ ] Review database migrations for forward compatibility and documented rollback/roll-forward.
- [ ] Deploy to staging; run health, authentication, public catalogue, quiz submit and content publish smoke tests.
- [ ] Capture release version, commit SHA, migration version, operator and deployment time.
- [ ] Promote the same immutable artifact to production; avoid rebuilding from a different commit.
- [ ] Verify health, error rate, database connectivity and key user journeys after deployment.

## PostgreSQL backup and restore drill

Before enabling production, record the database provider, backup owner, retention policy, encryption, backup frequency and recovery objectives (RPO/RTO) in the environment operations record.

1. Confirm a recent encrypted backup exists and its checksum/metadata are recorded.
2. Restore it into an isolated, access-controlled recovery database; never overwrite production during a drill.
3. Verify schema migration state and representative row counts for users, published lesson revisions, quiz attempts and learner progress.
4. Run application smoke tests against the restored database with production integrations disabled or safely stubbed.
5. Record elapsed restore time, data-loss window, discrepancies and corrective actions.
6. Delete the temporary recovery environment according to the approved data-retention process.

## Rollback and recovery

- Prefer a forward fix when a migration is destructive or has already accepted production writes.
- Revert application deployment to the previous immutable artifact only when its schema compatibility is confirmed.
- Do not blindly roll back database migrations after user data has been written.
- For data corruption, stop writes, preserve logs and backups, involve the database owner, restore to a separate instance, validate, then execute the approved cutover.
- Document the incident timeline, recovery point and follow-up actions.

## Evidence required before production sign-off

| Control | Evidence / owner |
|---|---|
| Automated backup and retention | Provider configuration export / platform owner |
| Restore drill | Dated drill record, restore duration and validation |
| Monitoring and alert routing | Dashboard and test alert |
| TLS, secrets and access policy | Configuration review |
| Staging smoke tests | Release run URL and checklist |
| Rollback decision | Release owner approval |
