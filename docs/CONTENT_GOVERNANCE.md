# JIA Content Governance and Admin Workflow

## Content lifecycle
DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED

A reviewer may return an item to the editor with CHANGES_REQUESTED or reject it. A published lesson is never overwritten in place: edits create a new revision, and publication promotes an approved revision. Unpublishing/archiving preserves history and audit events.

## Roles
- LEARNER: read published material, take assessments and manage own progress.
- EDITOR: create/update drafts, attach sources, submit for review.
- REVIEWER: review submissions, request changes, approve or reject. Must not silently edit the author's material.
- ADMIN: manage users/roles, academy settings, publishing and releases.
- PLATFORM_ADMIN: reserved future global operator role; not self-assignable.

Role grants must be operator-controlled and audited. Academy-specific editor/reviewer grants should be supported as the platform expands. Public registration always creates LEARNER.

## Provenance
Each content item records USER_ORIGINAL, ADMIN_AUTHORED or EXTERNAL_REFERENCE. External material requires source attribution. User-originated text and examples are preserved; supplements are separate content blocks. AI-generated drafts must be explicitly labelled and reviewed before publication.

## Lesson structure
A structured lesson document can contain:
- overview, learning objectives and prerequisites
- concise interview answer
- detailed explanation and internal working
- code examples with language/JDK/framework version and test status
- diagrams/media references
- practical use cases, trade-offs and common mistakes
- troubleshooting and follow-up questions
- quiz/question references
- sources and attribution

The lesson document schema is versioned. Content is delivered independently of mobile binary releases.

## Admin operations
1. Select academy context (JIA at launch).
2. Create/edit a track, category or lesson draft.
3. Save revision and change note.
4. Validate required fields, references, code metadata and provenance.
5. Submit to review.
6. Reviewer approves, requests changes or rejects with comment.
7. Admin publishes an approved revision.
8. Generate a content release manifest and checksums.
9. Clients sync only changed content; offline clients verify checksums.
10. Audit every state transition and privileged action.

## Safety and integrity controls
- Server-side permission checks for every mutation.
- Optimistic locking/revision checks to prevent lost updates.
- No hard deletion of published content; archive instead.
- Audit actor, academy, entity, action, timestamp and safe metadata.
- Never place secrets or unnecessary personal data in audit metadata.
- Preview must use learner rendering rules and must not expose drafts publicly.
- Cross-academy IDs must be rejected.
- Import/export must preserve provenance, revision and attribution.

## Current implementation status

The Spring Boot primary backend now has an initial content-authoring API increment:
- Create and update unpublished lesson drafts, recording immutable revision snapshots and change notes.
- Create a new draft revision for an already-published lesson without changing the learner-visible revision.
- Submit a draft revision for review; the review task is bound to the exact revision.
- List pending reviews; reviewers can approve, request changes or reject.
- Prevent authors from reviewing their own submissions.
- Promote an approved revision atomically; catalogue reads use the published revision pointer, not mutable draft data.
- Existing published lessons are backfilled to their current immutable revision during migration.
- Basic role/permission checks use the existing EDITOR, REVIEWER and ADMIN roles.
- Essential lifecycle actions are recorded in the existing audit_events table.
- Authorized editors and reviewers can preview the revision selected for authoring without changing public catalogue visibility; cross-academy preview requests are hidden as not found.
- Academy-scoped media metadata and direct-to-object-storage uploads use short-lived S3-compatible presigned PUT URLs. The API verifies uploaded object size and content type before marking metadata READY and records an audit event.

Current MVP boundary: published lessons can receive a separate draft revision and promote it after approval. Authorized editor/reviewer preview is available through the admin API and uses the same structured content payload as learner reading, while public catalogue routes continue to expose only published revisions. Changing a published lesson's slug or category is intentionally rejected in this workflow; taxonomy moves need their own reviewed operation. Media upload, React admin portal and Laravel implementation remain future increments. Do not describe the full CMS as complete.
