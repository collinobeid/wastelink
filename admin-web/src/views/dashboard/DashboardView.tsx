import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LoadingSpinner } from '../../components/feedback/LoadingSpinner';
import {
  Users,
  Truck,
  AlertCircle,
  ShieldCheck,
  Activity,
  ArrowRight,
  Clock,
  Radio,
  CheckCircle2,
  RefreshCw,
  AlertTriangle,
  WifiOff,
  ShieldAlert,
} from 'lucide-react';
import {
  fetchDashboardMetrics,
  fetchRecentAuditLogs,
  DataServiceError,
  parseFirestoreError,
} from '../../services/dataService';
import { DashboardMetrics, AdminActionLog } from '../../domain/types';

export const DashboardView: React.FC = () => {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [recentLogs, setRecentLogs] = useState<AdminActionLog[]>([]);
  const [isLive, setIsLive] = useState<boolean>(true);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [queryError, setQueryError] = useState<DataServiceError | null>(null);

  const navigate = useNavigate();

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setQueryError(null);
    try {
      const [metricsRes, logs] = await Promise.all([
        fetchDashboardMetrics(),
        fetchRecentAuditLogs(6),
      ]);

      setMetrics(metricsRes.metrics);
      setIsLive(metricsRes.isLive);
      setRecentLogs(logs);
      setIsLoading(false);
    } catch (err: unknown) {
      setQueryError(parseFirestoreError(err, 'Failed to load dashboard operational data.'));
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (isLoading) {
    return <LoadingSpinner label="Loading operational dashboard..." size="lg" />;
  }

  if (queryError) {
    const isPermission = queryError.kind === 'PERMISSION_DENIED';
    const isNetwork = queryError.kind === 'NETWORK_ERROR';

    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Operations Dashboard</h1>
          <p className="text-xs text-slate-400 mt-1">
            Authoritative municipal overview of citizens, requests, disputes, and field fleet units.
          </p>
        </div>

        <Card variant="bordered" className="border-rose-500/40 bg-rose-950/20">
          <CardContent className="p-6 space-y-4">
            <div className="flex items-start gap-4">
              <div className="w-10 h-10 rounded-xl bg-rose-900/40 border border-rose-500/30 flex items-center justify-center text-rose-400 flex-shrink-0">
                {isPermission ? (
                  <ShieldAlert className="w-5 h-5" />
                ) : isNetwork ? (
                  <WifiOff className="w-5 h-5" />
                ) : (
                  <AlertTriangle className="w-5 h-5" />
                )}
              </div>
              <div className="space-y-1 flex-1">
                <h3 className="text-base font-semibold text-rose-200">
                  {isPermission
                    ? 'Security Rule Access Denied'
                    : isNetwork
                    ? 'Firestore Connectivity Offline'
                    : 'Backend Operational Query Failure'}
                </h3>
                <p className="text-sm text-rose-300/80 leading-relaxed">
                  {queryError.message}
                </p>
                <p className="text-xs text-rose-400/60 font-mono mt-1">
                  Error Code: {queryError.kind} — Authoritative counts cannot be reported under query failure.
                </p>
              </div>
            </div>

            <div className="pt-2 flex items-center gap-3">
              <Button
                variant="danger"
                size="sm"
                onClick={() => loadData()}
                leftIcon={<RefreshCw className="w-4 h-4" />}
              >
                Retry Authoritative Query
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  const isDatasetEmpty =
    (metrics?.totalResidents ?? 0) === 0 &&
    (metrics?.activeCollectors ?? 0) === 0 &&
    (metrics?.pendingRequests ?? 0) === 0 &&
    (metrics?.pendingCollectorVetting ?? 0) === 0 &&
    (metrics?.openDisputes ?? 0) === 0;

  const metricCards = [
    {
      title: 'Registered Residents',
      value: metrics?.totalResidents ?? 0,
      icon: Users,
      color: 'emerald',
      subtext: 'Citizen accounts in good standing',
      link: '/users',
    },
    {
      title: 'Active Collectors',
      value: metrics?.activeCollectors ?? 0,
      icon: Truck,
      color: 'emerald',
      subtext: 'Vetted & operational fleet drivers',
      link: '/users',
    },
    {
      title: 'Pending Requests',
      value: metrics?.pendingRequests ?? 0,
      icon: Clock,
      color: 'amber',
      subtext: 'Awaiting dispatch allocation',
      link: '/requests',
    },
    {
      title: 'Pending Vetting',
      value: metrics?.pendingCollectorVetting ?? 0,
      icon: ShieldCheck,
      color: 'amber',
      subtext: 'Applications requiring review',
      link: '/vetting',
    },
    {
      title: 'Active Disputes',
      value: metrics?.openDisputes ?? 0,
      icon: AlertCircle,
      color: 'rose',
      subtext: 'Grievances in arbitration',
      link: '/disputes',
    },
    {
      title: 'Live Telemetry',
      value: 'Standby',
      icon: Radio,
      color: 'blue',
      subtext: 'Collector GPS radar feed',
      link: '/fleet',
      isPendingBackend: true,
    },
  ];

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Operations Dashboard</h1>
          <p className="text-xs text-slate-400 mt-1">
            Authoritative municipal overview of citizens, requests, disputes, and field fleet units.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Badge variant={isLive ? 'emerald' : 'slate'} size="md">
            <span className="w-1.5 h-1.5 rounded-full bg-current animate-pulse" />
            {metrics?.systemHealth || (isLive ? 'Authoritative Firestore Live' : 'Offline / Standby Mode')}
          </Badge>
        </div>
      </div>

      {/* Dataset Status Banner for genuinely empty dataset */}
      {isDatasetEmpty && (
        <Card variant="glass" className="border-slate-700/60 bg-slate-900/40">
          <CardContent className="p-4 flex items-center gap-3 text-xs text-slate-300">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            <span>
              <strong>Authoritative Database Connected:</strong> All Firestore queries executed successfully, returning zero active records. Collections are currently empty.
            </span>
          </CardContent>
        </Card>
      )}

      {/* Metrics Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {metricCards.map((card) => {
          const Icon = card.icon;
          return (
            <Card
              key={card.title}
              variant="default"
              className="hover:border-slate-600/80 transition-all cursor-pointer group"
              onClick={() => navigate(card.link)}
            >
              <CardContent className="p-5 flex items-start justify-between">
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      {card.title}
                    </p>
                    {card.isPendingBackend && (
                      <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20">
                        Sensor Pending
                      </span>
                    )}
                  </div>
                  <p className="text-3xl font-extrabold text-white tracking-tight">
                    {card.value}
                  </p>
                  <p className="text-xs text-slate-400">{card.subtext}</p>
                </div>
                <div className="w-10 h-10 rounded-xl bg-slate-900 border border-slate-700/60 flex items-center justify-center text-slate-300 group-hover:text-emerald-400 transition-colors">
                  <Icon className="w-5 h-5" />
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Two-Column Layout: Action Shortcuts & Audit Log */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Priority Actions */}
        <div className="space-y-4">
          <Card variant="default">
            <CardHeader>
              <h2 className="text-sm font-semibold text-slate-100 flex items-center gap-2">
                <Activity className="w-4 h-4 text-emerald-400" />
                Quick Operations
              </h2>
            </CardHeader>
            <CardContent className="p-4 space-y-3">
              <Button
                variant="secondary"
                size="sm"
                className="w-full justify-between"
                onClick={() => navigate('/requests')}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Inspect Waste Requests
              </Button>
              <Button
                variant="secondary"
                size="sm"
                className="w-full justify-between"
                onClick={() => navigate('/vetting')}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Review Collector Vetting
              </Button>
              <Button
                variant="secondary"
                size="sm"
                className="w-full justify-between"
                onClick={() => navigate('/disputes')}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Arbitrate Open Disputes
              </Button>
              <Button
                variant="secondary"
                size="sm"
                className="w-full justify-between"
                onClick={() => navigate('/reports')}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Generate Export Reports
              </Button>
            </CardContent>
          </Card>

          <Card variant="glass">
            <CardContent className="p-4 space-y-2 text-xs text-slate-400 leading-relaxed">
              <div className="flex items-center gap-2 text-slate-200 font-semibold">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                Platform Reliability
              </div>
              <p>
                All metric counts reflect authoritative documents in Cloud Firestore evaluated under strict server security rules. Client caching does not override backend verification.
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Recent Platform Audit Feed */}
        <div className="lg:col-span-2">
          <Card variant="default" className="h-full flex flex-col">
            <CardHeader>
              <h2 className="text-sm font-semibold text-slate-100 flex items-center gap-2">
                <Clock className="w-4 h-4 text-slate-400" />
                Recent Administrative Actions Log
              </h2>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => navigate('/audit')}
                rightIcon={<ArrowRight className="w-3.5 h-3.5" />}
              >
                View Full Audit
              </Button>
            </CardHeader>
            <CardContent className="p-0 flex-1">
              {recentLogs.length === 0 ? (
                <div className="py-12 text-center text-slate-400 text-xs">
                  <p>No recent administrative mutations recorded in `/admin_actions_log`.</p>
                  <p className="text-[11px] text-slate-500 mt-1">Actions performed by administrators will appear here automatically.</p>
                </div>
              ) : (
                <div className="divide-y divide-slate-800/80">
                  {recentLogs.map((log) => (
                    <div key={log.id} className="p-4 flex items-start justify-between gap-4 hover:bg-slate-800/30 transition-colors">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold text-slate-200">{log.title || log.action}</span>
                          <Badge variant="slate" size="sm">{log.action}</Badge>
                        </div>
                        <p className="text-xs text-slate-400">{log.description || log.reason}</p>
                      </div>
                      <span className="text-[11px] text-slate-500 shrink-0 font-mono">
                        {new Date(log.timestampMs).toLocaleTimeString()}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
};
