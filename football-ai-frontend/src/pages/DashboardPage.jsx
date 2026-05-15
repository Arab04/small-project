import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Plus, TrendingUp, Activity, Trophy, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { matchesApi } from '@/api/matches';
import { formatDate } from '@/lib/utils';
import { useAuthStore } from '@/stores/authStore';

export function DashboardPage() {
  const { user } = useAuthStore();
  const navigate = useNavigate();

  const { data: matches = [], isLoading } = useQuery({
    queryKey: ['matches'],
    queryFn: () => matchesApi.list({ limit: 5 }),
  });

  const recentMatches = Array.isArray(matches) ? matches.slice(0, 5) : [];
  const analyzedCount = recentMatches.filter((m) => m.analyzed).length;

  return (
    <div className="min-h-full">
      {/* Top header */}
      <div className="px-8 py-7 border-b border-hairline border-white/[0.06]">
        <div className="flex items-end justify-between">
          <div>
            <div className="text-2xs text-ink-300 font-medium tracking-wider uppercase mb-1">
              {formatDate(new Date().toISOString())}
            </div>
            <div className="text-2xl font-semibold tracking-tight">
              Salom, {user?.fullName?.split(' ')[0] || user?.username || 'Murabbiy'}
            </div>
            <div className="text-sm text-ink-300 mt-1">
              {user?.club?.name || 'Imaan Tech FC'} · taktik tahlil platformasi
            </div>
          </div>
          <Button
            variant="primary"
            icon={<Plus className="w-4 h-4" strokeWidth={2} />}
            onClick={() => navigate('/matches/new')}
          >
            Yangi o'yin
          </Button>
        </div>
      </div>

      {/* Stats overview */}
      <div className="grid grid-cols-4 gap-px bg-white/[0.06] border-b border-hairline border-white/[0.06]">
        <StatCard
          icon={<Trophy className="w-4 h-4 text-lime-electric" strokeWidth={1.8} />}
          label="O'YINLAR"
          value={recentMatches.length}
          sub="Tahlil qilingan"
        />
        <StatCard
          icon={<Activity className="w-4 h-4 text-lime-electric" strokeWidth={1.8} />}
          label="TUGAGAN TAHLIL"
          value={analyzedCount}
          sub="Hisobot tayyor"
        />
        <StatCard
          icon={<TrendingUp className="w-4 h-4 text-lime-electric" strokeWidth={1.8} />}
          label="JAMI O'YINCHILAR"
          value={recentMatches.length * 22}
          sub="Tracking qilingan"
        />
        <StatCard
          icon={<Sparkles className="w-4 h-4 text-lime-electric" strokeWidth={1.8} />}
          label="CLAUDE INSIGHTS"
          value={analyzedCount * 3}
          sub="Avtomatik xulosa"
        />
      </div>

      {/* Recent matches */}
      <div className="px-8 py-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <div className="text-base font-semibold tracking-tight">So'nggi o'yinlar</div>
            <div className="text-xs text-ink-300 mt-0.5">Eng yangi 5 ta tahlil</div>
          </div>
          <button
            onClick={() => navigate('/matches')}
            className="text-xs text-lime-electric hover:underline underline-offset-2"
          >
            Hammasi →
          </button>
        </div>

        {isLoading ? (
          <div className="flex flex-col gap-2">
            {[1, 2, 3].map((i) => (
              <div key={i} className="card h-[68px] shimmer" />
            ))}
          </div>
        ) : recentMatches.length === 0 ? (
          <EmptyState onAction={() => navigate('/matches/new')} />
        ) : (
          <div className="flex flex-col gap-2">
            {recentMatches.map((match) => (
              <MatchRow key={match.id} match={match} onClick={() => navigate(`/matches/${match.id}`)} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function StatCard({ icon, label, value, sub }) {
  return (
    <div className="bg-ink-900 px-6 py-5">
      <div className="flex items-center justify-between mb-3">
        <span className="text-2xs text-ink-300 tracking-wider font-medium font-mono">
          {label}
        </span>
        {icon}
      </div>
      <div className="text-3xl font-semibold tracking-tighter tabular leading-none">
        {value}
      </div>
      <div className="text-2xs text-ink-300 mt-2">{sub}</div>
    </div>
  );
}

function MatchRow({ match, onClick }) {
  return (
    <div
      onClick={onClick}
      className="card hover:bg-white/[0.04] transition-all cursor-pointer p-4 flex items-center gap-4"
    >
      <div className="text-2xs text-ink-300 font-mono w-24 shrink-0">
        {formatDate(match.kickoffAt || match.matchDate)}
      </div>

      <div className="flex-1 flex items-center gap-3 min-w-0">
        <div className="text-sm font-medium truncate">
          {match.ourTeamName || match.homeTeam?.name || 'Uy'}
        </div>
        <div className="flex items-center gap-2 px-2 py-0.5 bg-ink-800 rounded text-xs font-semibold tabular shrink-0">
          <span>{match.ourScore ?? match.homeScore ?? '—'}</span>
          <span className="text-ink-400">:</span>
          <span>{match.opponentScore ?? match.awayScore ?? '—'}</span>
        </div>
        <div className="text-sm text-ink-200 truncate">
          {match.opponentName || match.awayTeam?.name || match.opponent?.name || 'Mehmon'}
        </div>
      </div>

      <div className="shrink-0">
        {match.analyzed ? (
          <Badge variant="live">Tahlil tayyor</Badge>
        ) : match.videoUploaded ? (
          <Badge variant="info">Tahlil kutilmoqda</Badge>
        ) : (
          <Badge variant="neutral">Video yo'q</Badge>
        )}
      </div>
    </div>
  );
}

function EmptyState({ onAction }) {
  return (
    <div className="card p-12 text-center">
      <div className="w-14 h-14 mx-auto rounded-2xl bg-lime-electric/10 flex items-center justify-center mb-4">
        <Trophy className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />
      </div>
      <div className="text-base font-semibold mb-1">Hali o'yin yo'q</div>
      <div className="text-sm text-ink-300 mb-5 max-w-sm mx-auto">
        Birinchi o'yinni qo'shib, video yuklang va AI tahlilni boshlang
      </div>
      <Button variant="primary" icon={<Plus className="w-4 h-4" />} onClick={onAction}>
        Birinchi o'yinni qo'shish
      </Button>
    </div>
  );
}
