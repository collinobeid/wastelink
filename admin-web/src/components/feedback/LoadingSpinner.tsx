import React from 'react';
import { Loader2 } from 'lucide-react';

export interface LoadingSpinnerProps {
  label?: string;
  size?: 'sm' | 'md' | 'lg';
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  label = 'Loading...',
  size = 'md',
}) => {
  const sizeClasses = {
    sm: 'w-4 h-4',
    md: 'w-8 h-8',
    lg: 'w-12 h-12',
  };

  return (
    <div className="flex flex-col items-center justify-center py-12 space-y-3" role="status" aria-live="polite">
      <Loader2 className={`${sizeClasses[size]} text-emerald-500 animate-spin`} />
      {label && <p className="text-xs text-slate-400 font-medium">{label}</p>}
      <span className="sr-only">{label}</span>
    </div>
  );
};
