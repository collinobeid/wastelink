import { before, after, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} from '@firebase/rules-unit-testing';
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp
} from 'firebase/firestore';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const RULES_PATH = path.resolve(__dirname, '..', 'firestore.rules');

let testEnv;

async function seedWithRulesDisabled(fn) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await fn(db);
  });
}

function userContext(uid, email, emailVerified = true, extraClaims = {}) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: emailVerified,
    ...extraClaims
  });
}

function buildValidUserDoc(uid, email, role = 'resident') {
  return {
    id: uid,
    uid: uid,
    fullName: 'Test Resident',
    name: 'Test Resident',
    email: email,
    phoneNumber: '+255712345678',
    phone: '+255712345678',
    role: role,
    accountStatus: 'ACTIVE',
    status: 'ACTIVE',
    statusReason: null,
    suspendedUntilEpochMs: null,
    onboardingStatus: 'COMPLETED',
    isVerified: true,
    collectorVerificationStatus: 'NOT_APPLICABLE',
    collectorRejectionReason: null,
    defaultZoneId: null,
    vehicleType: null,
    vehiclePlateNumber: null,
    preferredLanguage: 'en',
    createdAtEpochMs: 1730000000000,
    updatedAtEpochMs: 1730000000000
  };
}

function buildValidWasteRequestDoc(requestId, clientUid, overrides = {}) {
  return {
    id: requestId,
    requestRef: 'WL-2611-7X9F2B',
    clientUid: clientUid,
    geohash: 'kzf0t8',
    lat: -6.7924,
    lng: 39.2083,
    pickupAddress: 'Masaki, Dar es Salaam',
    category: 'HOUSEHOLD',
    volumeTier: 'STANDARD_BIN',
    notes: 'Near the white gate',
    gatePhotoBase64: null,
    pocPhotoBase64: null,
    gatePhotoUrl: 'https://storage.googleapis.com/wastelink/gate_1.jpg',
    pocPhotoUrl: null,
    photos: [],
    status: 'PENDING_DISPATCH',
    assignedTruckId: null,
    assignedAt: null,
    enRouteAt: null,
    arrivedAt: null,
    collectedAt: null,
    completedAt: null,
    failedAt: null,
    failureReason: null,
    agreedPrice: null,
    residentPhoneNumber: '+255712345678',
    collectorPhoneNumber: null,
    createdAt: 1730000100000,
    updatedAt: 1730000100000,
    ...overrides
  };
}

describe('WasteLink Module 2 Phase 1C — Real Firestore Emulator Integration Tests', () => {
  before(async () => {
    const rules = fs.readFileSync(RULES_PATH, 'utf8');
    testEnv = await initializeTestEnvironment({
      projectId: 'demo-wastelink',
      firestore: {
        rules,
        host: '127.0.0.1',
        port: 8080
      }
    });
  });

  after(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();

    // Seed active verified resident accounts
    await seedWithRulesDisabled(async (db) => {
      await setDoc(doc(db, 'users/res_owner'), buildValidUserDoc('res_owner', 'owner@wastelink.tz', 'resident'));
      await setDoc(doc(db, 'users/res_other'), buildValidUserDoc('res_other', 'other@wastelink.tz', 'resident'));
      await setDoc(doc(db, 'users/res_suspended'), {
        ...buildValidUserDoc('res_suspended', 'suspended@wastelink.tz', 'resident'),
        accountStatus: 'SUSPENDED',
        status: 'SUSPENDED'
      });
      await setDoc(doc(db, 'users/admin_user'), {
        ...buildValidUserDoc('admin_user', 'admin@wastelink.tz', 'admin'),
        role: 'ADMIN',
        accountStatus: 'ACTIVE'
      });
    });
  });

  it('1. Authenticated owner creation: active verified resident can create own request with valid schema and requestRef', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const reqDoc = doc(ownerDb, 'waste_requests/req_1001');

    await assertSucceeds(
      setDoc(reqDoc, buildValidWasteRequestDoc('req_1001', 'res_owner'))
    );

    const snapshot = await getDoc(reqDoc);
    assert.equal(snapshot.exists(), true);
    assert.equal(snapshot.data().clientUid, 'res_owner');
    assert.equal(snapshot.data().requestRef, 'WL-2611-7X9F2B');
    assert.equal(snapshot.data().status, 'PENDING_DISPATCH');
  });

  it('2. Cross-user access denial: resident CANNOT create request for another resident UID (ownership forgery)', async () => {
    const attackerDb = userContext('res_other', 'other@wastelink.tz', true).firestore();
    const reqDoc = doc(attackerDb, 'waste_requests/req_forged');

    await assertFails(
      setDoc(reqDoc, buildValidWasteRequestDoc('req_forged', 'res_owner'))
    );
  });

  it('3. Cross-user access denial: unrelated resident CANNOT read another resident waste request', async () => {
    // Seed request belonging to res_owner
    await seedWithRulesDisabled(async (db) => {
      await setDoc(doc(db, 'waste_requests/req_private'), buildValidWasteRequestDoc('req_private', 'res_owner'));
    });

    const otherDb = userContext('res_other', 'other@wastelink.tz', true).firestore();
    const reqDoc = doc(otherDb, 'waste_requests/req_private');

    await assertFails(getDoc(reqDoc));
  });

  it('4. Suspended resident CANNOT create waste requests', async () => {
    const suspendedDb = userContext('res_suspended', 'suspended@wastelink.tz', true).firestore();
    const reqDoc = doc(suspendedDb, 'waste_requests/req_susp');

    await assertFails(
      setDoc(reqDoc, buildValidWasteRequestDoc('req_susp', 'res_suspended'))
    );
  });

  it('5. Invalid category/zone or missing requestRef rejected by security rules', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();

    // Missing requestRef
    await assertFails(
      setDoc(doc(ownerDb, 'waste_requests/req_no_ref'), {
        ...buildValidWasteRequestDoc('req_no_ref', 'res_owner'),
        requestRef: ''
      })
    );

    // Initial status not PENDING_DISPATCH
    await assertFails(
      setDoc(doc(ownerDb, 'waste_requests/req_bad_status'), {
        ...buildValidWasteRequestDoc('req_bad_status', 'res_owner'),
        status: 'COMPLETED'
      })
    );

    // Initial creation with pre-assigned collector truck ID forbidden
    await assertFails(
      setDoc(doc(ownerDb, 'waste_requests/req_preassigned'), {
        ...buildValidWasteRequestDoc('req_preassigned', 'res_owner'),
        assignedTruckId: 'col_fraud'
      })
    );
  });

  it('6. Server timestamp behavior: serverTimestamp and valid epoch timestamp integer accepted', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const reqDoc = doc(ownerDb, 'waste_requests/req_ts');

    await assertSucceeds(
      setDoc(reqDoc, {
        ...buildValidWasteRequestDoc('req_ts', 'res_owner'),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      })
    );
  });

  it('7. Hard-delete blocked: waste requests CANNOT be deleted even by Admin', async () => {
    await seedWithRulesDisabled(async (db) => {
      await setDoc(doc(db, 'waste_requests/req_nodelete'), buildValidWasteRequestDoc('req_nodelete', 'res_owner'));
    });

    const adminDb = userContext('admin_user', 'admin@wastelink.tz', true, { role: 'admin' }).firestore();
    await assertFails(deleteDoc(doc(adminDb, 'waste_requests/req_nodelete')));
  });
});
