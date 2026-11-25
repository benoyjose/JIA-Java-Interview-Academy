import { expect, test } from "@playwright/test";

test("admin shell exposes skip navigation, named landmarks and keyboard focus", async ({ page }) => {
  const user = { id: "a11y-admin", name: "Accessibility Admin", email: "a11y@example.test", roles: ["ADMIN"] };
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    if (path === "/auth/login" && route.request().method() === "POST") {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
        accessToken: "a11y-token", refreshToken: "a11y-refresh", tokenType: "Bearer",
        expiresAt: "2026-12-01T00:00:00Z", user,
      }) });
    }
    if (path === "/auth/refresh") return route.fulfill({ status: 401, contentType: "application/json", body: "{}" });
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      items: [], page: 0, size: 25, totalItems: 0, totalPages: 0,
    }) });
  });

  await page.goto("/");
  await page.getByLabel("Email address").fill(user.email);
  await page.getByLabel("Password").fill("test-only-password");
  await page.getByRole("button", { name: "Sign in" }).click();

  const skip = page.getByRole("link", { name: "Skip to main content" });
  await expect(skip).toBeAttached();
  await skip.focus();
  await expect(skip).toBeFocused();
  await expect(skip).toBeVisible();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/#main-content$/);
  await expect(page.getByRole("main")).toBeVisible();

  const navigation = page.getByRole("navigation", { name: "Main navigation" });
  await expect(navigation.getByRole("button", { name: /Dashboard/ })).toHaveAttribute("aria-current", "page");
  await expect(page.getByRole("complementary", { name: "Workspace navigation" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Workspace header" })).toBeVisible();
});

test("taxonomy actions expose item-specific accessible names", async ({ page }) => {
  const user = { id: "taxonomy-a11y", name: "Taxonomy Editor", email: "taxonomy@example.test", roles: ["ADMIN"] };
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    if (path === "/auth/login" && route.request().method() === "POST") {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
        accessToken: "taxonomy-token", refreshToken: "taxonomy-refresh", tokenType: "Bearer",
        expiresAt: "2026-12-01T00:00:00Z", user,
      }) });
    }
    if (path === "/auth/refresh") return route.fulfill({ status: 401, contentType: "application/json", body: "{}" });
    if (path === "/admin/taxonomy/technologies") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([
      { id: "technology-1", slug: "java", name: "Java", description: null, sortOrder: 0, status: "ACTIVE" },
    ]) });
    if (path === "/admin/taxonomy/categories") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([
      { id: "category-1", technologySlug: "java", parentId: null, slug: "streams", name: "Streams", sortOrder: 0, status: "ACTIVE", lessonCount: 0, questionCount: 0 },
    ]) });
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 }) });
  });
  await page.goto("/");
  await page.getByLabel("Email address").fill(user.email);
  await page.getByLabel("Password").fill("test-only-password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByRole("button", { name: "Taxonomy" }).click();
  await expect(page.getByRole("heading", { name: "Taxonomy" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Edit category Streams" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Archive category Streams" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Edit technology Java" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Archive technology Java" })).toBeVisible();
});
