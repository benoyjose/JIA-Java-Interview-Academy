# Question Import Contract (v1)

This contract defines a create-only import format for reviewed objective question drafts. Import is an authoring operation. It must never publish a question or expose its answer key to learner-facing APIs.

## File format

- UTF-8 JSON document, one object per file.
- `formatVersion` is the integer `1`.
- `questions` contains 1–100 records per batch.
- Each record is keyed by `categorySlug` + `slug`; the slug is stable within its category.
- Difficulty is exactly `BEGINNER`, `NOVICE`, `PROFESSIONAL` or `EXPERT`.
- V1 supports only deterministic objective types already defined by the canonical content model: `SINGLE_SELECT`, `MULTI_SELECT` and `TRUE_FALSE`.
- Provenance uses `USER_ORIGINAL`, `ADMIN_AUTHORED` or `EXTERNAL_REFERENCE`. External references require attribution.
- Prompt, options, answer and explanation follow the canonical question revision payload in [CONTENT_MODEL.md](CONTENT_MODEL.md).

## Example

```json
{
  "formatVersion": 1,
  "questions": [
    {
      "categorySlug": "core-java",
      "slug": "string-equality-operator",
      "questionType": "SINGLE_SELECT",
      "difficulty": "BEGINNER",
      "prompt": {
        "text": "Which operator compares primitive values for equality?"
      },
      "options": [
        {"value": "A", "label": "=="},
        {"value": "B", "label": "equals()"},
        {"value": "C", "label": "compareTo()"}
      ],
      "answer": {"correctValues": ["A"]},
      "explanation": "For primitive operands, == compares their values.",
      "hint": "Distinguish primitive comparison from object equality.",
      "provenance": {
        "sourceType": "ADMIN_AUTHORED",
        "sourceDescription": "JIA editorial team",
        "attribution": null
      },
      "changeNote": "Initial editorial import"
    }
  ]
}
```

## Validation rules

1. Reject unknown format versions and unknown fields at the batch and question-record levels.
2. Reject empty batches and batches larger than 100.
3. Reject duplicate `categorySlug` + `slug` keys within one file before writing.
4. Require non-blank category slug, slug, prompt text, explanation and provenance source description. Enforce field length limits in the JSON Schema.
5. Validate difficulty, question type and provenance enums exactly; do not silently coerce values.
6. `SINGLE_SELECT` requires at least two distinct options and exactly one correct value that exists in the option values.
7. `MULTI_SELECT` requires at least two distinct options and one or more correct values, all of which exist in the option values. Partial credit is not supported in v1.
8. `TRUE_FALSE` requires exactly the `true` and `false` options and exactly one correct value.
9. External references require non-blank attribution. Do not accept external content without attribution.
10. Validate the complete batch before persistence. A validation error must not leave a partially imported batch.
11. Existing question slugs must never be overwritten. A conflict is reported; imports do not silently update or create a new revision.
12. Every accepted record creates a draft question and immutable draft revision with the importing actor, provenance and change note. It does not submit, approve or publish automatically.

## Learner API and answer-key boundary

The importer stores the answer key in the revision's grading payload. Quiz-start and other learner-facing responses must return only the prompt, options and hint; they must never return `answer` / `answer_json`. The answer is used only by the server-side deterministic evaluator after submission. Explanations are returned after grading.

## Re-import and duplicate policy

V1 is deliberately create-only. Re-importing the same `categorySlug` + `slug` is a conflict. A future explicit revision-import operation must identify the target question, preserve the published revision until review and approval, and retain immutable history.

## Deferred question types

Output prediction, debugging, coding, SQL and scenario questions are not accepted by v1. They require their own response formats and deterministic evaluators or explicit reviewed rubrics. Do not import them as objective questions or imply that free-form answers are automatically graded.

## Export compatibility

A future export must retain `formatVersion`, stable slugs, type, difficulty, prompt, options, answer, explanation, hint, provenance, attribution and revision metadata. Database UUIDs and actor IDs are environment-specific and must not be used as portable import keys.
