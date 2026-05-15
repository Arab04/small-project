import { cn } from '@/lib/utils';

export function Card({ children, className, padding = true, ...props }) {
  return (
    <div
      className={cn(
        'card',
        padding && 'p-5',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}

export function CardHeader({ children, className }) {
  return (
    <div className={cn('flex items-center justify-between mb-4', className)}>
      {children}
    </div>
  );
}

export function CardTitle({ children, subtitle, className }) {
  return (
    <div className={className}>
      <div className="text-sm font-semibold tracking-tight">{children}</div>
      {subtitle && <div className="text-2xs text-ink-300 mt-0.5">{subtitle}</div>}
    </div>
  );
}
