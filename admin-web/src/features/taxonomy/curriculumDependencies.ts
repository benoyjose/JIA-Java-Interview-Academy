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
