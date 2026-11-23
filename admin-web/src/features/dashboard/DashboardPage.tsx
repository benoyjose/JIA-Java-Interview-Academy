import { useEffect, useState } from "react";
import { ApiError, api } from "../../api/client";
import type { AcademyAnalyticsSummary } from "../../api/analytics";
import { StudyPlanInsights } from "./StudyPlanInsights";

const WINDOWS = [7, 30, 90] as const;
type WindowDays = typeof WINDOWS[number];

function formatCount(value: number): string {
  return Number.isFinite(value) ? new Intl.NumberFormat().format(value) : "—";
}
function formatScore(value: number | null): string {
  return value !== null && Number.isFinite(value) ? value.toFixed(1) + "%" : "—";
}

export function DashboardPage() {
  const [windowDays, setWindowDays] = useState<WindowDays>(30);
  const [summary, setSummary] = useState<AcademyAnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    setLoading(true);
    setError(null);
    api.analyticsSummary(windowDays, controller.signal)
      .then(data => { if (current) setSummary(data); })
      .catch((cause: unknown) => {
        if (!current) return;
        setError(cause instanceof ApiError ? cause.message : "Analytics could not be loaded. Please try again.");
      })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; controller.abort(); };
  }, [windowDays, retryKey]);

  const metrics = summary ? [
    { label: "Total learners", value: formatCount(summary.totalLearners), note: "Active academy membership" },
    { label: "Active learners", value: formatCount(summary.activeLearners), note: "Activity in selected period" },
    { label: "Lessons completed", value: formatCount(summary.lessonsCompleted), note: "All-time completion records" },
    { label: "Quiz attempts", value: formatCount(summary.quizAttempts), note: "Submitted in selected period" },
  ] : [];

  return <section className="analytics-dashboard">
    <div className="page-heading">
      <div><p className="eyebrow">ACADEMY OVERVIEW</p><h1>Dashboard</h1>
        <p className="muted">{summary ? `${summary.academyCode} · Academy performance and learner engagement` : "Academy performance and learner engagement"}</p>
      </div>
      <label className="analytics-window">Reporting period
        <select aria-label="Reporting period" value={windowDays} onChange={event => setWindowDays(Number(event.target.value) as WindowDays)}>
          {WINDOWS.map(days => <option key={days} value={days}>Last {days} days</option>)}
        </select>
      </label>
    </div>
    {error && <div className="notice error" role="alert"><span>{error}</span><button className="button button-secondary" onClick={() => setRetryKey(value => value + 1)}>Retry</button></div>}
    {loading && !summary ? <div className="panel analytics-loading" role="status">Loading academy analytics…</div>
      : !summary && !error ? <div className="panel analytics-empty"><h2>No analytics available yet</h2><p className="muted">Metrics will appear after learners complete lessons or submit quizzes.</p></div>
      : summary && <>
        <div className="stat-grid analytics-stat-grid">
          {metrics.map(metric => <article className="stat-card" key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.note}</small></article>)}
        <article className="stat-card score-card"><span>Average quiz score</span><strong>{formatScore(summary.averageScorePercent)}</strong><small>Across {formatCount(summary.scoredAttempts)} scored attempts</small></article>
      </div>
      <StudyPlanInsights summary={summary} />
      <div className="panel analytics-topics">
        <div className="panel-heading"><div><h2>Most active topics</h2><p className="muted small-copy">Ranked by submitted quiz attempts in the selected period</p></div><span className="count-pill">{summary.topTopics.length} topics</span></div>
        {summary.topTopics.length === 0 ? <div className="analytics-empty compact">No submitted quiz attempts in this period.</div>
          : <div className="table-wrap"><table className="data-table"><thead><tr><th>Topic</th><th>Attempts</th><th>Average score</th></tr></thead><tbody>
            {summary.topTopics.map(topic => <tr key={topic.topicSlug}><td><strong>{topic.topicName}</strong><small>{topic.topicSlug}</small></td><td>{formatCount(topic.attempts)}</td><td>{formatScore(topic.averageScorePercent)}</td></tr>)}
          </tbody></table></div>}
      </div>
      <p className="analytics-footnote">Active learners, quiz attempts and topic scores use the selected period. Lesson completions and total learners are lifetime/current academy counts respectively.</p>
    </>}
  </section>;
}
