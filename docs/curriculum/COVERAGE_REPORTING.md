# Curriculum coverage reporting

Run from the repository root:

```bash
node scripts/report-curriculum-coverage.mjs
```

The command prints a JSON report for the versioned coverage matrix: domain targets, mapped draft-file presence, and missing mapped files. It exits non-zero if a matrix entry points to a missing draft.

**Interpretation boundary:** this report does not count authored lesson/question records, reviewed material, imports, or published content. The matrix remains a roadmap; release inventory must be derived from approved and published revision records.
