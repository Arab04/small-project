/**
 * 4 ta asosiy metric — possession, sprints, distance, top speed.
 *
 * Har birida o'ziga xos micro-viz: progress bar, sparkline, mini bars, avatar.
 */
export function MetricsRow({ result }) {
  if (!result) {
    return <MetricsRowSkeleton />;
  }

  const periodComp = result.period_comparison || result.periodComparison;
  const homePoss = periodComp?.first_half?.home_possession_pct
    ? Math.round((periodComp.first_half.home_possession_pct + (periodComp.second_half?.home_possession_pct || periodComp.first_half.home_possession_pct)) / 2)
    : 50;
  const awayPoss = 100 - homePoss;

  // Stamina'dan jami sprintlar
  const stamina = result.stamina_analysis || result.staminaAnalysis;
  const homeSprints = stamina?.sprint_buckets_home?.reduce((a, b) => a + b, 0) || 0;
  const awaySprints = stamina?.sprint_buckets_away?.reduce((a, b) => a + b, 0) || 0;

  // Distance — barcha o'yinchilar yig'indisi
  const players = result.detected_players || result.detectedPlayers || [];
  const totalDistance = players.reduce((sum, p) => sum + (p.total_distance_m || p.totalDistanceM || 0), 0);
  const totalDistanceKm = totalDistance / 1000;

  // Top speed
  const topSpeedPlayer = players.reduce((max, p) => {
    const speed = p.max_speed_kmh || p.maxSpeedKmh || 0;
    return speed > (max?.max_speed_kmh || max?.maxSpeedKmh || 0) ? p : max;
  }, null);
  const topSpeed = topSpeedPlayer?.max_speed_kmh || topSpeedPlayer?.maxSpeedKmh || 0;

  return (
    <div className="grid grid-cols-4 gap-px bg-white/[0.06] border-b border-hairline border-white/[0.06]">
      <MetricCard
        index="01"
        label="POSSESSION"
        delta={homePoss - 50}
        deltaUnit="%"
      >
        <div className="text-[30px] font-semibold tracking-tighter tabular leading-none">
          {homePoss}<span className="text-ink-300 text-lg">%</span>
        </div>
        <div className="flex gap-0.5 mt-3 h-1 rounded-full overflow-hidden">
          <div
            className="bg-lime-electric rounded-full"
            style={{ width: `${homePoss}%` }}
          />
          <div
            className="bg-ink-600 rounded-full"
            style={{ width: `${awayPoss}%` }}
          />
        </div>
        <div className="text-[11px] text-ink-300 mt-2 tabular">
          vs {awayPoss}% mehmon
        </div>
      </MetricCard>

      <MetricCard
        index="02"
        label="SPRINTS"
        delta={homeSprints && awaySprints ? Math.round(((homeSprints - awaySprints) / awaySprints) * 100) : 0}
        deltaUnit="%"
      >
        <div className="text-[30px] font-semibold tracking-tighter tabular leading-none">
          {homeSprints}
        </div>
        <Sparkline buckets={stamina?.sprint_buckets_home || [0, 0, 0, 0, 0, 0]} />
        <div className="text-[11px] text-ink-300 mt-1 tabular">
          Mehmon: {awaySprints} sprint
        </div>
      </MetricCard>

      <MetricCard
        index="03"
        label="DISTANCE"
        delta={null}
        deltaUnit="km"
      >
        <div className="text-[30px] font-semibold tracking-tighter tabular leading-none">
          {totalDistanceKm.toFixed(1)}
          <span className="text-base text-ink-300 font-normal"> km</span>
        </div>
        <DistanceBars buckets={stamina?.sprint_buckets_home || [0, 0, 0, 0, 0, 0]} />
        <div className="text-[11px] text-ink-300 mt-1.5">
          {players.length} o'yinchi · butun klip
        </div>
      </MetricCard>

      <MetricCard
        index="04"
        label="TOP SPEED"
        accent="coral"
        accentLabel="PEAK"
      >
        <div className="text-[30px] font-semibold tracking-tighter tabular leading-none">
          {topSpeed.toFixed(1)}
          <span className="text-base text-ink-300 font-normal"> km/h</span>
        </div>
        <div className="flex items-center gap-2 mt-3">
          <div className="w-[22px] h-[22px] rounded-full bg-gradient-to-br from-blue-900/80 to-blue-950 flex items-center justify-center text-[9px] font-semibold tracking-tighter">
            {topSpeedPlayer ? `#${topSpeedPlayer.jersey_number || topSpeedPlayer.track_id || '—'}` : '—'}
          </div>
          <div>
            <div className="text-xs font-medium">
              O'yinchi #{topSpeedPlayer?.track_id ?? '—'}
            </div>
            <div className="text-2xs text-ink-300">
              forward
            </div>
          </div>
        </div>
      </MetricCard>
    </div>
  );
}

function MetricCard({ index, label, delta, deltaUnit, accent, accentLabel, children }) {
  return (
    <div className="bg-ink-900 px-5 py-4">
      <div className="flex items-center justify-between mb-3.5">
        <span className="text-[11px] text-ink-300 font-medium tracking-widest font-mono">
          {label}
        </span>
        {accent === 'coral' && accentLabel ? (
          <span className="text-[10px] text-coral font-semibold tracking-wider">
            {accentLabel}
          </span>
        ) : delta !== null && delta !== undefined ? (
          <span
            className={`text-[10px] font-medium tabular ${
              delta > 0 ? 'text-lime-electric' : delta < 0 ? 'text-coral' : 'text-ink-300'
            }`}
          >
            {delta > 0 ? '+' : ''}
            {delta}
            {deltaUnit}
          </span>
        ) : null}
      </div>
      {children}
    </div>
  );
}

function Sparkline({ buckets }) {
  const max = Math.max(...buckets, 1);
  const points = buckets
    .map((b, i) => `${(i / (buckets.length - 1)) * 100},${18 - (b / max) * 14}`)
    .join(' ');

  return (
    <svg viewBox="0 0 100 18" className="w-full h-4 mt-3" preserveAspectRatio="none">
      <polyline
        points={points}
        fill="none"
        stroke="#c5ff50"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function DistanceBars({ buckets }) {
  const max = Math.max(...buckets, 1);
  return (
    <div className="flex gap-[3px] mt-3 h-[18px] items-end">
      {buckets.map((b, i) => (
        <div
          key={i}
          className="w-1 rounded-sm"
          style={{
            height: `${Math.max((b / max) * 100, 25)}%`,
            background: i < 4 ? '#c5ff50' : '#2a2a32',
          }}
        />
      ))}
    </div>
  );
}

function MetricsRowSkeleton() {
  return (
    <div className="grid grid-cols-4 gap-px bg-white/[0.06] border-b border-hairline border-white/[0.06]">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="bg-ink-900 px-5 py-4 h-[120px]">
          <div className="h-3 w-20 shimmer rounded mb-3" />
          <div className="h-8 w-24 shimmer rounded mb-2" />
          <div className="h-2 w-full shimmer rounded mt-3" />
        </div>
      ))}
    </div>
  );
}
