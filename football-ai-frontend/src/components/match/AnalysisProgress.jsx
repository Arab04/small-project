import { Loader2 } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { phaseLabel, statusLabel, formatRelativeDuration } from '@/lib/utils';

/**
 * Real-time tahlil progressi.
 *
 * Qisqa video tahlili (5-10 daqiqalik klip) 5-15 daqiqa davom etadi.
 * Foydalanuvchini quyidagi ma'lumot bilan boqib turamiz:
 *   - Hozirgi qadam (DOWNLOADING / EXTRACTING / ANALYZING / ...)
 *   - Progress bar % bilan
 *   - ETA (taxminiy qolgan vaqt)
 */
export function AnalysisProgress({ job }) {
  const progress = job?.progress ?? 0;
  const currentMin = job?.currentMinute ?? 0;
  const totalMin = job?.totalMinutes ?? 0;
  const eta = job?.etaSeconds;
  const phase = job?.currentPhase;
  const isLongForm = job?.isLongForm;

  const steps = [
    { key: 'DOWNLOADING', label: 'Video yuklash', range: [0, 5] },
    { key: 'EXTRACTING_FRAMES', label: 'Maydon kalibrlash', range: [5, 10] },
    { key: 'ANALYZING_PLAYERS', label: "O'yinchi tracking", range: [10, 72] },
    { key: 'DETECTING_PHASES', label: 'Yarmilar aniqlash', range: [72, 78] },
    { key: 'COMPUTING_TIMELINE', label: 'Timeline analytics', range: [78, 85] },
    { key: 'GENERATING_REPORTS', label: 'Heatmap yaratish', range: [85, 95] },
    { key: 'DETECTING_EVENTS', label: 'Eventlar (gollar)', range: [95, 100] },
  ];

  const currentStepIdx = steps.findIndex((s) => s.key === job?.status);

  return (
    <div className="px-6 py-10">
      <div className="max-w-3xl mx-auto">
        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div className="w-10 h-10 rounded-xl bg-lime-electric/10 flex items-center justify-center">
            <Loader2 className="w-5 h-5 text-lime-electric animate-spin" strokeWidth={1.8} />
          </div>
          <div className="flex-1">
            <div className="text-base font-semibold tracking-tight">
              Tahlil qilinmoqda
            </div>
            <div className="text-2xs text-ink-300 mt-0.5">
              {statusLabel(job?.status)} · {progress}% tugadi
            </div>
          </div>
          {eta > 0 && (
            <div className="text-right">
              <div className="text-2xs text-ink-300">Taxminiy qolgan</div>
              <div className="text-sm font-semibold tabular text-lime-electric">
                {formatRelativeDuration(eta)}
              </div>
            </div>
          )}
        </div>

        {/* Big progress bar */}
        <div className="card p-6 mb-5">
          {/* Bar */}
          <div className="relative h-2 bg-ink-700 rounded-full overflow-hidden mb-5">
            <div
              className="absolute top-0 left-0 h-full bg-lime-electric transition-all duration-500"
              style={{ width: `${progress}%` }}
            >
              <div className="absolute inset-0 shimmer" />
            </div>
          </div>

          {/* Current minute / total */}
          {totalMin > 0 && (
            <div className="grid grid-cols-3 gap-4">
              <div>
                <div className="text-2xs text-ink-300 tracking-wider mb-1">VIDEO DAQIQASI</div>
                <div className="text-2xl font-semibold tabular tracking-tight">
                  {currentMin.toFixed(1)}
                  <span className="text-sm text-ink-300 font-normal">/{totalMin.toFixed(0)} min</span>
                </div>
              </div>
              <div>
                <div className="text-2xs text-ink-300 tracking-wider mb-1">HOZIRGI YARIM</div>
                <div className="text-2xl font-semibold tracking-tight">
                  {phase ? phaseLabel(phase) : '—'}
                </div>
              </div>
              <div>
                <div className="text-2xs text-ink-300 tracking-wider mb-1">PROGRESS</div>
                <div className="text-2xl font-semibold tabular tracking-tight text-lime-electric">
                  {progress}%
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Steps */}
        <div className="card p-5">
          <div className="text-2xs text-ink-300 tracking-wider mb-3">JARAYON BOSQICHLARI</div>
          <div className="flex flex-col gap-1.5">
            {steps.map((step, idx) => {
              const done = currentStepIdx > idx || progress >= step.range[1];
              const active = currentStepIdx === idx;
              const upcoming = !done && !active;
              return (
                <div
                  key={step.key}
                  className={`flex items-center gap-3 px-3 py-2 rounded-md transition-colors ${
                    active ? 'bg-lime-electric/5' : ''
                  }`}
                >
                  <div className="w-5 h-5 rounded-full flex items-center justify-center shrink-0">
                    {done ? (
                      <div className="w-4 h-4 rounded-full bg-lime-electric flex items-center justify-center">
                        <svg viewBox="0 0 12 12" className="w-2.5 h-2.5">
                          <path
                            d="M2 6L5 9L10 3"
                            stroke="#0a0a0d"
                            strokeWidth="2"
                            fill="none"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                        </svg>
                      </div>
                    ) : active ? (
                      <Loader2 className="w-4 h-4 text-lime-electric animate-spin" strokeWidth={2} />
                    ) : (
                      <div className="w-4 h-4 rounded-full border-2 border-ink-600" />
                    )}
                  </div>
                  <div
                    className={`flex-1 text-sm ${
                      active ? 'text-lime-electric font-medium'
                      : done ? 'text-ink-200'
                      : 'text-ink-300'
                    }`}
                  >
                    {step.label}
                  </div>
                  {active && (
                    <div className="text-2xs text-lime-electric font-mono tabular">
                      {progress}%
                    </div>
                  )}
                  {done && (
                    <div className="text-2xs text-ink-300 font-mono">
                      ✓
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        <div className="text-2xs text-ink-300 text-center mt-5">
          5-10 daqiqalik video tahlili odatda 5-15 daqiqa davom etadi.<br />
          Bu sahifada qolib turish shart emas — tugagandan keyin Telegram'ga xabar boradi.
        </div>
      </div>
    </div>
  );
}
