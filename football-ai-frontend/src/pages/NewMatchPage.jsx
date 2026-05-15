import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Save } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { matchesApi } from '@/api/matches';

/**
 * Yangi o'yin yaratish formasi.
 * Saqlangach — match detail sahifasiga o'tadi.
 */
export function NewMatchPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();

  const [form, setForm] = useState({
    homeTeamName: '',
    awayTeamName: '',
    homeScore: '',
    awayScore: '',
    league: '',
    venue: '',
    matchday: '',
    kickoffAt: new Date().toISOString().slice(0, 16),
  });

  const update = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const createMutation = useMutation({
    mutationFn: (data) => matchesApi.create(data),
    onSuccess: (newMatch) => {
      qc.invalidateQueries({ queryKey: ['matches'] });
      navigate(`/matches/${newMatch.id}/upload`);
    },
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    const payload = {
      homeTeamName: form.homeTeamName,
      awayTeamName: form.awayTeamName,
      homeScore: form.homeScore ? parseInt(form.homeScore) : null,
      awayScore: form.awayScore ? parseInt(form.awayScore) : null,
      league: form.league || null,
      venue: form.venue || null,
      matchday: form.matchday ? parseInt(form.matchday) : null,
      kickoffAt: new Date(form.kickoffAt).toISOString(),
    };
    createMutation.mutate(payload);
  };

  return (
    <div>
      <div className="px-6 py-4 border-b border-hairline border-white/[0.06]">
        <Button
          variant="ghost"
          size="sm"
          icon={<ArrowLeft className="w-3.5 h-3.5" />}
          onClick={() => navigate('/matches')}
        >
          Orqaga
        </Button>
      </div>

      <div className="px-6 py-8 max-w-2xl mx-auto">
        <div className="mb-6">
          <div className="text-2xs text-ink-300 tracking-wider uppercase mb-1">
            Yangi yozuv
          </div>
          <div className="text-2xl font-semibold tracking-tight">O'yin qo'shish</div>
          <div className="text-sm text-ink-300 mt-1">
            Asosiy ma'lumotlarni kiriting — keyin video yuklanadi
          </div>
        </div>

        <form onSubmit={handleSubmit} className="card p-6 flex flex-col gap-5">
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Uy jamoasi"
              value={form.homeTeamName}
              onChange={update('homeTeamName')}
              placeholder="Imaan Tech FC"
              required
            />
            <Input
              label="Mehmon jamoasi"
              value={form.awayTeamName}
              onChange={update('awayTeamName')}
              placeholder="Bunyodkor"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Input
              type="number"
              label="Uy hisobi"
              value={form.homeScore}
              onChange={update('homeScore')}
              placeholder="0"
              min={0}
            />
            <Input
              type="number"
              label="Mehmon hisobi"
              value={form.awayScore}
              onChange={update('awayScore')}
              placeholder="0"
              min={0}
            />
          </div>

          <Input
            type="datetime-local"
            label="O'yin vaqti"
            value={form.kickoffAt}
            onChange={update('kickoffAt')}
            required
          />

          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Liga"
              value={form.league}
              onChange={update('league')}
              placeholder="Pro League"
            />
            <Input
              type="number"
              label="Tur (matchday)"
              value={form.matchday}
              onChange={update('matchday')}
              placeholder="14"
              min={1}
            />
          </div>

          <Input
            label="Maydon"
            value={form.venue}
            onChange={update('venue')}
            placeholder="Bunyodkor stadioni"
          />

          {createMutation.isError && (
            <div className="text-2xs text-coral">
              Xato: {createMutation.error?.response?.data?.message || createMutation.error?.message}
            </div>
          )}

          <div className="flex gap-2 pt-3 border-t border-hairline border-white/[0.06]">
            <Button type="button" variant="ghost" onClick={() => navigate('/matches')}>
              Bekor qilish
            </Button>
            <Button
              type="submit"
              variant="primary"
              icon={<Save className="w-4 h-4" />}
              loading={createMutation.isPending}
              className="flex-1"
            >
              Saqlash va video yuklash
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
