import type { CategoryItem } from "../../api/types";

export type CategoryTreeRow = { item: CategoryItem; depth: number };

/** Sorts categories into a stable parent-first tree and retains ancestor context for filters. */
export function buildCategoryTree(
  categories: CategoryItem[],
  searchText = "",
  status = "ALL",
): CategoryTreeRow[] {
  const query = searchText.trim().toLocaleLowerCase();
  const byId = new Map(categories.map(item => [item.id, item]));
  const children = new Map<string | null, CategoryItem[]>();
  for (const item of categories) {
    const parent = item.parentId && byId.has(item.parentId) ? item.parentId : null;
    const bucket = children.get(parent) ?? [];
    bucket.push(item);
    children.set(parent, bucket);
  }
  const compare = (a: CategoryItem, b: CategoryItem) =>
    a.sortOrder - b.sortOrder || a.name.localeCompare(b.name) || a.id.localeCompare(b.id);
  for (const bucket of children.values()) bucket.sort(compare);

  const matches = (item: CategoryItem) =>
    (status === "ALL" || item.status === status)
    && (!query || [item.name, item.slug, item.status].some(value => value.toLocaleLowerCase().includes(query)));
  const included = new Set<string>();
  function includeAncestors(item: CategoryItem) {
    if (included.has(item.id)) return;
    included.add(item.id);
    if (item.parentId && byId.has(item.parentId)) includeAncestors(byId.get(item.parentId)!);
  }
  for (const item of categories) if (matches(item)) includeAncestors(item);

  const rows: CategoryTreeRow[] = [];
  function visit(parentId: string | null, depth: number) {
    for (const item of children.get(parentId) ?? []) {
      if (included.has(item.id)) {
        rows.push({ item, depth });
        visit(item.id, depth + 1);
      }
    }
  }
  visit(null, 0);
  return rows;
}
