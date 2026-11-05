import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import {
  User as FirebaseUser,
  onIdTokenChanged,
  signInWithEmailAndPassword,
  signOut as fbSignOut,
} from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from '../config/firebase';
import { UserProfile } from '../domain/types';

interface AuthContextType {
  currentUser: FirebaseUser | null;
  userProfile: UserProfile | null;
  isAdmin: boolean;
  isLoading: boolean;
  error: string | null;
  signIn: (email: string, pass: string) => Promise<{ success: boolean; error?: string }>;
  signOut: () => Promise<void>;
  refreshClaims: () => Promise<boolean>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentUser, setCurrentUser] = useState<FirebaseUser | null>(null);
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);
  const [isAdmin, setIsAdmin] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const evalSeqRef = useRef<number>(0);
  const activeSessionUidRef = useRef<string | null>(null);

  const evaluateAdminToken = async (user: FirebaseUser | null): Promise<boolean> => {
    const currentSeq = ++evalSeqRef.current;
    activeSessionUidRef.current = user ? user.uid : null;

    const isSessionAuthoritative = (): boolean => {
      // 1. Sequence check: Must still match the latest evaluation sequence
      if (currentSeq !== evalSeqRef.current) return false;
      // 2. Active session identity check: Must match the current active session UID
      if (activeSessionUidRef.current !== (user ? user.uid : null)) return false;
      // 3. Auth client check: If Firebase Auth reports a currentUser, it must match this user's UID
      if (auth.currentUser && user && auth.currentUser.uid !== user.uid) return false;
      return true;
    };

    if (!user) {
      if (isSessionAuthoritative()) {
        setCurrentUser(null);
        setIsAdmin(false);
        setUserProfile(null);
      }
      return false;
    }

    // Fail-closed immediately for this session while evaluating
    if (isSessionAuthoritative()) {
      setIsAdmin(false);
      setUserProfile(null);
    }

    const safeSignOutIfAuthoritative = async (errorMessage: string): Promise<void> => {
      // Reliable session identity and sequence check immediately before performing state mutation & sign-out
      if (!isSessionAuthoritative()) {
        return;
      }

      setError(errorMessage);
      setCurrentUser(null);
      setIsAdmin(false);
      setUserProfile(null);

      // Verify again immediately before the asynchronous sign-out operation
      if (isSessionAuthoritative()) {
        try {
          await fbSignOut(auth);
        } catch {
          // ignore signOut error during offline/network failure
        }
      }
    };

    try {
      const tokenResult = await user.getIdTokenResult(true);
      if (!isSessionAuthoritative()) return false;

      // Strict custom-claim validation: Only literal boolean true qualifies
      const hasAdminClaim = tokenResult.claims.admin === true;
      if (!hasAdminClaim) {
        await safeSignOutIfAuthoritative(
          'Access denied: Account does not possess authoritative administrator claims.'
        );
        return false;
      }

      // Fetch user profile from Firestore /users/{uid}
      const userDocRef = doc(db, 'users', user.uid);
      const snapshot = await getDoc(userDocRef);
      if (!isSessionAuthoritative()) return false;

      if (!snapshot.exists()) {
        await safeSignOutIfAuthoritative(
          'Access denied: Administrative profile document /users/{uid} does not exist.'
        );
        return false;
      }

      const data = snapshot.data() as UserProfile;
      // Authoritative requirement: must have role === 'admin' and strictly accountStatus === 'ACTIVE'
      // Legacy status must NOT be used as a fallback for ACTIVE.
      if (data.role !== 'admin' || data.accountStatus !== 'ACTIVE') {
        const reason = data.role !== 'admin'
          ? `Account has non-administrative role '${data.role}'`
          : `Account standing is ${data.accountStatus || 'UNDEFINED'}`;
        await safeSignOutIfAuthoritative(
          `Access denied: ${reason}. Only ACTIVE administrators may access the console.`
        );
        return false;
      }

      // Profile is valid and ACTIVE; if session is still authoritative, grant session
      if (isSessionAuthoritative()) {
        setCurrentUser(user);
        setUserProfile({ ...data, id: user.uid, uid: user.uid });
        setIsAdmin(true);
        setError(null);
        return true;
      }
      return false;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to verify admin credentials';
      await safeSignOutIfAuthoritative(`Access denied: ${msg}`);
      return false;
    }
  };

  useEffect(() => {
    const unsubscribe = onIdTokenChanged(auth, async (user) => {
      setIsLoading(true);
      setError(null);
      await evaluateAdminToken(user);
      setIsLoading(false);
    });

    return () => unsubscribe();
  }, []);

  const signIn = async (email: string, pass: string): Promise<{ success: boolean; error?: string }> => {
    setIsLoading(true);
    setError(null);
    try {
      const cred = await signInWithEmailAndPassword(auth, email.trim(), pass);
      const isVerifiedAdmin = await evaluateAdminToken(cred.user);

      if (!isVerifiedAdmin) {
        const errMsg = error || 'Access denied: Account does not possess authoritative administrator claims.';
        setIsLoading(false);
        return { success: false, error: errMsg };
      }

      setIsLoading(false);
      return { success: true };
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Authentication failed';
      setError(errMsg);
      setIsLoading(false);
      return { success: false, error: errMsg };
    }
  };

  const signOut = async () => {
    setIsLoading(true);
    try {
      await fbSignOut(auth);
    } finally {
      setCurrentUser(null);
      setUserProfile(null);
      setIsAdmin(false);
      setError(null);
      setIsLoading(false);
    }
  };

  const refreshClaims = async (): Promise<boolean> => {
    if (!auth.currentUser) return false;
    return evaluateAdminToken(auth.currentUser);
  };

  return (
    <AuthContext.Provider
      value={{
        currentUser,
        userProfile,
        isAdmin,
        isLoading,
        error,
        signIn,
        signOut,
        refreshClaims,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
