import { describe, expect, it } from "vitest";
import { createLessonBlock, parseLessonBlocks, serializeLessonBlocks } from "./LessonContentWorkbench";

describe("lesson content workbench model", () => {
  it("round-trips structured blocks without dropping advanced fields", () => {
    const raw = JSON.stringify({ schemaVersion: 1, blocks: [
      { type: "code", title: "Streams", language: "java", source: "stream()", reviewerNote: "preserve me" },
      { type: "futureBlock", arbitrary: { enabled: true } },
    ]});
    expect(serializeLessonBlocks(parseLessonBlocks(raw))).toBe(JSON.stringify(JSON.parse(raw), null, 2));
  });

  it("rejects unsupported document shapes instead of replacing content", () => {
    expect(() => parseLessonBlocks('[]')).toThrow("Document must be a JSON object");
    expect(() => parseLessonBlocks('{"schemaVersion":2,"blocks":[]}')).toThrow("schemaVersion 1");
    expect(() => parseLessonBlocks('{"schemaVersion":1,"blocks":[null]}')).toThrow("Every block");
  });

  it("creates complete editable templates for each supported block type", () => {
    for (const type of ["markdown","callout","code","list","interviewAnswer","diagram","resourceLink"]) {
      expect(createLessonBlock(type).type).toBe(type);
    }
    expect(() => createLessonBlock("unknown")).toThrow("Unsupported block");
  });
});
