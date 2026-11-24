export type PublishIssue = { path: string; message: string };
type Block = Record<string, unknown> & { type?: unknown };
/** Pure preflight used before content is sent to the publish endpoint. */
export function evaluatePublishReadiness(raw: string): PublishIssue[] {
  let doc: unknown;
  try { doc = JSON.parse(raw); } catch { return [{ path: "$", message: "Content must be valid JSON." }]; }
  if (!doc || typeof doc !== "object" || Array.isArray(doc)) return [{ path: "$", message: "Content root must be an object." }];
  const value = doc as { schemaVersion?: unknown; blocks?: unknown };
  const issues: PublishIssue[] = [];
  if (value.schemaVersion !== 1) issues.push({ path: "schemaVersion", message: "Only schema version 1 can be published." });
  if (!Array.isArray(value.blocks) || value.blocks.length === 0) return [...issues, { path: "blocks", message: "Add at least one content block before publishing." }];
  if (value.blocks.length > 250) issues.push({ path: "blocks", message: "A lesson may contain at most 250 blocks." });
  value.blocks.forEach((entry, index) => {
    const path = `blocks[${index}]`;
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) { issues.push({ path, message: "Block must be an object." }); return; }
    const block = entry as Block;
    if (typeof block.type !== "string" || !block.type.trim()) issues.push({ path: path + ".type", message: "Block type is required." });
    if (block.type === "code" && (typeof block.source !== "string" || !block.source.trim() || typeof block.language !== "string" || !block.language.trim()))
      issues.push({ path, message: "Code blocks require both a language and source." });
    if (block.type === "resourceLink") {
      try { const url = new URL(String(block.url)); if (url.protocol !== "https:") throw new Error(); }
      catch { issues.push({ path: path + ".url", message: "Resource links must use a valid HTTPS URL." }); }
    }
  });
  return issues;
}
