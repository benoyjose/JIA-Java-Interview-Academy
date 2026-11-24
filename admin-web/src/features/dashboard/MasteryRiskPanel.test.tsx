import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MasteryRiskPanel } from "./MasteryRiskPanel";
describe("MasteryRiskPanel",()=>{it("labels aggregate accuracy as signals rather than learner certification",()=>{
 const summary:any={topTopics:[{topicSlug:"jvm",topicName:"JVM",attempts:12,averageScorePercent:52},{topicSlug:"spring",topicName:"Spring",attempts:5,averageScorePercent:90}]};
 render(<MasteryRiskPanel summary={summary}/>);
 expect(screen.getByText(/not individual learner certification/i)).toBeTruthy();
 expect(screen.getByText("JVM · 52.0% average")).toBeTruthy();
});});