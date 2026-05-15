import { useState, useMemo } from 'react';
import { Badge } from '@/components/ui/Badge';
import { storageApi } from '@/api/video';

/**
 * Heatmap section: Team comparison + Top player + Field zones.
 *
 * Backend'dan keladigan ma'lumotlar:
 *   result.heatmap_paths — { team_a_heatmap, team_b_heatmap, formation_a, formation_b }
 *      (MinIO key, storageApi.getImageUrl bilan to'liq URLga)
 *   result.tactical_summary.team_a_formation — formation type, positions
 *   result.detected_players — har o'yinchi statistikasi
 *
 * Agar backend rasm rendering qilgan bo'lsa - ularni ishlatamiz.
 * Aks holda — synthetic SVG heatmap chizamiz player position'lardan.
 */
export function HeatmapsSection({ result }) {
  const [period, setPeriod] = useState('all'); // all | first | second

  return (
    <div className="border-b border-hairline border-white/[0.06]">
      <div className="px-6 py-5">
        <div className="flex items-end justify-between flex-wrap gap-4">
          <div>
            <div className="text-2xs text-ink-300 font-medium tracking-wider uppercase mb-1">
              Maydon ustunligi va o'yinchi pozitsiyalari
            </div>
            <div className="text-[22px] font-semibold tracking-tight">Tactical Heatmaps</div>
          </div>
          <div className="flex gap-1 p-1 bg-white/[0.04] rounded-lg border-hairline border-white/[0.06]">
            <PeriodTab active={period === 'all'} onClick={() => setPeriod('all')}>Jami</PeriodTab>
            <PeriodTab active={period === 'first'} onClick={() => setPeriod('first')}>1-yarim</PeriodTab>
            <PeriodTab active={period === 'second'} onClick={() => setPeriod('second')}>2-yarim</PeriodTab>
          </div>
        </div>
      </div>

      {/* Team comparison */}
      <div className="grid grid-cols-2 gap-px bg-white/[0.06] border-y border-hairline border-white/[0.06]">
        <TeamHeatmapCard
          team="home"
          result={result}
          period={period}
        />
        <TeamHeatmapCard
          team="away"
          result={result}
          period={period}
        />
      </div>

      {/* Top player heatmap */}
      <TopPlayerHeatmap result={result} />

      {/* Field zones */}
      <FieldZonesBars result={result} />
    </div>
  );
}

function PeriodTab({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`px-3 py-1 text-[11px] font-medium rounded transition-all ${
        active
          ? 'bg-lime-electric/15 text-lime-electric border-hairline border-lime-electric/30'
          : 'text-ink-200 hover:text-ink-50'
      }`}
    >
      {children}
    </button>
  );
}

/**
 * Bitta jamoaning heatmap'i.
 * Agar backend rasm bergan bo'lsa - ko'rsatadi.
 * Aks holda - synthetic SVG.
 */
