import {describe,expect,it} from "vitest";
import {bulkReviewProgress,reconcileReviewSelection,selectableReviewIds} from "./bulkReviewSelection";
describe("bulk review selection safeguards",()=>{
 it("selects only pending rows and caps at fifty",()=>{const rows=Array.from({length:65},(_,i)=>({taskId:String(i),status:i===3?"APPROVED":"PENDING"}));expect(selectableReviewIds(rows)).toHaveLength(50);expect(selectableReviewIds(rows)).not.toContain("3");});
 it("removes stale ids and duplicates after refresh",()=>expect(reconcileReviewSelection(["x","x","gone"],["x","y"])).toEqual(["x"]));
 it("reports partial failures rather than atomic success",()=>expect(bulkReviewProgress(8,5,2)).toEqual({total:8,completed:5,failed:2,remaining:3,success:3}));
});
