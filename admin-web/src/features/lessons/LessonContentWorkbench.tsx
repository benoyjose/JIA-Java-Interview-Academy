import { useMemo, useState } from "react";
import "./lesson-content-workbench.css";

export type LessonBlockRecord = Record<string, unknown> & { type: string; title?: string };

const templates: Record<string, LessonBlockRecord> = {
  markdown: { type: "markdown", title: "Explanation", body: "" },
  callout: { type: "callout", title: "Key point", body: "" },
  code: { type: "code", title: "Example", language: "java", source: "" },
  list: { type: "list", title: "Checklist", items: [""] },
  interviewAnswer: { type: "interviewAnswer", title: "Interview answer", body: [""], expandedExplanation: "" },
  diagram: { type: "diagram", title: "Architecture", source: "" },
  resourceLink: { type: "resourceLink", title: "Further reading", url: "https://", label: "" },
};

export function parseLessonBlocks(raw: string): LessonBlockRecord[] {
  const document: unknown = JSON.parse(raw);
  if (!document || typeof document !== "object" || Array.isArray(document)) throw new Error("Document must be a JSON object.");
  const value = document as { schemaVersion?: unknown; blocks?: unknown };
  if (value.schemaVersion !== 1 || !Array.isArray(value.blocks)) throw new Error("Expected schemaVersion 1 and a blocks array.");
  if (!value.blocks.every(block => block && typeof block === "object" && !Array.isArray(block) && typeof (block as {type?: unknown}).type === "string")) {
    throw new Error("Every block must be an object with a type.");
  }
  return value.blocks as LessonBlockRecord[];
}

export function serializeLessonBlocks(blocks: LessonBlockRecord[]): string {
  return JSON.stringify({ schemaVersion: 1, blocks }, null, 2);
}

export function createLessonBlock(type: string): LessonBlockRecord {
  const template = templates[type];
  if (!template) throw new Error("Unsupported block type.");
  return structuredClone(template);
}

/** Structured block editor with a raw JSON escape hatch for advanced and future block types. */
export function LessonContentWorkbench({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [rawMode, setRawMode] = useState(false);
  const [selectedType, setSelectedType] = useState("markdown");
  const parsed = useMemo(() => {
    try { return { blocks: parseLessonBlocks(value), error: "" }; }
    catch (error) { return { blocks: [] as LessonBlockRecord[], error: error instanceof Error ? error.message : "Invalid document" }; }
  }, [value]);

  function changeBlock(index: number, patch: Partial<LessonBlockRecord>) {
    const blocks = parseLessonBlocks(value).map((block, i) => i === index ? { ...block, ...patch } : block);
    onChange(serializeLessonBlocks(blocks));
  }
  function addBlock() {
    const blocks = parseLessonBlocks(value);
    if (blocks.length >= 250) return;
    blocks.push(createLessonBlock(selectedType));
    onChange(serializeLessonBlocks(blocks));
  }
  function removeBlock(index: number) {
    onChange(serializeLessonBlocks(parseLessonBlocks(value).filter((_, i) => i !== index)));
  }
  function moveBlock(index: number, delta: number) {
    const blocks = parseLessonBlocks(value);
    const target = index + delta;
    if (target < 0 || target >= blocks.length) return;
    [blocks[index], blocks[target]] = [blocks[target], blocks[index]];
    onChange(serializeLessonBlocks(blocks));
  }

  if (rawMode) return <div className="content-workbench">
    <div className="workbench-toolbar"><strong>Advanced JSON editor</strong><button type="button" className="text-button" onClick={() => setRawMode(false)}>Use block editor</button></div>
    <textarea aria-label="Raw lesson JSON" className="code-input" rows={16} spellCheck={false} value={value} onChange={event => onChange(event.target.value)} />
    {parsed.error && <p className="form-error" role="alert">{parsed.error}</p>}
  </div>;

  return <div className="content-workbench">
    <div className="workbench-toolbar"><span><strong>Lesson blocks</strong> <small>{parsed.blocks.length}/250</small></span>
      <button type="button" className="text-button" onClick={() => setRawMode(true)}>Edit raw JSON</button></div>
    {parsed.error ? <div className="notice error" role="alert">{parsed.error} <button type="button" className="text-button" onClick={() => setRawMode(true)}>Repair JSON</button></div> :
      parsed.blocks.length === 0 ? <p className="muted small-copy">Start with a text explanation, code sample or interview answer.</p> :
      parsed.blocks.map((block, index) => <article className="workbench-block" key={index}>
        <div className="workbench-block-heading"><strong>{index + 1}. {block.type}</strong><div>
          <button type="button" className="text-button" aria-label={`Move block ${index + 1} up`} disabled={index === 0} onClick={() => moveBlock(index, -1)}>↑</button>
          <button type="button" className="text-button" aria-label={`Move block ${index + 1} down`} disabled={index === parsed.blocks.length - 1} onClick={() => moveBlock(index, 1)}>↓</button>
          <button type="button" className="text-button" aria-label={`Delete block ${index + 1}`} onClick={() => removeBlock(index)}>Remove</button>
        </div></div>
        <label>Block title<input maxLength={200} value={String(block.title ?? "")} onChange={event => changeBlock(index, { title: event.target.value })} /></label>
        {["markdown","section","overview","objectives","prerequisites","explanation","tradeoffs","commonMistakes","troubleshooting","followUps","callout"].includes(block.type) &&
          <label>Body<textarea rows={4} value={String(block.body ?? "")} onChange={event => changeBlock(index, { body: event.target.value })} /></label>}
        {block.type === "code" && <><label>Language<input value={String(block.language ?? "")} onChange={event => changeBlock(index, { language: event.target.value })} /></label><label>Code<textarea className="code-input" rows={8} spellCheck={false} value={String(block.source ?? "")} onChange={event => changeBlock(index, { source: event.target.value })} /></label></>}
        {block.type === "list" && <label>List items (one per line)<textarea rows={5} value={Array.isArray(block.items) ? block.items.map(String).join("\n") : ""} onChange={event => changeBlock(index, { items: event.target.value.split("\n") })} /></label>}
        {block.type === "interviewAnswer" && <><label>Answer points (one per line)<textarea rows={5} value={Array.isArray(block.body) ? block.body.map(String).join("\n") : Array.isArray(block.points) ? block.points.map(String).join("\n") : ""} onChange={event => changeBlock(index, { body: event.target.value.split("\n") })} /></label><label>Expanded explanation<textarea rows={3} value={String(block.expandedExplanation ?? "")} onChange={event => changeBlock(index, { expandedExplanation: event.target.value })} /></label></>}
        {["diagram","resourceLink","image","video"].includes(block.type) && <label>Source / URL<input value={String(block.source ?? block.url ?? "")} onChange={event => changeBlock(index, block.type === "diagram" ? { source: event.target.value } : { url: event.target.value })} /></label>}
        {!Object.hasOwn(templates, block.type) && <p className="muted small-copy">This advanced block is preserved without transformation. Use raw JSON to edit its schema.</p>}
      </article>)}
    <div className="workbench-add"><select aria-label="New block type" value={selectedType} onChange={event => setSelectedType(event.target.value)}>{Object.keys(templates).map(type => <option key={type} value={type}>{type}</option>)}</select>
      <button type="button" className="button button-secondary" disabled={parsed.error !== "" || parsed.blocks.length >= 250} onClick={addBlock}>＋ Add block</button></div>
  </div>;
}
