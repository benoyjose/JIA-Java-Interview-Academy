# CMS Curriculum Authoring Workflow

This document defines the intended authoring workflow for issue #190 and the contract that UI implementation should satisfy.

## Curriculum navigation
- Organize content by track, domain, topic, lesson, and revision.
- Show draft, in-review, approved, published, and archived states distinctly.
- Provide search and filters for status, difficulty, author, reviewer, and coverage tags.
- Display prerequisite and learning-outcome relationships without requiring users to infer order from slugs.

## Lesson editor
- Edit structured lesson blocks with stable block IDs and explicit block types.
- Preview the learner rendering at mobile and web widths before submission.
- Validate required fields, payload limits, links, code blocks, and question references inline.
- Preserve drafts and warn before navigating away from unsaved changes.
- Show revision history and a diff before publishing a new revision.

## Review and publication
- Author submits a revision for review; reviewer sees provenance, attribution, validation results, and changes from the previous revision.
- Authors cannot approve their own revision when reviewer separation is enabled.
- Publish only an approved, valid revision; publishing creates an immutable revision pointer and records actor/time.
- Failed validation must identify the block/field and never silently discard content.

## Accessibility and auditability
- Keyboard-operable tree, editor, preview, and review actions.
- Screen-reader announcements for validation and save/publish outcomes.
- Audit log records authoring, review, publish, unpublish, and archive events without storing secrets.

## Acceptance evidence
Test curriculum navigation, create/edit/preview/save, validation errors, review, revision diff, publish, and permission denial. Verify published content remains stable while a new draft is edited.