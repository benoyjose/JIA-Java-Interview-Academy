import { expect, test } from "@playwright/test";

const user = { id: "admin-1", name: "JIA Admin", email: "admin@example.test", roles: ["ADMIN"] };
const lessonId = "lesson-java-streams";
const taskId = "review-java-streams";
const lesson = {
  id: lessonId,
  slug: "java-streams",
  title: "Java Streams",
  categorySlug: "java",
  status: "DRAFT",
  contentRevision: 1,
  publishedRevision: null,
  draftRevision: 1,
  updatedAt: "2026-11-19T10:00:00Z",
};
const lessonDetail = {
  id: lessonId,
  slug: "java-streams",
  title: "Java Streams",
  summary: "Learn stream pipelines.",
  categorySlug: "java",
  difficulty: "BEGINNER",
  sourceType: "ADMIN_AUTHORED",
  contentJson: JSON.stringify({ schemaVersion: 1, blocks: [{ type: "text", text: "Stream fundamentals" }] }),
  status: "DRAFT",
  revisionNumber: 1,
  publishedRevision: null,
  editingDraft: true,
  changeNote: null,
  updatedAt: "2026-11-19T10:00:00Z",
};
const reviewDetail = {
  taskId,
  lessonId,
  revisionId: "revision-1",
  slug: "java-streams",
  title: "Java Streams",
  summary: "Learn stream pipelines.",
  difficulty: "BEGINNER",
  sourceType: "ADMIN_AUTHORED",
  contentJson: lessonDetail.contentJson,
  revisionNumber: 1,
  changeNote: "Initial lesson",
  submittedBy: "Content Author",
  submittedAt: "2026-11-19T10:05:00Z",
  lessonStatus: "IN_REVIEW",
};

test("admin can create, submit, review, approve and publish a lesson", async ({ page }) => {
  let status = "DRAFT";
  let submitted = false;
  let approved = false;
  let published = false;
  let lessonCreated = false;
  let sawBearerToken = false;

  await page.route("**/api/v1/**", async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace("/api/v1", "");
    const method = request.method();
    const json = (body: unknown, responseStatus = 200) => route.fulfill({
      status: responseStatus,
      contentType: "application/json",
      body: JSON.stringify(body),
    });

    if (path === "/auth/login" && method === "POST") {
      return json({ accessToken: "e2e-access-token", tokenType: "Bearer", expiresAt: "2026-11-20T10:00:00Z", user });
    }
    if (path === "/admin/lessons" && method === "GET") {
      sawBearerToken = request.headers()["authorization"] === "Bearer e2e-access-token";
      const current = {
        ...lesson,
        status: published ? "PUBLISHED" : approved ? "APPROVED" : submitted ? "IN_REVIEW" : status,
        publishedRevision: published ? 1 : null,
      };
      return json({ items: lessonCreated ? [current] : [], page: 0, size: 25, totalItems: lessonCreated ? 1 : 0, totalPages: lessonCreated ? 1 : 0 });
    }
    if (path === "/admin/lessons" && method === "POST") {
      lessonCreated = true;
      status = "DRAFT";
      return json({ id: lessonId, slug: "java-streams", title: "Java Streams", status, revisionNumber: 1 }, 201);
    }
    if (path === `/admin/lessons/${lessonId}` && method === "GET") {
      return json({
        ...lessonDetail,
        status: published ? "PUBLISHED" : approved ? "APPROVED" : submitted ? "IN_REVIEW" : status,
        publishedRevision: published ? 1 : null,
        editingDraft: !published,
      });
    }
    if (path === `/admin/lessons/${lessonId}/submit` && method === "POST") {
      submitted = true;
      status = "IN_REVIEW";
      return json({ taskId }, 200);
    }
    if (path === "/admin/reviews" && method === "GET") {
      return json({
        items: submitted && !approved ? [{
          taskId, lessonId, revisionId: "revision-1", slug: "java-streams",
          title: "Java Streams", submittedBy: "Content Author",
          status: "PENDING", submittedAt: reviewDetail.submittedAt,
        }] : [],
        page: 0, size: 25, totalItems: submitted && !approved ? 1 : 0,
        totalPages: submitted && !approved ? 1 : 0,
      });
    }
    if (path === `/admin/reviews/${taskId}` && method === "GET") return json(reviewDetail);
    if (path === `/admin/reviews/${taskId}/decision` && method === "POST") {
      approved = request.postDataJSON().decision === "APPROVE";
      status = approved ? "APPROVED" : "DRAFT";
      return json({ status });
    }
    if (path === `/admin/lessons/${lessonId}/publish` && method === "POST") {
      published = true;
      status = "PUBLISHED";
      return json({ status });
    }
    return json({ code: "NOT_FOUND", message: `Unexpected E2E request: ${method} ${path}` }, 404);
  });

  await page.goto("/");
  await page.getByLabel("Email address").fill(user.email);
  await page.getByLabel("Password").fill("test-password");
  await page.getByRole("button", { name: "Sign in" }).click();

  // Admins now land on the analytics dashboard; navigate to the lesson workflow explicitly.
  await page.getByRole("navigation", { name: "Main navigation" }).getByRole("button", { name: /Lessons/ }).click();
  await expect(page.getByRole("heading", { name: "Lessons", exact: true })).toBeVisible();
  await page.getByRole("button", { name: /New lesson/ }).click();
  await page.getByLabel("Lesson title").fill("Java Streams");
  await page.getByLabel("Category slug").fill("java");
  await page.getByLabel("Lesson slug").fill("java-streams");
  await page.getByRole("button", { name: "Edit raw JSON" }).click();
  await page.getByLabel("Raw lesson JSON").fill(lessonDetail.contentJson);
  await page.getByRole("button", { name: "Save & submit" }).click();

  await expect(page.getByRole("status")).toContainText("submitted for review");
  await page.getByRole("button", { name: /Review queue/ }).click();
  await expect(page.getByRole("heading", { name: "Review queue" })).toBeVisible();
  // ReviewsPage automatically selects the first pending item and loads its immutable revision.
  await expect(page.getByRole("heading", { name: "Java Streams" })).toBeVisible();
  await expect(page.getByText("Stream fundamentals")).toBeVisible();
  await page.getByRole("button", { name: "Approve revision" }).click();
  await expect(page.getByRole("status")).toContainText("Revision approved");

  await page.getByRole("navigation", { name: "Main navigation" }).getByRole("button", { name: /Lessons/ }).click();
  await page.getByRole("button", { name: /Java Streams/ }).click();
  await expect(page.getByRole("button", { name: "Publish approved" })).toBeVisible();
  await page.getByRole("button", { name: "Publish approved" }).click();
  await expect(page.getByRole("status")).toContainText("Lesson published successfully");
  expect(sawBearerToken).toBe(true);
});

