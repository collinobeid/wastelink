import { render, screen, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { AuthProvider, useAuth } from '../src/context/AuthContext';
import * as FirebaseAuth from 'firebase/auth';
import * as Firestore from 'firebase/firestore';

// Mock Firebase Auth and Firestore modules
vi.mock('firebase/auth', async () => {
  const actual = await vi.importActual<typeof FirebaseAuth>('firebase/auth');
  return {
    ...actual,
    onIdTokenChanged: vi.fn(),
    signInWithEmailAndPassword: vi.fn(),
    signOut: vi.fn().mockResolvedValue(undefined),
  };
});

vi.mock('firebase/firestore', async () => {
  const actual = await vi.importActual<typeof Firestore>('firebase/firestore');
  return {
    ...actual,
    doc: vi.fn((_db, coll, id) => ({ path: `${coll}/${id}` })),
    getDoc: vi.fn(),
  };
});

// Test consumer component
const AuthConsumer: React.FC = () => {
  const { currentUser, userProfile, isAdmin, isLoading, error } = useAuth();
  return (
    <div>
      <div data-testid="loading">{isLoading ? 'LOADING' : 'READY'}</div>
      <div data-testid="is-admin">{isAdmin ? 'ADMIN' : 'NOT_ADMIN'}</div>
      <div data-testid="current-user">{currentUser ? currentUser.uid : 'NO_USER'}</div>
      <div data-testid="profile">{userProfile ? userProfile.email : 'NO_PROFILE'}</div>
      <div data-testid="error">{error || 'NO_ERROR'}</div>
    </div>
  );
};

describe('Admin Authentication Remediation Suite', () => {
  let authCallback: (user: any) => Promise<void>;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(Firestore.getDoc).mockReset();
    vi.mocked(FirebaseAuth.onIdTokenChanged).mockImplementation((_auth, cb: any) => {
      authCallback = cb;
      return () => {};
    });
  });

  // Test 1: Admin profile missing: access denied
  it('1. Admin profile missing: fails closed and denies access', async () => {
    const mockUser = {
      uid: 'admin_missing_doc',
      email: 'missing@wastelink.go.tz',
      getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
    };

    vi.mocked(Firestore.getDoc).mockResolvedValueOnce({
      exists: () => false,
      data: () => undefined,
    } as any);

    render(
      <AuthProvider>
        <AuthConsumer />
      </AuthProvider>
    );

    await act(async () => {
      await authCallback(mockUser);
    });

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('READY');
    });

    expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
    expect(screen.getByTestId('profile')).toHaveTextContent('NO_PROFILE');
    expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
    expect(screen.getByTestId('error')).toHaveTextContent(/Administrative profile document \/users\/\{uid\} does not exist/);
    expect(FirebaseAuth.signOut).toHaveBeenCalled();
  });

  // Test 2: Admin profile ACTIVE: access permitted only with valid admin claim
  it('2. Admin profile ACTIVE: access permitted with valid admin claim and active profile', async () => {
    const mockUser = {
      uid: 'admin_active',
      email: 'admin@wastelink.go.tz',
      getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
    };

    vi.mocked(Firestore.getDoc).mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        role: 'admin',
        accountStatus: 'ACTIVE',
        email: 'admin@wastelink.go.tz',
        fullName: 'Municipal Director',
        phoneNumber: '+255712345678',
        onboardingStatus: 'COMPLETED',
        isVerified: true,
        collectorVerificationStatus: 'NOT_APPLICABLE',
        preferredLanguage: 'en',
        createdAtEpochMs: 1730000000000,
        updatedAtEpochMs: 1730000000000,
      }),
    } as any);

    render(
      <AuthProvider>
        <AuthConsumer />
      </AuthProvider>
    );

    await act(async () => {
      await authCallback(mockUser);
    });

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('READY');
    });

    expect(screen.getByTestId('is-admin')).toHaveTextContent('ADMIN');
    expect(screen.getByTestId('profile')).toHaveTextContent('admin@wastelink.go.tz');
    expect(screen.getByTestId('current-user')).toHaveTextContent('admin_active');
    expect(screen.getByTestId('error')).toHaveTextContent('NO_ERROR');
    expect(FirebaseAuth.signOut).not.toHaveBeenCalled();
  });

  // Test 3: Admin profile BANNED with legacy status ACTIVE: access denied
  it('3. Admin profile BANNED with legacy status ACTIVE: fails closed and denies access', async () => {
    const mockUser = {
      uid: 'admin_banned',
      email: 'banned@wastelink.go.tz',
      getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
    };

    // Note: status is ACTIVE (legacy field), but authoritative accountStatus is BANNED
    vi.mocked(Firestore.getDoc).mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        role: 'admin',
        accountStatus: 'BANNED',
        status: 'ACTIVE', // legacy decoy field
        email: 'banned@wastelink.go.tz',
        fullName: 'Banned Admin',
      }),
    } as any);

    render(
      <AuthProvider>
        <AuthConsumer />
      </AuthProvider>
    );

    await act(async () => {
      await authCallback(mockUser);
    });

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('READY');
    });

    expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
    expect(screen.getByTestId('profile')).toHaveTextContent('NO_PROFILE');
    expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
    expect(screen.getByTestId('error')).toHaveTextContent(/Account standing is BANNED/);
    expect(FirebaseAuth.signOut).toHaveBeenCalled();
  });

  // Test 4: Admin profile SUSPENDED: access denied
  it('4. Admin profile SUSPENDED: fails closed and denies access', async () => {
    const mockUser = {
      uid: 'admin_suspended',
      email: 'suspended@wastelink.go.tz',
      getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
    };

    vi.mocked(Firestore.getDoc).mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        role: 'admin',
        accountStatus: 'SUSPENDED',
        email: 'suspended@wastelink.go.tz',
        fullName: 'Suspended Admin',
      }),
    } as any);

    render(
      <AuthProvider>
        <AuthConsumer />
      </AuthProvider>
    );

    await act(async () => {
      await authCallback(mockUser);
    });

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('READY');
    });

    expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
    expect(screen.getByTestId('profile')).toHaveTextContent('NO_PROFILE');
    expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
    expect(screen.getByTestId('error')).toHaveTextContent(/Account standing is SUSPENDED/);
    expect(FirebaseAuth.signOut).toHaveBeenCalled();
  });

  // Test 5: Profile retrieval permission failure: access denied
  it('5. Profile retrieval permission failure: fails closed and denies access', async () => {
    const mockUser = {
      uid: 'admin_perm_fail',
      email: 'fail@wastelink.go.tz',
      getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
    };

    vi.mocked(Firestore.getDoc).mockRejectedValueOnce(
      new Error('Missing or insufficient permissions.')
    );

    render(
      <AuthProvider>
        <AuthConsumer />
      </AuthProvider>
    );

    await act(async () => {
      await authCallback(mockUser);
    });

    await waitFor(() => {
      expect(screen.getByTestId('loading')).toHaveTextContent('READY');
    });

    expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
    expect(screen.getByTestId('profile')).toHaveTextContent('NO_PROFILE');
    expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
    expect(screen.getByTestId('error')).toHaveTextContent(/Missing or insufficient permissions/);
    expect(FirebaseAuth.signOut).toHaveBeenCalled();
  });

  // Test 6: Stale asynchronous auth result cannot overwrite a newer session
  it('6. Stale asynchronous auth result cannot overwrite a newer session', async () => {
    let resolveUserAProfile: (val: any) => void;
    const slowUserAPromise = new Promise((resolve) => {
      resolveUserAProfile = resolve;
    });

    const userA = {
      uid: 'user_a_slow_admin',
      email: 'usera@wastelink.go.tz',
      getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
    };

    const userB = {
      uid: 'user_b_unauthorized',
      email: 'userb@wastelink.go.tz',
      getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: false } }),
    };

    vi.mocked(Firestore.getDoc).mockImplementationOnce(() => slowUserAPromise as any);

    render(
      <AuthProvider>
        <AuthConsumer />
      </AuthProvider>
    );

    // Step A: Trigger User A evaluation (it will wait on slowUserAPromise)
    act(() => {
      authCallback(userA);
    });

    // Step B: Trigger User B evaluation immediately afterwards (superceding User A)
    await act(async () => {
      await authCallback(userB);
    });

    // User B evaluation finishes: User B has no admin claim -> denied
    expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
    expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');

    // Step C: Now User A's slow profile fetch finally resolves with ACTIVE admin data
    await act(async () => {
      resolveUserAProfile!({
        exists: () => true,
        data: () => ({
          role: 'admin',
          accountStatus: 'ACTIVE',
          email: 'usera@wastelink.go.tz',
        }),
      });
    });

    // State MUST NOT be overwritten with User A! User B's superceding state is preserved.
    expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
    expect(screen.getByTestId('profile')).toHaveTextContent('NO_PROFILE');
    expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
  });

  // Strict Custom-Claim Validation Suite
  describe('Strict Boolean Custom-Claim Validation', () => {
    const activeAdminDoc = {
      exists: () => true,
      data: () => ({
        role: 'admin',
        accountStatus: 'ACTIVE',
        email: 'claim_test@wastelink.go.tz',
        fullName: 'Claim Test Admin',
      }),
    };

    it('accepts claim when admin === true', async () => {
      const mockUser = {
        uid: 'user_claim_true',
        email: 'true@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
      };
      vi.mocked(Firestore.getDoc).mockResolvedValueOnce(activeAdminDoc as any);

      render(
        <AuthProvider>
          <AuthConsumer />
        </AuthProvider>
      );

      await act(async () => {
        await authCallback(mockUser);
      });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('READY');
      });

      expect(screen.getByTestId('is-admin')).toHaveTextContent('ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('user_claim_true');
      expect(screen.getByTestId('error')).toHaveTextContent('NO_ERROR');
      expect(FirebaseAuth.signOut).not.toHaveBeenCalled();
    });

    it('rejects claim when admin === false', async () => {
      const mockUser = {
        uid: 'user_claim_false',
        email: 'false@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: false } }),
      };

      render(
        <AuthProvider>
          <AuthConsumer />
        </AuthProvider>
      );

      await act(async () => {
        await authCallback(mockUser);
      });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('READY');
      });

      expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
      expect(screen.getByTestId('error')).toHaveTextContent(/Access denied: Account does not possess authoritative administrator claims/);
      expect(FirebaseAuth.signOut).toHaveBeenCalled();
    });

    it('rejects claim when admin === "true" (string)', async () => {
      const mockUser = {
        uid: 'user_claim_str_true',
        email: 'strtrue@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: 'true' } }),
      };

      render(
        <AuthProvider>
          <AuthConsumer />
        </AuthProvider>
      );

      await act(async () => {
        await authCallback(mockUser);
      });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('READY');
      });

      expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
      expect(screen.getByTestId('error')).toHaveTextContent(/Access denied: Account does not possess authoritative administrator claims/);
      expect(FirebaseAuth.signOut).toHaveBeenCalled();
    });

    it('rejects claim when admin === "false" (string)', async () => {
      const mockUser = {
        uid: 'user_claim_str_false',
        email: 'strfalse@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: 'false' } }),
      };

      render(
        <AuthProvider>
          <AuthConsumer />
        </AuthProvider>
      );

      await act(async () => {
        await authCallback(mockUser);
      });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('READY');
      });

      expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
      expect(screen.getByTestId('error')).toHaveTextContent(/Access denied: Account does not possess authoritative administrator claims/);
      expect(FirebaseAuth.signOut).toHaveBeenCalled();
    });

    it('rejects claim when admin claim is missing/undefined', async () => {
      const mockUser = {
        uid: 'user_claim_missing',
        email: 'missing_claim@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockResolvedValue({ claims: {} }),
      };

      render(
        <AuthProvider>
          <AuthConsumer />
        </AuthProvider>
      );

      await act(async () => {
        await authCallback(mockUser);
      });

      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('READY');
      });

      expect(screen.getByTestId('is-admin')).toHaveTextContent('NOT_ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('NO_USER');
      expect(screen.getByTestId('error')).toHaveTextContent(/Access denied: Account does not possess authoritative administrator claims/);
      expect(FirebaseAuth.signOut).toHaveBeenCalled();
    });
  });

  // Stale Sign-Out Protection Regression Suite
  describe('Stale Sign-Out Protection Regression', () => {
    it('prevents an obsolete failed evaluation for User A from signing out newer authoritative User B', async () => {
      let rejectUserAToken: (err: any) => void;
      const slowUserATokenPromise = new Promise((_, reject) => {
        rejectUserAToken = reject;
      });

      const userA = {
        uid: 'user_a_failing',
        email: 'usera@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockImplementation(() => slowUserATokenPromise),
      };

      const userB = {
        uid: 'user_b_active_admin',
        email: 'userb@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
      };

      // User B's profile document in Firestore
      vi.mocked(Firestore.getDoc).mockResolvedValueOnce({
        exists: () => true,
        data: () => ({
          role: 'admin',
          accountStatus: 'ACTIVE',
          email: 'userb@wastelink.go.tz',
          fullName: 'Active Director B',
        }),
      } as any);

      render(
        <AuthProvider>
          <AuthConsumer />
        </AuthProvider>
      );

      // 1. User A starts an asynchronous authentication evaluation
      act(() => {
        authCallback(userA);
      });

      // 2. User B becomes the current authenticated user
      await act(async () => {
        await authCallback(userB);
      });

      // User B must be authoritative admin
      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('READY');
      });
      expect(screen.getByTestId('is-admin')).toHaveTextContent('ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('user_b_active_admin');
      expect(screen.getByTestId('profile')).toHaveTextContent('userb@wastelink.go.tz');

      // Clear any prior signOut calls
      vi.mocked(FirebaseAuth.signOut).mockClear();

      // 3. User A's evaluation fails asynchronously
      await act(async () => {
        rejectUserAToken!(new Error('User A network failure / token revoked'));
      });

      // 4. User A's obsolete callback must not sign out User B
      expect(FirebaseAuth.signOut).not.toHaveBeenCalled();

      // 5. User B's authentication state must remain authoritative
      expect(screen.getByTestId('is-admin')).toHaveTextContent('ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('user_b_active_admin');
      expect(screen.getByTestId('profile')).toHaveTextContent('userb@wastelink.go.tz');
      expect(screen.getByTestId('error')).toHaveTextContent('NO_ERROR');
    });

    it('prevents an obsolete failed profile lookup for User A from signing out newer authoritative User B', async () => {
      let rejectUserAProfile: (err: any) => void;
      const slowUserAProfilePromise = new Promise((_, reject) => {
        rejectUserAProfile = reject;
      });

      const userA = {
        uid: 'user_a_failing_profile',
        email: 'usera_prof@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
      };

      const userB = {
        uid: 'user_b_active_admin_2',
        email: 'userb2@wastelink.go.tz',
        getIdTokenResult: vi.fn().mockResolvedValue({ claims: { admin: true } }),
      };

      // Mock getDoc based on user UID
      vi.mocked(Firestore.getDoc).mockImplementation((docRef: any) => {
        if (docRef.path === 'users/user_a_failing_profile') {
          return slowUserAProfilePromise as any;
        }
        if (docRef.path === 'users/user_b_active_admin_2') {
          return Promise.resolve({
            exists: () => true,
            data: () => ({
              role: 'admin',
              accountStatus: 'ACTIVE',
              email: 'userb2@wastelink.go.tz',
              fullName: 'Active Director B2',
            }),
          }) as any;
        }
        return Promise.resolve({ exists: () => false }) as any;
      });

      render(
        <AuthProvider>
          <AuthConsumer />
        </AuthProvider>
      );

      // 1. User A starts an asynchronous authentication evaluation (hits slow getDoc)
      act(() => {
        authCallback(userA);
      });

      // Wait until User A has invoked getDoc
      await waitFor(() => {
        expect(Firestore.getDoc).toHaveBeenCalledWith({ path: 'users/user_a_failing_profile' });
      });

      // 2. User B becomes the current authenticated user
      await act(async () => {
        await authCallback(userB);
      });

      // User B must be authoritative admin
      await waitFor(() => {
        expect(screen.getByTestId('loading')).toHaveTextContent('READY');
      });
      expect(screen.getByTestId('is-admin')).toHaveTextContent('ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('user_b_active_admin_2');
      expect(screen.getByTestId('profile')).toHaveTextContent('userb2@wastelink.go.tz');

      // Clear any prior signOut calls
      vi.mocked(FirebaseAuth.signOut).mockClear();

      // 3. User A's profile evaluation fails asynchronously
      await act(async () => {
        rejectUserAProfile!(new Error('User A Firestore permission failure'));
      });

      // 4. User A's obsolete callback must not sign out User B
      expect(FirebaseAuth.signOut).not.toHaveBeenCalled();

      // 5. User B's authentication state must remain authoritative
      expect(screen.getByTestId('is-admin')).toHaveTextContent('ADMIN');
      expect(screen.getByTestId('current-user')).toHaveTextContent('user_b_active_admin_2');
      expect(screen.getByTestId('profile')).toHaveTextContent('userb2@wastelink.go.tz');
      expect(screen.getByTestId('error')).toHaveTextContent('NO_ERROR');
    });
  });
});
