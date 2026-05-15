import { useMemo } from 'react';
import {
  AreaChart,
  Area,
  Line,
  LineChart,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  ReferenceArea,
  ReferenceLine,
  ReferenceDot,
  CartesianGrid,
} from 'recharts';

/**
 * Momentum Chart — har 5 daqiqa oynasidagi possession va sprint.
 *
 * Recharts'ning AreaChart'i — Real Madrid solid (lime), Barcelona dashed (gray).
 * Halftime — kulrang vertical band.
 * Gollar — circle marker'lar.
 */
export function MomentumChart({ result }) {
  const { data, halftime, goals } = useMemo(() => {
    const timeline = result?.timeline || [];

    const data = timeline.map((s) => ({
      minute: Math.round((s.window_start_min + s.window_end_min) / 2),
      home: s.home_possession_pct,
      away: s.away_possession_pct,
      sprintHome: s.home_sprint_count,
      sprintAway: s.away_sprint_count,
      phase: s.phase,
    }));

    // Halftime band
    const halftimePhase = (result?.match_phases || []).find(
      (p) => p.phase_type === 'HALFTIME'
    );
    const halftime = halftimePhase
      ? {
          start: halftimePhase.start_seconds / 60,
          end: halftimePhase.end_seconds / 60,
        }
      : null;

    // Goals
    const goals = (result?.events || [])
      .filter((e) => e.event_type === 'GOAL')
      .map((e) => ({
        minute: Math.round(e.frame_timestamp / 60),
        team: e.team_side,
      }));

    return { data, halftime, goals };
  }, [result]);

  if (!data || data.length === 0) {
    return (
      <div className="px-6 py-5 border-b border-hairline border-white/[0.06]">
        <div className="text-sm font-semibold mb-2">Momentum Chart</div>
        <div className="text-2xs text-ink-300">Timeline ma'lumotlari hali yo'q</div>
        <div className="h-[180px] mt-4 shimmer rounded" />
      </div>
    );
  }

  return (
    <div className="px-6 py-5 border-b border-hairline border-white/[0.06]">
      <div className="flex items-center justify-between mb-4">
        <div>
          <div className="text-sm font-semibold tracking-tight">Momentum Chart</div>
          <div className="text-2xs text-ink-300 mt-0.5">
            5 daqiqalik oynalar bo'yicha possession va sprint
          </div>
        </div>
        <div className="flex gap-4 text-[11px]">
          <div className="flex items-center gap-2">
            <div className="w-3.5 h-0.5 bg-lime-electric rounded-full" />
            <span className="text-ink-100">Uy</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-3.5 border-t-[1.5px] border-dashed border-ink-300" />
            <span className="text-ink-200">Mehmon</span>
          </div>
        </div>
      </div>

      <div className="h-[200px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 5 }}>
            <defs>
              <linearGradient id="lime-fill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#c5ff50" stopOpacity={0.3} />
                <stop offset="100%" stopColor="#c5ff50" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="rgba(255,255,255,0.04)" strokeWidth={0.5} />
            <XAxis
              dataKey="minute"
              tick={{ fill: '#6d6d75', fontSize: 10, fontFamily: 'ui-monospace, monospace' }}
              axisLine={{ stroke: 'rgba(255,255,255,0.08)' }}
              tickLine={false}
              tickFormatter={(v) => `${v}'`}
            />
            <YAxis
              tick={{ fill: '#6d6d75', fontSize: 10 }}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v) => `${v}%`}
              domain={[0, 100]}
            />
            <Tooltip content={<MomentumTooltip />} cursor={{ stroke: '#3a3a45', strokeWidth: 0.5 }} />

            {halftime && (
              <ReferenceArea
                x1={halftime.start}
                x2={halftime.end}
                fill="rgba(255,255,255,0.025)"
                stroke="none"
                label={{
                  value: 'HT',
                  position: 'insideTop',
                  fill: '#6d6d75',
                  fontSize: 10,
                }}
              />
            )}

            <Area
              type="monotone"
              dataKey="home"
              stroke="#c5ff50"
              strokeWidth={2}
              fill="url(#lime-fill)"
              dot={false}
              activeDot={{ r: 4, fill: '#c5ff50', stroke: '#0a0a0d', strokeWidth: 2 }}
            />
            <Line
              type="monotone"
              dataKey="away"
              stroke="#6d6d75"
              strokeWidth={1.5}
              strokeDasharray="4 3"
              dot={false}
            />

            {goals.map((g, i) => (
              <ReferenceDot
                key={i}
                x={g.minute}
                y={g.team === 'HOME' ? 75 : 35}
                r={4}
                fill={g.team === 'HOME' ? '#c5ff50' : '#6d6d75'}
                stroke={g.team === 'HOME' ? '#c5ff50' : 'transparent'}
                strokeOpacity={0.4}
                strokeWidth={6}
              />
            ))}
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function MomentumTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const home = payload.find((p) => p.dataKey === 'home');
  const away = payload.find((p) => p.dataKey === 'away');
  return (
    <div className="bg-ink-850 border border-hairline border-white/[0.08] rounded-md px-3 py-2 shadow-lg">
      <div className="text-[11px] font-mono text-ink-300 mb-1.5">
        {label}-daqiqa
      </div>
      <div className="flex flex-col gap-0.5">
        {home && (
          <div className="flex items-center gap-2 text-xs">
            <div className="w-2 h-2 rounded-sm bg-lime-electric" />
            <span className="text-ink-100">Uy:</span>
            <span className="font-semibold tabular">{home.value}%</span>
          </div>
        )}
        {away && (
          <div className="flex items-center gap-2 text-xs">
            <div className="w-2 h-2 rounded-sm bg-ink-300" />
            <span className="text-ink-100">Mehmon:</span>
            <span className="font-semibold tabular">{away.value}%</span>
          </div>
        )}
      </div>
    </div>
  );
}
