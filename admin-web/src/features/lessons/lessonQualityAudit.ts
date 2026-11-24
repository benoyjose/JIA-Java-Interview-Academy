export type LessonQualityInput={title?:string;objectives?:string[];estimatedMinutes?:number;questionCount?:number;tags?:string[]};
export type QualityFinding={field:string;code:"missing"|"invalid"|"too_short"};
export function auditLessonQuality(input:LessonQualityInput):QualityFinding[]{
 const f:QualityFinding[]=[];
 if(!input.title?.trim())f.push({field:"title",code:"missing"});else if(input.title.trim().length<8)f.push({field:"title",code:"too_short"});
 if(!input.objectives?.some(x=>x.trim().length>=8))f.push({field:"objectives",code:"missing"});
 if(!Number.isInteger(input.estimatedMinutes)||!input.estimatedMinutes||input.estimatedMinutes<1||input.estimatedMinutes>480)f.push({field:"estimatedMinutes",code:"invalid"});
 if(!Number.isInteger(input.questionCount)||input.questionCount!<0)f.push({field:"questionCount",code:"invalid"});
 if(!input.tags?.some(x=>x.trim()))f.push({field:"tags",code:"missing"});
 return f;
}