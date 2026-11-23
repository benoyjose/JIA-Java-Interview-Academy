import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { SpacedReviewPolicyPanel, isValidSpacedReviewPolicy } from "./SpacedReviewPolicyPanel";
describe("spaced review policy",()=>{
 it("enforces ordered intervals and supported ease bounds",()=>{
  expect(isValidSpacedReviewPolicy({firstIntervalDays:1,secondIntervalDays:6,maximumIntervalDays:365,minimumEase:1.3})).toBe(true);
  expect(isValidSpacedReviewPolicy({firstIntervalDays:8,secondIntervalDays:6,maximumIntervalDays:365,minimumEase:1.3})).toBe(false);
  expect(isValidSpacedReviewPolicy({firstIntervalDays:1,secondIntervalDays:6,maximumIntervalDays:365,minimumEase:1.1})).toBe(false);
 });
 it("renders labelled settings, guarded save and explicit local-storage scope",()=>{
  const html=renderToStaticMarkup(<SpacedReviewPolicyPanel/>);
  expect(html).toContain('aria-labelledby="review-policy-title"');
  expect(html).toContain('aria-label');expect(html).toContain("Save policy");
  expect(html).toContain("browser-local");
 });
});
