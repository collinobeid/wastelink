import React from 'react';
import { AlertCircle, AlertTriangle, CheckCircle, Info, X } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface AlertBannerProps {
  type?: 'info' | 'warning' | 'error' | 'success';
  title?: string;
  message: string;
  onClose?: () => void;
  className?: string;
}

export const AlertBanner: React.FC<AlertBannerProps> = ({
  type = 'info',
  title,
  message,
  onClose,
  className,
}) => {
  const styles = {
    info: 'bg-blue-950/60 border-blue-800 text-blue-200',
    warning: 'bg-amber-950/60 border-amber-800 text-amber-200',
    error: 'bg-rose-950/60 border-rose-800 text-rose-200',
    success: 'bg-emerald-950/60 border-emerald-800 text-emerald-200',
  };

  const icons = {
    info: <Info className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />,
    warning: <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />,
    error: <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />,
    success: <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />,
  };

  return (
    <div
      role="alert"
      className={twMerge(
        clsx(
          'p-3.5 rounded-xl border flex items-start justify-between gap-3 text-xs leading-relaxed',
          styles[type],
          className
        )
      )}
    >
      <div className="flex items-start gap-2.5">
        {icons[type]}
        <div>
          {title && <p className="font-semibold mb-0.5">{title}</p>}
          <p>{message}</p>
        </div>
      </div>
      {onClose && (
        <button
          onClick={onClose}
          aria-label="Dismiss alert"
          className="text-slate-400 hover:text-slate-200 transition-colors p-0.5 -mr-1"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
};
