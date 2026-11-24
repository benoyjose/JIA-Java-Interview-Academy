import {useState} from "react";
export type DraftLesson={id:string;title:string;status:"DRAFT"|"IN_REVIEW"|"APPROVED"|"PUBLISHED"|"ARCHIVED";version:number};
export type PublishResult={published:string[];failed:{id:string;message:string}[]};
type Props={items:DraftLesson[];publish:(id:string,expectedVersion:number)=>Promise<void>;refresh:()=>void};
export function eligibleBulkPublishItems(items:DraftLesson[]):DraftLesson[]{return items.filter(item=>item.status==="APPROVED").slice(0,100);}
/** Sequentially publishes reviewed items; failed records remain selected for retry. */
export function BulkContentPublishing({items,publish,refresh}:Props){
 const eligible=eligibleBulkPublishItems(items);const [selected,setSelected]=useState<string[]>([]);const [running,setRunning]=useState(false);const [result,setResult]=useState<PublishResult|null>(null);
 const selectedRows=eligible.filter(item=>selected.includes(item.id));
 async function run(){if(running||!selectedRows.length)return;setRunning(true);const published:string[]=[];const failed:PublishResult["failed"]=[];
  try{for(const item of selectedRows){try{await publish(item.id,item.version);published.push(item.id);}catch(error){failed.push({id:item.id,message:error instanceof Error?error.message:"Publishing failed"});}}
   setResult({published,failed});setSelected(failed.map(item=>item.id));if(published.length)refresh();
  }finally{setRunning(false);}}
 return <section aria-label="Bulk content publishing"><h2>Bulk publish approved lessons</h2><p>{selectedRows.length} selected · maximum 100 per run</p>
  <label><input type="checkbox" checked={eligible.length>0&&selectedRows.length===eligible.length} onChange={event=>setSelected(event.target.checked?eligible.map(item=>item.id):[])}/> Select approved lessons on this page</label>
  <ul>{eligible.map(item=><li key={item.id}><label><input type="checkbox" checked={selected.includes(item.id)} onChange={event=>setSelected(old=>event.target.checked?(old.length<100?[...old,item.id]:old):old.filter(id=>id!==item.id))}/>{item.title} · revision {item.version}</label></li>)}</ul>
  <button disabled={running||!selectedRows.length} onClick={()=>void run()}>{running?"Publishing…":"Publish selected"}</button>
  {result&&<div role="status">{result.published.length} published; {result.failed.length} failed. Failed lessons remain selected for retry.</div>}
  <p className="muted">Each publish request is checked by the API; the page does not bypass reviewer permissions or publication rules.</p></section>;
}
