import { lazy, Suspense, useState } from "react";
import { AppShell } from "./components/AppShell";
import { LoginPage } from "./components/LoginPage";
import { useAuth } from "./auth/AuthContext";
const LessonsPage = lazy(() => import("./features/lessons/LessonsPage").then(module => ({ default: module.LessonsPage })));
const ReviewsPage = lazy(() => import("./features/reviews/ReviewsPage").then(module => ({ default: module.ReviewsPage })));
type Tab = "lessons" | "reviews";
export function App() {
  const { user, loading } = useAuth(); const [activeTab, setActiveTab] = useState<Tab>("lessons");
  if (loading) return <div className="full-loading"><div className="brand-mark">J</div><span>Loading secure workspace…</span></div>;
  if (!user) return <LoginPage />;
  return <AppShell activeTab={activeTab} onNavigate={setActiveTab}><Suspense fallback={<div className="loading-line">Loading workspace…</div>}>
    {activeTab === "lessons" ? <LessonsPage /> : <ReviewsPage />}
  </Suspense></AppShell>;
}