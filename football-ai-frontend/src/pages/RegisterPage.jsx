import { useState } from 'react';
import { useNavigate, Link, Navigate } from 'react-router-dom';
import { Building2, User, Mail, Lock } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useAuthStore } from '@/stores/authStore';

export function RegisterPage() {
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    clubName: '',
    password: '',
  });
  const { register, isLoading, error, isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  if (isAuthenticated()) {
    return <Navigate to="/" replace />;
  }

  const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    const ok = await register(form);
    if (ok) navigate('/', { replace: true });
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-8 bg-ink-900">
      <div className="w-full max-w-[440px]">
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
            <div className="text-lg font-semibold tracking-tight">Klubni ro'yxatdan o'tkazish</div>
            <div className="text-sm text-ink-300 mt-1">
              Klubingizni yaratib, jamoa va o'yinlarni qo'shing
            </div>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Input
              label="Klub nomi"
              value={form.clubName}
              onChange={update('clubName')}
              placeholder="Imaan Tech FC"
              icon={<Building2 className="w-4 h-4" strokeWidth={1.6} />}
              required
            />
            <Input
              label="To'liq ism"
              value={form.fullName}
              onChange={update('fullName')}
              placeholder="Abdulloh Boltayev"
              icon={<User className="w-4 h-4" strokeWidth={1.6} />}
              required
            />
            <Input
              label="Email"
              type="email"
              value={form.email}
              onChange={update('email')}
              placeholder="abdulloh@imaantech.uz"
              icon={<Mail className="w-4 h-4" strokeWidth={1.6} />}
              required
            />
            <Input
              type="password"
              label="Parol"
              value={form.password}
              onChange={update('password')}
              placeholder="••••••••"
              icon={<Lock className="w-4 h-4" strokeWidth={1.6} />}
              required
              hint="Kamida 8 belgi"
              error={error}
            />

            <Button type="submit" variant="primary" size="lg" loading={isLoading} className="mt-2">
              Hisob yaratish
            </Button>
          </form>

          <div className="mt-5 pt-5 border-t border-hairline border-white/[0.06] text-center text-sm text-ink-300">
            Hisobingiz bormi?{' '}
            <Link to="/login" className="text-lime-electric hover:underline underline-offset-2">
              Tizimga kirish
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
