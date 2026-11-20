import type { AdminLesson, LessonDetail, LessonDraftRequest, LessonDraftResponse, PageResult, ReviewDetail, ReviewQueueItem, TokenResponse, User, ContentRelease, TechnologyItem, CategoryItem, CategoryRequest } from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "/api/v1").replace(/\/$/, "");
const TOKEN_KEY = "jia.admin.access-token";

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}
export function getStoredToken(): string | null { return window.sessionStorage.getItem(TOKEN_KEY); }
export function storeToken(token: string): void { window.sessionStorage.setItem(TOKEN_KEY, token); }
export function clearStoredToken(): void { window.sessionStorage.removeItem(TOKEN_KEY); }

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body !== undefined) headers.set("Content-Type", "application/json");
  const token = getStoredToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers, credentials: "same-origin" });
  } catch {
    throw new ApiError("Unable to reach the JIA API. Check your connection and try again.", 0);
  }
  if (response.status === 401 && getStoredToken()) {
    clearStoredToken();
    window.dispatchEvent(new Event("jia:session-expired"));
  }
  if (!response.ok) {
    let payload: { code?: string; message?: string } = {};
    try { payload = (await response.json()) as typeof payload; } catch { /* safe fallback */ }
    throw new ApiError(payload.message || (response.status === 403
      ? "Your account does not have permission for this action."
      : `The request failed (HTTP ${response.status}).`), response.status, payload.code);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  login: (email: string, password: string) => request<TokenResponse>("/auth/login", {
    method: "POST", body: JSON.stringify({ email, password, deviceName: "JIA Admin Web" }),
  }),
  me: () => request<User>("/auth/me"),
  logout: () => request<void>("/auth/logout", { method: "POST" }),
  lessons: (params: { status?: string; q?: string; page: number; size: number }, signal?: AbortSignal) => {
    const query = new URLSearchParams({ page: String(params.page), size: String(params.size) });
    if (params.status) query.set("status", params.status);
    if (params.q) query.set("q", params.q);
    return request<PageResult<AdminLesson>>(`/admin/lessons?${query.toString()}`, { signal });
  },
  lesson: (id: string, signal?: AbortSignal) => request<LessonDetail>(`/admin/lessons/${encodeURIComponent(id)}`, { signal }),
  createLesson: (body: LessonDraftRequest) => request<LessonDraftResponse>("/admin/lessons", { method: "POST", body: JSON.stringify(body) }),
  updateLesson: (id: string, body: LessonDraftRequest) => request<LessonDraftResponse>(`/admin/lessons/${encodeURIComponent(id)}`, { method: "PUT", body: JSON.stringify(body) }),
  submitLesson: (id: string) => request<{ taskId: string }>(`/admin/lessons/${encodeURIComponent(id)}/submit`, { method: "POST" }),
  publishLesson: (id: string) => request<{ status: string }>(`/admin/lessons/${encodeURIComponent(id)}/publish`, { method: "POST" }),
  createContentRelease: () => request<ContentRelease>("/admin/content-releases", { method: "POST" }),
  taxonomyTechnologies: () => request<TechnologyItem[]>("/admin/taxonomy/technologies"),
  taxonomyCategories: (technology?: string) => request<CategoryItem[]>(`/admin/taxonomy/categories${technology ? `?technology=${encodeURIComponent(technology)}` : ""}`),
  createCategory: (body: CategoryRequest) => request<CategoryItem>("/admin/taxonomy/categories", { method: "POST", body: JSON.stringify(body) }),
  updateCategory: (id: string, body: CategoryRequest) => request<CategoryItem>(`/admin/taxonomy/categories/${encodeURIComponent(id)}`, { method: "PUT", body: JSON.stringify(body) }),
  archiveCategory: (id: string) => request<void>(`/admin/taxonomy/categories/${encodeURIComponent(id)}`, { method: "DELETE" }),
  reviews: (params: { q?: string; page: number; size: number }, signal?: AbortSignal) => {
    const query = new URLSearchParams({ page: String(params.page), size: String(params.size) });
    if (params.q) query.set("q", params.q);
    return request<PageResult<ReviewQueueItem>>(`/admin/reviews?${query.toString()}`, { signal });
  },
  review: (taskId: string, signal?: AbortSignal) => request<ReviewDetail>(`/admin/reviews/${encodeURIComponent(taskId)}`, { signal }),
  decideReview: (taskId: string, decision: "APPROVE" | "REQUEST_CHANGES" | "REJECT", comment: string) =>
    request<{ status: string }>(`/admin/reviews/${encodeURIComponent(taskId)}/decision`, {
      method: "POST", body: JSON.stringify({ decision, comment: comment || null }),
    }),
};

export function validateLessonContent(raw: string): string | null {
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return "Lesson content must be a JSON object.";
    const document = parsed as { schemaVersion?: unknown; blocks?: unknown };
    if (typeof document.schemaVersion !== "number" || !Array.isArray(document.blocks))
      return "Content must include a numeric schemaVersion and a blocks array.";
    return null;
  } catch { return "Lesson content is not valid JSON."; }
}