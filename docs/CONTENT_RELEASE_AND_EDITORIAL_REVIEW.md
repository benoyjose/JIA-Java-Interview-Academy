# JIA Editorial Review and Initial Content Release

This document governs the first reviewed content tranche for roadmap issue #9. It does not mark any lesson or question as approved or published.

## Release gates

1. **Author** creates a lesson or question using the versioned import format in `CONTENT_IMPORT.md` or `QUESTION_IMPORT.md`.
2. **Automated validation** checks schema version, stable slug, technology/category references, difficulty, required content blocks, answer contract, provenance and attribution.
3. **Duplicate review** compares stable slug and normalized prompt/title. A matching slug is an update candidate, never an automatic overwrite.
4. **Editorial review** verifies technical correctness against the declared JDK/framework version, explanation quality, examples, and interview relevance.
5. **Code-example verification** compiles and runs examples in an isolated test job. The result and toolchain version are retained with the review record.
6. **Approval** is recorded against the exact content revision. Any material edit creates a new revision and invalidates approval for the changed revision.
7. **Publication** is a separate privileged action. Imported and AI-assisted content remains non-public until approved.

## Minimum review rubric

| Dimension | Required evidence |
|---|---|
| Accuracy | Correct behavior, version boundaries and caveats |
| Completeness | Definition, reasoning, example, trade-offs and failure modes |
| Interview value | Appropriate level and realistic follow-up questions |
| Example quality | Reproducible code/output or a clearly defined scenario |
| Provenance | Author/source type, attribution and source reference |
| Assessment quality | Unambiguous prompt, response schema, answer key and explanation |
| Accessibility | Clear language, readable code and no meaning conveyed by color alone |

## Initial track release targets

Targets are planning thresholds, not existing content counts. A track is launch-ready only after editorial sign-off.

| Track | Beginner | Novice | Professional | Expert |
|---|---:|---:|---:|---:|
| Core Java | 20 | 20 | 20 | 10 |
| Advanced Java/JVM | 10 | 15 | 20 | 15 |
| Spring and Spring Boot | 15 | 20 | 20 | 10 |
| JPA and databases | 10 | 15 | 20 | 10 |
| Microservices and messaging | 5 | 10 | 20 | 15 |
| System design, cloud and DevOps | 5 | 10 | 20 | 20 |

## Assessment type readiness

- `SINGLE_SELECT`, `MULTI_SELECT` and `TRUE_FALSE`: deterministic grading using the canonical answer values.
- `OUTPUT_PREDICTION`, `DEBUGGING` and `SQL`: require a declared normalized expected response and documented comparison policy before publication.
- `CODING`: requires an isolated execution grader, resource limits, hidden tests and deterministic timeout behavior. Do not publish as auto-graded until this exists.
- `SCENARIO`: requires a rubric and human/editorial evaluation; do not present exact-match grading as a quality score.

## Import and revision safety

- Preserve original import file checksum and provenance metadata.
- Re-import of the same stable slug must be idempotent or create a reviewable revision.
- Never replace an existing approved/published revision in place.
- Never auto-publish imported or AI-generated content.
- Keep the published revision immutable for historical quiz and lesson references.
- Record reviewer, review timestamp, decision and concise rationale.

## Required evidence for closing issue #9

- Validated sample lesson and question packages for each launch track.
- Automated schema and idempotency tests.
- Duplicate-detection test evidence.
- Editorial sign-off for the agreed release targets.
- Compiled/tested examples with exact toolchain metadata.
- Published question counts greater than zero across agreed launch topics and difficulty levels.
