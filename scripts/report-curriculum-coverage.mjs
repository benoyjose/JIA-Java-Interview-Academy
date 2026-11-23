import fs from "node:fs";
import path from "node:path";

const matrixPath = "docs/curriculum/COVERAGE_MATRIX_V1.json";
const matrix = JSON.parse(fs.readFileSync(matrixPath, "utf8"));
if (matrix.schemaVersion !== 1 || !Array.isArray(matrix.domains)) {
  throw new Error("Unsupported or malformed curriculum coverage matrix");
}

const domains = matrix.domains.map((domain) => {
  const mappedDrafts = (domain.drafts ?? []).map((relativePath) => {
    const file = path.join("docs/curriculum", relativePath);
    return { path: relativePath, exists: fs.existsSync(file) };
  });
  return {
    slug: domain.slug,
    targetLessons: domain.targetLessons,
    targetQuestions: domain.targetQuestions,
    draftStatus: domain.status,
    mappedDrafts,
    mappedDraftCount: mappedDrafts.filter((draft) => draft.exists).length,
    missingDrafts: mappedDrafts.filter((draft) => !draft.exists).map((draft) => draft.path),
  };
});

const report = {
  reportVersion: 1,
  matrix: matrixPath,
  generatedAt: new Date().toISOString(),
  interpretation: "Planning targets and mapped draft-file presence only; not authored lesson/question counts, editorial approval, import, or publication inventory.",
  totals: {
    domains: domains.length,
    targetLessons: domains.reduce((sum, domain) => sum + domain.targetLessons, 0),
    targetQuestions: domains.reduce((sum, domain) => sum + domain.targetQuestions, 0),
    mappedDraftFiles: domains.reduce((sum, domain) => sum + domain.mappedDraftCount, 0),
    missingDraftFiles: domains.reduce((sum, domain) => sum + domain.missingDrafts.length, 0),
  },
  domains,
};

console.log(JSON.stringify(report, null, 2));
if (report.totals.missingDraftFiles > 0) process.exitCode = 1;
