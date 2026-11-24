import { describe, expect, it } from "vitest";
import { parseLessonDraftRecovery, serializeLessonDraftRecovery } from "./lessonDraftRecovery";

const fields = {
  categorySlug: "java", slug: "streams", title: "Java Streams", summary: null,
  difficulty: "PROFESSIONAL" as const, sourceType: "ADMIN_AUTHORED" as const,
  contentJson: '{"schemaVersion":1,"blocks":[]}', changeNote: null,
};

describe("lesson draft recovery", () => {
  it("round-trips valid editor state", () => {
    const now = new Date("2026-11-24T12:00:00Z");
    const recovered = parseLessonDraftRecovery(serializeLessonDraftRecovery(fields, now), now);
    expect(recovered?.fields).toEqual(fields);
  });
  it("rejects malformed, unsupported and expired recovery data", () => {
    const now = new Date("2026-11-24T12:00:00Z");
    expect(parseLessonDraftRecovery("{broken", now)).toBeNull();
    expect(parseLessonDraftRecovery(JSON.stringify({ version: 2, savedAt: now.toISOString(), fields }), now)).toBeNull();
    expect(parseLessonDraftRecovery(serializeLessonDraftRecovery(fields, new Date("2026-10-01T00:00:00Z")), now)).toBeNull();
  });
});
