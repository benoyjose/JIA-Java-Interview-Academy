export type ReviewWorkbenchRow={id:string;title:string;status:string;updatedAt:string;reviewer?:string;severity?:number};
export type ReviewFilters={query:string;status:"ALL"|"PENDING"|"APPROVED"|"CHANGES_REQUESTED"|"REJECTED";reviewer:string;sort:"updated-desc"|"updated-asc"|"severity-desc"};
export function filterReviewWorkbench(rows:ReviewWorkbenchRow[],filters:ReviewFilters):ReviewWorkbenchRow[]{
 const q=filters.query.trim().toLocaleLowerCase();
 return rows.filter(r=>(!q||r.title.toLocaleLowerCase().includes(q)||r.id.toLocaleLowerCase().includes(q))&&(filters.status==="ALL"||r.status.toUpperCase()===filters.status)&&(filters.reviewer==="ALL"||r.reviewer===filters.reviewer))
 .sort((a,b)=>filters.sort==="updated-asc"?a.updatedAt.localeCompare(b.updatedAt):filters.sort==="severity-desc"?(b.severity??0)-(a.severity??0):b.updatedAt.localeCompare(a.updatedAt));
}
export function reviewWorkbenchSummary(rows:ReviewWorkbenchRow[]){return {total:rows.length,pending:rows.filter(r=>r.status.toUpperCase()==="PENDING").length,needsChanges:rows.filter(r=>r.status.toUpperCase()==="CHANGES_REQUESTED").length,reviewers:new Set(rows.map(r=>r.reviewer).filter(Boolean)).size};}
