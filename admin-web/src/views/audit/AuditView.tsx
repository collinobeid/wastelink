import React, { useEffect, useState } from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Table, Column } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { fetchRecentAuditLogs, parseFirestoreError } from '../../services/dataService';
import { AdminActionLog } from '../../domain/types';
import { History, Search, RefreshCw, ShieldCheck } from 'lucide-react';

export const AuditView: React.FC = () => {
  const [logs, setLogs] = useState<AdminActionLog[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [queryError, setQueryError] = useState<string | null>(null);

  const loadLogs = async () => {
    setIsLoading(true);
    setQueryError(null);
    try {
      const data = await fetchRecentAuditLogs(50);
      setLogs(data);
    } catch (err: unknown) {
      const parsed = parseFirestoreError(err, 'Failed to fetch administrative audit logs.');
      setQueryError(parsed.message);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadLogs();
  }, []);

  const filteredLogs = logs.filter((l) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      l.action.toLowerCase().includes(q) ||
      l.reason.toLowerCase().includes(q) ||
      (l.adminId && l.adminId.toLowerCase().includes(q)) ||
      (l.targetUserId && l.targetUserId.toLowerCase().includes(q))
    );
  });

  const columns: Column<AdminActionLog>[] = [
    {
      key: 'timestampMs',
      header: 'Timestamp',
      render: (l) => (
        <span className="font-mono text-xs text-slate-400">
          {new Date(l.timestampMs).toLocaleString()}
        </span>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      render: (l) => <Badge variant="slate">{l.action}</Badge>,
    },
    {
      key: 'adminId',
      header: 'Acting Admin UID',
      render: (l) => <span className="font-mono text-xs text-slate-300">{l.adminId || l.actorId || 'System'}</span>,
    },
    {
      key: 'targetUserId',
      header: 'Target Entity',
      render: (l) => (
        <span className="font-mono text-xs text-slate-400">
          {l.targetUserId || l.targetId || 'N/A'}
        </span>
      ),
    },
    {
      key: 'reason',
      header: 'Justification / Reason',
      render: (l) => <span className="text-xs text-slate-200 line-clamp-1">{l.reason}</span>,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <History className="w-6 h-6 text-emerald-400" />
            Administrative Audit Ledger
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Immutable, append-only record of all privileged administrative mutations and moderation decisions.
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={loadLogs} leftIcon={<RefreshCw className="w-4 h-4" />}>
          Refresh Ledger
        </Button>
      </div>

      {queryError && (
        <AlertBanner type="error" message={queryError} onClose={() => setQueryError(null)} />
      )}

      <AlertBanner
        type="info"
        title="Tamper-Proof Audit Guarantee"
        message="In accordance with firestore.rules lines 539-561, documents in /admin_actions_log are strictly append-only. No user, administrator, or API key is permitted to update or delete any audit entry."
      />

      <Card variant="default">
        <CardHeader className="flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
          <div className="w-full sm:w-72">
            <Input
              placeholder="Search action, reason, actor UID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
            />
          </div>
          <span className="text-xs text-slate-400 flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            Append-Only Enforcement
          </span>
        </CardHeader>
        <CardContent className="p-0">
          <Table
            columns={columns}
            data={filteredLogs}
            keyExtractor={(l) => l.id}
            isLoading={isLoading}
            emptyMessage="No administrative actions recorded in /admin_actions_log."
          />
        </CardContent>
      </Card>
    </div>
  );
};
