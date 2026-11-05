import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Card, CardContent } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { ShieldAlert, LogOut } from 'lucide-react';

export const UnauthorizedView: React.FC = () => {
  const { currentUser, signOut } = useAuth();
  const navigate = useNavigate();

  const handleSignOut = async () => {
    await signOut();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md px-4 sm:px-0">
        <Card variant="default">
          <CardContent className="text-center py-8 space-y-4">
            <div className="w-16 h-16 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-400 mx-auto flex items-center justify-center">
              <ShieldAlert className="w-8 h-8" />
            </div>

            <div className="space-y-1">
              <h2 className="text-xl font-bold text-white">Access Denied (403)</h2>
              <p className="text-xs text-slate-400 leading-relaxed max-w-sm mx-auto">
                Account <span className="font-mono text-slate-300 font-semibold">{currentUser?.email || 'authenticated'}</span> does not possess cryptographic administrator claims (<code className="text-rose-400">token.admin == true</code>).
              </p>
            </div>

            <div className="p-3 bg-slate-900 border border-slate-700/60 rounded-xl text-xs text-left text-slate-400 space-y-1">
              <p className="font-semibold text-slate-200">Why am I seeing this?</p>
              <p>
                WasteLink enforces true security boundaries. Simply having a resident or collector account does not permit access to municipal operations or dispatch ledgers.
              </p>
            </div>

            <div className="pt-2 flex justify-center">
              <Button
                variant="danger"
                size="md"
                onClick={handleSignOut}
                leftIcon={<LogOut className="w-4 h-4" />}
              >
                Sign Out & Return to Login
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
