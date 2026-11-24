import {useState} from "react";
export type DraftLesson={id:string;title:string;status:"DRAFT"|"IN_REVIEW"|"APPROVED"|"PUBLISHED"|"ARCHIVED";version:number};
export type PublishResult={published:string[];failed:{id:string;message:string}[]};
type Props={items:DraftLesson[];publish:(id:string,expectedVersion:number)=>Promise<void>;refresh:()=>void};
/** Safe bulk publisher: only reviewed drafts, capped selection, sequential optimistic version checks. */
export function BulkContentPublishing({items,publish,refresh}:Props){
 const eligible=items.filter(x=>x.status==="IN_REVIEW").slice(0,100);const [selected,setSelected]=useState<string[]>([]);
 const [running,setRunning]=useState(false);const [result,setResult]=useState<PublishResult|null>(null);
 const selectedRows=eligible.filter(x=>selected.includes(x.id));
 async function run(){if(running||!selectedRows.length)return;setRunning(true);const published:string[]=[];const failed:PublishResult["failed"]=[];
  try{for(const item of selectedRows){try{await publish(item.id,item.version);published.push(item.id);}catch(error){failed.push({id:item.id,message:error instanceof Error?error.message:"Publishing failed"});}}
   setResult({published,failed});setSelected([]);refresh();
  }finally{setRunning(false);}}
 return <section aria-label="Bulk content publishing"><h2>Publish reviewed content</h2><p>{selectedRows.length} selected / 100 maximum</p>
  <label><input type="checkbox" checked={eligible.length>0&&selectedRows.length===eligible.length} onChange={e=>setSelected(e.target.checked?eligible.map(x=>x.id):[])}/> Select reviewed drafts</label>
  <ul>{eligible.map(item=><li key={item.id}><label><input type="checkbox" checked={selected.includes(item.id)} onChange={e=>setSelected(old=>e.target.checked?(old.length<100?[...old,item.id]:old):old.filter(id=>id!==item.id))}/>{item.title} · v{item.version}</label></li>)}</ul>
  <button disabled={running||!selectedRows.length} onClick={()=>void run()}>{running?"Publishing…":"Publish selected"}</button>
  {result&&<div role="status">{result.published.length} published; {result.failed.length} failed. Failed items are retained for retry.</div>}
  <p>Server must enforce editor permission, reviewed state, version precondition, audit event and idempotency.</p></section>;
}
