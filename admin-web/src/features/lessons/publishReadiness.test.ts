import { describe, expect, it } from "vitest";
import { evaluatePublishReadiness } from "./publishReadiness";
describe("publish readiness preflight", () => {
  it("rejects invalid JSON, empty lessons, and malformed code blocks", () => {
    expect(evaluatePublishReadiness("{")[0]?.path).toBe("$");
    expect(evaluatePublishReadiness(JSON.stringify({ schemaVersion: 1, blocks: [] }))).toHaveLength(1);
    expect(evaluatePublishReadiness(JSON.stringify({ schemaVersion: 1, blocks: [{ type: "code", source: "" }] }))).toContainEqual(expect.objectContaining({ message: "Code blocks require both a language and source." }));
  });
  it("requires HTTPS for resource links and allows valid authored content", () => {
    expect(evaluatePublishReadiness(JSON.stringify({ schemaVersion: 1, blocks: [{ type: "resourceLink", url: "http://example.com" }] }))).toContainEqual(expect.objectContaining({ path: "blocks[0].url" }));
    expect(evaluatePublishReadiness(JSON.stringify({ schemaVersion: 1, blocks: [{ type: "markdown", body: "Ready" }] }))).toEqual([]);
  });
});
