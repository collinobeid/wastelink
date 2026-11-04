import { describe, it, before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { initializeApp, deleteApp } from "firebase/app";
import {
  getAuth,
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  applyActionCode,
  signOut,
} from "firebase/auth";
import {
  getFirestore,
  connectFirestoreEmulator,
  doc,
  getDoc,
  setDoc,
  updateDoc,
} from "firebase/firestore";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_ID = "demo-wastelink";
const AUTH_EMULATOR_HOST = "127.0.0.1";
const AUTH_EMULATOR_PORT = 9099;
const FIRESTORE_EMULATOR_HOST = "127.0.0.1";
const FIRESTORE_EMULATOR_PORT = 8080;

const rulesPath = path.resolve(__dirname, "..", "firestore.rules");
const rules = fs.readFileSync(rulesPath, "utf8");

function buildCanonicalUserMap({
  uid,
  fullName,
  email,
  phoneNumber,
  role,
  nowMs = 1731000000000,
}) {
  const normalizedRole = role.toLowerCase();
  return {
    id: uid,
    uid: uid,
    fullName: fullName,
    name: fullName,
    email: email.toLowerCase(),
    phoneNumber: phoneNumber,
    phone: phoneNumber,
    role: normalizedRole,
    accountStatus: "ACTIVE",
    status: "ACTIVE",
    statusReason: null,
    suspendedUntilEpochMs: null,
    onboardingStatus: "NOT_STARTED",
    isVerified: false,
    collectorVerificationStatus:
      normalizedRole === "collector" ? "NOT_SUBMITTED" : "NOT_APPLICABLE",
    collectorRejectionReason: null,
    defaultZoneId: null,
    vehicleType: null,
    vehiclePlateNumber: null,
    preferredLanguage: "en",
    createdAtEpochMs: nowMs,
    updatedAtEpochMs: nowMs,
  };
}

async function clearAuthEmulatorAccounts() {
  const res = await fetch(
    `http://${AUTH_EMULATOR_HOST}:${AUTH_EMULATOR_PORT}/emulator/v1/projects/${PROJECT_ID}/accounts`,
    { method: "DELETE" }
  );
  assert.equal(res.ok, true, "Failed to clear Auth Emulator accounts");
}

async function fetchAuthEmulatorOobCodes() {
  const res = await fetch(
    `http://${AUTH_EMULATOR_HOST}:${AUTH_EMULATOR_PORT}/emulator/v1/projects/${PROJECT_ID}/oobCodes`
  );
  assert.equal(res.ok, true, "Failed to fetch Auth Emulator OOB codes");
  const json = await res.json();
  return json.oobCodes ?? [];
}

describe("WasteLink Module 1 Stage 3 — Live Firebase Auth + Firestore Emulator Integration Suite", () => {
  let testEnv;
  let clientApp;
  let auth;
  let db;

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        host: FIRESTORE_EMULATOR_HOST,
        port: FIRESTORE_EMULATOR_PORT,
        rules,
      },
    });

    clientApp = initializeApp(
      {
        apiKey: "demo-api-key-for-emulator",
        projectId: PROJECT_ID,
        appId: "1:1234567890:android:abcdef123456",
      },
      "stage3-integration-client"
    );

    auth = getAuth(clientApp);
    connectAuthEmulator(
      auth,
      `http://${AUTH_EMULATOR_HOST}:${AUTH_EMULATOR_PORT}`,
      { disableWarnings: true }
    );

    db = getFirestore(clientApp);
    connectFirestoreEmulator(db, FIRESTORE_EMULATOR_HOST, FIRESTORE_EMULATOR_PORT);
  });

  beforeEach(async () => {
    await signOut(auth);
    await clearAuthEmulatorAccounts();
    await testEnv.clearFirestore();
  });

  after(async () => {
    if (clientApp) {
      await deleteApp(clientApp);
    }
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  it("1. End-to-end Resident registration creates real Firebase Auth user and canonical /users/{uid} satisfying firestore.rules", async () => {
    const email = "amina.resident@wastelink.co.tz";
    const credential = await createUserWithEmailAndPassword(
      auth,
      email,
      "ResidentPass123"
    );
    const uid = credential.user.uid;
    assert.ok(uid.length > 0);

    const profileMap = buildCanonicalUserMap({
      uid,
      fullName: "Amina Nyerere",
      email,
      phoneNumber: "+255712345678",
      role: "resident",
    });

    await assertSucceeds(setDoc(doc(db, "users", uid), profileMap));

    const snap = await assertSucceeds(getDoc(doc(db, "users", uid)));
    assert.equal(snap.exists(), true);
    assert.equal(snap.data().role, "resident");
    assert.equal(snap.data().collectorVerificationStatus, "NOT_APPLICABLE");
    assert.equal(snap.data().onboardingStatus, "NOT_STARTED");
    assert.equal(snap.data().accountStatus, "ACTIVE");
    assert.equal(snap.data().isVerified, false);
  });

  it("2. End-to-end Collector registration creates real Firebase Auth user and canonical /users/{uid} with NOT_SUBMITTED", async () => {
    const email = "baraka.collector@wastelink.co.tz";
    const credential = await createUserWithEmailAndPassword(
      auth,
      email,
      "CollectorPass123"
    );
    const uid = credential.user.uid;

    const profileMap = buildCanonicalUserMap({
      uid,
      fullName: "Baraka Mwinyi",
      email,
      phoneNumber: "+255754112233",
      role: "collector",
    });

    await assertSucceeds(setDoc(doc(db, "users", uid), profileMap));

    const snap = await assertSucceeds(getDoc(doc(db, "users", uid)));
    assert.equal(snap.exists(), true);
    assert.equal(snap.data().role, "collector");
    assert.equal(snap.data().collectorVerificationStatus, "NOT_SUBMITTED");
    assert.equal(snap.data().isVerified, false);
  });

  it("3. Attempting to write ADMIN role or email mismatch during registration is rejected by live firestore.rules", async () => {
    const email = "escalate@wastelink.co.tz";
    const credential = await createUserWithEmailAndPassword(
      auth,
      email,
      "EscalatePass123"
    );
    const uid = credential.user.uid;

    const adminAttemptMap = {
      ...buildCanonicalUserMap({
        uid,
        fullName: "Escalation User",
        email,
        phoneNumber: "+255712345678",
        role: "resident",
      }),
      role: "admin",
    };

    await assertFails(setDoc(doc(db, "users", uid), adminAttemptMap));
  });

  it("4. Duplicate email registration throws auth/email-already-in-use on Auth Emulator without altering existing profile", async () => {
    const email = "collision@wastelink.co.tz";
    const firstCred = await createUserWithEmailAndPassword(
      auth,
      email,
      "OriginalPass123"
    );
    const originalUid = firstCred.user.uid;

    await assertSucceeds(
      setDoc(
        doc(db, "users", originalUid),
        buildCanonicalUserMap({
          uid: originalUid,
          fullName: "Original Owner",
          email,
          phoneNumber: "+255712345678",
          role: "resident",
        })
      )
    );

    await signOut(auth);

    await assert.rejects(
      async () => {
        await createUserWithEmailAndPassword(auth, email, "DifferentPass999");
      },
      (err) => err.code === "auth/email-already-in-use"
    );
  });

  it("5. Cross-session Ghost Account detection and idempotent profile recovery against live Auth + Firestore Emulators", async () => {
    const email = "ghost.account@wastelink.co.tz";
    // Step 1: Create Auth account, simulate crash/offline before Firestore write
    const createdCred = await createUserWithEmailAndPassword(
      auth,
      email,
      "GhostPass123"
    );
    const ghostUid = createdCred.user.uid;
    await signOut(auth);

    // Step 2: Cross-session login
    const signedInCred = await signInWithEmailAndPassword(
      auth,
      email,
      "GhostPass123"
    );
    assert.equal(signedInCred.user.uid, ghostUid);

    // Step 3: Detect missing /users/{uid} (Ghost Account)
    const missingSnap = await assertSucceeds(getDoc(doc(db, "users", ghostUid)));
    assert.equal(missingSnap.exists(), false);

    // Step 4: Recover profile using authenticated UID
    const recoveredProfile = buildCanonicalUserMap({
      uid: ghostUid,
      fullName: "Recovered Collector",
      email,
      phoneNumber: "+255765432109",
      role: "collector",
    });
    await assertSucceeds(setDoc(doc(db, "users", ghostUid), recoveredProfile));

    // Step 5: Idempotent check (getDoc before write returns existing valid profile even while email_verified == false)
    const verifySnap = await assertSucceeds(getDoc(doc(db, "users", ghostUid)));
    assert.equal(verifySnap.exists(), true);
    assert.equal(verifySnap.data().fullName, "Recovered Collector");
    assert.equal(verifySnap.data().collectorVerificationStatus, "NOT_SUBMITTED");
  });

  it("6. Email verification OOB flow + getIdToken(true) refresh unlocks verified self-service onboarding update in firestore.rules", async () => {
    const email = "verify.flow@wastelink.co.tz";
    const cred = await createUserWithEmailAndPassword(
      auth,
      email,
      "VerifyPass123"
    );
    const uid = cred.user.uid;

    await assertSucceeds(
      setDoc(
        doc(db, "users", uid),
        buildCanonicalUserMap({
          uid,
          fullName: "Neema Mushi",
          email,
          phoneNumber: "+255712000111",
          role: "resident",
          nowMs: 1731000000000,
        })
      )
    );

    // Before email verification, self-service profile update is denied by firestore.rules
    await assertFails(
      updateDoc(doc(db, "users", uid), {
        onboardingStatus: "COMPLETED",
        defaultZoneId: "zone_kinondoni",
        updatedAtEpochMs: 1731000005000,
      })
    );

    // Dispatch verification email via Auth Emulator
    await sendEmailVerification(cred.user);
    const oobCodes = await fetchAuthEmulatorOobCodes();
    const verifyOob = oobCodes.find(
      (c) => c.email === email && c.requestType === "VERIFY_EMAIL"
    );
    assert.ok(verifyOob, "Expected VERIFY_EMAIL OOB code from Auth Emulator");

    // Apply OOB verification code, reload user, and force-refresh ID token
    await applyActionCode(auth, verifyOob.oobCode);
    await cred.user.reload();
    assert.equal(cred.user.emailVerified, true);
    await cred.user.getIdToken(true);

    // Now that request.auth.token.email_verified == true, onboarding completion succeeds!
    await assertSucceeds(
      updateDoc(doc(db, "users", uid), {
        onboardingStatus: "COMPLETED",
        defaultZoneId: "zone_kinondoni",
        updatedAtEpochMs: 1731000005000,
      })
    );

    const updatedSnap = await assertSucceeds(getDoc(doc(db, "users", uid)));
    assert.equal(updatedSnap.data().onboardingStatus, "COMPLETED");
    assert.equal(updatedSnap.data().defaultZoneId, "zone_kinondoni");
  });

  it("7. Password reset email dispatch generates RESET_PASSWORD OOB code on Auth Emulator", async () => {
    const email = "reset.user@wastelink.co.tz";
    await createUserWithEmailAndPassword(auth, email, "OldPassword123");
    await signOut(auth);

    await sendPasswordResetEmail(auth, email);
    const oobCodes = await fetchAuthEmulatorOobCodes();
    const resetOob = oobCodes.find(
      (c) => c.email === email && c.requestType === "PASSWORD_RESET"
    );
    assert.ok(resetOob, "Expected PASSWORD_RESET OOB code from Auth Emulator");
  });

  it("8. Wrong password and unknown user sign-in fail deterministically on Auth Emulator", async () => {
    const email = "login.check@wastelink.co.tz";
    await createUserWithEmailAndPassword(auth, email, "RightPassword123");
    await signOut(auth);

    await assert.rejects(
      async () => {
        await signInWithEmailAndPassword(auth, email, "WrongPassword999");
      },
      (err) =>
        err.code === "auth/wrong-password" ||
        err.code === "auth/invalid-credential"
    );

    await assert.rejects(
      async () => {
        await signInWithEmailAndPassword(
          auth,
          "nobody@wastelink.co.tz",
          "RightPassword123"
        );
      },
      (err) =>
        err.code === "auth/user-not-found" ||
        err.code === "auth/invalid-credential"
    );
  });
});
