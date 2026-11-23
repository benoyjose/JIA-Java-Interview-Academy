import { describe, expect, it } from "vitest";
import { buildStudyFocus } from "./studyPlanInsights";
import type { AcademyAnalyticsSummary } from "../../api/analytics";
const summary: AcademyAnalyticsSummary = {
  academyId: "jia", academyCode: "JIA", windowDays: 30, totalLearners: 50, activeLearners: 20,
  lessonsCompleted: 90, quizAttempts: 110, averageScorePercent: 70, scoredAttempts: 100,
  topTopics: [
    { topicSlug: "streams", topicName: "Streams", attempts: 20, averageScorePercent: 42 },
    { topicSlug: "collections", topicName: "Collections", attempts: 30, averageScorePercent: 92 },
    { topicSlug: "threads", topicName: "Threads", attempts: 10, averageScorePercent: null },
  ],
};
describe("buildStudyFocus", () => {
  it("orders weak and unmeasured topics and excludes mastered topics", () => {
    expect(buildStudyFocus(summary).map(item => item.topicSlug)).toEqual(["threads", "streams"]);
  });
  it("caps recommendations to five", () => {
    const topics = Array.from({ length: 9 }, (_, i) => ({ topicSlug: String(i), topicName: String(i), attempts: 1, averageScorePercent: 20 + i }));
    expect(buildStudyFocus({ ...summary, topTopics: topics })).toHaveLength(5);
  });
});
