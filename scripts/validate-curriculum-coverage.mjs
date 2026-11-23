import fs from "node:fs";
import path from "node:path";

const file = "docs/curriculum/COVERAGE_MATRIX_V1.json";
const matrix = JSON.parse(fs.readFileSync(file, "utf8"));
if (matrix.schemaVersion !== 1 || !Array.isArray(matrix.domains) || matrix.domains.length === 0) {
  throw new Error("Coverage matrix must declare schemaVersion 1 and non-empty domains");
}
const slugs = new Set();
let lessonTargets = 0;
let questionTargets = 0;
for (const domain of matrix.domains) {
  if (!domain.slug || slugs.has(domain.slug)) throw new Error("Missing or duplicate domain slug: " + domain.slug);
  slugs.add(domain.slug);
  if (!Number.isInteger(domain.targetLessons) || domain.targetLessons < 1
      || !Number.isInteger(domain.targetQuestions) || domain.targetQuestions < 1) {
    throw new Error("Invalid lesson/question target in " + domain.slug);
  }
  if (!Array.isArray(domain.prerequisites) || !Array.isArray(domain.drafts)
      || !Array.isArray(domain.difficulty) || domain.status !== "DRAFT") {
    throw new Error("Domain fields/status invalid: " + domain.slug);
  }
  for (const draft of domain.drafts) {
    const resolved = path.join("docs/curriculum", draft);
    if (!fs.existsSync(resolved)) throw new Error("Missing mapped curriculum draft: " + resolved);
  }
  lessonTargets += domain.targetLessons;
  questionTargets += domain.targetQuestions;
}
for (const domain of matrix.domains) {
  for (const prerequisite of domain.prerequisites) {
    if (!slugs.has(prerequisite)) throw new Error("Unknown prerequisite " + prerequisite + " in " + domain.slug);
    if (prerequisite === domain.slug) throw new Error("Self prerequisite: " + domain.slug);
  }
}
// Reject prerequisite cycles using DFS so study order is unambiguous.
const visiting = new Set(), visited = new Set();
function visit(slug) {
  if (visiting.has(slug)) throw new Error("Curriculum prerequisite cycle at " + slug);
  if (visited.has(slug)) return;
  visiting.add(slug);
  for (const p of matrix.domains.find(d => d.slug === slug).prerequisites) visit(p);
  visiting.delete(slug); visited.add(slug);
}
for (const domain of matrix.domains) visit(domain.slug);
console.log("Validated " + matrix.domains.length + " curriculum domains; target " + lessonTargets
  + " lessons / " + questionTargets + " questions. Draft status only; not publication inventory.");
