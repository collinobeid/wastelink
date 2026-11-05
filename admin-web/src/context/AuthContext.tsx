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

  const evaluateAdminToken = async (user: FirebaseUser | null): Promise<boolean> => {
    const currentSeq = ++evalSeqRef.current;

    // Fail-closed immediately: clear any existing admin authority while evaluating
    setIsAdmin(false);
    setUserProfile(null);

    if (!user) {
      setCurrentUser(null);
      return false;
    }

    try {
      const tokenResult = await user.getIdTokenResult(true);
      if (currentSeq !== evalSeqRef.current) return false;

      const hasAdminClaim = Boolean(tokenResult.claims.admin);
      if (!hasAdminClaim) {
        if (currentSeq === evalSeqRef.current) {
          setError('Access denied: Account does not possess authoritative administrator claims.');
          setCurrentUser(null);
          try {
            await fbSignOut(auth);
          } catch {
            // ignore signOut error during offline/network failure
          }
        }
        return false;
      }

      // Fetch user profile from Firestore /users/{uid}
      const userDocRef = doc(db, 'users', user.uid);
      const snapshot = await getDoc(userDocRef);
      if (currentSeq !== evalSeqRef.current) return false;

      if (!snapshot.exists()) {
        if (currentSeq === evalSeqRef.current) {
          setError('Access denied: Administrative profile document /users/{uid} does not exist.');
          setCurrentUser(null);
          try {
            await fbSignOut(auth);
          } catch {
            // ignore
          }
        }
        return false;
      }

      const data = snapshot.data() as UserProfile;
      // Authoritative requirement: must have role === 'admin' and strictly accountStatus === 'ACTIVE'
      // Legacy status must NOT be used as a fallback for ACTIVE.
      if (data.role !== 'admin' || data.accountStatus !== 'ACTIVE') {
        if (currentSeq === evalSeqRef.current) {
          const reason = data.role !== 'admin'
            ? `Account has non-administrative role '${data.role}'`
            : `Account standing is ${data.accountStatus || 'UNDEFINED'}`;
          setError(`Access denied: ${reason}. Only ACTIVE administrators may access the console.`);
          setCurrentUser(null);
          try {
            await fbSignOut(auth);
          } catch {
            // ignore
          }
        }
        return false;
      }

      // Profile is valid and ACTIVE; if sequence is still active, grant session
      if (currentSeq === evalSeqRef.current) {
        setCurrentUser(user);
        setUserProfile({ ...data, id: user.uid, uid: user.uid });
        setIsAdmin(true);
        setError(null);
        return true;
      }
      return false;
    } catch (err: unknown) {
      if (currentSeq === evalSeqRef.current) {
        const msg = err instanceof Error ? err.message : 'Failed to verify admin credentials';
        setError(`Access denied: ${msg}`);
        setIsAdmin(false);
        setUserProfile(null);
        setCurrentUser(null);
        try {
          await fbSignOut(auth);
        } catch {
          // ignore
        }
      }
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
