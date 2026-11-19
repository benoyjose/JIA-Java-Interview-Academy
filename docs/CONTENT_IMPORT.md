# Content Import Contract (v1)

This contract defines the first import format for **lesson drafts**. Import is an authoring operation, not a publishing operation: imported lessons must enter the existing draft/review workflow and must never become learner-visible automatically.

## File format

- UTF-8 JSON document, one object per file.
- `formatVersion` is the integer `1`.
- `lessons` contains 1–100 lesson records per batch.
- Each lesson is keyed by `categorySlug` + `slug`; slugs are stable identifiers within a category.
- `sourceType` is one of `USER_ORIGINAL`, `ADMIN_AUTHORED`, `EXTERNAL_REFERENCE`.
- `sourceDescription` is required. `attribution` is mandatory for `EXTERNAL_REFERENCE`.
- Content uses the canonical lesson schema v1 from [CONTENT_MODEL.md](CONTENT_MODEL.md).
- Difficulty is one of `BEGINNER`, `NOVICE`, `PROFESSIONAL`, `EXPERT`.

## Example

```json
{
  "formatVersion": 1,
  "lessons": [
    {
      "categorySlug": "core-java",
      "slug": "java-immutability",
      "title": "Immutability in Java",
      "summary": "Designing immutable objects and understanding their benefits.",
      "difficulty": "PROFESSIONAL",
      "provenance": {
        "sourceType": "ADMIN_AUTHORED",
        "sourceDescription": "JIA editorial team",
        "attribution": null
      },
      "content": {
        "schemaVersion": 1,
        "blocks": [
          {
            "type": "markdown",
            "key": "overview",
            "title": "Overview",
            "body": "An immutable object cannot be changed after construction."
          },
          {
            "type": "code",
            "key": "implementation",
            "language": "java",
            "javaVersion": "17",
            "title": "Immutable value object",
            "source": "public record Employee(String id, String name) {}",
            "explanation": "A record exposes final component state."
          }
        ]
      },
      "changeNote": "Initial editorial import"
    }
  ]
}
```

## Validation rules

1. Reject unknown `formatVersion` values and unknown top-level fields.
2. Reject empty batches and batches larger than 100.
3. Reject duplicate `categorySlug` + `slug` keys within one file before writing anything.
4. Require non-blank category slug, slug, title and source description; enforce the same field length limits as the authoring API.
5. Validate difficulty and provenance enums exactly; do not silently coerce values.
6. Require non-blank attribution for external references. Do not accept external content without attribution.
7. Validate `content` with the canonical schema v1 validator, including the 250-block limit.
8. Validate the complete batch before persistence. A validation error must not leave a partially imported batch.
9. Existing lesson slugs must never be overwritten by an import. Report conflicts and require an explicit revision/import operation in a later increment.
10. Every accepted record creates a normal draft and immutable revision with the importing actor, provenance and change note. It does not submit, approve or publish automatically.

## Re-import and duplicate policy

The first importer is deliberately create-only. Re-importing the same `categorySlug` + `slug` is a conflict, not an update. This prevents accidental overwrites of hand-edited or user-owned material. A later revision-import operation must require an explicit target lesson ID and preserve the currently published revision until the new revision passes review.

## Export compatibility

A future export must include the same `formatVersion`, stable category/lesson slugs, provenance, attribution, canonical content, and revision metadata. Exported records must be safe to re-import into a separate environment; database UUIDs and actor IDs are not portable identifiers and should not be used as import keys.

## Question import

Question imports are intentionally not enabled in v1. The canonical question revision contract already defines prompt/options/answer/explanation and objective response shapes in [CONTENT_MODEL.md](CONTENT_MODEL.md). A question importer must be added only with deterministic validation for supported evaluator types, provenance and attribution, duplicate detection, immutable revisions, and the same draft/review/publish boundary. Do not import question records as published content.
