import { useMemo } from 'react';
import { Badge } from '@/components/ui/Badge';

/**
 * 1-yarim vs 2-yarim taqqoslash + Stamina (charchash) tahlili.
 * Yonma-yon split.
 */
export function PeriodStaminaSplit({ result }) {
  const periodComp = result?.period_comparison || result?.periodComparison;
  const stamina = result?.stamina_analysis || result?.staminaAnalysis;

  return (
    <div className="grid grid-cols-2 gap-px bg-white/[0.06] border-b border-hairline border-white/[0.06]">
      <PeriodCompCard periodComp={periodComp} />
      <StaminaCard stamina={stamina} />
    </div>
  );
}

function PeriodCompCard({ periodComp }) {
  if (!periodComp || !periodComp.first_half) {
    return (
      <div className="bg-ink-900 px-5 py-5">
        <div className="text-sm font-semibold mb-3">1-yarim · 2-yarim</div>
        <div className="text-2xs text-ink-300">Ma'lumot yo'q</div>
      </div>
    );
  }

  const first = periodComp.first_half;
  const second = periodComp.second_half;

  // Momentum shift
  const momentumLabels = {
    HOME_GAINED: 'Uy ustunlik oldi',
    AWAY_GAINED: 'Mehmon ustunlik oldi',
    STABLE: 'Barqaror',
  };
  const momentumLabel = momentumLabels[periodComp.momentum_shift] || 'Barqaror';
  const momentumVariant = periodComp.momentum_shift === 'HOME_GAINED' ? 'success' : periodComp.momentum_shift === 'AWAY_GAINED' ? 'info' : 'neutral';

  const rows = [
    {
      label: 'Possession',
      first: `${first.home_possession_pct?.toFixed(0)}%`,
      second: `${second?.home_possession_pct?.toFixed(0) || '—'}%`,
      firstNum: first.home_possession_pct || 0,
      secondNum: second?.home_possession_pct || 0,
    },
    {
      label: 'Sprints',
      first: first.home_sprint_count,
      second: second?.home_sprint_count || 0,
      firstNum: first.home_sprint_count || 0,
      secondNum: second?.home_sprint_count || 0,
    },
    {
      label: "Hujum 1/3 da vaqt",
      first: `${first.home_attacking_third_pct?.toFixed(0)}%`,
      second: `${second?.home_attacking_third_pct?.toFixed(0) || '—'}%`,
      firstNum: first.home_attacking_third_pct || 0,
      secondNum: second?.home_attacking_third_pct || 0,
    },
  ];

  return (
    <div className="bg-ink-900 px-5 py-5">
      <div className="flex items-center justify-between mb-4">
        <div className="text-sm font-semibold tracking-tight">1-yarim · 2-yarim</div>
        <Badge variant={momentumVariant} icon={periodComp.momentum_shift === 'HOME_GAINED' ? '↗' : periodComp.momentum_shift === 'AWAY_GAINED' ? '↘' : '→'}>
          {momentumLabel}
        </Badge>
      </div>

      <div className="flex flex-col gap-3">
        {rows.map((row) => (
          <ComparisonRow key={row.label} {...row} />
        ))}
      </div>

      {periodComp.insights && periodComp.insights.length > 0 && (
        <div className="mt-4 pt-3 border-t border-hairline border-white/[0.06]">
          <div className="text-2xs text-ink-300 italic line-clamp-2">
            {periodComp.insights[0]}
          </div>
        </div>
      )}
    </div>
  );
}

