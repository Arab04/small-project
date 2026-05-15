import { cn } from '@/lib/utils';

/**
 * Status pill/badge.
 *
 * Variant'lar:
 *   success - Lime (tugagan, faol)
 *   warning - Coral (xato, charchash)
 *   info    - Blue (jarayonda)
 *   neutral - Default
 *   live    - Pulsating lime (real-time)
 */
export function Badge({ children, variant = 'neutral', icon, className }) {
  const variants = {
    success:
      'bg-lime-electric/10 text-lime-electric border-lime-electric/25',
    warning:
      'bg-coral/10 text-coral border-coral/25',
    info:
      'bg-sky-electric/10 text-sky-electric border-sky-electric/25',
    neutral:
      'bg-white/[0.04] text-ink-200 border-white/[0.08]',
    live:
      'bg-lime-electric/10 text-lime-electric border-lime-electric/25',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-md',
        'border-hairline text-2xs font-medium tracking-wider',
        variants[variant],
        className
      )}
    >
      {variant === 'live' && (
        <span className="w-1.5 h-1.5 rounded-full bg-lime-electric live-pulse shrink-0" />
      )}
      {icon && <span className="shrink-0">{icon}</span>}
      {children}
    </span>
  );
}
