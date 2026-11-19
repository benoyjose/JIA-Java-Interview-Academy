import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, clearStoredToken, getStoredToken, storeToken, validateLessonContent } from "./client";
afterEach(() => { vi.unstubAllGlobals(); clearStoredToken(); });
describe("admin API client", () => {
  it("attaches the tab-scoped bearer token", async () => {
    storeToken("test-token");
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "u-1", name: "Admin", email: "admin@example.test", roles: ["ADMIN"] }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const user = await api.me();
    expect(user.name).toBe("Admin");
    expect(fetchMock.mock.calls[0][1].headers.get("Authorization")).toBe("Bearer test-token");
    expect(getStoredToken()).toBe("test-token");
  });
  it("normalizes the shared API error envelope", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "FORBIDDEN", message: "Permission denied." }), { status: 403, headers: { "Content-Type": "application/json" } })));
    await expect(api.me()).rejects.toMatchObject<ApiError>({ status: 403, code: "FORBIDDEN", message: "Permission denied." });
  });
  it("validates the lesson document contract without accepting non-object JSON", () => {
    expect(validateLessonContent('{"schemaVersion":1,"blocks":[]}')).toBeNull();
    expect(validateLessonContent('{"blocks":[]}')).not.toBeNull();
    expect(validateLessonContent("[]")).not.toBeNull();
    expect(validateLessonContent("{broken")).not.toBeNull();
  });
});