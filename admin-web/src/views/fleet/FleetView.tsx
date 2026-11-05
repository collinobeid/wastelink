import React from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { EmptyState } from '../../components/feedback/EmptyState';
import { Radio, Truck, Shield } from 'lucide-react';

export const FleetView: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <Radio className="w-6 h-6 text-emerald-400" />
            Fleet Operations & Telemetry Radar
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Operational status of registered municipal trucks, compactors, and active collector units.
          </p>
        </div>
        <Badge variant="amber" size="md">
          <span className="w-1.5 h-1.5 rounded-full bg-current animate-pulse" />
          Field Telemetry Standby
        </Badge>
      </div>

      <AlertBanner
        type="info"
        title="Authoritative Telemetry Notice"
        message="WasteLink does not simulate live truck movement or fabricate synthetic ETAs. Real-time tracking requires field telemetry emitted by active collectors running the WasteLink Collector app. Live location coordinates are persisted to /live_locations/{collectorId} under strict relationship rules."
      />

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card variant="default">
          <CardHeader>
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-300 flex items-center gap-2">
              <Truck className="w-4 h-4 text-emerald-400" />
              Operational Units
            </span>
          </CardHeader>
          <CardContent className="p-6">
            <EmptyState
              icon={<Truck className="w-6 h-6" />}
              title="No Active Vehicles In Transit"
              description="Vehicles in route to pickup points or dumpsites will appear here when drivers start field execution."
              isBackendPending={false}
            />
          </CardContent>
        </Card>

        <Card variant="default" className="md:col-span-2">
          <CardHeader>
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-300 flex items-center gap-2">
              <Shield className="w-4 h-4 text-blue-400" />
              Telemetry Invariant Rules
            </span>
          </CardHeader>
          <CardContent className="p-6 space-y-3 text-xs text-slate-300 leading-relaxed">
            <p>
              In accordance with <code>firestore.rules</code> Section 7:
            </p>
            <ul className="list-disc list-inside space-y-1.5 text-slate-400 pl-1">
              <li>Admins may read and list all collector locations in <code>/live_locations</code>.</li>
              <li>A collector may only write to their own UID document.</li>
              <li>A resident can only read collector telemetry when an active request assigned to that collector exists.</li>
              <li>Simulated fake animations are strictly forbidden in production.</li>
            </ul>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
