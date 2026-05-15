import { useState } from 'react';
import { useNavigate, Link, Navigate } from 'react-router-dom';
import { Lock, Mail } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useAuthStore } from '@/stores/authStore';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const { login, isLoading, error, isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  if (isAuthenticated()) {
    return <Navigate to="/" replace />;
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    const ok = await login(email, password);
    if (ok) {
      navigate('/', { replace: true });
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-4 bg-ink-900">
      {/* Background gradient */}
      <div
        className="fixed inset-0 pointer-events-none opacity-20"
        style={{
          background: 'radial-gradient(circle at 20% 50%, rgba(197,255,80,0.08) 0%, transparent 50%), radial-gradient(circle at 80% 80%, rgba(107,180,255,0.05) 0%, transparent 50%)',
        }}
      />

      <div className="relative w-full max-w-[400px]">
        {/* Logo */}
        <div className="mb-8 flex items-center justify-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-lime-electric flex items-center justify-center font-bold text-ink-900 text-lg tracking-tighter">
            F
          </div>
          <div className="font-semibold text-xl tracking-tight">
            football<span className="text-lime-electric">.ai</span>
          </div>
        </div>

        <div className="card p-7">
          <div className="mb-6">
            <div className="text-lg font-semibold tracking-tight">Tizimga kirish</div>
            <div className="text-sm text-ink-300 mt-1">
              Klubingiz hisobiga kirib, video tahlilni boshlang
            </div>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Input
              type="email"
              label="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="murabbiy@klub.uz"
              icon={<Mail className="w-4 h-4" strokeWidth={1.6} />}
              required
              autoFocus
            />
            <Input
              type="password"
              label="Parol"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              icon={<Lock className="w-4 h-4" strokeWidth={1.6} />}
              required
              error={error}
            />

            <Button type="submit" variant="primary" size="lg" loading={isLoading}>
              Kirish
            </Button>
          </form>

          <div className="mt-5 pt-5 border-t border-hairline border-white/[0.06] text-center text-sm text-ink-300">
            Hisobingiz yo'qmi?{' '}
            <Link
              to="/register"
              className="text-lime-electric hover:underline underline-offset-2"
            >
              Klubni ro'yxatdan o'tkazish
            </Link>
          </div>
        </div>

        <div className="mt-4 text-center text-2xs text-ink-300">
          v2.0 — futbol o'yin tahlil platformasi
        </div>
      </div>
    </div>
  );
}
