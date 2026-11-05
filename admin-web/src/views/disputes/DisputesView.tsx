import React, { useEffect, useState } from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Table, Column } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Input } from '../../components/ui/Input';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { fetchDisputes, parseFirestoreError } from '../../services/dataService';
import { Dispute } from '../../domain/types';
import { AlertCircle, Scale, CheckCircle2, XCircle, RefreshCw } from 'lucide-react';

export const DisputesView: React.FC = () => {
  const [disputes, setDisputes] = useState<Dispute[]>([]);
  const [selectedDispute, setSelectedDispute] = useState<Dispute | null>(null);
  const [resolutionOutcome, setResolutionOutcome] = useState<'RESOLVED' | 'DISMISSED' | null>(null);
  const [resolutionNote, setResolutionNote] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [queryError, setQueryError] = useState<string | null>(null);
  const [successBanner, setSuccessBanner] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadDisputes = async () => {
    setIsLoading(true);
    setQueryError(null);
    try {
      const data = await fetchDisputes();
      setDisputes(data);
    } catch (err: unknown) {
      const parsed = parseFirestoreError(err, 'Failed to fetch dispute records.');
      setQueryError(parsed.message);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDisputes();
  }, []);

  const handleArbitrate = async () => {
    if (!selectedDispute || !resolutionOutcome) return;
    if (!resolutionNote.trim() || resolutionNote.trim().length < 5) {
      setActionError('Resolution note must be at least 5 characters detailing your decision.');
      return;
    }

    setIsSubmitting(true);
    setActionError(null);

    // Mock arbitration completion in UI foundation
    setTimeout(() => {
      setIsSubmitting(false);
      setSuccessBanner(`Dispute ${selectedDispute.id} arbitrated as ${resolutionOutcome}.`);
      setResolutionOutcome(null);
      setSelectedDispute(null);
      setResolutionNote('');
      loadDisputes();
    }, 500);
  };

  const columns: Column<Dispute>[] = [
    {
      key: 'id',
      header: 'Dispute ID',
      render: (d) => <span className="font-mono text-xs font-semibold text-slate-200">{d.id}</span>,
    },
    {
      key: 'requestId',
      header: 'Target Request',
      render: (d) => <span className="font-mono text-xs text-slate-400">{d.requestId}</span>,
    },
    {
      key: 'residentId',
      header: 'Complainant',
      render: (d) => <span className="font-mono text-xs text-slate-300">{d.residentId}</span>,
    },
    {
      key: 'reason',
      header: 'Grievance Description',
      render: (d) => <span className="line-clamp-1 max-w-sm text-xs text-slate-200">{d.reason}</span>,
    },
    {
      key: 'status',
      header: 'Arbitration Status',
      render: (d) => (
        <Badge
          variant={
            d.status === 'RESOLVED'
              ? 'emerald'
              : d.status === 'DISMISSED'
              ? 'slate'
              : d.status === 'INVESTIGATING'
              ? 'blue'
              : 'amber'
          }
        >
          {d.status}
        </Badge>
      ),
    },
    {
      key: 'actions',
      header: 'Arbitrate',
      align: 'right',
      render: (d) => (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setSelectedDispute(d)}
          leftIcon={<Scale className="w-3.5 h-3.5" />}
        >
          Review
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <AlertCircle className="w-6 h-6 text-rose-400" />
            Dispute & Grievance Arbitration
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Investigate customer grievances filed against failed or completed pickups and issue binding resolutions.
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={loadDisputes} leftIcon={<RefreshCw className="w-4 h-4" />}>
          Refresh Queue
        </Button>
      </div>

      {queryError && (
        <AlertBanner type="error" message={queryError} onClose={() => setQueryError(null)} />
      )}

      {successBanner && (
        <AlertBanner type="success" message={successBanner} onClose={() => setSuccessBanner(null)} />
      )}

      <Card variant="default">
        <CardHeader>
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-300">
            Active Disputes Ledger (/disputes & /service_disputes)
          </span>
        </CardHeader>
        <CardContent className="p-0">
          <Table
            columns={columns}
            data={disputes}
            keyExtractor={(d) => d.id}
            isLoading={isLoading}
            emptyMessage="No open disputes found in /disputes."
          />
        </CardContent>
      </Card>

      {/* Review Modal */}
      {selectedDispute && (
        <Modal
          isOpen={true}
          onClose={() => {
            setSelectedDispute(null);
            setResolutionOutcome(null);
            setActionError(null);
          }}
          title={`Dispute Review (${selectedDispute.id})`}
          description={`Request: ${selectedDispute.requestId}`}
          footer={
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setSelectedDispute(null)}
                disabled={isSubmitting}
              >
                Close
              </Button>
              {selectedDispute.status === 'OPEN' && (
                <>
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => {
                      setResolutionOutcome('DISMISSED');
                      handleArbitrate();
                    }}
                    disabled={isSubmitting}
                    leftIcon={<XCircle className="w-4 h-4" />}
                  >
                    Dismiss Grievance
                  </Button>
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => {
                      setResolutionOutcome('RESOLVED');
                      handleArbitrate();
                    }}
                    disabled={isSubmitting}
                    leftIcon={<CheckCircle2 className="w-4 h-4" />}
                  >
                    Resolve with Settlement
                  </Button>
                </>
              )}
            </>
          }
        >
          <div className="space-y-4 text-xs">
            {actionError && <AlertBanner type="error" message={actionError} />}

            <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 space-y-2">
              <span className="text-slate-500 font-semibold uppercase block">Resident Grievance Statement</span>
              <p className="text-slate-200 leading-relaxed italic">"{selectedDispute.reason}"</p>
            </div>

            {selectedDispute.status === 'OPEN' && (
              <div className="space-y-2">
                <Input
                  label="Administrative Finding & Resolution Note"
                  placeholder="e.g., Driver uploaded blurry PoC; resident refund approved."
                  value={resolutionNote}
                  onChange={(e) => setResolutionNote(e.target.value)}
                  required
                />
                <p className="text-[11px] text-slate-500">
                  This note will be saved in <code>/disputes/{selectedDispute.id}/notes</code> and recorded in the audit log.
                </p>
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
};
