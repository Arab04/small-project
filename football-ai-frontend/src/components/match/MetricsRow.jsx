/**
 * Ikkala jamoaning asosiy ko'rsatkichlari — possession, sprints, distance, top speed.
 * Har bir metrikada HOME vs AWAY solishtiriladi.
 */
export function MetricsRow({ result }) {
  if (!result) {
    return <MetricsRowSkeleton />;
  }

  const homeName = result.home_team_name || 'Uy jamoasi';
  const awayName = result.away_team_name || 'Mehmon';

  // === POSSESSION ===
  const ra = result.raw_analytics || {};
  const poss = ra.possession || {};
  const homePoss = poss.team_1?.possession_pct ?? 50;
  const awayPoss = poss.team_2?.possession_pct ?? (100 - homePoss);

  // === SPRINTS ===
  const stamina = result.stamina_analysis || result.staminaAnalysis || {};
  const homeSprints = stamina.sprint_buckets_home?.reduce((a, b) => a + b, 0) || 0;
  const awaySprints = stamina.sprint_buckets_away?.reduce((a, b) => a + b, 0) || 0;

  // === DISTANCE (per-player sum yoki raw_analytics) ===
  const players = result.detected_players || result.detectedPlayers || [];
  const totalDistance = players.reduce((sum, p) => sum + (p.total_distance_m || p.totalDistanceM || 0), 0);
  const totalDistanceKm = totalDistance / 1000;

  // === TOP SPEED ===
  const speed = ra.speed || {};
  const homeMaxSpeed = speed.team_1?.max_speed_kmh ?? 0;
  const awayMaxSpeed = speed.team_2?.max_speed_kmh ?? 0;
  const homeAvgSpeed = speed.team_1?.avg_speed_kmh ?? 0;
  const awayAvgSpeed = speed.team_2?.avg_speed_kmh ?? 0;

  // Per-player top speed
  const topSpeedPlayer = players.reduce((max, p) => {
    const s = p.max_speed_kmh || p.maxSpeedKmh || 0;
    return s > (max?.max_speed_kmh || max?.maxSpeedKmh || 0) ? p : max;
  }, null);

  return (
    <div className="border-b border-hairline border-white/[0.06]">
      {/* Team name header */}
      <div className="grid grid-cols-[1fr_auto_1fr] items-center px-6 py-3 bg-white/[0.02] border-b border-hairline border-white/[0.06]">
        <div className="text-sm font-semibold text-lime-electric">{homeName}</div>
        <div className="text-2xs text-ink-300 tracking-widest font-mono px-4">VS</div>
        <div className="text-sm font-semibold text-sky-400 text-right">{awayName}</div>
      </div>

      {/* Metrics grid */}
      <div className="grid grid-cols-4 gap-px bg-white/[0.06]">
        {/* POSSESSION */}
        <CompareMetric
          label="POSSESSION"
          homeValue={`${Math.round(homePoss)}%`}
          awayValue={`${Math.round(awayPoss)}%`}
          homeRaw={homePoss}
          awayRaw={awayPoss}
          barType="split"
        />

        {/* SPRINTS */}
        <CompareMetric
          label="SPRINTS"
          homeValue={homeSprints}
          awayValue={awaySprints}
          homeRaw={homeSprints}
          awayRaw={awaySprints}
          barType="versus"
          homeBuckets={stamina.sprint_buckets_home}
          awayBuckets={stamina.sprint_buckets_away}
        />

        {/* DISTANCE */}
        <div className="bg-ink-900 px-5 py-4">
          <div className="text-[11px] text-ink-300 font-medium tracking-widest font-mono mb-3">
            DISTANCE
          </div>
          <div className="text-[28px] font-semibold tracking-tighter tabular leading-none">
            {totalDistanceKm.toFixed(1)}
            <span className="text-base text-ink-300 font-normal"> km</span>
          </div>
          <div className="text-[11px] text-ink-300 mt-2 tabular">
            {players.length} o'yinchi jami
          </div>
        </div>

        {/* TOP SPEED */}
        <CompareMetric
          label="TOP SPEED"
          homeValue={`${homeMaxSpeed.toFixed(1)}`}
          awayValue={`${awayMaxSpeed.toFixed(1)}`}
          homeRaw={homeMaxSpeed}
          awayRaw={awayMaxSpeed}
          unit="km/h"
          barType="versus"
          sub={`O'rtacha: ${homeAvgSpeed.toFixed(1)} vs ${awayAvgSpeed.toFixed(1)} km/h`}
        />
      </div>
    </div>
  );
}

