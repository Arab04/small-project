import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Upload, Play, RefreshCcw, AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { matchesApi } from '@/api/matches';
import { analysisApi } from '@/api/video';
import { MatchHero } from '@/components/match/MatchHero';
import { MetricsRow } from '@/components/match/MetricsRow';
import { MomentumChart } from '@/components/match/MomentumChart';
import { PeriodStaminaSplit } from '@/components/match/PeriodStaminaSplit';
import { HeatmapsSection } from '@/components/match/HeatmapsSection';
import { InsightsSection, ClaudePromptBar } from '@/components/match/InsightsSection';
import { AnalysisProgress } from '@/components/match/AnalysisProgress';
import { AnnotatedVideoSection } from '@/components/match/AnnotatedVideoSection';
import { TacticalDashboard } from '@/components/match/TacticalDashboard';
import { phaseLabel, statusLabel, formatRelativeDuration } from '@/lib/utils';

/**
 * Match Detail page — hamma kompont shu yerda.
 *
 * 4 ta holat:
 *   1. Video yuklanmagan → "Video yuklash" CTA
 *   2. Video bor, tahlil yo'q → "Tahlilni boshlash" CTA
 *   3. Tahlil davom etmoqda → AnalysisProgress (real-time poll)
 *   4. Tahlil tugadi → To'liq dashboard (hero + metrics + charts + heatmaps + insights)
 */
