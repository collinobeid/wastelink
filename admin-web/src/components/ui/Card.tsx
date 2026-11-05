import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'glass' | 'bordered';
}

export const Card: React.FC<CardProps> = ({
  children,
  className,
  variant = 'default',
  ...props
}) => {
  const baseStyles = 'rounded-xl transition-all duration-200';

  const variantStyles = {
    default: 'bg-slate-800/80 border border-slate-700/60 shadow-md backdrop-blur-sm',
    glass: 'bg-slate-800/40 border border-slate-700/40 backdrop-blur-md shadow-lg',
    bordered: 'bg-slate-900 border border-slate-700 shadow-sm',
  };

  return (
    <div className={twMerge(clsx(baseStyles, variantStyles[variant], className))} {...props}>
      {children}
    </div>
  );
};

export const CardHeader: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({
  children,
  className,
  ...props
}) => {
  return (
    <div className={twMerge('px-6 py-4 border-b border-slate-700/50 flex items-center justify-between', className)} {...props}>
      {children}
    </div>
  );
};

export const CardContent: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({
  children,
  className,
  ...props
}) => {
  return (
    <div className={twMerge('p-6', className)} {...props}>
      {children}
    </div>
  );
};

export const CardFooter: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({
  children,
  className,
  ...props
}) => {
  return (
    <div className={twMerge('px-6 py-3 border-t border-slate-700/50 bg-slate-850/40 rounded-b-xl flex items-center justify-end gap-3', className)} {...props}>
      {children}
    </div>
  );
};
