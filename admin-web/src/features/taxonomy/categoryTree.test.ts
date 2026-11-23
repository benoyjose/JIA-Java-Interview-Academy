import { describe, expect, it } from "vitest";
import type { CategoryItem } from "../../api/types";
import { buildCategoryTree } from "./categoryTree";

const item = (id: string, name: string, parentId: string | null, sortOrder = 0, status = "ACTIVE"): CategoryItem => ({
  id, name, parentId, parentSlug: null, technologyId: "t", technologySlug: "java",
  slug: name.toLowerCase(), sortOrder, status, lessonCount: 2, questionCount: 3,
});

describe("buildCategoryTree", () => {
  it("sorts parent-first and respects configured order", () => {
    const rows = buildCategoryTree([
      item("child", "Child", "root"),
      item("late", "Late", null, 9),
      item("root", "Root", null, 1),
      item("early", "Early", null, 0),
    ]);
    expect(rows.map(row => [row.item.id, row.depth])).toEqual([
      ["early", 0], ["root", 0], ["child", 1], ["late", 0],
    ]);
  });

  it("keeps ancestors when a descendant matches search", () => {
    const rows = buildCategoryTree([item("parent", "Java", null), item("child", "Streams", "parent")], "stream");
    expect(rows.map(row => row.item.id)).toEqual(["parent", "child"]);
  });

  it("applies status filter to matching nodes but preserves context ancestors", () => {
    const rows = buildCategoryTree([item("parent", "Java", null), item("child", "Archived", "parent", 0, "ARCHIVED")], "", "ARCHIVED");
    expect(rows.map(row => row.item.id)).toEqual(["parent", "child"]);
  });
});
