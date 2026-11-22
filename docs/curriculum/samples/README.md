# Reviewed curriculum import samples

`samples/java-foundations-import-v1.json` is a small editorial starter bundle for validating the import workflow. It contains two original lessons and two questions across Beginner/Novice difficulty.

- Content is marked `ADMIN_AUTHORED`; it is not automatically published.
- Review it in the admin portal before importing into a production academy.
- The bundle is intended for the Laravel `jia:validate-curriculum` offline validator and for splitting into the existing lesson/question batch import payloads.
- It is deliberately a starter sample, not the complete launch curriculum or a coverage claim.

Importing into a live academy requires a category with slug `java-language-fundamentals` and normal editorial approval.


`java-foundations-question-bank-v1.json` is a 100-question original Java foundations seed bank covering Beginner through Expert and SINGLE_SELECT, MULTI_SELECT and TRUE_FALSE. It conforms to `question-import-v1` and is intended for the Spring Boot admin question import endpoint described in [QUESTION_IMPORT.md](../../QUESTION_IMPORT.md).

**Editorial status:** seed content is authored but not editorially approved. Every record imports as `DRAFT`; review explanations and version-sensitive details before approval. The file is a broad starter bank, not a curriculum coverage completion claim. The expanded set includes a range of Java syntax, object-oriented programming, collections, streams, concurrency, JVM/runtime, and modern language/API topics. It contains no external references or copied interview questions.