test("expired stored session is cleared and returns the admin to sign-in", async ({ page }) => {
  await page.addInitScript(() => {
    window.sessionStorage.setItem("jia.admin.access-token", "expired-token");
  });
  await page.route("**/api/v1/auth/me", route => route.fulfill({
    status: 401,
    contentType: "application/json",
    body: JSON.stringify({ code: "UNAUTHENTICATED", message: "Authentication is required." }),
  }));

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await expect.poll(() => page.evaluate(() => window.sessionStorage.getItem("jia.admin.access-token"))).toBeNull();
});


test("lesson editor blocks malformed content before issuing a save request", async ({ page }) => {
  let saveRequests = 0;
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    if (path === "/auth/login") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ accessToken: "e2e-token", tokenType: "Bearer", expiresAt: "2026-12-01T00:00:00Z", user }) });
    if (path === "/admin/lessons" && route.request().method() === "GET") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 }) });
    if (path === "/admin/lessons" && route.request().method() === "POST") { saveRequests++; return route.fulfill({ status: 201, contentType: "application/json", body: "{}" }); }
    return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });
  await page.goto("/");
  await page.getByLabel("Email address").fill(user.email);
  await page.getByLabel("Password").fill("test-password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByRole("navigation", { name: "Main navigation" }).getByRole("button", { name: /Lessons/ }).click();
  await page.getByRole("button", { name: /New lesson/ }).click();
  await page.getByLabel("Lesson title").fill("Invalid content test");
  await page.getByLabel("Category slug").fill("java");
  await page.getByLabel("Lesson slug").fill("invalid-content-test");
  await page.getByRole("button", { name: "Edit raw JSON" }).click();
  await page.getByLabel("Raw lesson JSON").fill("{invalid");
  await page.getByRole("button", { name: "Save draft" }).click();
  await expect(page.getByText("Lesson content is not valid JSON.")).toBeVisible();
  expect(saveRequests).toBe(0);
});


test("editor previews valid draft blocks locally without publishing or saving", async ({ page }) => {
  let writeRequests = 0;
  await page.route("**/api/v1/**", async route => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    if (path === "/auth/login") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ accessToken: "preview-token", tokenType: "Bearer", expiresAt: "2026-12-01T00:00:00Z", user }) });
    if (path === "/admin/lessons" && route.request().method() === "GET") return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 }) });
    if (["POST", "PUT"].includes(route.request().method()) && path.startsWith("/admin/lessons")) writeRequests++;
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({}) });
  });
  await page.goto("/");
  await page.getByLabel("Email address").fill(user.email);
  await page.getByLabel("Password").fill("test-password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByRole("navigation", { name: "Main navigation" }).getByRole("button", { name: /Lessons/ }).click();
  await page.getByRole("button", { name: /New lesson/ }).click();
  await page.getByLabel("Lesson title").fill("Preview-only draft");
  await page.getByLabel("Category slug").fill("java");
  await page.getByLabel("Lesson slug").fill("preview-only-draft");
  await page.getByRole("button", { name: "Edit raw JSON" }).click();
  await page.getByLabel("Raw lesson JSON").fill(JSON.stringify({ schemaVersion: 1, blocks: [{ type: "markdown", title: "Preview heading", body: "Preview body content" }] }));
  await page.getByRole("button", { name: "Preview as learner" }).click();
  await expect(page.getByRole("region", { name: "Private learner preview" })).toContainText("Preview body content");
  await expect(page.getByText("Draft only · not publicly visible")).toBeVisible();
  expect(writeRequests).toBe(0);
});
