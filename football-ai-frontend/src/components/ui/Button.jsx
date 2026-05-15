import { cn } from '@/lib/utils';

/**
 * Button component.
 *
 * Variant'lar:
 *   primary   - Lime accent (asosiy harakatlar uchun)
 *   secondary - Toza outline (yumshoq harakatlar)
 *   ghost     - Faqat hover'da fon
 *   danger    - Coral (o'chirish, bekor qilish)
 *
 * Sizes: sm | md | lg
 */
export function Button({
  children,
  variant = 'primary',
  size = 'md',
  className,
  disabled,
  loading,
  icon,
  ...props
}) {
  const base = 'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-all disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-ink-900';

  const variants = {
    primary:
      'bg-lime-electric text-ink-900 hover:bg-lime-electric/90 focus:ring-lime-electric/50 active:scale-[0.98]',
    secondary:
      'bg-white/[0.04] text-ink-50 border-hairline border-white/[0.08] hover:bg-white/[0.08] hover:border-white/[0.12]',
    ghost:
      'bg-transparent text-ink-100 hover:bg-white/[0.04] hover:text-ink-50',
    danger:
      'bg-coral/15 text-coral border-hairline border-coral/30 hover:bg-coral/25',
  };

  const sizes = {
    sm: 'text-xs px-3 py-1.5',
    md: 'text-sm px-4 py-2.5',
    lg: 'text-base px-5 py-3',
  };

  return (
    <button
      className={cn(base, variants[variant], sizes[size], className)}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? (
        <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
      ) : icon ? (
        <span className="shrink-0">{icon}</span>
      ) : null}
      {children}
    </button>
  );
}
