import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'emerald' | 'amber' | 'rose' | 'blue' | 'slate' | 'purple';
  size?: 'sm' | 'md';
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  className,
  variant = 'slate',
  size = 'md',
  ...props
}) => {
  const baseStyles = 'inline-flex items-center font-medium rounded-full select-none';

  const sizeStyles = {
    sm: 'text-xs px-2 py-0.5 gap-1',
    md: 'text-xs px-2.5 py-1 gap-1.5',
  };

  const variantStyles = {
    emerald: 'bg-emerald-950/80 text-emerald-300 border border-emerald-700/50',
    amber: 'bg-amber-950/80 text-amber-300 border border-amber-700/50',
    rose: 'bg-rose-950/80 text-rose-300 border border-rose-700/50',
    blue: 'bg-blue-950/80 text-blue-300 border border-blue-700/50',
    slate: 'bg-slate-800 text-slate-300 border border-slate-700',
    purple: 'bg-purple-950/80 text-purple-300 border border-purple-700/50',
  };

  return (
    <span className={twMerge(clsx(baseStyles, sizeStyles[size], variantStyles[variant], className))} {...props}>
      {children}
    </span>
  );
};
