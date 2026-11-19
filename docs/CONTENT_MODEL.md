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

## Difficulty taxonomy and learner quizzes

Question difficulty is a separate, controlled dimension from lesson difficulty. Use exactly these API/database values and display labels:

| Stored value | Display label | Expected question profile |
|---|---|---|
| BEGINNER | Beginner | Definitions, syntax, core concepts and direct examples |
| NOVICE | Novice | Apply concepts, trace short code, recognize common pitfalls |
| PROFESSIONAL | Professional | Multi-concept problem solving, framework behavior, concurrency and data access |
| EXPERT | Expert | JVM/runtime internals, distributed systems, architecture, performance trade-offs and complex scenarios |

Difficulty is assigned to each reviewed question, not inferred from the learner's self-selected level. Editors should calibrate questions against the profile above. A learner may select any level at any time; the system must not lock higher levels behind a progression gate.

### Quiz selection

A learner can start a quiz by selecting:
- difficulty (required: one of the four levels);
- technology track (optional);
- category (optional, constrained to the selected technology);
- question count (default 10; configurable within server limits);
- mode: PRACTICE, TIMED_MOCK, DAILY_CHALLENGE or WEAK_AREA.

Only PUBLISHED question revisions are eligible. The API returns prompt/options/hint but never the answer key or grading-only fields before submission. A quiz attempt snapshots the exact question revision IDs and order so later edits cannot change historic grading.

### Performance tracking

For each learner, aggregate at least:
- attempts, questions attempted, correct answers and accuracy percentage;
- latest attempt and best score;
- average completion time where available;
- per-difficulty performance across all topics;
- per-topic and per-category performance within each difficulty;
- incorrect/retry queue and recent attempt history.

The learner dashboard should show a four-level comparison and topic breakdown. Do not automatically promote or demote a learner based on one quiz. Recommendations should use a meaningful sample size; label sparse data as "not enough attempts". Keep public lesson and quiz discovery readable without login, but require login to save server-side attempts and cross-device performance history. An anonymous user may preview quiz availability and be prompted to sign in when saving a result.

### Question provenance and review

Question revisions use the same provenance contract as lesson revisions: USER_ORIGINAL, ADMIN_AUTHORED or EXTERNAL_REFERENCE, with attribution for external material. Only APPROVED content may be published. Objective answer keys must be reviewed before publication. Store answer keys separately from the learner-facing prompt payload and never return them in quiz-start responses.

### Attempt grading

Grade deterministic formats on the server. Store the submitted response, correctness, marks and time spent. Keep the question revision snapshot on each attempt. The initial release should support single-select, multi-select and true/false objective grading; output prediction, debugging, SQL and coding formats can be added with their own explicit evaluators. Do not pretend free-form coding or architecture answers are objectively graded until an evaluation rubric or test harness exists.
