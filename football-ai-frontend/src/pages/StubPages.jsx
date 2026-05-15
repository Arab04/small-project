import { Users, FileText, Sparkles, Settings as SettingsIcon } from 'lucide-react';

/**
 * Stub sahifa — tarkib keyinroq qo'shiladi.
 */
function StubPage({ icon, title, description, comingSoon = true }) {
  return (
    <div className="min-h-full">
      <div className="px-8 py-7 border-b border-hairline border-white/[0.06]">
        <div className="text-2xl font-semibold tracking-tight">{title}</div>
        <div className="text-sm text-ink-300 mt-1">{description}</div>
      </div>
      <div className="px-8 py-16">
        <div className="card p-12 text-center max-w-2xl mx-auto">
          <div className="w-14 h-14 mx-auto rounded-2xl bg-lime-electric/10 flex items-center justify-center mb-4">
            {icon}
          </div>
          <div className="text-base font-semibold mb-1">{title}</div>
          {comingSoon && (
            <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-lime-electric/10 text-lime-electric text-2xs font-medium tracking-wider mt-2 mb-3">
              TEZ ORADA
            </div>
          )}
          <div className="text-sm text-ink-300 max-w-md mx-auto">
            Bu bo'lim hozir ishlanmoqda. Tez orada ishga tushiriladi.
          </div>
        </div>
      </div>
    </div>
  );
}

export function TeamsPage() {
  return (
    <StubPage
      icon={<Users className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />}
      title="Jamoalar"
      description="Klubdagi jamoa va o'yinchilarni boshqarish"
    />
  );
}

export function ReportsPage() {
  return (
    <StubPage
      icon={<FileText className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />}
      title="Hisobotlar"
      description="Tahlil natijalarini PDF / Word ko'rinishida eksport qilish"
    />
  );
}

export function ClaudeChatPage() {
  return (
    <StubPage
      icon={<Sparkles className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />}
      title="Claude AI"
      description="Tahlilingiz haqida Claude'dan so'rang"
    />
  );
}

export function SettingsPage() {
  return (
    <StubPage
      icon={<SettingsIcon className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />}
      title="Sozlamalar"
      description="Klub profil, integratsiyalar, va boshqa parametrlar"
    />
  );
}

export function ReportPage() {
  return (
    <StubPage
      icon={<FileText className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />}
      title="Match hisoboti"
      description="Bu o'yin uchun to'liq PDF hisobot"
    />
  );
}

export function PlayersPage() {
  return (
    <StubPage
      icon={<Users className="w-6 h-6 text-lime-electric" strokeWidth={1.5} />}
      title="O'yinchilar"
      description="Tracking qilingan o'yinchilar ro'yxati"
    />
  );
}
