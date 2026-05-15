import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  Trophy,
  Users,
  FileText,
  LogOut,
  Settings,
  Sparkles,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/stores/authStore';

/**
 * Asosiy ilova layout — sidebar + content area.
 *
 * Login qilingandan keyin barcha sahifalar shu layout ichida.
 */
export function AppLayout() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navItems = [
    { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/matches', icon: Trophy, label: 'O\'yinlar' },
    { to: '/teams', icon: Users, label: 'Jamoalar' },
    { to: '/reports', icon: FileText, label: 'Hisobotlar' },
  ];

  return (
    <div className="min-h-screen flex bg-ink-900 text-ink-50">
      {/* Sidebar */}
      <aside className="w-60 border-r border-hairline border-white/[0.06] bg-ink-950 flex flex-col">
        {/* Logo */}
        <div className="px-5 py-5 border-b border-hairline border-white/[0.06]">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-lg bg-lime-electric flex items-center justify-center font-bold text-ink-900 text-sm tracking-tight">
              F
            </div>
            <div className="font-semibold text-[15px] tracking-tight">
              football<span className="text-lime-electric">.ai</span>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-3 py-4 flex flex-col gap-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] font-medium transition-colors',
                  isActive
                    ? 'bg-white/[0.06] text-ink-50'
                    : 'text-ink-200 hover:bg-white/[0.03] hover:text-ink-50'
                )
              }
            >
              <item.icon className="w-4 h-4 shrink-0" strokeWidth={1.6} />
              {item.label}
            </NavLink>
          ))}

          <div className="mt-3 pt-3 border-t border-hairline border-white/[0.06]">
            <NavLink
              to="/claude"
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] font-medium transition-colors',
                  isActive
                    ? 'bg-lime-electric/10 text-lime-electric'
                    : 'text-ink-200 hover:bg-lime-electric/5 hover:text-lime-electric'
                )
              }
            >
              <Sparkles className="w-4 h-4 shrink-0" strokeWidth={1.6} />
              Claude AI
              <span className="ml-auto text-2xs px-1.5 py-0.5 rounded bg-lime-electric/15 text-lime-electric font-mono">
                NEW
              </span>
            </NavLink>
          </div>
        </nav>

        {/* User block */}
        <div className="p-3 border-t border-hairline border-white/[0.06]">
          <div className="flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-white/[0.03] transition-colors">
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-ink-500 to-ink-700 flex items-center justify-center text-xs font-semibold shrink-0">
              {(user?.fullName || user?.username || 'A')[0].toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-[13px] font-medium truncate">
                {user?.fullName || user?.username || 'User'}
              </div>
              <div className="text-2xs text-ink-300 truncate">
                {user?.club?.name || 'Imaan Tech FC'}
              </div>
            </div>
          </div>

          <div className="flex gap-1 mt-2">
            <button
              className="flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 text-2xs text-ink-200 rounded-md hover:bg-white/[0.04]"
              onClick={() => navigate('/settings')}
            >
              <Settings className="w-3 h-3" strokeWidth={1.6} />
              Sozlamalar
            </button>
            <button
              className="flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 text-2xs text-ink-200 rounded-md hover:bg-coral/10 hover:text-coral"
              onClick={handleLogout}
            >
              <LogOut className="w-3 h-3" strokeWidth={1.6} />
              Chiqish
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  );
}
