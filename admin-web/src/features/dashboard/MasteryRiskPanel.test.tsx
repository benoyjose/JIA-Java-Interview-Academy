import { describe, expect, it } from "vitest";
import type { AcademyAnalyticsSummary } from "../../api/analytics";
import { masterySignalCounts } from "./MasteryRiskPanel";
describe("masterySignalCounts", () => {
 it("classifies aggregate accuracy while excluding empty and unscored topics", () => {
  const summary = { topTopics: [
   {topicSlug:"weak",topicName:"Weak",attempts:4,averageScorePercent:50},
   {topicSlug:"mid",topicName:"Mid",attempts:3,averageScorePercent:72},
   {topicSlug:"strong",topicName:"Strong",attempts:8,averageScorePercent:91},
   {topicSlug:"none",topicName:"None",attempts:0,averageScorePercent:99},
   {topicSlug:"null",topicName:"Null",attempts:2,averageScorePercent:null}
  ] } as AcademyAnalyticsSummary;
  expect(masterySignalCounts(summary)).toEqual({developing:1,practicing:1,mastered:1});
 });
});