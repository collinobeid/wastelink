import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Card, CardContent } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { Recycle, Shield, Lock, Mail } from 'lucide-react';

export const LoginView: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const { signIn, isLoading, error: authError } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/dashboard';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);

    if (!email.trim()) {
      setValidationError('Please enter your administrator email address.');
      return;
    }
    if (!password) {
      setValidationError('Please enter your password.');
      return;
    }

    const res = await signIn(email, password);
    if (res.success) {
      navigate(from, { replace: true });
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center space-y-3">
        <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 mx-auto flex items-center justify-center shadow-lg shadow-emerald-500/5">
          <Recycle className="w-7 h-7" />
        </div>
        <h2 className="text-2xl font-bold tracking-tight text-white">WasteLink Administration</h2>
        <p className="text-xs text-slate-400 max-w-xs mx-auto">
          Municipal Operations, Dispatch & Platform Governance Console
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4 sm:px-0">
        <Card variant="default">
          <CardContent className="space-y-6">
            {(validationError || authError) && (
              <AlertBanner
                type="error"
                message={validationError || authError || 'Authentication failed'}
                onClose={() => setValidationError(null)}
              />
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <Input
                label="Administrator Email"
                type="email"
                autoComplete="email"
                placeholder="admin@wastelink.go.tz"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                leftIcon={<Mail className="w-4 h-4" />}
                required
              />

              <Input
                label="Password"
                type="password"
                autoComplete="current-password"
                placeholder="••••••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                leftIcon={<Lock className="w-4 h-4" />}
                required
              />

              <div className="pt-2">
                <Button
                  type="submit"
                  variant="primary"
                  className="w-full"
                  isLoading={isLoading}
                  leftIcon={<Shield className="w-4 h-4" />}
                >
                  Sign In to Console
                </Button>
              </div>
            </form>

            <div className="pt-2 border-t border-slate-700/60 text-center">
              <p className="text-[11px] text-slate-400 leading-relaxed">
                <strong className="text-slate-300">Security Notice:</strong> Administrative access is restricted to authorized personnel with cryptographically signed server claims. Self-registration is strictly disabled.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