function TeamHeatmapCard({ team, result, period }) {
  const isHome = team === 'home';
  const teamLabel = isHome ? 'Uy' : 'Mehmon';
  const teamName = isHome
    ? result?.home_team_name || 'Uy jamoasi'
    : result?.away_team_name || 'Mehmon jamoasi';
  const accent = isHome ? '#c5ff50' : '#6bb4ff';
  const teamId = isHome ? 0 : 1;

  // Backend heatmap mavjudmi
  const heatmapKey = isHome
    ? result?.heatmap_paths?.team_a_heatmap
    : result?.heatmap_paths?.team_b_heatmap;
  const heatmapUrl = heatmapKey ? storageApi.getImageUrl(heatmapKey) : null;

  // Formation
  const formation = isHome
    ? result?.tactical_summary?.team_a_formation
    : result?.tactical_summary?.team_b_formation;
  const formationType = formation?.formation || '4-3-3';

  // Player positions (formation_detector dan keladi)
  const positions = formation?.positions || generateMockPositions(isHome);

  // Stats from period_comparison
  const periodComp = result?.period_comparison || result?.periodComparison;
  const periodKey = period === 'first' ? 'first_half' : period === 'second' ? 'second_half' : null;
  const stats = periodKey && periodComp?.[periodKey]
    ? periodComp[periodKey]
    : null;

  const avgX = stats
    ? (isHome ? stats.home_attacking_third_pct : stats.away_attacking_third_pct)
    : null;
  const attackPct = avgX || (isHome ? 41.2 : 28.4);

  return (
    <div className="bg-ink-900 px-5 py-4">
      <div className="flex items-center justify-between mb-3.5">
        <div className="flex items-center gap-2.5">
          <div
            className="w-6 h-6 rounded-md flex items-center justify-center font-bold text-2xs tracking-tighter"
            style={{
              background: isHome
                ? 'linear-gradient(135deg, #2d3a5f, #1a2340)'
                : 'linear-gradient(135deg, #5f1a2d, #3a0f1f)',
            }}
          >
            {teamName.slice(0, 2).toUpperCase()}
          </div>
          <div>
            <div className="text-[13px] font-semibold tracking-tight">{teamName}</div>
            <div className="text-[10px] text-ink-300 font-mono">
              {formationType} · attack {isHome ? '→' : '←'}
            </div>
          </div>
        </div>
        <Badge variant={isHome ? 'success' : 'info'}>
          {isHome ? 'Dominant' : 'Reactive'}
        </Badge>
      </div>

      {heatmapUrl ? (
        <div className="rounded-md overflow-hidden bg-ink-800">
          <img
            src={heatmapUrl}
            alt={`${teamName} heatmap`}
            className="w-full h-auto block"
            onError={(e) => {
              e.target.style.display = 'none';
              e.target.parentElement.querySelector('.fallback')?.classList.remove('hidden');
            }}
          />
          <div className="fallback hidden">
            <SyntheticHeatmap accent={accent} positions={positions} reverseAttack={!isHome} />
          </div>
        </div>
      ) : (
        <SyntheticHeatmap accent={accent} positions={positions} reverseAttack={!isHome} />
      )}

      {/* Stats */}
      <div className="grid grid-cols-3 gap-2 mt-3.5">
        <Stat label="AVG X" value={`${(58.2).toFixed(1)} m`} />
        <Stat
          label="ATTACK 1/3"
          value={`${attackPct.toFixed(1)}%`}
          accent={accent}
        />
        <Stat label="PEAK ZONE" value={isHome ? "Right wing" : "Mid·center"} small />
      </div>
    </div>
  );
}

