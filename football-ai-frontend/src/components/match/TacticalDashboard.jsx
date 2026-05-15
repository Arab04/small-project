import { useState } from 'react';
import {
  Shield, Zap, ArrowUpRight, MapPin, Target, Clock,
  ChevronDown, ChevronUp, AlertTriangle, TrendingDown
} from 'lucide-react';

/**
 * Trener uchun professional taktik tahlil paneli.
 * 7 ta metrik: PPDA, Transitions, Progressive Passes,
 * Recovery Map, Danger Zones, Player Vulnerability, Fatigue Timeline
 */
export function TacticalDashboard({ result }) {
  const tactical = result?.tactical_analysis || result?.tacticalAnalysis;
  if (!tactical) return null;

  const homeName = result?.home_team_name || 'Team A';
  const awayName = result?.away_team_name || 'Team B';

  const team1 = tactical.team_1 || {};
  const team2 = tactical.team_2 || {};

  return (
    <div className="border-b border-hairline border-white/[0.06]">
      {/* Header */}
      <div className="px-6 py-4 bg-gradient-to-r from-ink-900 via-ink-850 to-ink-900 border-b border-hairline border-white/[0.06]">
        <div className="flex items-center gap-2.5">
          <div className="w-[22px] h-[22px] rounded-md bg-amber-500/12 flex items-center justify-center">
            <Target className="w-3 h-3 text-amber-400" strokeWidth={1.8} />
          </div>
          <div className="text-sm font-semibold tracking-tight">Taktik Tahlil</div>
          <div className="ml-auto text-[11px] text-ink-300 font-mono">COACH ANALYTICS</div>
        </div>
      </div>

      {/* Grid layout — 2 jamoani bir qator */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-px bg-white/[0.04]">
        <TeamTacticalColumn
          teamName={homeName}
          data={team1}
          color="lime"
          side="HOME"
        />
        <TeamTacticalColumn
          teamName={awayName}
          data={team2}
          color="sky"
          side="AWAY"
        />
      </div>
    </div>
  );
}

function TeamTacticalColumn({ teamName, data, color, side }) {
  const colorClasses = color === 'lime'
    ? { accent: 'text-lime-electric', bg: 'bg-lime-electric', bgFaint: 'bg-lime-electric/8', border: 'border-lime-electric/20' }
    : { accent: 'text-sky-400', bg: 'bg-sky-400', bgFaint: 'bg-sky-400/8', border: 'border-sky-400/20' };

  return (
    <div className="bg-ink-900">
      {/* Team header */}
      <div className={`px-5 py-3 border-b border-hairline border-white/[0.06] ${colorClasses.bgFaint}`}>
        <div className={`text-xs font-semibold tracking-wide ${colorClasses.accent}`}>
          {teamName}
          <span className="text-ink-400 font-normal ml-2">{side}</span>
        </div>
      </div>

      <div className="divide-y divide-white/[0.04]">
        <PPDACard data={data.ppda} colors={colorClasses} />
        <TransitionsCard data={data.transitions} colors={colorClasses} />
        <ProgressivePassesCard data={data.progressive_passes} colors={colorClasses} />
        <RecoveryMapCard data={data.recovery_map} colors={colorClasses} />
        <DangerZonesCard data={data.danger_zones} colors={colorClasses} />
        <VulnerabilityCard data={data.player_vulnerability} colors={colorClasses} />
        <FatigueCard data={data.fatigue_timeline} colors={colorClasses} />
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
/* 1. PPDA                                             */
/* ═══════════════════════════════════════════════════ */
function PPDACard({ data, colors }) {
  if (!data || data.ppda === null) return null;

  const ppda = data.ppda;
  // PPDA rang: past=yashil (agressiv), baland=qizil (sust)
  const ppdaColor = ppda < 8 ? 'text-emerald-400' : ppda < 12 ? 'text-amber-400' : ppda < 16 ? 'text-orange-400' : 'text-coral';
  const barWidth = Math.min(100, Math.max(10, (1 - ppda / 25) * 100));

  return (
    <div className="px-5 py-4">
      <div className="flex items-center gap-2 mb-3">
        <Shield className="w-3.5 h-3.5 text-ink-400" strokeWidth={1.8} />
        <span className="text-[11px] text-ink-300 font-medium tracking-widest font-mono">PPDA</span>
      </div>

      <div className="flex items-baseline gap-3">
        <span className={`text-2xl font-semibold tabular ${ppdaColor}`}>{ppda}</span>
        <span className="text-[11px] text-ink-400">{data.interpretation}</span>
      </div>

      {/* PPDA bar */}
      <div className="mt-3 h-1.5 bg-white/[0.04] rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all ${
            ppda < 8 ? 'bg-emerald-400' : ppda < 12 ? 'bg-amber-400' : ppda < 16 ? 'bg-orange-400' : 'bg-coral'
          }`}
          style={{ width: `${barWidth}%` }}
        />
      </div>
      <div className="flex justify-between mt-1.5 text-[9px] text-ink-400 font-mono">
        <span>Agressiv</span>
        <span>Sust</span>
      </div>

      {data.opponent_passes != null && (
        <div className="mt-2 text-[10px] text-ink-400 tabular">
          Raqib paslar: {data.opponent_passes} | Himoyaviy harakatlar: {data.defensive_actions}
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
/* 2. Transitions                                      */
/* ═══════════════════════════════════════════════════ */
function TransitionsCard({ data, colors }) {
  if (!data) return null;

  return (
    <div className="px-5 py-4">
      <div className="flex items-center gap-2 mb-3">
        <Zap className="w-3.5 h-3.5 text-ink-400" strokeWidth={1.8} />
        <span className="text-[11px] text-ink-300 font-medium tracking-widest font-mono">TRANSITIONS</span>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatBox
          label="Hujumga"
          value={data.attack_transitions_count}
          sub={`o'rt. ${data.avg_attack_advance_m}m`}
        />
        <StatBox
          label="Xavfli zona"
          value={data.danger_zone_reached}
          sub={`${data.danger_zone_pct}%`}
          highlight={data.danger_zone_pct > 30}
        />
        <StatBox
          label="Himoyada"
          value={data.defence_transitions_count}
          sub={`o'rt. ${data.avg_conceded_advance_m}m`}
          warning={data.avg_conceded_advance_m > 10}
        />
      </div>

      {/* Top transitions */}
      {data.top_transitions?.length > 0 && (
        <div className="mt-3">
          <div className="text-[10px] text-ink-400 mb-1.5">Eng yaxshi counter-attack:</div>
          <div className="flex gap-1.5">
            {data.top_transitions.slice(0, 3).map((t, i) => (
              <div key={i} className="flex-1 px-2 py-1.5 bg-white/[0.03] rounded text-center">
                <div className="text-xs font-semibold tabular">{t.advance_m}m</div>
                <div className="text-[9px] text-ink-400">{t.time_s}s | {t.max_speed_kmh} km/h</div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
/* 3. Progressive Passes                               */
/* ═══════════════════════════════════════════════════ */
function ProgressivePassesCard({ data, colors }) {
  if (!data) return null;

  return (
    <div className="px-5 py-4">
      <div className="flex items-center gap-2 mb-3">
        <ArrowUpRight className="w-3.5 h-3.5 text-ink-400" strokeWidth={1.8} />
        <span className="text-[11px] text-ink-300 font-medium tracking-widest font-mono">PROGRESSIVE PASSES</span>
      </div>

      <div className="flex items-baseline gap-3 mb-2">
        <span className="text-2xl font-semibold tabular">{data.count}</span>
        <span className="text-[11px] text-ink-400">pas (o'rt. {data.avg_advance_m}m oldinga)</span>
      </div>

      {data.top_progressors?.length > 0 && (
        <div className="space-y-1.5">
          <div className="text-[10px] text-ink-400">Top pas beruchilar:</div>
          {data.top_progressors.map((p, i) => (
            <div key={i} className="flex items-center gap-2">
              <span className="text-[10px] font-mono text-ink-400 w-4">{i + 1}.</span>
              <span className="text-xs font-mono">#{p.player_id}</span>
              <div className="flex-1 h-1 bg-white/[0.04] rounded-full overflow-hidden">
                <div
                  className={`h-full rounded-full ${colors.bg}/60`}
                  style={{ width: `${Math.min(100, (p.count / Math.max(data.top_progressors[0].count, 1)) * 100)}%` }}
                />
              </div>
              <span className="text-[10px] tabular text-ink-300">{p.count}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
/* 4. Recovery Map                                     */
/* ═══════════════════════════════════════════════════ */
function RecoveryMapCard({ data, colors }) {
  if (!data || data.total_recoveries === 0) return null;

  const zones = data.zone_pct || {};
  const dist = data.zone_distribution || {};

  return (
    <div className="px-5 py-4">
      <div className="flex items-center gap-2 mb-3">
        <MapPin className="w-3.5 h-3.5 text-ink-400" strokeWidth={1.8} />
        <span className="text-[11px] text-ink-300 font-medium tracking-widest font-mono">RECOVERY MAP</span>
        <span className="ml-auto text-[10px] text-ink-400 tabular">{data.total_recoveries} marta</span>
      </div>

      {/* Zona bar */}
      <div className="flex gap-0.5 h-6 rounded overflow-hidden mb-2">
        <ZoneBar label="Himoya" pct={zones.defensive || 0} count={dist.defensive} color="bg-emerald-500/60" />
        <ZoneBar label="O'rta" pct={zones.middle || 0} count={dist.middle} color="bg-amber-400/60" />
        <ZoneBar label="Hujum" pct={zones.attacking || 0} count={dist.attacking} color="bg-coral/60" />
      </div>

      <div className="flex justify-between text-[9px] text-ink-400 font-mono">
        <span>Himoya ({zones.defensive || 0}%)</span>
        <span>O'rta ({zones.middle || 0}%)</span>
        <span>Hujum ({zones.attacking || 0}%)</span>
      </div>
    </div>
  );
}

function ZoneBar({ label, pct, count, color }) {
  if (pct <= 0) return null;
  return (
    <div
      className={`${color} flex items-center justify-center transition-all`}
      style={{ width: `${Math.max(pct, 8)}%` }}
    >
      <span className="text-[9px] font-semibold text-white/80">{count}</span>
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
/* 5. Danger Zones (raqibning zaif joylari)            */
/* ═══════════════════════════════════════════════════ */
function DangerZonesCard({ data, colors }) {
  if (!data) return null;

  return (
    <div className="px-5 py-4">
      <div className="flex items-center gap-2 mb-3">
        <AlertTriangle className="w-3.5 h-3.5 text-ink-400" strokeWidth={1.8} />
        <span className="text-[11px] text-ink-300 font-medium tracking-widest font-mono">DANGER ZONES</span>
      </div>

      <div className="flex items-baseline gap-3 mb-2">
        <span className="text-2xl font-semibold tabular">{data.opponent_ball_losses}</span>
        <span className="text-[11px] text-ink-400">raqib to'p yo'qotishlari</span>
      </div>

      {data.weak_side && (
        <div className={`px-3 py-2 rounded-md ${
          data.weak_side === 'left' ? 'bg-coral/8 border border-coral/15' : 'bg-amber-500/8 border border-amber-500/15'
        } mb-2`}>
          <div className="text-[11px] text-ink-200">{data.weak_side_detail}</div>
        </div>
      )}

      {data.zone_distribution && (
        <div className="grid grid-cols-3 gap-2 mt-2">
          <MiniZone label="Himoya" value={data.zone_distribution.defensive_third || 0} />
          <MiniZone label="O'rta" value={data.zone_distribution.middle_third || 0} />
          <MiniZone label="Hujum" value={data.zone_distribution.attacking_third || 0} />
        </div>
      )}
    </div>
  );
}

function MiniZone({ label, value }) {
  return (
    <div className="text-center px-2 py-1.5 bg-white/[0.02] rounded">
      <div className="text-sm font-semibold tabular">{value}</div>
      <div className="text-[9px] text-ink-400">{label}</div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
/* 6. Player Vulnerability                             */
/* ═══════════════════════════════════════════════════ */
function VulnerabilityCard({ data, colors }) {
  if (!data || !data.vulnerable_players?.length) return null;

  return (
    <div className="px-5 py-4">
      <div className="flex items-center gap-2 mb-3">
        <Target className="w-3.5 h-3.5 text-ink-400" strokeWidth={1.8} />
        <span className="text-[11px] text-ink-300 font-medium tracking-widest font-mono">VULNERABILITY</span>
      </div>

      {data.recommendation && (
        <div className="px-3 py-2 bg-coral/6 border border-coral/12 rounded-md mb-3">
          <div className="text-[11px] text-ink-200">{data.recommendation}</div>
        </div>
      )}

      <div className="space-y-1.5">
        {data.vulnerable_players.map((p, i) => (
          <div key={i} className="flex items-center gap-3 px-3 py-2 bg-white/[0.02] rounded">
            <span className="text-xs font-mono font-semibold w-8">#{p.player_id}</span>
            <div className="flex-1">
              <div className="flex items-center gap-3">
                <div className="flex-1 h-1.5 bg-white/[0.04] rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full bg-coral/70"
                    style={{ width: `${Math.min(100, p.turnover_rate_per_min * 15)}%` }}
                  />
                </div>
              </div>
            </div>
            <span className="text-[10px] text-ink-300 tabular">{p.turnovers} turnover</span>
            <span className="text-[10px] text-coral tabular">{p.turnover_rate_per_min}/min</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
/* 7. Fatigue Timeline                                 */
/* ═══════════════════════════════════════════════════ */
function FatigueCard({ data, colors }) {
  if (!data || !data.windows?.length) return null;

  const maxSprints = Math.max(...data.windows.map(w => w.sprint_frames), 1);

  return (
    <div className="px-5 py-4">
      <div className="flex items-center gap-2 mb-3">
        <TrendingDown className="w-3.5 h-3.5 text-ink-400" strokeWidth={1.8} />
        <span className="text-[11px] text-ink-300 font-medium tracking-widest font-mono">FATIGUE</span>
        {data.fatigue_detected && (
          <span className="ml-auto text-[10px] text-coral font-medium">Charchoq aniqlandi</span>
        )}
      </div>

      {data.fatigue_detected && (
        <div className="px-3 py-2 bg-coral/6 border border-coral/12 rounded-md mb-3">
          <div className="text-[11px] text-ink-200">{data.interpretation}</div>
        </div>
      )}

      {/* Sprint timeline bar chart */}
      <div className="flex items-end gap-1 h-12">
        {data.windows.map((w, i) => {
          const h = Math.max((w.sprint_frames / maxSprints) * 100, 5);
          const isFatigued = data.fatigue_detected && data.fatigue_start_minute && w.minute_start >= data.fatigue_start_minute;
          return (
            <div key={i} className="flex-1 flex flex-col items-center gap-1">
              <div
                className={`w-full rounded-t transition-all ${
                  isFatigued ? 'bg-coral/60' : `${colors.bg}/50`
                }`}
                style={{ height: `${h}%` }}
                title={`${w.minute_start}-${w.minute_end} daq: ${w.sprint_frames} sprint`}
              />
            </div>
          );
        })}
      </div>
      <div className="flex gap-1 mt-1">
        {data.windows.map((w, i) => (
          <div key={i} className="flex-1 text-center text-[8px] text-ink-400 font-mono">
            {Math.round(w.minute_start)}'
          </div>
        ))}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
/* Shared                                              */
/* ═══════════════════════════════════════════════════ */
function StatBox({ label, value, sub, highlight, warning }) {
  return (
    <div className={`px-3 py-2.5 rounded-md ${
      highlight ? 'bg-lime-electric/6 border border-lime-electric/12'
      : warning ? 'bg-coral/6 border border-coral/12'
      : 'bg-white/[0.025] border border-white/[0.04]'
    }`}>
      <div className="text-lg font-semibold tabular leading-none">{value}</div>
      <div className="text-[10px] text-ink-300 mt-1">{label}</div>
      {sub && <div className="text-[9px] text-ink-400 mt-0.5">{sub}</div>}
    </div>
  );
}
