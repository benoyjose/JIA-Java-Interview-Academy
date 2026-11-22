# Staging deployment and smoke validation

The repository now contains immutable container build definitions and a manual, tag-based staging workflow. **A real staging deployment is not complete until the host, GitHub environment and secrets below are provisioned and the workflow is run successfully.**

## One-time staging host setup

1. Provision a supported Linux host with Docker Engine and Docker Compose v2.
2. Create a deployment directory, for example `/srv/jia-staging`.
3. Copy `infra/staging.env.example` to `/srv/jia-staging/.env`; replace every placeholder with generated secrets. Do not commit the populated file.
4. Ensure the SSH deployment user can run Docker Compose and has write access to the deployment directory.
5. Configure the host firewall to expose only the TLS reverse proxy. Compose binds API and admin ports to loopback.
6. Configure a TLS reverse proxy to the admin portal at `127.0.0.1:8081`; the Nginx container proxies `/api/` to the API service.
7. Configure automated PostgreSQL backups to encrypted off-host storage and verify a restore before accepting staging as production-ready.

## GitHub environment secrets

Create a protected GitHub Environment named `staging` and configure:

- `STAGING_HOST` — host or IP
- `STAGING_USER` — restricted SSH deployment user
- `STAGING_SSH_KEY` — private key for that user
- `STAGING_KNOWN_HOSTS` — pinned host-key line(s), collected and verified out of band
- `STAGING_DEPLOY_PATH` — absolute deployment directory
- `STAGING_GHCR_USERNAME` — package-read identity
- `STAGING_GHCR_TOKEN` — least-privilege token with read access to the two GHCR packages

The workflow uses the repository's `GITHUB_TOKEN` to publish immutable images. The staging host uses the separate read-only GHCR token.

## Deploy a release

1. Tag a reviewed commit with an immutable semantic version, e.g. `v1.2.3`.
2. Open Actions → **Container build and staging deploy** → Run workflow.
3. Enter the tag exactly; the workflow validates the tag and builds/pushes the API and admin images.
4. Approve the protected `staging` environment if required.
5. The deploy job copies the compose file, pulls the exact tag, starts services with health gates and runs smoke tests:
   - API readiness at `/actuator/health/readiness`
   - Admin Nginx health endpoint
   - API catalogue response from the admin container's network

## Rollback

Redeploy the last known-good immutable tag through the same workflow. Do not overwrite or reuse release tags. Check API and admin health, then review application logs and database migration compatibility before deciding whether a database restore is required.

## Backup and restore drill

A staging restore drill must record:
- backup timestamp, database version and checksum
- isolated restore target and restore command
- schema/Flyway status after restore
- smoke-test outcome and data-integrity checks
- measured RTO/RPO and approval

The workflow does not automatically restore or delete the staging database. A restore is an explicit operational action.

## Current limitation

No staging host or environment secrets are available from this repository workflow. The CI container builds validate Dockerfiles; staging deployment, external TLS, backup/restore and production-like smoke evidence remain blocked until the environment owner provisions and runs the protected workflow.