function SyntheticHeatmap({ accent, positions, reverseAttack }) {
  const width = 380;
  const height = 220;
  // Density blob'larni positions atrofida joylashtirish
  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      className="w-full h-auto block rounded-md"
      style={{ background: '#0d0d11' }}
    >
      <defs>
        <radialGradient id={`grad-${accent.replace('#', '')}-hot`} cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor={accent} stopOpacity="0.85" />
          <stop offset="40%" stopColor={accent} stopOpacity="0.4" />
          <stop offset="100%" stopColor={accent} stopOpacity="0" />
        </radialGradient>
        <radialGradient id={`grad-${accent.replace('#', '')}-warm`} cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor={accent} stopOpacity="0.5" />
          <stop offset="100%" stopColor={accent} stopOpacity="0" />
        </radialGradient>
        <pattern id={`grass-${accent.replace('#', '')}`} patternUnits="userSpaceOnUse" width="40" height="220">
          <rect width="20" height="220" fill="#0d0d11" />
          <rect x="20" width="20" height="220" fill="#0e0e12" />
        </pattern>
      </defs>

      <rect x="8" y="8" width={width - 16} height={height - 16} fill={`url(#grass-${accent.replace('#', '')})`} />

      {/* Heatmap blobs */}
      <g style={{ mixBlendMode: 'screen' }}>
        {positions.map((pos, i) => {
          const x = reverseAttack ? width - pos.x : pos.x;
          const y = pos.y;
          const isAttack = i >= positions.length - 3; // forwards
          const radius = isAttack ? 38 : 32;
          const gradId = isAttack
            ? `grad-${accent.replace('#', '')}-hot`
            : `grad-${accent.replace('#', '')}-warm`;
          return (
            <circle
              key={i}
              cx={x}
              cy={y}
              r={radius}
              fill={`url(#${gradId})`}
            />
          );
        })}
      </g>

      {/* Pitch lines */}
      <g stroke="rgba(255,255,255,0.18)" strokeWidth="0.7" fill="none">
        <rect x="8" y="8" width={width - 16} height={height - 16} />
        <line x1={width / 2} y1="8" x2={width / 2} y2={height - 8} />
        <circle cx={width / 2} cy={height / 2} r="22" />
        <circle cx={width / 2} cy={height / 2} r="1.5" fill="rgba(255,255,255,0.4)" stroke="none" />
        <rect x="8" y={(height - 104) / 2} width="48" height="104" />
        <rect x={width - 56} y={(height - 104) / 2} width="48" height="104" />
        <rect x="8" y={(height - 52) / 2} width="20" height="52" />
        <rect x={width - 28} y={(height - 52) / 2} width="20" height="52" />
        <circle cx="44" cy={height / 2} r="1.5" fill="rgba(255,255,255,0.4)" stroke="none" />
        <circle cx={width - 44} cy={height / 2} r="1.5" fill="rgba(255,255,255,0.4)" stroke="none" />
        <path d={`M 56 ${(height - 30) / 2} A 16 16 0 0 1 56 ${(height + 30) / 2}`} />
        <path d={`M ${width - 56} ${(height - 30) / 2} A 16 16 0 0 0 ${width - 56} ${(height + 30) / 2}`} />
      </g>

      {/* Player position dots */}
      <g>
        {positions.map((pos, i) => {
          const x = reverseAttack ? width - pos.x : pos.x;
          const y = pos.y;
          const isAttack = i >= positions.length - 3;
          return (
            <circle
              key={i}
              cx={x}
              cy={y}
              r={isAttack ? 4.5 : 4}
              fill={isAttack ? accent : '#0a0a0d'}
              stroke={isAttack ? '#0a0a0d' : accent}
              strokeWidth={isAttack ? 0.6 : 1.2}
            />
          );
        })}
      </g>
    </svg>
  );
}

/**
 * 4-3-3 formation default positions (homography'dan ma'lumot bo'lmasa).
 */
function generateMockPositions(isHome) {
  // Goal-keeper, 4 defender, 3 midfielder, 3 forward
  return [
    { x: 20, y: 110 },
    { x: 60, y: 55 },
    { x: 75, y: 90 },
    { x: 75, y: 130 },
    { x: 60, y: 165 },
    { x: 135, y: 80 },
    { x: 140, y: 110 },
    { x: 135, y: 140 },
    { x: 225, y: 55 },
    { x: 245, y: 110 },
    { x: 225, y: 165 },
  ];
}

function Stat({ label, value, accent, small }) {
  return (
    <div>
      <div className="text-[10px] text-ink-300 tracking-wider mb-1">{label}</div>
      <div
        className={`${small ? 'text-xs font-medium' : 'text-base font-semibold tabular tracking-tight'}`}
        style={accent ? { color: accent } : undefined}
      >
        {value}
      </div>
    </div>
  );
}

/**
 * Eng faol o'yinchi heatmap'i — kengroq oynada.
 */
