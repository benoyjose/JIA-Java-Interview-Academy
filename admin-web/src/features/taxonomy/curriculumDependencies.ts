export type DependencyIssue = { lessonId: string; dependencyId: string; reason: "missing" | "self" | "duplicate" | "cycle" };

export function validateCurriculumDependencies(graph: Record<string, string[]>): DependencyIssue[] {
  const issues: DependencyIssue[] = [];
  for (const [lessonId, dependencies] of Object.entries(graph)) {
    const seen = new Set<string>();
    for (const dependencyId of dependencies) {
      if (seen.has(dependencyId)) { issues.push({ lessonId, dependencyId, reason: "duplicate" }); continue; }
      seen.add(dependencyId);
      if (dependencyId === lessonId) issues.push({ lessonId, dependencyId, reason: "self" });
      else if (!(dependencyId in graph)) issues.push({ lessonId, dependencyId, reason: "missing" });
    }
  }
  const visiting = new Set<string>(), visited = new Set<string>();
  const visit = (node: string, stack: string[]) => {
    if (visiting.has(node)) {
      const from = stack[stack.length - 1];
      if (from) issues.push({ lessonId: from, dependencyId: node, reason: "cycle" });
      return;
    }
    if (visited.has(node)) return;
    visiting.add(node);
    for (const dep of graph[node] ?? []) if (dep in graph) visit(dep, [...stack, node]);
    visiting.delete(node); visited.add(node);
  };
  for (const node of Object.keys(graph)) visit(node, []);
  return issues;
}

/** Returns a stable prerequisite-first lesson order, or null when the graph is invalid. */
export function getCurriculumSequence(graph: Record<string, string[]>): string[] | null {
  if (validateCurriculumDependencies(graph).length > 0) return null;
  const indegree = new Map<string, number>();
  const dependents = new Map<string, string[]>();
  for (const lessonId of Object.keys(graph)) {
    indegree.set(lessonId, graph[lessonId].length);
    for (const prerequisiteId of graph[lessonId]) {
      dependents.set(prerequisiteId, [...(dependents.get(prerequisiteId) ?? []), lessonId]);
    }
  }
  // Sorting each ready batch makes output independent of object/edge insertion order.
  const ready = [...indegree].filter(([, degree]) => degree === 0).map(([id]) => id).sort();
  const sequence: string[] = [];
  while (ready.length > 0) {
    const current = ready.shift()!;
    sequence.push(current);
    for (const dependent of (dependents.get(current) ?? []).sort()) {
      const next = indegree.get(dependent)! - 1;
      indegree.set(dependent, next);
      if (next === 0) {
        ready.push(dependent);
        ready.sort();
      }
    }
  }
  return sequence.length === Object.keys(graph).length ? sequence : null;
}
