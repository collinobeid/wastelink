import React from 'react';
import { Inbox } from 'lucide-react';
import { Button } from '../ui/Button';

export interface EmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  isBackendPending?: boolean;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  actionLabel,
  onAction,
  isBackendPending = false,
}) => {
  return (
    <div className="py-12 px-6 flex flex-col items-center justify-center text-center max-w-sm mx-auto space-y-4">
      <div className="w-12 h-12 rounded-2xl bg-slate-800 border border-slate-700/60 flex items-center justify-center text-slate-400">
        {icon || <Inbox className="w-6 h-6" />}
      </div>
      <div className="space-y-1">
        <h4 className="text-sm font-semibold text-slate-100 flex items-center justify-center gap-2">
          {title}
          {isBackendPending && (
            <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20">
              Pending Backend
            </span>
          )}
        </h4>
        <p className="text-xs text-slate-400 leading-relaxed">{description}</p>
      </div>
      {actionLabel && onAction && (
        <Button variant="secondary" size="sm" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
};
