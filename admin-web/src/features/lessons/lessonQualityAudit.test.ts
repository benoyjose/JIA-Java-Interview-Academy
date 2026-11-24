import {describe,expect,it} from "vitest";
import {auditLessonQuality} from "./lessonQualityAudit";
describe("lesson quality audit",()=>{
 it("returns field-specific findings",()=>expect(auditLessonQuality({title:"Intro",estimatedMinutes:0,questionCount:-1})).toHaveLength(5));
 it("accepts complete content",()=>expect(auditLessonQuality({title:"Spring transactions deep dive",objectives:["Explain transaction boundaries"],estimatedMinutes:30,questionCount:8,tags:["spring"]})).toEqual([]));
});