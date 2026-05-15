import { useState } from 'react';
import { Sparkles, Send, FileText } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useNavigate } from 'react-router-dom';

/**
 * Claude AI insight cards.
 * period_comparison.insights — Uzbek tilida avtomatik xulosalar.
 */
export function InsightsSection({ result }) {
  const insights = result?.period_comparison?.insights
    || result?.periodComparison?.insights
    || [];

  // Stamina insight'larni ham qo'shamiz
  const stamina = result?.stamina_analysis || result?.staminaAnalysis;
  const allInsights = [...insights];
  if (stamina?.away_fatigue_detected) {
    allInsights.push({
      text: `Mehmon jamoasida charchash: ${stamina.fatigue_minute_away || 75}-daqiqadan boshlab sprint kamaydi`,
      type: 'warning',
    });
  }
  if (stamina?.home_fatigue_detected) {
    allInsights.push({
      text: `Uy jamoasida charchash: ${stamina.fatigue_minute_home || 75}-daqiqadan boshlab sprint kamaydi`,
      type: 'warning',
    });
  }

  // Comparison — kuchli/zaif tomonlar
  const comparison = result?.raw_analytics?.comparison || {};
  const homeName = result?.home_team_name || 'Uy';
  const awayName = result?.away_team_name || 'Mehmon';
  if (comparison.team_1?.strengths?.length > 0) {
    comparison.team_1.strengths.forEach(s => allInsights.push({ text: `${homeName} kuchli: ${s}`, type: 'success' }));
  }
  if (comparison.team_2?.strengths?.length > 0) {
    comparison.team_2.strengths.forEach(s => allInsights.push({ text: `${awayName} kuchli: ${s}`, type: 'success' }));
  }
  if (comparison.team_1?.weaknesses?.length > 0) {
    comparison.team_1.weaknesses.forEach(w => allInsights.push({ text: `${homeName} zaif: ${w}`, type: 'warning' }));
  }
  if (comparison.team_2?.weaknesses?.length > 0) {
    comparison.team_2.weaknesses.forEach(w => allInsights.push({ text: `${awayName} zaif: ${w}`, type: 'warning' }));
  }
  if (comparison.narrative) {
    allInsights.push({ text: comparison.narrative, type: 'info' });
  }

  if (allInsights.length === 0) {
    return null;
  }

  return (
    <div className="px-6 py-5 bg-gradient-to-b from-ink-900 to-ink-850 border-b border-hairline border-white/[0.06]">
      <div className="flex items-center gap-2.5 mb-3.5">
        <div className="w-[22px] h-[22px] rounded-md bg-lime-electric/12 flex items-center justify-center">
          <Sparkles className="w-3 h-3 text-lime-electric" strokeWidth={1.8} />
        </div>
        <div className="text-sm font-semibold tracking-tight">Claude AI Insights</div>
        <div className="ml-auto text-[11px] text-ink-300">
          {allInsights.length} ta avtomatik xulosa
        </div>
      </div>

      <div className="flex flex-col gap-2.5">
        {allInsights.map((insight, i) => {
          const text = typeof insight === 'string' ? insight : insight.text;
          const type = typeof insight === 'object' ? insight.type : 'info';
          return (
            <InsightCard key={i} index={i + 1} text={text} type={type} />
          );
        })}
      </div>
    </div>
  );
}

function InsightCard({ index, text, type = 'info' }) {
  const styles = {
    info: 'bg-white/[0.025] border-white/[0.04] text-ink-100',
    warning: 'bg-coral/[0.04] border-coral/[0.12] text-ink-100',
    success: 'bg-lime-electric/[0.04] border-lime-electric/[0.12] text-ink-100',
  };

  const indexColor = {
    info: 'text-lime-electric',
    warning: 'text-coral',
    success: 'text-lime-electric',
  };

  return (
    <div
      className={`flex gap-3 px-3.5 py-3 border-hairline rounded-lg ${styles[type]}`}
    >
      <span
        className={`text-[11px] font-semibold font-mono pt-0.5 shrink-0 ${indexColor[type]}`}
      >
        {String(index).padStart(2, '0')}
      </span>
      <div className="text-[13px] leading-relaxed">{text}</div>
    </div>
  );
}

/**
 * Pastki bar — Claude'ga savol berish + Hisobot olish.
 */
export function ClaudePromptBar({ matchId, onAsk }) {
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async () => {
    if (!question.trim()) return;
    setLoading(true);
    try {
      if (onAsk) {
        await onAsk(question);
      }
      setQuestion('');
    } finally {
      setLoading(false);
    }
  };

  const handleReport = () => {
    navigate(`/matches/${matchId}/report`);
  };

  return (
    <div className="px-6 py-3.5 flex items-center gap-3">
      <div className="flex-1 flex items-center gap-2.5 px-3.5 py-2.5 bg-white/[0.03] border-hairline border-white/[0.06] rounded-lg focus-within:border-lime-electric/30 focus-within:bg-white/[0.05] transition-all">
        <Sparkles className="w-3.5 h-3.5 text-lime-electric shrink-0" strokeWidth={1.8} />
        <input
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
          placeholder="Claude'dan o'yin haqida so'rang... masalan: 'Nega Mehmon 2-yarimda charchadi?'"
          className="flex-1 bg-transparent border-none outline-none text-[13px] text-ink-100 placeholder:text-ink-300"
          disabled={loading}
        />
        <kbd className="text-2xs text-ink-300 font-mono px-1.5 py-0.5 bg-white/[0.04] rounded">
          ⌘K
        </kbd>
      </div>
      <Button
        variant="ghost"
        size="md"
        icon={<FileText className="w-3.5 h-3.5" strokeWidth={1.8} />}
        onClick={handleReport}
      >
        Hisobot
      </Button>
      <Button
        variant="primary"
        size="md"
        icon={loading ? null : <Send className="w-3.5 h-3.5" strokeWidth={2} />}
        onClick={handleSubmit}
        loading={loading}
      >
        So'rash
      </Button>
    </div>
  );
}
