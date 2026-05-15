import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Plus, Search, Filter } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { matchesApi } from '@/api/matches';
import { formatDate } from '@/lib/utils';

export function MatchesPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all'); // all | analyzed | pending

  const { data: matches = [], isLoading } = useQuery({
    queryKey: ['matches'],
    queryFn: () => matchesApi.list(),
  });

  const matchList = Array.isArray(matches) ? matches : [];
  const filtered = matchList.filter((m) => {
    if (filter === 'analyzed' && !m.analyzed) return false;
    if (filter === 'pending' && m.analyzed) return false;
    if (search) {
      const q = search.toLowerCase();
      return (
        (m.ourTeamName || '').toLowerCase().includes(q)
        || (m.opponentName || '').toLowerCase().includes(q)
        || m.homeTeam?.name?.toLowerCase().includes(q)
        || m.awayTeam?.name?.toLowerCase().includes(q)
        || m.opponent?.name?.toLowerCase().includes(q)
        || m.venue?.toLowerCase().includes(q)
      );
    }
    return true;
  });

  return (
    <div>
      <div className="px-8 py-7 border-b border-hairline border-white/[0.06]">
        <div className="flex items-end justify-between mb-5">
          <div>
            <div className="text-2xs text-ink-300 tracking-wider uppercase mb-1">
              Tahlil va boshqaruv
            </div>
            <div className="text-2xl font-semibold tracking-tight">O'yinlar</div>
            <div className="text-sm text-ink-300 mt-1">
              {matchList.length} ta o'yin · {matchList.filter((m) => m.analyzed).length} tahlil qilingan
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

        <div className="flex items-center gap-3">
          <div className="flex-1 max-w-md">
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Jamoa nomi yoki maydon..."
              icon={<Search className="w-4 h-4" strokeWidth={1.6} />}
            />
          </div>
          <div className="flex gap-1 p-1 bg-white/[0.04] rounded-lg border-hairline border-white/[0.06]">
            <FilterTab active={filter === 'all'} onClick={() => setFilter('all')}>
              Hammasi
            </FilterTab>
            <FilterTab active={filter === 'analyzed'} onClick={() => setFilter('analyzed')}>
              Tahlil qilingan
            </FilterTab>
            <FilterTab active={filter === 'pending'} onClick={() => setFilter('pending')}>
              Kutilmoqda
            </FilterTab>
          </div>
        </div>
      </div>

      <div className="px-8 py-6">
        {isLoading ? (
          <div className="flex flex-col gap-2">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="card h-[68px] shimmer" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="card p-12 text-center">
            <div className="text-sm font-medium mb-1">Hech narsa topilmadi</div>
            <div className="text-2xs text-ink-300">
              {search ? 'Qidiruv natijalarini o\'zgartiring' : 'Yangi o\'yin qo\'shing'}
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {filtered.map((match) => (
              <MatchListRow key={match.id} match={match} onClick={() => navigate(`/matches/${match.id}`)} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function FilterTab({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`px-3.5 py-1.5 text-xs font-medium rounded-md transition-all ${
        active
          ? 'bg-lime-electric/15 text-lime-electric'
          : 'text-ink-200 hover:text-ink-50'
      }`}
    >
      {children}
    </button>
  );
}

function MatchListRow({ match, onClick }) {
  return (
    <div
      onClick={onClick}
      className="card hover:bg-white/[0.04] transition-all cursor-pointer p-4 flex items-center gap-4"
    >
      <div className="w-28 shrink-0">
        <div className="text-2xs text-ink-300 font-mono">
          {formatDate(match.kickoffAt)}
        </div>
        <div className="text-2xs text-ink-300 mt-0.5 truncate">
          {match.league || 'Mahalliy'}
        </div>
      </div>

      <div className="flex-1 flex items-center gap-3 min-w-0">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-3">
            <div className="text-sm font-medium truncate">
              {match.ourTeamName || match.homeTeam?.name || 'Uy jamoasi'}
            </div>
            <div className="flex items-center gap-1.5 px-2.5 py-0.5 bg-ink-800 rounded text-sm font-semibold tabular shrink-0">
              <span>{match.ourScore ?? match.homeScore ?? '—'}</span>
              <span className="text-ink-400">:</span>
              <span>{match.opponentScore ?? match.awayScore ?? '—'}</span>
            </div>
            <div className="text-sm text-ink-200 truncate">
              {match.opponentName || match.awayTeam?.name || match.opponent?.name || 'Mehmon'}
            </div>
          </div>
          {match.venue && (
            <div className="text-2xs text-ink-300 mt-1 truncate">
              {match.venue}
            </div>
          )}
        </div>
      </div>

      <div className="shrink-0">
        {match.analyzed ? (
          <Badge variant="live">Tahlil tayyor</Badge>
        ) : match.videoUploaded ? (
          <Badge variant="info">Tahlil kutmoqda</Badge>
        ) : (
          <Badge variant="neutral">Video yo'q</Badge>
        )}
      </div>
    </div>
  );
}
