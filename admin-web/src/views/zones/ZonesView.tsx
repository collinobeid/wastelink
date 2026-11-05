import React, { useState } from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Table, Column } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { SEED_ZONES } from '../../services/dataService';
import { ServiceZone } from '../../domain/types';
import { isCoordinatesInZone } from '../../domain/validators';
import { MapPin, Navigation, Compass, CheckCircle2, XCircle } from 'lucide-react';

export const ZonesView: React.FC = () => {
  const [testLat, setTestLat] = useState<string>('-6.815');
  const [testLng, setTestLng] = useState<string>('39.280');
  const [testResult, setTestResult] = useState<{ zoneName: string; isInside: boolean } | null>(null);

  const handleRunContainmentTest = () => {
    const lat = parseFloat(testLat);
    const lng = parseFloat(testLng);
    if (isNaN(lat) || isNaN(lng)) return;

    for (const zone of SEED_ZONES) {
      if (isCoordinatesInZone({ latitude: lat, longitude: lng }, zone)) {
        setTestResult({ zoneName: zone.name, isInside: true });
        return;
      }
    }
    setTestResult({ zoneName: 'None (Outside all active zones)', isInside: false });
  };

  const columns: Column<ServiceZone>[] = [
    {
      key: 'zoneId',
      header: 'Zone ID',
      render: (z) => <span className="font-mono text-xs font-semibold text-slate-200">{z.zoneId}</span>,
    },
    {
      key: 'name',
      header: 'Municipal Zone Name',
      render: (z) => <span className="font-semibold text-slate-100">{z.name}</span>,
    },
    {
      key: 'boundingBox',
      header: 'Axis-Aligned Bounding Box (AABB)',
      render: (z) => (
        <span className="font-mono text-xs text-slate-400">
          [{z.minLat.toFixed(3)}, {z.maxLat.toFixed(3)}] × [{z.minLng.toFixed(3)}, {z.maxLng.toFixed(3)}]
        </span>
      ),
    },
    {
      key: 'polygon',
      header: 'Polygon Mode',
      render: (z) => (
        <Badge variant={z.polygonVertices && z.polygonVertices.length >= 3 ? 'emerald' : 'slate'} size="sm">
          {z.polygonVertices ? `${z.polygonVertices.length} Vertices (Jordan Curve)` : 'AABB Approximation'}
        </Badge>
      ),
    },
    {
      key: 'isActive',
      header: 'Status',
      render: (z) =>
        z.isActive ? <Badge variant="emerald">ACTIVE</Badge> : <Badge variant="slate">INACTIVE</Badge>,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <MapPin className="w-6 h-6 text-emerald-400" />
            Service Zone Management
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Configurable municipal collection boundaries, bounding-box pre-filters, and planar point-in-polygon containment.
          </p>
        </div>
      </div>

      <AlertBanner
        type="info"
        title="GIS Boundary Notice & Pending Backend Rules"
        message="Service-zone data is dynamically configurable. No municipal district or city is hardcoded. Planar point-in-polygon verification is executed via the Jordan curve theorem in pure TypeScript/Kotlin. Live mutations to /service_zones are pending final security rule activation."
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Zones Table */}
        <div className="lg:col-span-2">
          <Card variant="default">
            <CardHeader>
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Registered Municipal Service Zones (/service_zones)
              </span>
            </CardHeader>
            <CardContent className="p-0">
              <Table
                columns={columns}
                data={SEED_ZONES}
                keyExtractor={(z) => z.zoneId}
                emptyMessage="No service zones registered."
              />
            </CardContent>
          </Card>
        </div>

        {/* Spatial Containment Test Tool */}
        <div>
          <Card variant="glass">
            <CardHeader>
              <h2 className="text-sm font-semibold text-slate-200 flex items-center gap-2">
                <Compass className="w-4 h-4 text-emerald-400" />
                Spatial Containment Tester
              </h2>
            </CardHeader>
            <CardContent className="p-5 space-y-4">
              <p className="text-xs text-slate-400">
                Test WGS 84 coordinate eligibility against registered zones using the boundary-inclusive ray casting algorithm:
              </p>

              <div className="space-y-3">
                <Input
                  label="Latitude"
                  type="number"
                  step="0.0001"
                  value={testLat}
                  onChange={(e) => setTestLat(e.target.value)}
                />
                <Input
                  label="Longitude"
                  type="number"
                  step="0.0001"
                  value={testLng}
                  onChange={(e) => setTestLng(e.target.value)}
                />
                <Button
                  variant="primary"
                  size="sm"
                  className="w-full"
                  onClick={handleRunContainmentTest}
                  leftIcon={<Navigation className="w-4 h-4" />}
                >
                  Verify Containment
                </Button>
              </div>

              {testResult && (
                <div
                  className={`p-3 rounded-lg border text-xs flex items-start gap-2.5 ${
                    testResult.isInside
                      ? 'bg-emerald-950/60 border-emerald-800 text-emerald-200'
                      : 'bg-rose-950/60 border-rose-800 text-rose-200'
                  }`}
                >
                  {testResult.isInside ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                  ) : (
                    <XCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                  )}
                  <div>
                    <p className="font-semibold">
                      {testResult.isInside ? 'Location Inside Zone' : 'Outside Service Boundary'}
                    </p>
                    <p className="text-[11px] mt-0.5">{testResult.zoneName}</p>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
};
