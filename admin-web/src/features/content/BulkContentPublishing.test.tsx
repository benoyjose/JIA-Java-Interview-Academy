import {describe,it,expect} from "vitest";
import {eligibleBulkPublishItems} from "./BulkContentPublishing";
import type {DraftLesson} from "./BulkContentPublishing";
describe("eligibleBulkPublishItems",()=>{
 it("only includes approved lessons",()=>{
  const items:DraftLesson[]=[{id:"d",title:"Draft",status:"DRAFT",version:1},{id:"r",title:"Review",status:"APPROVED",version:2},{id:"a",title:"Approved",status:"APPROVED",version:3},{id:"p",title:"Published",status:"PUBLISHED",version:4}];
  expect(eligibleBulkPublishItems(items).map(item=>item.id)).toEqual(["a"]);
 });
 it("caps each run at one hundred items",()=>{
  const items=Array.from({length:125},(_,i):DraftLesson=>({id:String(i),title:"lesson",status:"IN_REVIEW",version:1}));
  expect(eligibleBulkPublishItems(items)).toHaveLength(100);
 });
});