function CompareMetric({ label, homeValue, awayValue, homeRaw, awayRaw, unit, barType, sub, homeBuckets, awayBuckets }) {
  const homeWins = homeRaw > awayRaw;
  const total = homeRaw + awayRaw || 1;

  return (
    <div className="bg-ink-900 px-5 py-4">
      <div className="text-[11px] text-ink-300 font-medium tracking-widest font-mono mb-3">
        {label}
      </div>

      {/* Two values side by side */}
      <div className="flex items-baseline justify-between gap-2">
        <div className={`text-[26px] font-semibold tracking-tighter tabular leading-none ${homeWins ? 'text-lime-electric' : 'text-ink-100'}`}>
          {homeValue}
          {unit && <span className="text-xs text-ink-300 font-normal ml-0.5">{unit}</span>}
        </div>
        <div className="text-xs text-ink-400 font-mono">vs</div>
        <div className={`text-[26px] font-semibold tracking-tighter tabular leading-none text-right ${!homeWins ? 'text-sky-400' : 'text-ink-100'}`}>
          {awayValue}
          {unit && <span className="text-xs text-ink-300 font-normal ml-0.5">{unit}</span>}
        </div>
      </div>

      {/* Bar visualization */}
      {barType === 'split' && (
        <div className="flex gap-0.5 mt-3 h-1.5 rounded-full overflow-hidden">
          <div className="bg-lime-electric rounded-full transition-all" style={{ width: `${(homeRaw / total) * 100}%` }} />
          <div className="bg-sky-400 rounded-full transition-all" style={{ width: `${(awayRaw / total) * 100}%` }} />
        </div>
      )}

      {barType === 'versus' && (
        <div className="flex gap-1 mt-3 h-1.5 items-center">
          <div className="flex-1 flex justify-end">
            <div className="bg-lime-electric/70 h-full rounded-full" style={{ width: `${Math.max((homeRaw / (Math.max(homeRaw, awayRaw) || 1)) * 100, 10)}%` }} />
          </div>
          <div className="w-px h-3 bg-ink-600" />
          <div className="flex-1">
            <div className="bg-sky-400/70 h-full rounded-full" style={{ width: `${Math.max((awayRaw / (Math.max(homeRaw, awayRaw) || 1)) * 100, 10)}%` }} />
          </div>
        </div>
      )}

      {/* Sparkline for sprints */}
      {homeBuckets && awayBuckets && (
        <div className="mt-2">
          <DualSparkline homeBuckets={homeBuckets} awayBuckets={awayBuckets} />
        </div>
      )}

      {sub && <div className="text-[10px] text-ink-300 mt-2 tabular">{sub}</div>}
    </div>
  );
}

function DualSparkline({ homeBuckets, awayBuckets }) {
  const all = [...homeBuckets, ...awayBuckets];
  const max = Math.max(...all, 1);

  return (
    <svg viewBox="0 0 100 24" className="w-full h-5" preserveAspectRatio="none">
      <Sparkline buckets={homeBuckets} max={max} color="#c5ff50" y={0} />
      <Sparkline buckets={awayBuckets} max={max} color="#38bdf8" y={12} />
    </svg>
  );
}

function Sparkline({ buckets, max, color, y }) {
  if (!buckets || buckets.length === 0) return null;
  return (
    <g>
      {buckets.map((b, i) => {
        const w = 100 / buckets.length;
        const h = Math.max((b / max) * 10, 0.5);
        return (
          <rect key={i} x={i * w + 1} y={y + (10 - h)} width={w - 2} height={h} fill={color} opacity={0.7} rx={0.5} />
        );
      })}
    </g>
  );
}

function MetricsRowSkeleton() {
  return (
    <div className="border-b border-hairline border-white/[0.06]">
      <div className="h-10 shimmer" />
      <div className="grid grid-cols-4 gap-px bg-white/[0.06]">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="bg-ink-900 px-5 py-4 h-[120px]">
            <div className="h-3 w-20 shimmer rounded mb-3" />
            <div className="h-8 w-24 shimmer rounded mb-2" />
            <div className="h-2 w-full shimmer rounded mt-3" />
          </div>
        ))}
      </div>
    </div>
  );
}
