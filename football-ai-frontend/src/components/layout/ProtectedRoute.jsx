import { Navigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';

/**
 * Login qilingan foydalanuvchi uchun route'larni himoya qilish.
 */
export function ProtectedRoute({ children }) {
  const { isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-ink-900">
        <div className="w-7 h-7 border-2 border-lime-electric border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  return children;
}
