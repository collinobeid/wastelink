import React, { createContext, useContext, useEffect, useState } from 'react';
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

  const evaluateAdminToken = async (user: FirebaseUser | null): Promise<boolean> => {
    if (!user) {
      setIsAdmin(false);
      setUserProfile(null);
      return false;
    }

    try {
      const tokenResult = await user.getIdTokenResult(true);
      const hasAdminClaim = Boolean(tokenResult.claims.admin);

      if (!hasAdminClaim) {
        setIsAdmin(false);
        setUserProfile(null);
        return false;
      }

      // Fetch user profile from Firestore /users/{uid}
      const userDocRef = doc(db, 'users', user.uid);
      const snapshot = await getDoc(userDocRef);

      if (snapshot.exists()) {
        const data = snapshot.data() as UserProfile;
        // Verify account standing is ACTIVE
        if (data.accountStatus === 'ACTIVE' || data.status === 'ACTIVE') {
          setUserProfile({ ...data, id: user.uid, uid: user.uid });
          setIsAdmin(true);
          return true;
        } else {
          setIsAdmin(false);
          setUserProfile(null);
          setError(`Administrative account is locked (standing: ${data.accountStatus || data.status})`);
          return false;
        }
      } else {
        // Fallback for provisioned admins whose Firestore profile has not synced
        setUserProfile({
          id: user.uid,
          uid: user.uid,
          fullName: user.displayName || user.email || 'Administrator',
          email: user.email || '',
          phoneNumber: user.phoneNumber || '',
          role: 'admin',
          accountStatus: 'ACTIVE',
          onboardingStatus: 'COMPLETED',
          isVerified: true,
          collectorVerificationStatus: 'NOT_APPLICABLE',
          preferredLanguage: 'en',
          createdAtEpochMs: Date.now(),
          updatedAtEpochMs: Date.now(),
        });
        setIsAdmin(true);
        return true;
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to verify admin credentials';
      setError(msg);
      setIsAdmin(false);
      setUserProfile(null);
      return false;
    }
  };

  useEffect(() => {
    const unsubscribe = onIdTokenChanged(auth, async (user) => {
      setIsLoading(true);
      setError(null);
      setCurrentUser(user);
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
        await fbSignOut(auth);
        const errMsg = 'Access denied: Account does not possess authoritative administrator claims.';
        setError(errMsg);
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
