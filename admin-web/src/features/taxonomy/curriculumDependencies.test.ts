import { describe, expect, it } from "vitest";
import { validateCurriculumDependencies } from "./curriculumDependencies";
describe("curriculum dependency validation", () => {
 it("finds dangling references and cycles", () => {
  const issues=validateCurriculumDependencies({a:["b","missing"],b:["a"]});
  expect(issues.some(x=>x.reason==="missing")).toBe(true);
  expect(issues.some(x=>x.reason==="cycle")).toBe(true);
 });
 it("accepts an acyclic graph", () => { expect(validateCurriculumDependencies({a:[],b:["a"]})).toEqual([]); });
});
