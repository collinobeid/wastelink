import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ProtectedRoute } from '../src/routes/ProtectedRoute';
import * as AuthContextModule from '../src/context/AuthContext';

describe('ProtectedRoute Authorization Guard', () => {
  it('redirects unauthenticated user to /login', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      currentUser: null,
      userProfile: null,
      isAdmin: false,
      isLoading: false,
      error: null,
      signIn: vi.fn(),
      signOut: vi.fn(),
      refreshClaims: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>Secret Admin Console</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.queryByText('Secret Admin Console')).not.toBeInTheDocument();
    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });

  it('redirects authenticated non-admin user to /unauthorized', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      currentUser: { uid: 'resident-123', email: 'resident@test.com' } as any,
      userProfile: null,
      isAdmin: false,
      isLoading: false,
      error: null,
      signIn: vi.fn(),
      signOut: vi.fn(),
      refreshClaims: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>Secret Admin Console</div>
              </ProtectedRoute>
            }
          />
          <Route path="/unauthorized" element={<div>Access Denied Page</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.queryByText('Secret Admin Console')).not.toBeInTheDocument();
    expect(screen.getByText('Access Denied Page')).toBeInTheDocument();
  });

  it('renders protected child component when user possesses verified admin claim', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      currentUser: { uid: 'admin-123', email: 'admin@wastelink.go.tz' } as any,
      userProfile: {
        id: 'admin-123',
        uid: 'admin-123',
        fullName: 'Municipal Director',
        email: 'admin@wastelink.go.tz',
        role: 'admin',
        accountStatus: 'ACTIVE',
      } as any,
      isAdmin: true,
      isLoading: false,
      error: null,
      signIn: vi.fn(),
      signOut: vi.fn(),
      refreshClaims: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>Secret Admin Console</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Secret Admin Console')).toBeInTheDocument();
  });
});
