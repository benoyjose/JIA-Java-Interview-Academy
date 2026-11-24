import { describe, expect, it } from "vitest";
import { isPublishBlocked, scorePublishRisk } from "./publishRisk";

describe("publish risk scoring", () => {
  it("blocks missing answer keys and revision identifiers", () => {
    const findings = scorePublishRisk({ title: "Concurrency", body: " ".repeat(90), objectives: ["Explain locks"], questionCount: 3, hasAnswerKey: false, hasVersion: false });
    expect(findings.map((finding) => finding.code)).toContain("ANSWER_KEY_MISSING");
    expect(isPublishBlocked(findings)).toBe(true);
  });

  it("returns no findings for a complete lesson package", () => {
    expect(scorePublishRisk({ title: "Concurrency", body: "A detailed lesson ".repeat(8), objectives: ["Explain locks"], questionCount: 5, hasAnswerKey: true, hasVersion: true })).toEqual([]);
  });
});
