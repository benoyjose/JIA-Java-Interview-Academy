import type { AcademyAnalyticsSummary } from "../../api/analytics";

type Props = { summary: AcademyAnalyticsSummary };
export function masterySignalCounts(summary: AcademyAnalyticsSummary) {
  const scored = summary.topTopics.filter(topic => topic.averageScorePercent !== null && topic.attempts > 0);
  return { developing: scored.filter(topic => (topic.averageScorePercent ?? 0) < 60).length,
    practicing: scored.filter(topic => (topic.averageScorePercent ?? 0) >= 60 && (topic.averageScorePercent ?? 0) < 85).length,
    mastered: scored.filter(topic => (topic.averageScorePercent ?? 0) >= 85).length };
}
export function MasteryRiskPanel({ summary }: Props) {
  const scored = summary.topTopics.filter(topic => topic.averageScorePercent !== null && topic.attempts > 0);
  const counts = masterySignalCounts(summary);
  const developing = scored.filter(topic => (topic.averageScorePercent ?? 0) < 60);
  return <section className="panel mastery-risk-panel" aria-labelledby="mastery-risk-title">
    <div className="panel-heading"><div><h2 id="mastery-risk-title">Topic mastery signals</h2>
      <p className="muted small-copy">Aggregate quiz accuracy, not individual learner certification.</p></div></div>
    <div className="stat-grid">
      <article className="stat-card"><span>Developing</span><strong>{counts.developing}</strong><small>Topics below 60%</small></article>
      <article className="stat-card"><span>Practicing</span><strong>{counts.practicing}</strong><small>Topics from 60–84%</small></article>
      <article className="stat-card"><span>Mastery signal</span><strong>{counts.mastered}</strong><small>Topics at 85% or above</small></article>
    </div>
    {developing.length > 0 && <ul>{developing.map(topic => <li key={topic.topicSlug}>{topic.topicName} · {topic.averageScorePercent?.toFixed(1)}% average</li>)}</ul>}
    {scored.length === 0 && <p className="muted">No scored quiz activity in this reporting window.</p>}
  </section>;
}