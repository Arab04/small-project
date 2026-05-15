import { Badge } from '@/components/ui/Badge';
import { formatDate } from '@/lib/utils';

/**
 * Match'ning yuqori "hero" bloki — jamoa logolari, hisob, sana.
 *
 * Props:
 *   match: { id, homeTeam, awayTeam, homeScore, awayScore, kickoffAt, venue, league, ... }
 *   analysisStatus: 'COMPLETED' | 'ANALYZING' | 'PENDING' | null
 */
export function MatchHero({ match, analysisStatus }) {
  if (!match) return null;

  const homeWon = match.homeScore > match.awayScore;
  const awayWon = match.awayScore > match.homeScore;

  return (
    <div className="px-6 py-7 border-b border-hairline border-white/[0.06]">
      <div className="flex items-end justify-between gap-6">
        <div className="flex-1 min-w-0">
          {/* League + venue */}
          <div className="flex items-center gap-2 mb-3 flex-wrap">
            <span className="text-2xs text-ink-300 font-medium tracking-wider uppercase">
              {match.league || 'Liga'} · MD {match.matchday || '—'}
            </span>
            <span className="w-1 h-1 rounded-full bg-ink-400" />
            <span className="text-2xs text-ink-300">
              {formatDate(match.kickoffAt)}
              {match.venue && ` · ${match.venue}`}
            </span>
          </div>

          {/* Teams + scoreline */}
          <div className="flex items-center gap-6 flex-wrap">
            {/* Home team */}
            <div className="flex items-center gap-3">
              <TeamCrest team={match.homeTeam} variant="home" />
              <div className="text-[22px] font-semibold tracking-tight">
                {match.ourTeamName || match.homeTeam?.name || 'Uy jamoasi'}
              </div>
            </div>

            {/* Score */}
            <div className="flex items-center gap-3.5">
              <div
                className={`text-[44px] font-bold tracking-tighter tabular leading-none ${
                  awayWon ? 'text-ink-300' : 'text-ink-50'
                }`}
              >
                {match.ourScore ?? match.homeScore ?? '—'}
              </div>
              <div className="text-[22px] text-ink-400 font-light">:</div>
              <div
                className={`text-[44px] font-bold tracking-tighter tabular leading-none ${
                  homeWon ? 'text-ink-300' : 'text-ink-50'
                }`}
              >
                {match.opponentScore ?? match.awayScore ?? '—'}
              </div>
            </div>

            {/* Away team */}
            <div className="flex items-center gap-3">
              <div className="text-[22px] font-semibold tracking-tight text-ink-100">
                {match.opponentName || match.awayTeam?.name || 'Mehmon jamoasi'}
              </div>
              <TeamCrest team={match.awayTeam} variant="away" />
            </div>
          </div>
        </div>

        {/* Right side - status */}
        <div className="flex flex-col items-end gap-2 shrink-0">
          {analysisStatus === 'COMPLETED' && (
            <Badge variant="live" className="text-[11px] tracking-wider">
              Tahlil tugadi
            </Badge>
          )}
          {analysisStatus === 'ANALYZING' && (
            <Badge variant="info" className="text-[11px] tracking-wider">
              Tahlil qilinmoqda
            </Badge>
          )}
          {analysisStatus === 'FAILED' && (
            <Badge variant="warning" className="text-[11px] tracking-wider">
              Tahlil xatosi
            </Badge>
          )}

          {match.analysisDuration && (
            <div className="flex flex-col items-end gap-0.5 text-[11px] text-ink-300 font-mono leading-relaxed">
              <div>{Math.floor(match.analysisDuration / 60)}:{String(Math.floor(match.analysisDuration % 60)).padStart(2, '0')} captured</div>
              {match.framesProcessed && <div>{match.framesProcessed.toLocaleString()} frames</div>}
              {match.eventsCount && <div>{match.eventsCount} events</div>}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function TeamCrest({ team, variant = 'home' }) {
  const initials = (team?.name || (variant === 'home' ? 'HM' : 'AW'))
    .split(' ')
    .map((w) => w[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  // Random gradient based on team name (deterministic)
  const gradients = {
    home: 'from-blue-900/80 to-blue-950',
    away: 'from-red-900/80 to-red-950',
  };

  return (
    <div
      className={`w-9 h-9 rounded-lg bg-gradient-to-br ${gradients[variant]} flex items-center justify-center font-bold text-[13px] tracking-tighter`}
      style={{
        background: variant === 'home'
          ? 'linear-gradient(135deg, #2d3a5f, #1a2340)'
          : 'linear-gradient(135deg, #5f1a2d, #3a0f1f)',
      }}
    >
      {initials}
    </div>
  );
}
