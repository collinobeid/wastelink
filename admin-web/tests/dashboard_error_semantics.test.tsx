import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { DashboardView } from '../src/views/dashboard/DashboardView';
import * as DataServiceModule from '../src/services/dataService';

describe('Dashboard Error Semantics & Empty State Differentiation Suite', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Test 10a: Permission Denied failure is distinguishable and never shows a successful empty result
  it('10a. Firestore permission failure displays dedicated security error panel and never reports 0 or operational', async () => {
    vi.spyOn(DataServiceModule, 'fetchDashboardMetrics').mockRejectedValueOnce(
      new DataServiceModule.DataServiceError(
        'Access denied: Insufficient Firestore permissions.',
        'PERMISSION_DENIED'
      )
    );
    vi.spyOn(DataServiceModule, 'fetchRecentAuditLogs').mockResolvedValueOnce([]);

    render(
      <MemoryRouter>
        <DashboardView />
      </MemoryRouter>
    );

    // Initial loading
    expect(screen.getAllByText(/loading operational dashboard/i)[0]).toBeInTheDocument();

    // After failure
    await waitFor(() => {
      expect(screen.getByText('Security Rule Access Denied')).toBeInTheDocument();
    });

    expect(screen.getByText(/Insufficient Firestore permissions/)).toBeInTheDocument();
    expect(screen.getByText(/Error Code: PERMISSION_DENIED/)).toBeInTheDocument();
    expect(screen.getByText('Retry Authoritative Query')).toBeInTheDocument();

    // MUST NOT render fake 0 metric cards or claim operational
    expect(screen.queryByText('Registered Residents')).not.toBeInTheDocument();
    expect(screen.queryByText('100% Operational')).not.toBeInTheDocument();
    expect(screen.queryByText('Authoritative Connected (Operational)')).not.toBeInTheDocument();
  });

  // Test 10b: Network failure displays distinct offline connectivity banner
  it('10b. Firestore network failure displays distinct connectivity error panel', async () => {
    vi.spyOn(DataServiceModule, 'fetchDashboardMetrics').mockRejectedValueOnce(
      new DataServiceModule.DataServiceError(
        'Network connection failed or Firestore is unavailable.',
        'NETWORK_ERROR'
      )
    );
    vi.spyOn(DataServiceModule, 'fetchRecentAuditLogs').mockResolvedValueOnce([]);

    render(
      <MemoryRouter>
        <DashboardView />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Firestore Connectivity Offline')).toBeInTheDocument();
    });

    expect(screen.getByText(/Network connection failed or Firestore is unavailable/)).toBeInTheDocument();
    expect(screen.getByText(/Error Code: NETWORK_ERROR/)).toBeInTheDocument();
    expect(screen.getByText('Retry Authoritative Query')).toBeInTheDocument();

    expect(screen.queryByText('Registered Residents')).not.toBeInTheDocument();
  });

  // Test 10c: Genuinely empty dashboard correctly displays authoritative 0s with honest status
  it('10c. Genuinely empty database displays authoritative 0s and honest empty dataset status', async () => {
    vi.spyOn(DataServiceModule, 'fetchDashboardMetrics').mockResolvedValueOnce({
      metrics: {
        totalResidents: 0,
        activeCollectors: 0,
        pendingCollectorVetting: 0,
        pendingRequests: 0,
        activeFleetInTransit: 0,
        completedCollections: 0,
        openDisputes: 0,
        systemHealth: 'Authoritative Connected (Empty Dataset)',
      },
      isLive: true,
    });
    vi.spyOn(DataServiceModule, 'fetchRecentAuditLogs').mockResolvedValueOnce([]);

    render(
      <MemoryRouter>
        <DashboardView />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Operations Dashboard')).toBeInTheDocument();
    });

    // Honest empty dataset badge
    expect(screen.getByText('Authoritative Connected (Empty Dataset)')).toBeInTheDocument();

    // Informative empty state callout
    expect(
      screen.getByText(/All Firestore queries executed successfully, returning zero active records/)
    ).toBeInTheDocument();

    // Metric cards present with authoritative 0 values
    expect(screen.getByText('Registered Residents')).toBeInTheDocument();
    expect(screen.getByText('Active Collectors')).toBeInTheDocument();
    expect(screen.getByText('Pending Requests')).toBeInTheDocument();

    // Error panels must not be present
    expect(screen.queryByText('Security Rule Access Denied')).not.toBeInTheDocument();
    expect(screen.queryByText('Firestore Connectivity Offline')).not.toBeInTheDocument();
  });
});
