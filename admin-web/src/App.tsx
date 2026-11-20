import { lazy, Suspense, useState } from "react";
import { AppShell } from "./components/AppShell";
import { LoginPage } from "./components/LoginPage";
import { useAuth } from "./auth/AuthContext";
const LessonsPage = lazy(() => import("./features/lessons/LessonsPage").then(module => ({ default: module.LessonsPage })));
const ReviewsPage = lazy(() => import("./features/reviews/ReviewsPage").then(module => ({ default: module.ReviewsPage })));
type Tab = "lessons" | "reviews";
export function App() {
  const { user, loading } = useAuth(); const [activeTab, setActiveTab] = useState<Tab>("lessons");
  const canEdit = Boolean(user?.roles.some(role => role === "EDITOR" || role === "ADMIN"));
  const canReview = Boolean(user?.roles.some(role => role === "REVIEWER" || role === "ADMIN"));
  const firstAllowedTab: Tab | null = canEdit ? "lessons" : canReview ? "reviews" : null;
  const visibleTab = activeTab === "lessons" && canEdit ? "lessons"
    : activeTab === "reviews" && canReview ? "reviews" : firstAllowedTab;
  if (loading) return <div className="full-loading"><div className="brand-mark">J</div><span>Loading secure workspace…</span></div>;
  if (!user) return <LoginPage />;
  if (!visibleTab) return <div className="full-loading"><span>Your account has no content-operations permissions.</span></div>;
  return <AppShell activeTab={visibleTab} onNavigate={setActiveTab}><Suspense fallback={<div className="loading-line">Loading workspace…</div>}>
    {visibleTab === "lessons" ? <LessonsPage /> : <ReviewsPage />}
  </Suspense></AppShell>;
}