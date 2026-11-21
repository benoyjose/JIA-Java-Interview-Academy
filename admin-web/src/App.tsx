import { lazy, Suspense, useState } from "react";
import { AppShell } from "./components/AppShell";
import { LoginPage } from "./components/LoginPage";
import { useAuth } from "./auth/AuthContext";
const DashboardPage = lazy(() => import("./features/dashboard/DashboardPage").then(module => ({ default: module.DashboardPage })));
const LessonsPage = lazy(() => import("./features/lessons/LessonsPage").then(module => ({ default: module.LessonsPage })));
const ReviewsPage = lazy(() => import("./features/reviews/ReviewsPage").then(module => ({ default: module.ReviewsPage })));
const TaxonomyPage = lazy(() => import("./features/taxonomy/TaxonomyPage").then(module => ({ default: module.TaxonomyPage })));
const QuestionBankPage = lazy(() => import("./features/questions/QuestionBankPage").then(module => ({ default: module.QuestionBankPage })));
const UsersPage = lazy(() => import("./features/users/UsersPage").then(module => ({ default: module.UsersPage })));
type Tab = "dashboard" | "lessons" | "reviews" | "taxonomy" | "questions" | "users";
export function App() {
  const { user, loading } = useAuth();
  const [activeTab, setActiveTab] = useState<Tab>("dashboard");
  const canEdit = Boolean(user?.roles.some(role => role === "EDITOR" || role === "ADMIN"));
  const canReview = Boolean(user?.roles.some(role => role === "REVIEWER" || role === "ADMIN"));
  const canManageTaxonomy = Boolean(user?.roles.some(role => role === "EDITOR" || role === "ADMIN"));
  const canManageQuestions = Boolean(user?.roles.some(role => ["EDITOR", "REVIEWER", "ADMIN"].includes(role)));
  const canManageUsers = Boolean(user?.roles.includes("ADMIN"));
  const firstAllowedTab: Tab | null = canEdit ? "lessons" : canReview ? "reviews" : null;
  const visibleTab = activeTab === "dashboard" && canManageUsers ? "dashboard"
    : activeTab === "lessons" && canEdit ? "lessons"
    : activeTab === "reviews" && canReview ? "reviews"
    : activeTab === "taxonomy" && canManageTaxonomy ? "taxonomy"
    : activeTab === "questions" && canManageQuestions ? "questions"
    : activeTab === "users" && canManageUsers ? "users" : firstAllowedTab;
  if (loading) return <div className="full-loading"><div className="brand-mark">J</div><span>Loading secure workspace…</span></div>;
  if (!user) return <LoginPage />;
  if (!visibleTab) return <div className="full-loading"><span>Your account has no content-operations permissions.</span></div>;
  return <AppShell activeTab={visibleTab} onNavigate={setActiveTab}><Suspense fallback={<div className="loading-line">Loading workspace…</div>}>
    {visibleTab === "dashboard" ? <DashboardPage /> : visibleTab === "lessons" ? <LessonsPage /> : visibleTab === "reviews" ? <ReviewsPage /> : visibleTab === "taxonomy" ? <TaxonomyPage /> : visibleTab === "questions" ? <QuestionBankPage /> : <UsersPage />}
  </Suspense></AppShell>;
}
