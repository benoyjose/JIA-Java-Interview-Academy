# JIA Content Model

## Design principles

Content is versioned, provenance-aware and technology-neutral at the storage boundary. The backend stores canonical records; Android consumes published content through a versioned manifest and caches it locally.

## Core entities

| Entity | Purpose | Key fields |
|---|---|---|
| Technology | Top-level learning track | id, slug, name, description, sort_order, status |
| Category | Group within a technology | id, technology_id, parent_id, slug, name, sort_order |
| Lesson | Main learning unit | id, category_id, slug, title, summary, difficulty, status |
| LessonRevision | Immutable lesson revision | id, lesson_id, revision, content_json, provenance, review_status |
| Question | Reusable question bank item | id, category_id, lesson_id, type, difficulty, prompt |
| QuestionRevision | Versioned question and answer | id, question_id, revision, answer_json, explanation |
| Quiz | Assessment definition | id, title, mode, settings_json, status |
| QuizQuestion | Ordered quiz membership | quiz_id, question_id, position, marks |
| UserProgress | Learner completion state | user_id, lesson_id, status, completed_at |
| QuizAttempt | Assessment attempt | id, user_id, quiz_id, score, started_at, submitted_at |
| AttemptAnswer | Learner response | attempt_id, question_id, response_json, is_correct, time_ms |
| Bookmark | Saved learning item | user_id, item_type, item_id |
| ContentRelease | Published content snapshot | id, version, manifest, published_at |
| AppRelease | Android app distribution metadata | version_name, version_code, min_supported_version, artifact_uri, checksum |
| AuditEvent | Admin activity history | actor_id, action, entity_type, entity_id, occurred_at |

Use UUID primary keys unless a deployment-specific reason requires another strategy. Add created_at, updated_at and status fields where appropriate. Use soft deletion for user-facing content that may be referenced by historical attempts.

## Lesson content JSON shape

```json
{
  "schemaVersion": 1,
  "blocks": [
    { "type": "markdown", "key": "overview", "title": "Overview", "body": "..." },
    { "type": "markdown", "key": "internals", "title": "How it works", "body": "..." },
    {
      "type": "code",
      "key": "implementation",
      "language": "java",
      "title": "Example",
      "source": "...",
      "javaVersion": "17",
      "explanation": "..."
    },
    { "type": "callout", "key": "pitfalls", "tone": "warning", "body": "..." },
    {
      "type": "interviewAnswer",
      "key": "short-answer",
      "mode": "concise",
      "body": ["Point one", "Point two", "Point three"],
      "expandedExplanation": "..."
    }
  ]
}
```

The content renderer must validate supported block types and schema versions. Unknown blocks should be safely skipped with diagnostics rather than crashing the lesson screen.

## Question shape

A question revision should contain:
- prompt and optional code/media attachments;
- question type and difficulty;
- answer payload (not exposed to clients before submission when assessment rules require secrecy);
- explanation and optional references;
- tags, technology/category/lesson relationships;
- Java/runtime version applicability;
- provenance, review status and revision metadata.

For multiple-select questions, the answer payload must support a set of correct options. For code/output questions, support exact, normalized or test-based evaluation as configured. Do not use language-model grading as the sole authority for objective answers.

## Provenance contract

Required fields on content revisions:
- source_type: USER_ORIGINAL, ADMIN_AUTHORED or EXTERNAL_REFERENCE.
- source_description: original filename, URL or admin note where available.
- attribution: required for external material.
- review_status: DRAFT, IN_REVIEW, APPROVED or REJECTED.
- reviewed_by and reviewed_at when reviewed.

A user-original revision must remain retrievable even after later revisions. New supplements should be separate blocks or revisions and must not erase the original.

## Publishing and synchronization

1. Editors save drafts.
2. Reviewer previews content and approves it.
3. Publisher creates a monotonically increasing content release.
4. Release manifest lists changed item IDs, revisions, checksums and tombstones.
5. Android checks the manifest and downloads changed content only.
6. Android validates checksums before replacing cached content.
7. Last-known-good content remains available if a sync fails.

Content releases and APK releases are independent version streams.
