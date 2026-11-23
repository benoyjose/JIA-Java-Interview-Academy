import { useEffect, useState } from "react";
export type SpacedReviewPolicy={firstIntervalDays:number;secondIntervalDays:number;maximumIntervalDays:number;minimumEase:number};
const DEFAULT:SpacedReviewPolicy={firstIntervalDays:1,secondIntervalDays:6,maximumIntervalDays:365,minimumEase:1.3};
const KEY="jia.admin.spaced-review-policy.v1";
function valid(p:SpacedReviewPolicy){return Number.isInteger(p.firstIntervalDays)&&p.firstIntervalDays>=1&&p.secondIntervalDays>p.firstIntervalDays&&p.maximumIntervalDays>=p.secondIntervalDays&&p.maximumIntervalDays<=730&&p.minimumEase>=1.3&&p.minimumEase<=2.5;}
export function SpacedReviewPolicyPanel(){
 const [policy,setPolicy]=useState(DEFAULT);const [message,setMessage]=useState("");
 useEffect(()=>{try{const raw=window.localStorage.getItem(KEY);if(raw){const parsed=JSON.parse(raw) as SpacedReviewPolicy;if(valid(parsed))setPolicy(parsed);}}catch{setMessage("Saved policy could not be read; defaults loaded.");}},[]);
 function save(){if(!valid(policy)){setMessage("Intervals must increase and remain within safe limits.");return;}try{window.localStorage.setItem(KEY,JSON.stringify(policy));setMessage("Policy saved on this admin browser.");}catch{setMessage("Storage unavailable. Your current draft remains on screen.");}}
 return <section className="panel" aria-labelledby="review-policy-title"><div className="panel-heading"><div><h2 id="review-policy-title">Spaced-review policy</h2><p className="muted small-copy">Transparent SM-2 interval guardrails. This configuration is browser-local until a server policy endpoint is connected.</p></div><span className="count-pill">SM-2</span></div>
 <div className="form-grid">
 <label>First interval (days)<input type="number" min={1} max={14} value={policy.firstIntervalDays} onChange={e=>setPolicy({...policy,firstIntervalDays:Number(e.target.value)})}/></label>
 <label>Second interval (days)<input type="number" min={2} max={60} value={policy.secondIntervalDays} onChange={e=>setPolicy({...policy,secondIntervalDays:Number(e.target.value)})}/></label>
 <label>Maximum interval (days)<input type="number" min={7} max={730} value={policy.maximumIntervalDays} onChange={e=>setPolicy({...policy,maximumIntervalDays:Number(e.target.value)})}/></label>
 <label>Minimum ease factor<input type="number" min={1.3} max={2.5} step={0.1} value={policy.minimumEase} onChange={e=>setPolicy({...policy,minimumEase:Number(e.target.value)})}/></label></div>
 {!valid(policy)&&<p role="alert" className="form-error">Review intervals must be increasing and within supported bounds.</p>}
 <div className="panel-actions"><button className="button button-primary" disabled={!valid(policy)} onClick={save}>Save policy</button>{message&&<p role="status">{message}</p>}</div></section>;
}
