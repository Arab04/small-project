import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { AppLayout } from '@/components/layout/AppLayout';
import { ProtectedRoute } from '@/components/layout/ProtectedRoute';

import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { MatchesPage } from '@/pages/MatchesPage';
import { MatchDetailPage } from '@/pages/MatchDetailPage';
import { NewMatchPage } from '@/pages/NewMatchPage';
import { VideoUploadPage } from '@/pages/VideoUploadPage';
import {
  TeamsPage,
  ReportsPage,
  ClaudeChatPage,
  SettingsPage,
  ReportPage,
  PlayersPage,
} from '@/pages/StubPages';

export default function App() {
  const { init } = useAuthStore();

  // Sahifa yuklanganda — tokenni tekshirish
  useEffect(() => {
    init();
  }, [init]);

  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Protected routes */}
        <Route
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route path="/" element={<DashboardPage />} />
          <Route path="/matches" element={<MatchesPage />} />
          <Route path="/matches/new" element={<NewMatchPage />} />
          <Route path="/matches/:matchId" element={<MatchDetailPage />} />
          <Route path="/matches/:matchId/upload" element={<VideoUploadPage />} />
          <Route path="/matches/:matchId/report" element={<ReportPage />} />
          <Route path="/matches/:matchId/players" element={<PlayersPage />} />
          <Route path="/teams" element={<TeamsPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/claude" element={<ClaudeChatPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
