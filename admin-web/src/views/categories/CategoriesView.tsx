import React from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Table, Column } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { SEED_CATEGORIES } from '../../services/dataService';
import { WasteCategory } from '../../domain/types';
import { Trash2, AlertTriangle, ShieldAlert } from 'lucide-react';

export const CategoriesView: React.FC = () => {
  const columns: Column<WasteCategory>[] = [
    {
      key: 'id',
      header: 'Category Code',
      render: (c) => <span className="font-mono text-xs font-semibold text-slate-200">{c.id}</span>,
    },
    {
      key: 'displayName',
      header: 'Display Name',
      render: (c) => <span className="font-semibold text-slate-100">{c.displayName}</span>,
    },
    {
      key: 'isHazardous',
      header: 'Hazard Classification',
      render: (c) =>
        c.isHazardous ? (
          <Badge variant="rose" size="sm">
            <ShieldAlert className="w-3 h-3" />
            HAZARDOUS
          </Badge>
        ) : (
          <Badge variant="emerald" size="sm">Standard</Badge>
        ),
    },
    {
      key: 'requiresSpecialHandling',
      header: 'Handling Protocol',
      render: (c) =>
        c.requiresSpecialHandling ? (
          <span className="text-amber-400 font-medium text-xs flex items-center gap-1">
            <AlertTriangle className="w-3 h-3" />
            Special Handling Required
          </span>
        ) : (
          <span className="text-slate-400 text-xs">Standard Municipal Transport</span>
        ),
    },
    {
      key: 'baseEstimateTzs',
      header: 'Base Tariff (TZS)',
      render: (c) => (
        <span className="font-mono text-xs text-slate-200">
          {c.baseEstimateTzs ? `${c.baseEstimateTzs.toLocaleString()} TZS` : 'Custom Quote'}
        </span>
      ),
    },
    {
      key: 'isActive',
      header: 'Status',
      render: (c) =>
        c.isActive ? <Badge variant="emerald">ACTIVE</Badge> : <Badge variant="slate">INACTIVE</Badge>,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <Trash2 className="w-6 h-6 text-emerald-400" />
            Waste Category Administration
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Configurable waste categories and hazardous handling rules synchronized across Android and Web.
          </p>
        </div>
      </div>

      <AlertBanner
        type="warning"
        title="Pending Backend Security Rule Deployment"
        message="Remote mutations to /waste_categories are currently pending deployment of Firestore security rules match /waste_categories/{categoryId} { allow write: if isAdmin(); }. Displaying authoritative domain category specifications."
      />

      <Card variant="default">
        <CardHeader>
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-300">
            Canonical Category Catalog (/waste_categories)
          </span>
        </CardHeader>
        <CardContent className="p-0">
          <Table
            columns={columns}
            data={SEED_CATEGORIES}
            keyExtractor={(c) => c.id}
            emptyMessage="No categories registered."
          />
        </CardContent>
      </Card>
    </div>
  );
};
