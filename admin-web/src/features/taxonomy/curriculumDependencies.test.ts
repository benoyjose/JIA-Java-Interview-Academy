import { describe, expect, it } from "vitest";
import { getCurriculumSequence, validateCurriculumDependencies } from "./curriculumDependencies";
describe("curriculum dependency validation", () => {
 it("finds dangling references and cycles", () => {
  const issues=validateCurriculumDependencies({a:["b","missing"],b:["a"]});
  expect(issues.some(x=>x.reason==="missing")).toBe(true);
  expect(issues.some(x=>x.reason==="cycle")).toBe(true);
 });
 it("accepts an acyclic graph", () => { expect(validateCurriculumDependencies({a:[],b:["a"]})).toEqual([]); });
 it("reports duplicate prerequisite edges", () => { expect(validateCurriculumDependencies({lesson:["pre","pre"]})).toContainEqual({lessonId:"lesson",dependencyId:"pre",reason:"duplicate"}); });
});

describe("deterministic curriculum sequencing", () => {
 it("places prerequisites before dependent lessons", () => {
  expect(getCurriculumSequence({ interview: ["java", "spring"], spring: ["java"], java: [] }))
   .toEqual(["java", "spring", "interview"]);
 });
 it("produces the same sequence regardless of graph insertion order", () => {
  const first = getCurriculumSequence({ c: ["a"], b: [], a: [] });
  const second = getCurriculumSequence({ a: [], b: [], c: ["a"] });
  expect(first).toEqual(second);
 });
 it("returns null for invalid graphs rather than publishing a partial order", () => {
  expect(getCurriculumSequence({ a: ["b"], b: ["a"] })).toBeNull();
  expect(getCurriculumSequence({ a: ["missing"] })).toBeNull();
 });
});
