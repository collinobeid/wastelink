import React from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Table, Column } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { SEED_PRICING } from '../../services/dataService';
import { DollarSign, ShieldAlert } from 'lucide-react';

interface PricingItem {
  id: string;
  category: string;
  volumeTier: string;
  priceTzs: number | null;
  currency: string;
  effectiveDate: string;
}

export const PricingView: React.FC = () => {
  const columns: Column<PricingItem>[] = [
    {
      key: 'category',
      header: 'Waste Category',
      render: (p) => <span className="font-semibold text-slate-100">{p.category}</span>,
    },
    {
      key: 'volumeTier',
      header: 'Volume Tier Classification',
      render: (p) => <span className="text-slate-200">{p.volumeTier}</span>,
    },
    {
      key: 'priceTzs',
      header: 'Authoritative Tariff (TZS)',
      render: (p) => (
        <span className="font-mono text-xs font-semibold text-emerald-400">
          {p.priceTzs ? `${p.priceTzs.toLocaleString()} TZS` : 'Manual Dispatcher Quote'}
        </span>
      ),
    },
    {
      key: 'effectiveDate',
      header: 'Effective Date',
      render: (p) => <span className="font-mono text-xs text-slate-400">{p.effectiveDate}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      render: () => <Badge variant="emerald" size="sm">ACTIVE</Badge>,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <DollarSign className="w-6 h-6 text-emerald-400" />
            Pricing & Tariff Management
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Authoritative municipal collection tariffs by waste category, volume tier, and special handling.
          </p>
        </div>
      </div>

      <AlertBanner
        type="warning"
        title="Authoritative Pricing Principle & Pending Microservice"
        message="Client-side pricing calculations must never override authoritative backend pricing. Dynamic price mutation and history tracking in /pricing_rules is pending deployment of the municipal pricing microservice. Displaying approved baseline tariff schedules."
      />

      <Card variant="default">
        <CardHeader>
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-300">
            Approved Municipal Tariff Schedule (/pricing_rules)
          </span>
        </CardHeader>
        <CardContent className="p-0">
          <Table
            columns={columns}
            data={SEED_PRICING}
            keyExtractor={(p) => p.id}
            emptyMessage="No pricing schedules registered."
          />
        </CardContent>
      </Card>

      <Card variant="glass">
        <CardContent className="p-4 flex items-start gap-3 text-xs text-slate-400 leading-relaxed">
          <ShieldAlert className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <div>
            <span className="font-semibold text-slate-200 block mb-1">Audit Governance Notice</span>
            All tariff modifications will require an auditable trail recording the acting administrator UID, timestamp, and previous rate. Arbitrary client overrides are prevented by Firestore security rules.
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