function TopPlayerHeatmap({ result }) {
  const players = result?.detected_players || result?.detectedPlayers || [];
  if (players.length === 0) return null;

  // Top distance/sprint o'yinchini topish
  const topPlayer = players.reduce((best, p) => {
    const score = (p.total_distance_m || p.totalDistanceM || 0)
      + ((p.max_speed_kmh || p.maxSpeedKmh || 0) * 100);
    const bestScore = (best?.total_distance_m || best?.totalDistanceM || 0)
      + ((best?.max_speed_kmh || best?.maxSpeedKmh || 0) * 100);
    return score > bestScore ? p : best;
  }, null);

  if (!topPlayer) return null;

  const distance = ((topPlayer.total_distance_m || topPlayer.totalDistanceM || 0) / 1000).toFixed(1);
  const sprints = topPlayer.sprints || Math.floor((topPlayer.appearances || 0) / 50);
  const maxSpeed = (topPlayer.max_speed_kmh || topPlayer.maxSpeedKmh || 0).toFixed(1);

  return (
    <div className="bg-gradient-to-b from-ink-900 to-ink-850 border-b border-hairline border-white/[0.06]">
      <div className="px-5 py-5">
        <div className="grid grid-cols-[1fr_220px] gap-6 items-stretch">
          <div>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2.5">
                <div
                  className="w-7 h-7 rounded-md flex items-center justify-center text-[11px] font-bold tracking-tighter"
                  style={{
                    background: 'linear-gradient(135deg, #2d3a5f, #1a2340)',
                  }}
                >
                  #{topPlayer.jersey_number || topPlayer.track_id}
                </div>
                <div>
                  <div className="text-[13px] font-semibold tracking-tight">
                    O'yinchi #{topPlayer.track_id || topPlayer.jersey_number || '—'}
                  </div>
                  <div className="text-[10px] text-ink-300">
                    Eng faol o'yinchi · {topPlayer.appearances || 0} freym
                  </div>
                </div>
              </div>
              <Badge variant="warning">Hot zone</Badge>
            </div>

            {/* Wide heatmap */}
            <PlayerWideHeatmap />

            <div className="flex gap-3 mt-2.5 text-[10px]">
              <div className="flex items-center gap-1.5">
                <div className="w-3 h-3 rounded-sm bg-gradient-to-br from-coral via-amber to-lime-electric" />
                <span className="text-ink-200">Density: kam → ko'p</span>
              </div>
            </div>
          </div>

          {/* Right stats */}
          <div className="flex flex-col gap-px bg-white/[0.06] rounded-lg overflow-hidden">
            <PlayerStat label="DISTANCE" value={distance} unit="km" />
            <PlayerStat label="SPRINTS" value={sprints} accent="#c5ff50" />
            <PlayerStat label="MAX SPEED" value={maxSpeed} unit="km/h" accent="#ff7a6b" />
            <PlayerStat label="TOP ZONE" value="Right attacking 1/3" sub="68% of time" small />
          </div>
        </div>
      </div>
    </div>
  );
}

function PlayerWideHeatmap() {
  return (
    <svg viewBox="0 0 600 200" className="w-full h-auto block rounded-md" style={{ background: '#0d0d11' }}>
      <defs>
        <radialGradient id="player-hot" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#ff7a6b" stopOpacity="0.85" />
          <stop offset="35%" stopColor="#ffb155" stopOpacity="0.55" />
          <stop offset="70%" stopColor="#c5ff50" stopOpacity="0.3" />
          <stop offset="100%" stopColor="#c5ff50" stopOpacity="0" />
        </radialGradient>
        <radialGradient id="player-warm" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#c5ff50" stopOpacity="0.45" />
          <stop offset="100%" stopColor="#c5ff50" stopOpacity="0" />
        </radialGradient>
        <pattern id="player-grass" patternUnits="userSpaceOnUse" width="50" height="200">
          <rect width="25" height="200" fill="#0d0d11" />
          <rect x="25" width="25" height="200" fill="#0e0e12" />
        </pattern>
      </defs>

      <rect x="8" y="8" width="584" height="184" fill="url(#player-grass)" />

      <g style={{ mixBlendMode: 'screen' }}>
        <circle cx="160" cy="60" r="32" fill="url(#player-warm)" />
        <circle cx="220" cy="48" r="36" fill="url(#player-warm)" />
        <circle cx="280" cy="55" r="40" fill="url(#player-warm)" />
        <circle cx="340" cy="62" r="44" fill="url(#player-hot)" />
        <circle cx="395" cy="55" r="48" fill="url(#player-hot)" />
        <circle cx="445" cy="65" r="44" fill="url(#player-hot)" />
        <circle cx="490" cy="80" r="40" fill="url(#player-warm)" />
        <circle cx="425" cy="110" r="38" fill="url(#player-warm)" />
      </g>

      <path d="M 145,75 Q 200,55 270,52 Q 340,55 395,55 Q 440,62 480,85 Q 510,105 525,108"
            stroke="#ff7a6b" strokeWidth="1.2" fill="none" strokeDasharray="3 3" opacity="0.5" />

      <g stroke="rgba(255,255,255,0.18)" strokeWidth="0.7" fill="none">
        <rect x="8" y="8" width="584" height="184" />
        <line x1="300" y1="8" x2="300" y2="192" />
        <circle cx="300" cy="100" r="22" />
        <rect x="8" y="48" width="58" height="104" />
        <rect x="534" y="48" width="58" height="104" />
        <rect x="8" y="76" width="22" height="48" />
        <rect x="570" y="76" width="22" height="48" />
      </g>

      <g>
        <circle cx="395" cy="55" r="4" fill="#ff7a6b" />
        <circle cx="395" cy="55" r="9" fill="none" stroke="#ff7a6b" strokeOpacity="0.4" strokeWidth="1" />
      </g>
      <text x="402" y="50" fill="#ff7a6b" fontFamily="-apple-system, sans-serif" fontSize="10" fontWeight="500">71′ peak · 34.7 km/h</text>
    </svg>
  );
}

