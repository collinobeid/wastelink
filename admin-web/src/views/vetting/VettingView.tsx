import React, { useEffect, useState } from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Table, Column } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Input } from '../../components/ui/Input';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { fetchCollectorSubmissions, reviewCollectorVetting, parseFirestoreError } from '../../services/dataService';
import { CollectorSubmission } from '../../domain/types';
import { useAuth } from '../../context/AuthContext';
import { ShieldCheck, Check, X, RefreshCw } from 'lucide-react';

export const VettingView: React.FC = () => {
  const [submissions, setSubmissions] = useState<CollectorSubmission[]>([]);
  const [selectedSubmission, setSelectedSubmission] = useState<CollectorSubmission | null>(null);
  const [decisionModal, setDecisionModal] = useState<'APPROVED' | 'REJECTED' | null>(null);
  const [rejectionReason, setRejectionReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [queryError, setQueryError] = useState<string | null>(null);
  const [successBanner, setSuccessBanner] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const { currentUser } = useAuth();

  const loadSubmissions = async () => {
    setIsLoading(true);
    setQueryError(null);
    try {
      const data = await fetchCollectorSubmissions();
      setSubmissions(data);
    } catch (err: unknown) {
      const parsed = parseFirestoreError(err, 'Failed to fetch collector submissions.');
      setQueryError(parsed.message);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadSubmissions();
  }, []);

  const handleDecisionSubmit = async () => {
    if (!selectedSubmission || !decisionModal || !currentUser) return;
    setIsSubmitting(true);
    setActionError(null);

    const res = await reviewCollectorVetting(
      currentUser.uid,
      selectedSubmission.userId,
      decisionModal,
      selectedSubmission.submissionAttempt,
      decisionModal === 'REJECTED' ? rejectionReason : undefined
    );

    setIsSubmitting(false);

    if (res.success) {
      setSuccessBanner(`Collector ${selectedSubmission.userId} successfully ${decisionModal.toLowerCase()}.`);
      setDecisionModal(null);
      setSelectedSubmission(null);
      setRejectionReason('');
      await loadSubmissions();
    } else {
      setActionError(res.error || 'Review operation failed');
    }
  };

  const columns: Column<CollectorSubmission>[] = [
    {
      key: 'userId',
      header: 'Collector UID',
      render: (s) => <span className="font-mono text-xs text-slate-200">{s.userId}</span>,
    },
    {
      key: 'vehicleType',
      header: 'Vehicle & Plate',
      render: (s) => (
        <div>
          <p className="font-semibold text-slate-200">{s.vehicleType}</p>
          <p className="font-mono text-xs text-slate-400">{s.vehiclePlateNumber}</p>
        </div>
      ),
    },
    {
      key: 'licenseNumber',
      header: 'Driver License',
      render: (s) => <span className="font-mono text-xs text-slate-300">{s.licenseNumber}</span>,
    },
    {
      key: 'serviceZoneId',
      header: 'Assigned Zone',
      render: (s) => <span className="text-xs text-slate-300">{s.serviceZoneId}</span>,
    },
    {
      key: 'submissionAttempt',
      header: 'Cycle',
      render: (s) => <Badge variant="slate" size="sm">Attempt #{s.submissionAttempt}</Badge>,
    },
    {
      key: 'status',
      header: 'Review Status',
      render: (s) => (
        <Badge
          variant={s.status === 'APPROVED' ? 'emerald' : s.status === 'REJECTED' ? 'rose' : 'amber'}
        >
          {s.status.replace(/_/g, ' ')}
        </Badge>
      ),
    },
    {
      key: 'actions',
      header: 'Action',
      align: 'right',
      render: (s) => (
        <div className="flex items-center justify-end gap-2">
          {s.status === 'PENDING_REVIEW' ? (
            <>
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  setSelectedSubmission(s);
                  setDecisionModal('APPROVED');
                }}
                leftIcon={<Check className="w-3.5 h-3.5" />}
              >
                Approve
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => {
                  setSelectedSubmission(s);
                  setDecisionModal('REJECTED');
                }}
                leftIcon={<X className="w-3.5 h-3.5" />}
              >
                Reject
              </Button>
            </>
          ) : (
            <Badge variant="slate" size="sm">Reviewed</Badge>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <ShieldCheck className="w-6 h-6 text-emerald-400" />
            Collector Vetting Queue
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Review submitted vehicle specifications, driver licenses, and service zones before granting field access.
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={loadSubmissions} leftIcon={<RefreshCw className="w-4 h-4" />}>
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
            Pending Collector Submissions (/collector_submissions)
          </span>
        </CardHeader>
        <CardContent className="p-0">
          <Table
            columns={columns}
            data={submissions}
            keyExtractor={(s) => s.userId}
            isLoading={isLoading}
            emptyMessage="No pending collector vetting submissions in /collector_submissions."
          />
        </CardContent>
      </Card>

      {/* Decision Modal */}
      {decisionModal && selectedSubmission && (
        <Modal
          isOpen={true}
          onClose={() => {
            setDecisionModal(null);
            setActionError(null);
          }}
          title={decisionModal === 'APPROVED' ? 'Approve Collector Application' : 'Reject Collector Application'}
          description={`Collector UID: ${selectedSubmission.userId} (Attempt #${selectedSubmission.submissionAttempt})`}
          footer={
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setDecisionModal(null)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button
                variant={decisionModal === 'APPROVED' ? 'primary' : 'danger'}
                size="sm"
                onClick={handleDecisionSubmit}
                isLoading={isSubmitting}
              >
                Confirm {decisionModal === 'APPROVED' ? 'Approval' : 'Rejection'}
              </Button>
            </>
          }
        >
          <div className="space-y-4 text-xs">
            {actionError && <AlertBanner type="error" message={actionError} />}

            <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 space-y-1.5">
              <div className="flex justify-between">
                <span className="text-slate-500">Vehicle Type:</span>
                <span className="text-slate-200 font-semibold">{selectedSubmission.vehicleType}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">License Plate:</span>
                <span className="text-slate-200 font-mono font-semibold">{selectedSubmission.vehiclePlateNumber}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Driver License:</span>
                <span className="text-slate-200 font-mono">{selectedSubmission.licenseNumber}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Service Zone:</span>
                <span className="text-slate-200">{selectedSubmission.serviceZoneId}</span>
              </div>
            </div>

            {decisionModal === 'REJECTED' && (
              <div className="space-y-2">
                <Input
                  label="Rejection Justification (Mandatory)"
                  placeholder="e.g., Driver license image is unreadable or expired."
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  required
                />
                <p className="text-[11px] text-slate-500">
                  This reason will be recorded in <code>/collector_submissions/{selectedSubmission.userId}/history</code> and returned to the collector for remediation.
                </p>
              </div>
            )}

            <p className="text-slate-400 leading-relaxed text-[11px]">
              This review will atomically commit updates across <code>/collector_submissions</code>, <code>/users</code>, and <code>/admin_actions_log</code> in compliance with Firestore rules.
            </p>
          </div>
        </Modal>
      )}
    </div>
  );
};
