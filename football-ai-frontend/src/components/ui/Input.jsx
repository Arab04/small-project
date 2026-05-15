import { forwardRef } from 'react';
import { cn } from '@/lib/utils';

export const Input = forwardRef(function Input(
  { className, label, error, hint, icon, ...props },
  ref
) {
  return (
    <div className="w-full">
      {label && (
        <label className="block text-xs font-medium text-ink-200 mb-1.5 tracking-wide uppercase">
          {label}
        </label>
      )}
      <div className="relative">
        {icon && (
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-300">
            {icon}
          </span>
        )}
        <input
          ref={ref}
          className={cn(
            'w-full bg-white/[0.03] border-hairline border-white/[0.08] rounded-lg',
            'px-3.5 py-2.5 text-sm text-ink-50 placeholder:text-ink-300',
            'focus:outline-none focus:border-lime-electric/40 focus:bg-white/[0.05]',
            'transition-all duration-150',
            icon && 'pl-9',
            error && 'border-coral/40 focus:border-coral',
            className
          )}
          {...props}
        />
      </div>
      {error && <p className="text-2xs text-coral mt-1.5">{error}</p>}
      {hint && !error && <p className="text-2xs text-ink-300 mt-1.5">{hint}</p>}
    </div>
  );
});
