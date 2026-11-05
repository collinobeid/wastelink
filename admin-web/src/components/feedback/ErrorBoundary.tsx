import { Component, ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Button } from '../ui/Button';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[WasteLink Admin ErrorBoundary]', error, errorInfo);
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: null });
    window.location.reload();
  };

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="min-h-[400px] flex items-center justify-center p-6">
          <div className="max-w-md w-full bg-slate-900 border border-rose-500/30 rounded-2xl p-6 text-center shadow-xl space-y-4">
            <div className="w-12 h-12 rounded-full bg-rose-500/10 text-rose-400 mx-auto flex items-center justify-center">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <h2 className="text-lg font-semibold text-slate-100">Application Rendering Interrupted</h2>
              <p className="text-xs text-slate-400">
                A UI component encountered an unexpected runtime error. Your session data remains safe.
              </p>
            </div>
            {this.state.error && (
              <pre className="text-left text-xs bg-slate-950 p-3 rounded-lg border border-slate-800 text-rose-300 overflow-x-auto max-h-32">
                {this.state.error.message}
              </pre>
            )}
            <div className="pt-2">
              <Button variant="primary" onClick={this.handleReset} leftIcon={<RotateCcw className="w-4 h-4" />}>
                Reload Application
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
