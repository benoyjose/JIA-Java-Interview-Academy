import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, getStoredToken, storeSession, storeToken, validateLessonContent } from "./client";

const sessionValues = new Map<string, string>();
beforeEach(() => {
  sessionValues.clear();
  vi.stubGlobal("window", { sessionStorage: {
    getItem: (key: string) => sessionValues.get(key) ?? null,
    setItem: (key: string, value: string) => { sessionValues.set(key, value); },
    removeItem: (key: string) => { sessionValues.delete(key); },
  }, dispatchEvent: vi.fn() });
  it("enforces the server lesson document schema and block limits", () => {
    expect(validateLessonContent(JSON.stringify({ schemaVersion: 1, blocks: [{ type: "code", source: "x", language: "java" }] }))).toBeNull();
    expect(validateLessonContent(JSON.stringify({ schemaVersion: 2, blocks: [] }))).toContain("schemaVersion 1");
    expect(validateLessonContent(JSON.stringify({ schemaVersion: 1, blocks: ["invalid"] }))).toContain("must be an object");
    expect(validateLessonContent(JSON.stringify({ schemaVersion: 1, blocks: Array.from({ length: 251 }, () => ({ type: "note" })) }))).toContain("250 blocks");
    expect(validateLessonContent(JSON.stringify({ schemaVersion: 1, blocks: [{ type: "interviewAnswer", body: [] }] }))).toContain("answer points");
  });

});
afterEach(() => { vi.unstubAllGlobals(); });

describe("admin API client", () => {
  it("attaches the tab-scoped bearer token", async () => {
    storeToken("test-token");
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify({ id: "u-1", name: "Admin", email: "admin@example.test", roles: ["ADMIN"] }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    vi.stubGlobal("fetch", fetchMock);
    const user = await api.me();
    expect(user.name).toBe("Admin");
    const init = fetchMock.mock.calls[0]?.[1];
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer test-token");
    expect(getStoredToken()).toBe("test-token");
  });

  it("requests a bounded server-side lesson page and prefix filter", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify({ items: [], page: 1, size: 25, totalItems: 0, totalPages: 0 }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    vi.stubGlobal("fetch", fetchMock);
    const page = await api.lessons({ status: "DRAFT", q: "spring", page: 1, size: 25 });
    expect(page.page).toBe(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain("/admin/lessons?page=1&size=25&status=DRAFT&q=spring");
  });

  it("creates an immutable content release through the admin API", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify({ version: 3, createdAt: "2026-11-20T00:00:00Z", items: [] }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    vi.stubGlobal("fetch", fetchMock);
    const release = await api.createContentRelease();
    expect(release.version).toBe(3);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/admin/content-releases");
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe("POST");
  });

  it("loads academy analytics with a bounded reporting window and caller abort signal", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify({ academyId: "a-1", academyCode: "JIA", windowDays: 7, totalLearners: 12,
        activeLearners: 5, lessonsCompleted: 18, quizAttempts: 9, averageScorePercent: 76.5,
        scoredAttempts: 8, topTopics: [] }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();
    const summary = await api.analyticsSummary(7, controller.signal);
    expect(summary.activeLearners).toBe(5);
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe("/api/v1/admin/analytics/summary?windowDays=7");
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal);
  });

  it("normalizes the shared API error envelope", async () => {
    vi.stubGlobal("fetch", vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify({ code: "FORBIDDEN", message: "Permission denied." }),
      { status: 403, headers: { "Content-Type": "application/json" } },
    )));
    await expect(api.me()).rejects.toMatchObject({
      status: 403, code: "FORBIDDEN", message: "Permission denied.",
    });
  });

  it("clears an expired tab session on HTTP 401", async () => {
    storeToken("expired-token");
    vi.stubGlobal("fetch", vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify({ code: "UNAUTHENTICATED", message: "Authentication is required." }),
      { status: 401, headers: { "Content-Type": "application/json" } },
    )));
    await expect(api.me()).rejects.toMatchObject({ status: 401, code: "UNAUTHENTICATED" });
    expect(getStoredToken()).toBeNull();
  });


  it("shares one refresh-token rotation across concurrent unauthorized requests", async () => {
    storeSession("expired-token", "refresh-old");
    let resolveRefresh!: (response: Response) => void;
    const refreshResponse = new Promise<Response>(resolve => { resolveRefresh = resolve; });
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith("/auth/refresh")) return refreshResponse;
      const authorization = new Headers(init?.headers).get("Authorization");
      if (authorization === "Bearer expired-token") {
        return new Response(JSON.stringify({ code: "UNAUTHENTICATED" }), { status: 401 });
      }
      return new Response(JSON.stringify({ id: "u-1", name: "Admin", email: null, roles: ["ADMIN"] }), {
        status: 200, headers: { "Content-Type": "application/json" },
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    const first = api.me();
    const second = api.me();
    await Promise.resolve();
    await Promise.resolve();
    resolveRefresh(new Response(JSON.stringify({
      accessToken: "fresh-token", refreshToken: "refresh-new", tokenType: "Bearer",
      expiresAt: "2026-11-20T12:00:00Z",
      user: { id: "u-1", name: "Admin", email: null, roles: ["ADMIN"] },
    }), { status: 200, headers: { "Content-Type": "application/json" } }));

    const [a, b] = await Promise.all([first, second]);
    expect(a.id).toBe("u-1");
    expect(b.id).toBe("u-1");
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/auth/refresh"))).toHaveLength(1);
    expect(getStoredToken()).toBe("fresh-token");
    expect(sessionValues.get("jia.admin.refresh-token")).toBe("refresh-new");
  });

  it("validates the lesson document contract without accepting non-object JSON", () => {
    expect(validateLessonContent('{"schemaVersion":1,"blocks":[]}')).toBeNull();
    expect(validateLessonContent('{"blocks":[]}')).not.toBeNull();
    expect(validateLessonContent("[]")).not.toBeNull();
    expect(validateLessonContent("{broken")).not.toBeNull();
  });
});