export type User = { id: string; name: string; email: string | null; roles: string[] };
export type TokenResponse = { accessToken: string; refreshToken: string; tokenType: "Bearer"; expiresAt: string; user: User };
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
export type PageResult<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type ContentRelease = {
  version: number;
  createdAt: string;
  items: Array<{ id: string; slug: string; title: string; contentRevision: number; checksum: string }>;
};

export type TechnologyItem = { id: string; slug: string; name: string; description: string | null; sortOrder: number; status: string };
export type CategoryItem = { id: string; technologyId: string; technologySlug: string; parentId: string | null; parentSlug: string | null; slug: string; name: string; sortOrder: number; status: string; lessonCount: number; questionCount: number };
export type CategoryRequest = { technologySlug: string; parentId: string | null; slug: string; name: string; sortOrder: number };

export type RevisionHistoryItem = {
  revisionId: string; revisionNumber: number; title: string; summary: string | null;
  difficulty: string; sourceType: string; changedBy: string | null; changedByName: string;
  changeNote: string | null; createdAt: string; published: boolean; currentDraft: boolean;
  reviewStatus: string | null; reviewComment: string | null; reviewedAt: string | null; submittedBy: string | null;
};

export type UserAdminItem = { id: string; name: string; email: string | null; status: string; roles: string[]; createdAt: string };
export type UserAdminUpdate = { status: string; roles: string[] };
export type QuestionAdminItem = { id: string; slug: string; questionType: string; difficulty: string; status: string; revisionNumber: number; categoryName: string; categorySlug: string; technologyName: string; promptText: string; updatedAt: string };

export type TechnologyRequest = { slug: string; name: string; description: string | null; sortOrder: number };
