import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, getStoredToken, storeToken, validateLessonContent } from "./client";

const sessionValues = new Map<string, string>();
beforeEach(() => {
  sessionValues.clear();
  vi.stubGlobal("window", { sessionStorage: {
    getItem: (key: string) => sessionValues.get(key) ?? null,
    setItem: (key: string, value: string) => { sessionValues.set(key, value); },
    removeItem: (key: string) => { sessionValues.delete(key); },
  }, dispatchEvent: vi.fn() });
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

  it("normalizes the shared API error envelope", async () => {
    vi.stubGlobal("fetch", vi.fn<typeof fetch>().mockResolvedValue(new Response(
      JSON.stringify({ code: "FORBIDDEN", message: "Permission denied." }),
      { status: 403, headers: { "Content-Type": "application/json" } },
    )));
    await expect(api.me()).rejects.toMatchObject<ApiError>({
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

  it("validates the lesson document contract without accepting non-object JSON", () => {
    expect(validateLessonContent('{"schemaVersion":1,"blocks":[]}')).toBeNull();
    expect(validateLessonContent('{"blocks":[]}')).not.toBeNull();
    expect(validateLessonContent("[]")).not.toBeNull();
    expect(validateLessonContent("{broken")).not.toBeNull();
  });
});