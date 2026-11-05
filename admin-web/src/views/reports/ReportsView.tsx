import React, { useState } from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { fetchWasteRequests } from '../../services/dataService';
import { BarChart3, Download, Calendar } from 'lucide-react';

export const ReportsView: React.FC = () => {
  const [isExporting, setIsExporting] = useState(false);
  const [successExport, setSuccessExport] = useState<string | null>(null);

  const handleExportCSV = async () => {
    setIsExporting(true);
    setSuccessExport(null);

    const requests = await fetchWasteRequests();

    if (requests.length === 0) {
      // Export standard headers even if no rows exist
      const csvContent = 'data:text/csv;charset=utf-8,id,requestRef,clientUid,status,category,volumeTier,createdAt\n';
      const encodedUri = encodeURI(csvContent);
      const link = document.createElement('a');
      link.setAttribute('href', encodedUri);
      link.setAttribute('download', `wastelink-requests-${Date.now()}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } else {
      const headers = ['id', 'requestRef', 'clientUid', 'status', 'category', 'volumeTier', 'createdAt'];
      const rows = requests.map((r) => [
        r.id,
        r.requestRef || '',
        r.clientUid,
        r.status,
        r.category,
        r.volumeTier,
        r.createdAt ? new Date(r.createdAt).toISOString() : '',
      ]);

      const csvContent =
        'data:text/csv;charset=utf-8,' +
        [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');

      const encodedUri = encodeURI(csvContent);
      const link = document.createElement('a');
      link.setAttribute('href', encodedUri);
      link.setAttribute('download', `wastelink-requests-${Date.now()}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }

    setIsExporting(false);
    setSuccessExport('Export generated and downloaded successfully.');
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <BarChart3 className="w-6 h-6 text-emerald-400" />
            Operational Reports & Exports
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Verifiable collection records, dispute resolution rates, and municipal data exports.
          </p>
        </div>
        <Button
          variant="primary"
          size="sm"
          onClick={handleExportCSV}
          isLoading={isExporting}
          leftIcon={<Download className="w-4 h-4" />}
        >
          Export Requests CSV
        </Button>
      </div>

      {successExport && (
        <AlertBanner type="success" message={successExport} onClose={() => setSuccessExport(null)} />
      )}

      <AlertBanner
        type="info"
        title="Authoritative Data Integrity"
        message="WasteLink strictly prohibits synthetic or fabricated reporting statistics. All exported figures are derived directly from verified documents in Cloud Firestore."
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card variant="default">
          <CardHeader>
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-300 flex items-center gap-2">
              <Calendar className="w-4 h-4 text-emerald-400" />
              Municipal Export Profiles
            </span>
          </CardHeader>
          <CardContent className="p-6 space-y-4 text-xs text-slate-300">
            <p className="text-slate-400 leading-relaxed">
              Export comprehensive operational logs for audit, environmental compliance, and municipal partner reporting:
            </p>
            <div className="space-y-2">
              <div className="p-3 bg-slate-900 rounded-lg border border-slate-800 flex items-center justify-between">
                <div>
                  <p className="font-semibold text-slate-200">Request Lifecycle Log</p>
                  <p className="text-[11px] text-slate-400">All pickup requests with timestamps and completion statuses</p>
                </div>
                <Button variant="secondary" size="sm" onClick={handleExportCSV}>
                  Export
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
