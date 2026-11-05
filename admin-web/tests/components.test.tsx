import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Button } from '../src/components/ui/Button';
import { Badge } from '../src/components/ui/Badge';
import { Card, CardHeader, CardContent } from '../src/components/ui/Card';
import { Modal } from '../src/components/ui/Modal';
import { EmptyState } from '../src/components/feedback/EmptyState';
import { AlertBanner } from '../src/components/feedback/AlertBanner';

describe('UI Primitives and Design System Components', () => {
  it('renders Button with variants and handles click events', () => {
    const handleClick = vi.fn();
    render(
      <Button variant="primary" onClick={handleClick}>
        Confirm Dispatch
      </Button>
    );

    const button = screen.getByRole('button', { name: /confirm dispatch/i });
    expect(button).toBeInTheDocument();
    fireEvent.click(button);
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('disables Button and renders spinner during isLoading state', () => {
    const handleClick = vi.fn();
    render(
      <Button variant="primary" isLoading onClick={handleClick}>
        Saving Changes
      </Button>
    );

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(handleClick).not.toHaveBeenCalled();
  });

  it('renders Badge with correct variant styling', () => {
    render(<Badge variant="emerald">ACTIVE</Badge>);
    const badge = screen.getByText('ACTIVE');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveClass('text-emerald-300');
  });

  it('renders Card with Header and Content', () => {
    render(
      <Card variant="default">
        <CardHeader>Header Title</CardHeader>
        <CardContent>Body Paragraph</CardContent>
      </Card>
    );

    expect(screen.getByText('Header Title')).toBeInTheDocument();
    expect(screen.getByText('Body Paragraph')).toBeInTheDocument();
  });

  it('renders Modal when isOpen is true and calls onClose when pressing Escape', () => {
    const handleClose = vi.fn();
    const { rerender } = render(
      <Modal isOpen={false} onClose={handleClose} title="Inspection Dialog">
        Modal Body Content
      </Modal>
    );

    expect(screen.queryByText('Inspection Dialog')).not.toBeInTheDocument();

    rerender(
      <Modal isOpen={true} onClose={handleClose} title="Inspection Dialog">
        Modal Body Content
      </Modal>
    );

    expect(screen.getByText('Inspection Dialog')).toBeInTheDocument();
    expect(screen.getByText('Modal Body Content')).toBeInTheDocument();

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(handleClose).toHaveBeenCalled();
  });

  it('renders EmptyState with pending backend badge when flag is set', () => {
    render(
      <EmptyState
        title="No Live Radar Units"
        description="Vehicles will appear once collector app emits telemetry."
        isBackendPending={true}
      />
    );

    expect(screen.getByText('No Live Radar Units')).toBeInTheDocument();
    expect(screen.getByText('Pending Backend')).toBeInTheDocument();
  });

  it('renders AlertBanner and invokes onClose callback when clicked', () => {
    const handleClose = vi.fn();
    render(
      <AlertBanner
        type="warning"
        title="Security Rule Notice"
        message="Action requires admin token."
        onClose={handleClose}
      />
    );

    expect(screen.getByText('Security Rule Notice')).toBeInTheDocument();
    expect(screen.getByText('Action requires admin token.')).toBeInTheDocument();

    const closeBtn = screen.getByLabelText(/dismiss alert/i);
    fireEvent.click(closeBtn);
    expect(handleClose).toHaveBeenCalledTimes(1);
  });
});
