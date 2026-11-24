export type ReviewRow = { taskId: string; status: string };
export const MAX_BULK_REVIEW = 50;
export function selectableReviewIds(rows: ReviewRow[]): string[] {
 return rows.filter(row=>row.status.toUpperCase()==="PENDING").map(row=>row.taskId).filter(Boolean).slice(0,MAX_BULK_REVIEW);
}
export function reconcileReviewSelection(selected: string[], visiblePending: string[]): string[] {
 const allowed=new Set(visiblePending);return [...new Set(selected)].filter(id=>allowed.has(id)).slice(0,MAX_BULK_REVIEW);
}
export function bulkReviewProgress(total:number,completed:number,failed:number){
 const t=Math.max(0,Math.trunc(total)),c=Math.min(t,Math.max(0,Math.trunc(completed))),f=Math.min(c,Math.max(0,Math.trunc(failed)));
 return {total:t,completed:c,failed:f,remaining:t-c,success:c-f};
}
