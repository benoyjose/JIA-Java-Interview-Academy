import {useMemo,useState} from "react";
import {filterReviewWorkbench,reviewWorkbenchSummary,type ReviewWorkbenchRow,type ReviewFilters} from "./reviewWorkbench";
type Props={rows:ReviewWorkbenchRow[];onOpen:(id:string)=>void};
const initial:ReviewFilters={query:"",status:"PENDING",reviewer:"ALL",sort:"updated-desc"};
export function ReviewWorkbench({rows,onOpen}:Props){
 const [filters,setFilters]=useState(initial);
 const visible=useMemo(()=>filterReviewWorkbench(rows,filters),[rows,filters]);
 const summary=reviewWorkbenchSummary(rows);
 return <section aria-label="Review workbench">
  <header><h2>Content review workbench</h2><p>{summary.pending} pending · {summary.needsChanges} returned · {summary.reviewers} reviewers</p></header>
  <div>
   <label>Search <input value={filters.query} onChange={e=>setFilters(f=>({...f,query:e.target.value}))} placeholder="Lesson or ID"/></label>
   <label>Status <select value={filters.status} onChange={e=>setFilters(f=>({...f,status:e.target.value as ReviewFilters["status"]}))}><option value="PENDING">Pending</option><option value="ALL">All</option><option value="APPROVED">Approved</option><option value="CHANGES_REQUESTED">Changes requested</option><option value="REJECTED">Rejected</option></select></label>
   <label>Sort <select value={filters.sort} onChange={e=>setFilters(f=>({...f,sort:e.target.value as ReviewFilters["sort"]}))}><option value="updated-desc">Newest</option><option value="updated-asc">Oldest</option><option value="severity-desc">Risk first</option></select></label>
  </div>
  <ul>{visible.map(row=><li key={row.id}><button onClick={()=>onOpen(row.id)}>{row.title}</button><span>{row.status} · {row.reviewer??"Unassigned"}</span></li>)}</ul>
  {visible.length===0&&<p role="status">No review records match these filters.</p>}
 </section>;
}
