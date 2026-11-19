export type User = { id: string; name: string; email: string | null; roles: string[] };
export type TokenResponse = { accessToken: string; tokenType: "Bearer"; expiresAt: string; user: User };
export type LessonStatus = "DRAFT" | "IN_REVIEW" | "APPROVED" | "PUBLISHED" | "ARCHIVED";
export type AdminLesson = {
  id: string; slug: string; title: string; categorySlug: string; status: LessonStatus;
  contentRevision: number; publishedRevision: number | null; draftRevision: number | null; updatedAt: string;
};
export type LessonDetail = {
  id: string; slug: string; title: string; summary: string | null; categorySlug: string;
  difficulty: "BEGINNER" | "NOVICE" | "PROFESSIONAL" | "EXPERT";
  sourceType: "USER_ORIGINAL" | "ADMIN_AUTHORED" | "EXTERNAL_REFERENCE"; contentJson: string;
  status: LessonStatus; revisionNumber: number; publishedRevision: number | null;
  editingDraft: boolean; changeNote: string | null; updatedAt: string;
};
export type LessonDraftRequest = {
  categorySlug: string; slug: string; title: string; summary: string | null;
  difficulty: LessonDetail["difficulty"]; sourceType: LessonDetail["sourceType"];
  contentJson: string; changeNote: string | null;
};
export type LessonDraftResponse = { id: string; slug: string; title: string; status: string; revisionNumber: number };
export type ReviewQueueItem = {
  taskId: string; lessonId: string; revisionId: string; slug: string; title: string;
  submittedBy: string; status: "PENDING"; submittedAt: string;
};
export type ReviewDetail = {
  taskId: string; lessonId: string; revisionId: string; slug: string; title: string;
  summary: string | null; difficulty: string; sourceType: string; contentJson: string;
  revisionNumber: number; changeNote: string | null; submittedBy: string;
  submittedAt: string; lessonStatus: LessonStatus;
};