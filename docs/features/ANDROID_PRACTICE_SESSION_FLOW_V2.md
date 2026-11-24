# Android native practice-session flow
This slice introduces a deterministic session reducer and Compose question/check/next/completion UI:
- de-duplicates prompt IDs and guards invalid answer indexes;
- locks answer changes after submission, exposes explanation, and computes score locally;
- completion and empty-session states are explicit;
- controller state is exposed as StateFlow for lifecycle-aware collection.
Repository integration must fetch the learner-scoped queue, save answer events offline-first, synchronize retries, and navigate through the existing app graph. No account token or learner identifier is embedded in the reducer.
