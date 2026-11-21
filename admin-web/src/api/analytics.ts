export type AcademyAnalyticsSummary = {
  academyId: string;
  academyCode: string;
  windowDays: 7 | 30 | 90 | number;
  totalLearners: number;
  activeLearners: number;
  lessonsCompleted: number;
  quizAttempts: number;
  averageScorePercent: number | null;
  scoredAttempts: number;
  topTopics: Array<{
    topicSlug: string;
    topicName: string;
    attempts: number;
    averageScorePercent: number | null;
  }>;
};
