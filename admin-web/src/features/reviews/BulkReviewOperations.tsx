import {useMemo,useState} from "react";
import {selectableReviewIds,reconcileReviewSelection,bulkReviewProgress} from "./bulkReviewSelection";
type Row={taskId:string;title:string;status:string};
type Props={rows:Row[];onDecide:(id:string,decision:"APPROVE"|"REQUEST_CHANGES"|"REJECT")=>Promise<void>;onRefresh:()=>void};
export function BulkReviewOperations({rows,onDecide,onRefresh}:Props){
 const pending=useMemo(()=>selectableReviewIds(rows),[rows]);const [selected,setSelected]=useState<string[]>([]);
 const [decision,setDecision]=useState<"APPROVE"|"REQUEST_CHANGES"|"REJECT">("REQUEST_CHANGES");
 const [busy,setBusy]=useState(false);const [report,setReport]=useState<{completed:number;failed:number}|null>(null);
 const safe=reconcileReviewSelection(selected,pending);
 async function apply(){if(!safe.length||busy)return;setBusy(true);let completed=0,failed=0;
  try{for(const id of safe){try{await onDecide(id,decision);completed++;}catch{failed++;}}setReport({completed,failed});setSelected([]);onRefresh();}
  finally{setBusy(false);}}
 const progress=report?bulkReviewProgress(report.completed+report.failed,report.completed+report.failed,report.failed):null;
 return <section className="panel" aria-label="Bulk review operations"><div className="panel-heading"><h2>Bulk review actions</h2><span>{safe.length} selected (max 50)</span></div>
 <label><input type="checkbox" checked={pending.length>0&&safe.length===pending.length} onChange={e=>setSelected(e.target.checked?pending:[])}/> Select visible pending rows</label>
 <select aria-label="Decision" value={decision} onChange={e=>setDecision(e.target.value as typeof decision)}><option value="REQUEST_CHANGES">Request changes</option><option value="APPROVE">Approve</option><option value="REJECT">Reject</option></select>
 <ul>{rows.filter(row=>safe.includes(row.taskId)).map(row=><li key={row.taskId}>{row.title}</li>)}</ul>
 <button disabled={busy||!safe.length} onClick={()=>void apply()}>{busy?"Applying…":"Apply decision"}</button>
 {progress&&<p role="status">{progress.success} succeeded · {progress.failed} failed · {progress.remaining} not attempted</p>}
 <p className="muted">Sequential, non-atomic operation. Partial failures remain available for retry.</p></section>;
}
