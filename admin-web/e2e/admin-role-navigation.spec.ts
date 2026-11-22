import { expect, test } from "@playwright/test";

const roleCases = [
  { role: "EDITOR", visible: ["Lessons", "Taxonomy"], hidden: ["Review queue", "Users"] },
  { role: "REVIEWER", visible: ["Review queue", "Question bank"], hidden: ["Lessons", "Taxonomy", "Users"] },
  { role: "ADMIN", visible: ["Dashboard", "Lessons", "Review queue", "Taxonomy", "Question bank", "Users"], hidden: [] },
] as const;

for (const scenario of roleCases) {
  test(`navigation is permission-scoped for ${scenario.role}`, async ({ page }) => {
    const user = {
      id: `role-${scenario.role.toLowerCase()}`,
      name: `JIA ${scenario.role}`,
      email: `${scenario.role.toLowerCase()}@example.test`,
      roles: [scenario.role],
    };
    await page.route("**/api/v1/**", async route => {
      const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
      if (path === "/auth/login" && route.request().method() === "POST") {
        return route.fulfill({ status: 200, contentType: "application/json",
          body: JSON.stringify({ accessToken: "e2e-token", refreshToken: "e2e-refresh", tokenType: "Bearer",
            expiresAt: "2026-12-01T00:00:00Z", user }) });
      }
      if (path === "/auth/refresh") return route.fulfill({ status: 401, contentType: "application/json", body: "{}" });
      if (path === "/admin/reviews") return route.fulfill({ status: 200, contentType: "application/json",
        body: JSON.stringify({ items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 }) });
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([]) });
    });

    await page.goto("/");
    await page.getByLabel("Email address").fill(user.email);
    await page.getByLabel("Password").fill("not-a-real-password");
    await page.getByRole("button", { name: "Sign in" }).click();

    const nav = page.getByRole("navigation", { name: "Main navigation" });
    for (const label of scenario.visible) await expect(nav.getByRole("button", { name: new RegExp(label) })).toBeVisible();
    for (const label of scenario.hidden) await expect(nav.getByRole("button", { name: new RegExp(label) })).toHaveCount(0);
  });
}
