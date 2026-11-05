import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { Sidebar } from '../src/components/layout/Sidebar';

describe('Sidebar Navigation Component', () => {
  it('renders all canonical administrative navigation links', () => {
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Sidebar isOpen={true} />
      </MemoryRouter>
    );

    expect(screen.getByText('WasteLink')).toBeInTheDocument();
    expect(screen.getByText('Admin Console')).toBeInTheDocument();

    const expectedNavLabels = [
      'Dashboard',
      'Waste Requests',
      'User Directory',
      'Collector Vetting',
      'Waste Categories',
      'Service Zones',
      'Pricing Rules',
      'Disputes & Grievances',
      'Fleet & Telemetry',
      'Reports & Exports',
      'Administrative Audit',
    ];

    expectedNavLabels.forEach((label) => {
      expect(screen.getByText(label)).toBeInTheDocument();
    });
  });

  it('contains valid route paths for all links', () => {
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Sidebar isOpen={true} />
      </MemoryRouter>
    );

    const dashboardLink = screen.getByText('Dashboard').closest('a');
    expect(dashboardLink).toHaveAttribute('href', '/dashboard');

    const requestsLink = screen.getByText('Waste Requests').closest('a');
    expect(requestsLink).toHaveAttribute('href', '/requests');

    const auditLink = screen.getByText('Administrative Audit').closest('a');
    expect(auditLink).toHaveAttribute('href', '/audit');
  });
});
