export type CurriculumRow = { id: string; title: string; version: number; prerequisiteIds: string[] };
export type DiffKind = "added" | "removed" | "changed" | "unchanged";
export type CurriculumDiff = { id: string; kind: DiffKind; before?: CurriculumRow; after?: CurriculumRow; risk: "low" | "medium" | "high" };
const normalize=(r:CurriculumRow)=>JSON.stringify({title:r.title.trim(),version:r.version,prerequisiteIds:[...new Set(r.prerequisiteIds)].sort()});
export function previewCurriculumDiff(current:CurriculumRow[],incoming:CurriculumRow[]):CurriculumDiff[]{
 const validate=(rows:CurriculumRow[])=>{const ids=rows.map(r=>r.id.trim());if(ids.some(x=>!x)||new Set(ids).size!==ids.length)throw new Error("Curriculum IDs must be unique and non-empty");};
 validate(current);validate(incoming);const old=new Map(current.map(r=>[r.id,r])),next=new Map(incoming.map(r=>[r.id,r]));
 const result:CurriculumDiff[]=[];
 for(const [id,a] of next){const b=old.get(id);if(!b)result.push({id,kind:"added",after:a,risk:"medium"});else if(normalize(a)!==normalize(b))result.push({id,kind:"changed",before:b,after:a,risk:a.prerequisiteIds.some(x=>!next.has(x))?"high":"medium"});else result.push({id,kind:"unchanged",before:b,after:a,risk:"low"});}
 for(const [id,b] of old)if(!next.has(id))result.push({id,kind:"removed",before:b,risk:"high"});
 return result.sort((a,b)=>a.id.localeCompare(b.id));
}