export type PublishRiskInput = {
  title: string;
  body: string;
  objectives: string[];
  questionCount: number;
  hasAnswerKey: boolean;
  hasVersion: boolean;
};

export type PublishRiskFinding = {
  code: string;
  severity: "blocker" | "warning";
  message: string;
  remediation: string;
};

export function scorePublishRisk(input: PublishRiskInput): PublishRiskFinding[] {
  const findings: PublishRiskFinding[] = [];
  if (!input.title.trim()) findings.push({ code: "TITLE_MISSING", severity: "blocker", message: "Lesson title is missing.", remediation: "Add a concise learner-facing title." });
  if (input.body.trim().length < 80) findings.push({ code: "CONTENT_THIN", severity: "warning", message: "Lesson content is unusually short.", remediation: "Add explanation, examples, and key takeaways." });
  if (input.objectives.length === 0) findings.push({ code: "OBJECTIVES_MISSING", severity: "warning", message: "No learning objectives are defined.", remediation: "Add measurable objectives." });
  if (input.questionCount === 0) findings.push({ code: "ASSESSMENT_MISSING", severity: "warning", message: "No assessment questions are attached.", remediation: "Add at least one knowledge-check question." });
  if (!input.hasAnswerKey && input.questionCount > 0) findings.push({ code: "ANSWER_KEY_MISSING", severity: "blocker", message: "Questions do not have an answer key.", remediation: "Validate and attach correct answers before publishing." });
  if (!input.hasVersion) findings.push({ code: "VERSION_MISSING", severity: "blocker", message: "Content revision is not identified.", remediation: "Assign a revision identifier for auditability." });
  return findings;
}

export function isPublishBlocked(findings: PublishRiskFinding[]): boolean {
  return findings.some((finding) => finding.severity === "blocker");
}
