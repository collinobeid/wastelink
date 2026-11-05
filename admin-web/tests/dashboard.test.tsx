import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { DashboardView } from '../src/views/dashboard/DashboardView';
import * as DataServiceModule from '../src/services/dataService';

describe('DashboardView Component', () => {
  it('renders operational metric cards and audit feed', async () => {
    vi.spyOn(DataServiceModule, 'fetchDashboardMetrics').mockResolvedValue({
      metrics: {
        totalResidents: 142,
        activeCollectors: 18,
        pendingCollectorVetting: 3,
        pendingRequests: 7,
        activeFleetInTransit: 0,
        completedCollections: 89,
        openDisputes: 1,
        systemHealth: '100% Operational',
      },
      isLive: true,
    });

    vi.spyOn(DataServiceModule, 'fetchRecentAuditLogs').mockResolvedValue([
      {
        id: 'log-1',
        adminId: 'admin-1',
        action: 'COLLECTOR_APPROVED',
        reason: 'Valid license and inspection pass',
        title: 'Collector Application Approved',
        description: 'Collector vetted for pilot zone',
        timestampMs: Date.now(),
      },
    ]);

    render(
      <MemoryRouter>
        <DashboardView />
      </MemoryRouter>
    );

    // Initial loading spinner
    expect(screen.getAllByText(/loading operational dashboard/i)[0]).toBeInTheDocument();

    // After loading completes
    await waitFor(() => {
      expect(screen.getByText('Operations Dashboard')).toBeInTheDocument();
    });

    expect(screen.getByText('Authoritative Firestore Live')).toBeInTheDocument();
    expect(screen.getByText('142')).toBeInTheDocument(); // total residents
    expect(screen.getByText('18')).toBeInTheDocument();  // active collectors
    expect(screen.getByText('7')).toBeInTheDocument();   // pending requests
    expect(screen.getByText('3')).toBeInTheDocument();   // pending vetting
    expect(screen.getByText('1')).toBeInTheDocument();   // open disputes

    // Check recent log
    expect(screen.getByText('Collector Application Approved')).toBeInTheDocument();
  });
});
