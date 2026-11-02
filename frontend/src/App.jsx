import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import NotFoundPage from './pages/NotFoundPage.jsx';
import WorkspacePage from './pages/WorkspacePage.jsx';
import AuthPage from './pages/AuthPage.jsx';
import AccountPage from './pages/AccountPage.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import AdminRoute, { UserExperienceRoute } from './components/AdminRoute.jsx';
import { AdminShell, AdminDashboardPage, AdminUsersPage, AdminUserDetailPage, AdminRecordsPage, AdminSettingsPage } from './pages/AdminConsole.jsx';
import { AuthProvider } from './auth/AuthContext.jsx';

export function AppRoutes() {
  return <Routes>
    <Route path="/" element={<Navigate to="/overview" replace />} />
    <Route path="/overview" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/profile" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/onboarding" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/assessment" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/match" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/results/:id" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/careers" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/careers/:socCode" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/compare" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/roadmap" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/resume" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/skill-gaps" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/projects" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/job-matcher" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/applications" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/interview" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/saved" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/settings" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/login" element={<AuthPage mode="login" />} />
    <Route path="/register" element={<AuthPage mode="register" />} />
    <Route path="/admin/login" element={<AuthPage mode="admin" />} />
    <Route path="/dashboard" element={<Navigate to="/overview" replace />} />
    <Route path="/career-roles" element={<Navigate to="/careers" replace />} />
    <Route path="/resume-analyzer" element={<Navigate to="/resume" replace />} />
    <Route path="/interviews" element={<Navigate to="/interview" replace />} />
    <Route path="/mentor" element={<UserExperienceRoute><WorkspacePage /></UserExperienceRoute>} />
    <Route path="/account" element={<ProtectedRoute><AccountPage /></ProtectedRoute>} />
    <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
    <Route path="/admin/dashboard" element={<AdminRoute><AdminShell><AdminDashboardPage /></AdminShell></AdminRoute>} />
    <Route path="/admin/users" element={<AdminRoute><AdminShell><AdminUsersPage /></AdminShell></AdminRoute>} />
    <Route path="/admin/users/:id" element={<AdminRoute><AdminShell><AdminUserDetailPage /></AdminShell></AdminRoute>} />
    <Route path="/admin/profiles" element={<AdminRoute><AdminShell><AdminRecordsPage type="profiles" /></AdminShell></AdminRoute>} />
    <Route path="/admin/resumes" element={<AdminRoute><AdminShell><AdminRecordsPage type="resumes" /></AdminShell></AdminRoute>} />
    <Route path="/admin/assessments" element={<AdminRoute><AdminShell><AdminRecordsPage type="assessments" /></AdminShell></AdminRoute>} />
    <Route path="/admin/roadmaps" element={<AdminRoute><AdminShell><AdminRecordsPage type="roadmaps" /></AdminShell></AdminRoute>} />
    <Route path="/admin/applications" element={<AdminRoute><AdminShell><AdminRecordsPage type="applications" /></AdminShell></AdminRoute>} />
    <Route path="/admin/settings" element={<AdminRoute><AdminShell><AdminSettingsPage /></AdminShell></AdminRoute>} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes>;
}

export default function App() {
  return <BrowserRouter><AuthProvider><AppRoutes /></AuthProvider></BrowserRouter>;
}