export function MatchDetailPage() {
  const { matchId } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const { data: match, isLoading: matchLoading } = useQuery({
    queryKey: ['match', matchId],
    queryFn: () => matchesApi.getById(matchId),
    enabled: !!matchId,
  });

  // Job list — eng so'nggi job
  const { data: jobs = [], refetch: refetchJobs } = useQuery({
    queryKey: ['analysis-jobs', matchId],
    queryFn: () => analysisApi.listJobs(matchId),
    enabled: !!matchId,
  });

  const latestJob = Array.isArray(jobs) && jobs.length > 0 ? jobs[0] : null;
  const isAnalyzing = latestJob && !['COMPLETED', 'FAILED', 'CANCELLED'].includes(latestJob.status);
  const isCompleted = latestJob?.status === 'COMPLETED';
  const isFailed = latestJob?.status === 'FAILED';

  // To'liq tahlil natijasi (faqat tugagan bo'lsa)
  const { data: result } = useQuery({
    queryKey: ['analysis-result', matchId, latestJob?.id],
    queryFn: () => analysisApi.getResult(matchId, latestJob.id),
    enabled: isCompleted,
  });

  // Real-time job status polling
  useEffect(() => {
    if (!isAnalyzing) return;
    const interval = setInterval(() => refetchJobs(), 3000);
    return () => clearInterval(interval);
  }, [isAnalyzing, refetchJobs]);

  if (matchLoading) {
    return <PageSkeleton />;
  }

  if (!match) {
    return (
      <div className="p-8">
        <div className="text-sm text-ink-300">O'yin topilmadi</div>
        <Button variant="ghost" onClick={() => navigate('/matches')} className="mt-4">
          Orqaga
        </Button>
      </div>
    );
  }

  const handleStartAnalysis = async () => {
    try {
      await analysisApi.startLongForm(matchId);
      refetchJobs();
    } catch (e) {
      alert('Tahlil boshlanmadi: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleCancel = async () => {
    if (!latestJob || !confirm("Tahlilni bekor qilishni xohlaysizmi?")) return;
    await analysisApi.cancel(matchId, latestJob.id);
    refetchJobs();
  };

  const handleResume = async () => {
    if (!latestJob) return;
    await analysisApi.resume(matchId, latestJob.id);
    refetchJobs();
  };

  return (
    <div className="min-h-full">
      {/* Top breadcrumb bar */}
      <div className="px-6 py-3.5 border-b border-hairline border-white/[0.06] flex items-center justify-between">
        <Button variant="ghost" size="sm" icon={<ArrowLeft className="w-3.5 h-3.5" />} onClick={() => navigate('/matches')}>
          O'yinlar ro'yxati
        </Button>

        <div className="flex items-center gap-2">
          {isCompleted && (
            <Badge variant="success">
              {formatRelativeDuration(latestJob?.duration || 0)} da tahlil qilindi
            </Badge>
          )}
          {isAnalyzing && (
            <>
              <Badge variant="info">{statusLabel(latestJob.status)}</Badge>
              <Button variant="danger" size="sm" onClick={handleCancel}>
                Bekor qilish
              </Button>
            </>
          )}
          {isFailed && (
            <>
              <Badge variant="warning">Xato — qaytadan urinish mumkin</Badge>
              <Button variant="secondary" size="sm" icon={<RefreshCcw className="w-3.5 h-3.5" />} onClick={handleResume}>
                Davom etish
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Match hero */}
      <MatchHero
        match={match}
        analysisStatus={
          isCompleted ? 'COMPLETED'
          : isAnalyzing ? 'ANALYZING'
          : isFailed ? 'FAILED'
          : null
        }
      />

      {/* CTA states */}
      {!match.videoUrl && (
        <NoVideoState matchId={matchId} navigate={navigate} />
      )}

      {match.videoUrl && !latestJob && (
        <ReadyToAnalyzeState onStart={handleStartAnalysis} />
      )}

      {isAnalyzing && (
        <AnalysisProgress job={latestJob} />
      )}

      {isFailed && (
        <FailedState job={latestJob} onResume={handleResume} />
      )}

      {/* Full dashboard */}
      {isCompleted && (
        <>
          <AnnotatedVideoSection result={result} />
          <MetricsRow result={result} />
          <MomentumChart result={result} />
          <PeriodStaminaSplit result={result} />
          <HeatmapsSection result={result} />
          <TacticalDashboard result={result} />
          <InsightsSection result={result} />
          <ClaudePromptBar matchId={matchId} />
        </>
      )}
    </div>
  );
}

function NoVideoState({ matchId, navigate }) {
  return (
    <div className="px-6 py-16">
      <div className="card p-10 max-w-2xl mx-auto text-center">
        <div className="w-14 h-14 mx-auto rounded-2xl bg-lime-electric/10 flex items-center justify-center mb-5">
          <Upload className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />
        </div>
        <div className="text-lg font-semibold mb-2">Video hali yuklanmagan</div>
        <div className="text-sm text-ink-300 mb-6 max-w-md mx-auto">
          Tahlilni boshlash uchun o'yin videoini yuklang.
          5-10 daqiqalik klipler qo'llab-quvvatlanadi (500 MB gacha).
        </div>
        <Button
          variant="primary"
          icon={<Upload className="w-4 h-4" />}
          onClick={() => navigate(`/matches/${matchId}/upload`)}
        >
          Video yuklash
        </Button>
        <div className="mt-6 grid grid-cols-3 gap-3 text-2xs text-ink-300">
          <div className="px-3 py-2 bg-white/[0.03] rounded">
            <div className="font-mono text-lime-electric font-semibold">MP4 / AVI / MKV</div>
            <div className="mt-1">Video formatlar</div>
          </div>
          <div className="px-3 py-2 bg-white/[0.03] rounded">
            <div className="font-mono text-lime-electric font-semibold">500 MB</div>
            <div className="mt-1">Maksimal hajm</div>
          </div>
          <div className="px-3 py-2 bg-white/[0.03] rounded">
            <div className="font-mono text-lime-electric font-semibold">5-10 daq</div>
            <div className="mt-1">Tavsiya etiladi</div>
          </div>
        </div>
      </div>
    </div>
  );
}

function ReadyToAnalyzeState({ onStart }) {
  return (
    <div className="px-6 py-16">
      <div className="card p-10 max-w-2xl mx-auto text-center">
        <div className="w-14 h-14 mx-auto rounded-2xl bg-lime-electric/10 flex items-center justify-center mb-5">
          <Play className="w-6 h-6 text-lime-electric" strokeWidth={1.5} fill="currentColor" />
        </div>
        <div className="text-lg font-semibold mb-2">Tahlil tayyor</div>
        <div className="text-sm text-ink-300 mb-6 max-w-md mx-auto">
          Video yuklandi. AI tahlilni boshlash uchun tugmani bosing.
          5-10 daqiqalik video tahlili 5-15 daqiqa davom etadi.
        </div>
        <Button variant="primary" icon={<Play className="w-4 h-4" fill="currentColor" />} onClick={onStart}>
          Tahlilni boshlash
        </Button>
      </div>
    </div>
  );
}

function FailedState({ job, onResume }) {
  return (
    <div className="px-6 py-12">
      <div className="card p-8 max-w-2xl mx-auto">
        <div className="flex items-start gap-4">
          <div className="w-10 h-10 rounded-xl bg-coral/10 flex items-center justify-center shrink-0">
            <AlertTriangle className="w-5 h-5 text-coral" strokeWidth={1.6} />
          </div>
          <div className="flex-1">
            <div className="text-base font-semibold mb-1">Tahlil to'xtadi</div>
            <div className="text-sm text-ink-300 mb-3">
              {job?.errorMessage || "Texnik xato yuz berdi. Checkpoint'dan davom etish mumkin."}
            </div>
            {job?.lastCheckpointFrame && (
              <div className="text-2xs text-ink-300 mb-4 font-mono">
                Oxirgi checkpoint: freym {job.lastCheckpointFrame}
              </div>
            )}
            <Button
              variant="primary"
              size="sm"
              icon={<RefreshCcw className="w-3.5 h-3.5" />}
              onClick={onResume}
            >
              Davom etish
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

function PageSkeleton() {
  return (
    <div>
      <div className="h-12 border-b border-hairline border-white/[0.06] shimmer" />
      <div className="h-32 border-b border-hairline border-white/[0.06] shimmer" />
      <div className="grid grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-28 shimmer border-r border-hairline border-white/[0.06]" />
        ))}
      </div>
      <div className="h-56 shimmer m-6 rounded-lg" />
    </div>
  );
}
