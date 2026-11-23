import type { AcademyAnalyticsSummary } from "../../api/analytics";
import { buildStudyFocus } from "./studyPlanInsights";
export function StudyPlanInsights({ summary }: { summary: AcademyAnalyticsSummary }) {
  const focus = buildStudyFocus(summary);
  return <section className="panel" aria-labelledby="study-plan-insights-title">
    <div className="panel-heading"><div><h2 id="study-plan-insights-title">Curriculum focus plan</h2>
      <p className="muted small-copy">Aggregate signals for editorial and practice planning—not individual learner data.</p></div>
      <span className="count-pill">{focus.length} focus areas</span></div>
    {focus.length === 0 ? <p className="muted">Current reported topics are at or above the 80% mastery signal.</p>
      : <ol className="analytics-topic-list">{focus.map(item => <li key={item.topicSlug}>
          <strong>{item.topicName}</strong><span>{item.score === null ? "No scored baseline" : item.score.toFixed(1) + "% average"}</span>
          <p className="muted small-copy">{item.action}</p></li>)}</ol>}
  </section>;
}
