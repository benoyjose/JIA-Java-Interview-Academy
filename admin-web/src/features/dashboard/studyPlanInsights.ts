import type { AcademyAnalyticsSummary } from "../../api/analytics";
export type StudyFocus = { topicSlug: string; topicName: string; score: number | null; action: string };
export function buildStudyFocus(summary: AcademyAnalyticsSummary): StudyFocus[] {
  return summary.topTopics.filter(topic => topic.averageScorePercent === null || topic.averageScorePercent < 80)
    .map(topic => ({ topicSlug: topic.topicSlug, topicName: topic.topicName, score: topic.averageScorePercent,
      action: topic.averageScorePercent === null ? "Collect more scored attempts"
        : topic.averageScorePercent < 50 ? "Prioritize guided revision and beginner-level practice"
        : "Schedule a focused practice set and re-check mastery" }))
    .sort((a, b) => (a.score ?? -1) - (b.score ?? -1)).slice(0, 5);
}
