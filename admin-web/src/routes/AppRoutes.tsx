import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { AdminShell } from '../components/layout/AdminShell';
import { LoginView } from '../views/auth/LoginView';
import { UnauthorizedView } from '../views/auth/UnauthorizedView';
import { DashboardView } from '../views/dashboard/DashboardView';
import { RequestsView } from '../views/requests/RequestsView';
import { UsersView } from '../views/users/UsersView';
import { VettingView } from '../views/vetting/VettingView';
import { CategoriesView } from '../views/categories/CategoriesView';
import { ZonesView } from '../views/zones/ZonesView';
import { PricingView } from '../views/pricing/PricingView';
import { DisputesView } from '../views/disputes/DisputesView';
import { FleetView } from '../views/fleet/FleetView';
import { ReportsView } from '../views/reports/ReportsView';
import { AuditView } from '../views/audit/AuditView';

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      {/* Public Authentication Routes */}
      <Route path="/login" element={<LoginView />} />
      <Route path="/unauthorized" element={<UnauthorizedView />} />

      {/* Protected Administrative Console Routes */}
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AdminShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardView />} />
        <Route path="requests" element={<RequestsView />} />
        <Route path="users" element={<UsersView />} />
        <Route path="vetting" element={<VettingView />} />
        <Route path="categories" element={<CategoriesView />} />
        <Route path="zones" element={<ZonesView />} />
        <Route path="pricing" element={<PricingView />} />
        <Route path="disputes" element={<DisputesView />} />
        <Route path="fleet" element={<FleetView />} />
        <Route path="reports" element={<ReportsView />} />
        <Route path="audit" element={<AuditView />} />
      </Route>

      {/* Fallback Catch-all Route */}
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
};
