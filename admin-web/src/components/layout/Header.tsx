import React from 'react';
import { Menu, LogOut, Shield, User } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { Badge } from '../ui/Badge';
import { firebaseEnv } from '../../config/firebase';

interface HeaderProps {
  onToggleSidebar: () => void;
}

export const Header: React.FC<HeaderProps> = ({ onToggleSidebar }) => {
  const { currentUser, userProfile, signOut } = useAuth();

  const envBadgeVariant =
    firebaseEnv === 'PRODUCTION' ? 'rose' : firebaseEnv === 'STAGING' ? 'amber' : 'emerald';

  return (
    <header className="h-16 px-6 bg-slate-900/80 backdrop-blur-md border-b border-slate-800 sticky top-0 z-30 flex items-center justify-between">
      <div className="flex items-center gap-4">
        <button
          onClick={onToggleSidebar}
          className="lg:hidden text-slate-400 hover:text-white p-1 rounded-md"
          aria-label="Toggle navigation"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="hidden sm:flex items-center gap-2">
          <Badge variant={envBadgeVariant} size="sm">
            {firebaseEnv}
          </Badge>
          <span className="text-xs text-slate-400 flex items-center gap-1.5">
            <Shield className="w-3.5 h-3.5 text-emerald-400" />
            Verified Admin Portal
          </span>
        </div>
      </div>

      <div className="flex items-center gap-4">
        {currentUser && (
          <div className="flex items-center gap-3">
            <div className="text-right hidden sm:block">
              <p className="text-xs font-semibold text-slate-200">
                {userProfile?.fullName || currentUser.email}
              </p>
              <p className="text-[10px] text-slate-400 uppercase tracking-wider">
                Authorized Administrator
              </p>
            </div>
            <div className="w-8 h-8 rounded-full bg-emerald-950 border border-emerald-500/30 flex items-center justify-center text-emerald-400 font-bold text-xs">
              <User className="w-4 h-4" />
            </div>
            <button
              onClick={signOut}
              title="Sign Out"
              aria-label="Sign Out"
              className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-slate-800 rounded-lg transition-colors ml-1"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