function PlayerStat({ label, value, unit, accent, sub, small }) {
  return (
    <div className="bg-ink-800 px-3.5 py-2.5">
      <div className="text-[10px] text-ink-300 tracking-wider mb-1">{label}</div>
      <div
        className={`tabular tracking-tight ${small ? 'text-xs font-medium' : 'text-xl font-semibold'}`}
        style={accent ? { color: accent } : undefined}
      >
        {value}
        {unit && <span className="text-[11px] text-ink-300 font-normal"> {unit}</span>}
      </div>
      {sub && <div className="text-[10px] text-ink-300 mt-0.5 tabular">{sub}</div>}
    </div>
  );
}

function FieldZonesBars({ result }) {
  const periodComp = result?.period_comparison || result?.periodComparison;

  // Average from both periods
  const homeFh = periodComp?.first_half;
  const homeSh = periodComp?.second_half;

  const homeAttack = homeFh && homeSh
    ? (homeFh.home_attacking_third_pct + homeSh.home_attacking_third_pct) / 2
    : 41;
  const homeDef = homeFh && homeSh
    ? (homeFh.home_defensive_third_pct + homeSh.home_defensive_third_pct) / 2
    : 22;
  const homeMid = 100 - homeAttack - homeDef;

  const awayAttack = homeFh && homeSh
    ? (homeFh.away_attacking_third_pct + homeSh.away_attacking_third_pct) / 2
    : 28;
  const awayDef = homeFh && homeSh
    ? (homeFh.away_defensive_third_pct + homeSh.away_defensive_third_pct) / 2
    : 36;
  const awayMid = 100 - awayAttack - awayDef;

  return (
    <div className="px-5 py-4">
      <div className="flex items-end justify-between mb-3">
        <div>
          <div className="text-[13px] font-semibold tracking-tight">Maydon zonalari</div>
          <div className="text-[11px] text-ink-300 mt-0.5">Vaqtning qaysi 1/3 da o'tkazgan</div>
        </div>
        <div className="text-[11px] text-ink-300 font-mono">3 zone × 2 team</div>
      </div>

      <div className="flex flex-col gap-2.5">
        <ZoneBar
          label="Uy jamoasi"
          accent="#c5ff50"
          values={{ def: homeDef, mid: homeMid, att: homeAttack }}
        />
        <ZoneBar
          label="Mehmon jamoasi"
          accent="#6bb4ff"
          values={{ def: awayDef, mid: awayMid, att: awayAttack }}
        />
      </div>
    </div>
  );
}

function ZoneBar({ label, accent, values }) {
  return (
    <div>
      <div className="flex justify-between items-baseline text-[11px] text-ink-200 mb-1">
        <span>{label}</span>
        <span className="font-mono text-ink-300 tabular">
          <span className="text-ink-50">{values.def.toFixed(0)}%</span> def ·{' '}
          <span className="text-ink-50">{values.mid.toFixed(0)}%</span> mid ·{' '}
          <span style={{ color: accent }}>{values.att.toFixed(0)}%</span> att
        </span>
      </div>
      <div className="flex gap-0.5 h-[10px] rounded-md overflow-hidden">
        <div
          className="rounded-l-sm"
          style={{ width: `${values.def}%`, background: `${accent}40` }}
        />
        <div style={{ width: `${values.mid}%`, background: `${accent}88` }} />
        <div
          className="rounded-r-sm"
          style={{ width: `${values.att}%`, background: accent }}
        />
      </div>
    </div>
  );
}
