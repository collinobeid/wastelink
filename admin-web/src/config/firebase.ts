import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth, connectAuthEmulator } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator } from 'firebase/firestore';

const firebaseEnv = import.meta.env.VITE_FIREBASE_ENV || 'EMULATOR';
const projectId = import.meta.env.VITE_FIREBASE_PROJECT_ID || 'wastelink-27595';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || 'fake-api-key-for-emulator',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || `${projectId}.firebaseapp.com`,
  projectId: projectId,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || `${projectId}.appspot.com`,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '1234567890',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '1:1234567890:web:abcdef',
};

// Initialize Firebase singleton
export const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

// Connect to local emulators when running in EMULATOR environment
let emulatorsConnected = false;

export function initEmulators() {
  if (firebaseEnv === 'EMULATOR' && !emulatorsConnected) {
    try {
      const authUrl = import.meta.env.VITE_FIREBASE_AUTH_EMULATOR_URL || 'http://127.0.0.1:9099';
      const firestoreHost = import.meta.env.VITE_FIREBASE_FIRESTORE_EMULATOR_HOST || '127.0.0.1';
      const firestorePort = Number(import.meta.env.VITE_FIREBASE_FIRESTORE_EMULATOR_PORT) || 8080;

      connectAuthEmulator(auth, authUrl, { disableWarnings: true });
      connectFirestoreEmulator(db, firestoreHost, firestorePort);
      emulatorsConnected = true;
      console.log(`[WasteLink] Connected to Firebase Emulators (Auth: ${authUrl}, Firestore: ${firestoreHost}:${firestorePort})`);
    } catch (_err) {
      // Ignored if already connected in test runners
    }
  }
}

initEmulators();

export { firebaseEnv, projectId };
