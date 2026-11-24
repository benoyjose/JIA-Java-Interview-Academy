import type { LessonDraftRequest } from "../../api/types";

export type LessonDraftRecoveryFields = Pick<LessonDraftRequest,
  "categorySlug" | "slug" | "title" | "summary" | "difficulty" | "sourceType" | "contentJson" | "changeNote">;
export type LessonDraftRecovery = { version: 1; savedAt: string; fields: LessonDraftRecoveryFields };

const MAX_RECOVERY_AGE_MS = 30 * 24 * 60 * 60 * 1000;

export function serializeLessonDraftRecovery(fields: LessonDraftRecoveryFields, now = new Date()): string {
  return JSON.stringify({ version: 1, savedAt: now.toISOString(), fields } satisfies LessonDraftRecovery);
}

export function parseLessonDraftRecovery(raw: string | null, now = new Date()): LessonDraftRecovery | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<LessonDraftRecovery>;
    if (value.version !== 1 || typeof value.savedAt !== "string" || !value.fields) return null;
    const age = now.getTime() - Date.parse(value.savedAt);
    if (!Number.isFinite(age) || age < 0 || age > MAX_RECOVERY_AGE_MS) return null;
    const fields = value.fields as Partial<LessonDraftRecoveryFields>;
    if (typeof fields.categorySlug !== "string" || typeof fields.slug !== "string" ||
        typeof fields.title !== "string" || typeof fields.summary !== "string" && fields.summary !== null ||
        typeof fields.contentJson !== "string" || typeof fields.changeNote !== "string" && fields.changeNote !== null ||
        !["BEGINNER", "NOVICE", "PROFESSIONAL", "EXPERT"].includes(String(fields.difficulty)) ||
        !["USER_ORIGINAL", "ADMIN_AUTHORED", "EXTERNAL_REFERENCE"].includes(String(fields.sourceType))) return null;
    return value as LessonDraftRecovery;
  } catch {
    return null;
  }
}
