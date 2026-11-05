import React, { useEffect, useState, useCallback } from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Table, Column } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Input } from '../../components/ui/Input';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { fetchWasteRequests, parseFirestoreError } from '../../services/dataService';
import { WasteRequest, RequestLifecycleStatus } from '../../domain/types';
import { Search, Eye, RefreshCw } from 'lucide-react';

export const RequestsView: React.FC = () => {
  const [requests, setRequests] = useState<WasteRequest[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedRequest, setSelectedRequest] = useState<WasteRequest | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [queryError, setQueryError] = useState<string | null>(null);

  const loadRequests = useCallback(async () => {
    setIsLoading(true);
    setQueryError(null);
    try {
      const data = await fetchWasteRequests(statusFilter === 'ALL' ? undefined : statusFilter);
      setRequests(data);
    } catch (err: unknown) {
      const parsed = parseFirestoreError(err, 'Failed to fetch waste collection requests.');
      setQueryError(parsed.message);
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const filteredRequests = requests.filter((r) => {
    if (!searchQuery) return true;
    const query = searchQuery.toLowerCase();
    return (
      r.id.toLowerCase().includes(query) ||
      (r.requestRef && r.requestRef.toLowerCase().includes(query)) ||
      r.pickupAddress.toLowerCase().includes(query) ||
      r.clientUid.toLowerCase().includes(query)
    );
  });

  const getStatusBadge = (status: RequestLifecycleStatus) => {
    switch (status) {
      case 'PENDING_DISPATCH':
        return <Badge variant="amber">PENDING DISPATCH</Badge>;
      case 'ASSIGNED_TO_FLEET':
        return <Badge variant="blue">ASSIGNED</Badge>;
      case 'EN_ROUTE':
      case 'ARRIVED_AT_LOCATION':
      case 'COLLECTION_VERIFIED':
      case 'IN_TRANSIT_TO_DUMP':
        return <Badge variant="emerald">{status.replace(/_/g, ' ')}</Badge>;
      case 'COMPLETED':
        return <Badge variant="emerald">COMPLETED</Badge>;
      case 'CANCELLED':
      case 'FAILED':
        return <Badge variant="rose">{status}</Badge>;
      case 'DISPUTED':
        return <Badge variant="rose">DISPUTED</Badge>;
      case 'RESOLVED':
      case 'DISMISSED':
        return <Badge variant="slate">{status}</Badge>;
      case 'FLAGGED_FOR_REVIEW':
        return <Badge variant="amber">FLAGGED</Badge>;
      default:
        return <Badge variant="slate">{status}</Badge>;
    }
  };

  const columns: Column<WasteRequest>[] = [
    {
      key: 'requestRef',
      header: 'Reference',
      render: (r) => (
        <span className="font-mono text-xs font-semibold text-slate-200">
          {r.requestRef || r.id.substring(0, 8)}
        </span>
      ),
    },
    {
      key: 'pickupAddress',
      header: 'Pickup Address',
      render: (r) => <span className="line-clamp-1 max-w-xs">{r.pickupAddress}</span>,
    },
    {
      key: 'category',
      header: 'Category / Tier',
      render: (r) => (
        <span className="text-xs">
          <span className="capitalize font-medium text-slate-200">{r.category}</span>
          <span className="text-slate-400 block text-[11px]">{r.volumeTier.replace(/_/g, ' ')}</span>
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Operational Status',
      render: (r) => getStatusBadge(r.status),
    },
    {
      key: 'createdAt',
      header: 'Created',
      render: (r) => (
        <span className="text-xs text-slate-400">
          {r.createdAt ? new Date(r.createdAt).toLocaleDateString() : 'Pending'}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Inspect',
      align: 'right',
      render: (r) => (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setSelectedRequest(r)}
          leftIcon={<Eye className="w-3.5 h-3.5" />}
        >
          Details
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">Waste Request Management</h1>
          <p className="text-xs text-slate-400 mt-1">
            Authoritative municipal ledger of all resident pickup requests across 13 lifecycle states.
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={loadRequests} leftIcon={<RefreshCw className="w-4 h-4" />}>
          Refresh
        </Button>
      </div>

      {queryError && (
        <AlertBanner type="error" message={queryError} onClose={() => setQueryError(null)} />
      )}

      <Card variant="default">
        <CardHeader className="flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
          <div className="w-full sm:w-72">
            <Input
              placeholder="Search reference, address, UID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
            />
          </div>

          <div className="flex items-center gap-2 overflow-x-auto w-full sm:w-auto">
            {['ALL', 'PENDING_DISPATCH', 'ASSIGNED_TO_FLEET', 'COMPLETED', 'DISPUTED'].map((st) => (
              <button
                key={st}
                onClick={() => setStatusFilter(st)}
                className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-colors whitespace-nowrap ${
                  statusFilter === st
                    ? 'bg-emerald-600 text-white'
                    : 'bg-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                {st.replace(/_/g, ' ')}
              </button>
            ))}
          </div>
        </CardHeader>
        <CardContent className="p-0">
          <Table
            columns={columns}
            data={filteredRequests}
            keyExtractor={(r) => r.id}
            isLoading={isLoading}
            emptyMessage="No waste collection requests found in /waste_requests."
          />
        </CardContent>
      </Card>

      {/* Request Inspection Modal */}
      {selectedRequest && (
        <Modal
          isOpen={true}
          onClose={() => setSelectedRequest(null)}
          title={`Request ${selectedRequest.requestRef || selectedRequest.id}`}
          description={`Customer UID: ${selectedRequest.clientUid}`}
          footer={
            <Button variant="secondary" size="sm" onClick={() => setSelectedRequest(null)}>
              Close Inspection
            </Button>
          }
        >
          <div className="space-y-4 text-xs">
            <div className="grid grid-cols-2 gap-3 p-3 bg-slate-950 rounded-lg border border-slate-800">
              <div>
                <span className="text-slate-500 font-semibold uppercase">Operational Status</span>
                <p className="mt-1">{getStatusBadge(selectedRequest.status)}</p>
              </div>
              <div>
                <span className="text-slate-500 font-semibold uppercase">Volume Tier</span>
                <p className="text-slate-200 mt-1 capitalize font-medium">
                  {selectedRequest.volumeTier.replace(/_/g, ' ')}
                </p>
              </div>
              <div>
                <span className="text-slate-500 font-semibold uppercase">Category</span>
                <p className="text-slate-200 mt-1 capitalize font-medium">{selectedRequest.category}</p>
              </div>
              <div>
                <span className="text-slate-500 font-semibold uppercase">Assigned Collector</span>
                <p className="text-slate-200 mt-1 font-mono text-[11px]">
                  {selectedRequest.assignedTruckId || selectedRequest.assignedCollectorUid || 'Unassigned'}
                </p>
              </div>
            </div>

            <div>
              <span className="text-slate-400 font-semibold block mb-1">Pickup Address</span>
              <p className="p-2.5 bg-slate-950 rounded-lg text-slate-200 border border-slate-800">
                {selectedRequest.pickupAddress}
              </p>
            </div>

            {selectedRequest.notes && (
              <div>
                <span className="text-slate-400 font-semibold block mb-1">Resident Gate / Landmark Notes</span>
                <p className="p-2.5 bg-slate-950 rounded-lg text-slate-300 italic border border-slate-800">
                  "{selectedRequest.notes}"
                </p>
              </div>
            )}

            <div className="p-3 bg-emerald-950/30 border border-emerald-800/40 rounded-lg text-emerald-300 text-[11px] leading-relaxed">
              <strong>Immutable Transition Governance:</strong> Status transitions require compliance with the municipal state machine. Client-side mutations are verified against Firestore Security Rules.
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