function ComparisonRow({ label, first, second, firstNum, secondNum }) {
  const max = Math.max(firstNum, secondNum, 1);
  const firstPct = (firstNum / max) * 100;
  const secondPct = (secondNum / max) * 100;
  const secondLarger = secondNum > firstNum;

  return (
    <div>
      <div className="flex justify-between items-baseline text-[11px] text-ink-200 mb-1.5">
        <span>{label}</span>
        <span className="tabular text-ink-300">
          <span className="text-ink-50">{first}</span> → <span className="text-ink-50">{second}</span>
        </span>
      </div>
      <div className="flex gap-1 items-center">
        <div className="flex-1 h-[6px] bg-ink-700 rounded-full overflow-hidden">
          <div
            className="h-full bg-ink-300 rounded-full transition-all"
            style={{ width: `${firstPct}%` }}
          />
        </div>
        <div className="flex-1 h-[8px] bg-ink-700 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all ${secondLarger ? 'bg-lime-electric' : 'bg-lime-dim'}`}
            style={{ width: `${secondPct}%` }}
          />
        </div>
      </div>
    </div>
  );
}

function StaminaCard({ stamina }) {
  const data = useMemo(() => {
    if (!stamina) return null;
    const homeBuckets = stamina.sprint_buckets_home || [];
    const awayBuckets = stamina.sprint_buckets_away || [];
    const max = Math.max(...homeBuckets, ...awayBuckets, 1);
    return {
      home: homeBuckets,
      away: awayBuckets,
      max,
      homeFatigue: stamina.home_fatigue_detected,
      awayFatigue: stamina.away_fatigue_detected,
      fatigueMin: stamina.fatigue_minute_home || stamina.fatigue_minute_away,
    };
  }, [stamina]);

  if (!data) {
    return (
      <div className="bg-ink-900 px-5 py-5">
        <div className="text-sm font-semibold mb-3">Stamina · charchash</div>
        <div className="text-2xs text-ink-300">Ma'lumot yo'q</div>
      </div>
    );
  }

  const labels = ['15', '30', '45', '60', '75', '90'];

  return (
    <div className="bg-ink-900 px-5 py-5">
      <div className="flex items-center justify-between mb-4">
        <div className="text-sm font-semibold tracking-tight">Stamina · charchash</div>
        {data.awayFatigue && (
          <Badge variant="warning">
            Mehmon · {data.fatigueMin || 75}'dan
          </Badge>
        )}
        {data.homeFatigue && !data.awayFatigue && (
          <Badge variant="warning">
            Uy · {data.fatigueMin || 75}'dan
          </Badge>
        )}
      </div>

      {/* Bar chart */}
      <div className="flex items-end gap-2 h-[100px] relative">
        {data.home.map((value, i) => {
          const homeHeight = (value / data.max) * 100;
          const awayHeight = ((data.away[i] || 0) / data.max) * 100;
          return (
            <div key={i} className="flex-1 flex flex-col items-center gap-1 relative">
              <div className="flex-1 w-full flex items-end gap-0.5">
                {/* Home solid */}
                <div
                  className="flex-1 bg-lime-electric rounded-t-sm transition-all"
                  style={{
                    height: `${Math.max(homeHeight, 5)}%`,
                    opacity: 1 - i * 0.08,
                  }}
                />
                {/* Away outline */}
                <div
                  className="flex-1 border-2 border-coral border-dashed rounded-t-sm transition-all"
                  style={{
                    height: `${Math.max(awayHeight, 5)}%`,
                  }}
                />
              </div>
              <div className="text-[9px] font-mono text-ink-300">{labels[i]}</div>
            </div>
          );
        })}
        {/* Fatigue line */}
        {(data.homeFatigue || data.awayFatigue) && (
          <div
            className="absolute top-0 bottom-5 border-r border-dashed border-coral"
            style={{
              left: `${((data.fatigueMin || 75) / 90) * 100}%`,
            }}
          >
            <div className="absolute -top-0.5 -right-1 text-[9px] text-coral whitespace-nowrap font-medium">
              ↓ fatigue
            </div>
          </div>
        )}
      </div>

      <div className="flex gap-3 mt-3 text-2xs text-ink-300">
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-2 bg-lime-electric rounded-sm" />
          Uy sprint
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-2 border border-coral border-dashed rounded-sm" />
          Mehmon sprint
        </div>
      </div>
    </div>
  );
}
