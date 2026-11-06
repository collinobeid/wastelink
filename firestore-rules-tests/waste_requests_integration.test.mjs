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
  deleteDoc,
  serverTimestamp,
  runTransaction
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
    category: 'cat_general',
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

/**
 * Executes the Firestore transaction logic matching FirestoreWasteRequestRemoteDataSource.createRequest.
 */
async function executeWasteRequestTransaction(db, authenticatedUid, req) {
  const docRef = doc(db, 'waste_requests', req.id);
  const catRef = doc(db, 'waste_categories', req.categoryId);
  const zoneRef = doc(db, 'service_zones', req.serviceZoneId);

  try {
    return await runTransaction(db, async (tx) => {
      const snap = await tx.get(docRef);
      if (snap.exists()) {
        const data = snap.data();
        if (data.clientUid !== authenticatedUid) {
          return { status: 'REJECTED', errorCode: 'PERMISSION_DENIED' };
        }
        const matchesCategory = data.category === req.categoryId;
        const matchesVolumeTier = data.volumeTier === req.volumeTier;
        const matchesAddress = data.pickupAddress === req.pickupAddress;
        const matchesCoords = Math.abs(data.lat - req.coordinates.lat) < 0.00001 &&
                              Math.abs(data.lng - req.coordinates.lng) < 0.00001;
        const matchesNotes = (data.notes || '') === (req.notes || '');
        const matchesGatePhoto = (data.gatePhotoUrl || '') === (req.gatePhotoUrl || '');
        const matchesPhone = (data.residentPhoneNumber || '') === (req.residentPhoneNumber || '');

        if (!matchesCategory || !matchesVolumeTier || !matchesAddress || !matchesCoords ||
            !matchesNotes || !matchesGatePhoto || !matchesPhone) {
          return { status: 'REJECTED', errorCode: 'CONFLICTING_EXISTING_DOCUMENT' };
        }

        return {
          status: 'ALREADY_EXISTS',
          requestRef: data.requestRef,
          createdAt: data.createdAt,
          updatedAt: data.updatedAt
        };
      }

      const catSnap = await tx.get(catRef);
      if (!catSnap.exists()) {
        return { status: 'REJECTED', errorCode: 'UNKNOWN_CATEGORY' };
      }
      if (catSnap.data().isActive !== true) {
        return { status: 'REJECTED', errorCode: 'INACTIVE_CATEGORY' };
      }

      const zoneSnap = await tx.get(zoneRef);
      if (!zoneSnap.exists()) {
        return { status: 'REJECTED', errorCode: 'UNKNOWN_SERVICE_ZONE' };
      }
      if (zoneSnap.data().isActive !== true) {
        return { status: 'REJECTED', errorCode: 'INACTIVE_SERVICE_ZONE' };
      }

      const zoneData = zoneSnap.data();
      const { minLat, maxLat, minLng, maxLng } = zoneData;
      if (minLat == null || maxLat == null || minLng == null || maxLng == null ||
          minLat < -90 || maxLat > 90 || minLat > maxLat ||
          minLng < -180 || maxLng > 180 || minLng > maxLng) {
        return { status: 'REJECTED', errorCode: 'MALFORMED_SERVICE_ZONE_GEOMETRY' };
      }

      const reqLat = req.coordinates.lat;
      const reqLng = req.coordinates.lng;
      if (reqLat < minLat || reqLat > maxLat || reqLng < minLng || reqLng > maxLng) {
        return { status: 'REJECTED', errorCode: 'OUTSIDE_SERVICE_ZONE' };
      }

      const writeDoc = {
        id: req.id,
        requestRef: req.requestRef,
        clientUid: authenticatedUid,
        geohash: 'kzf0t8',
        lat: reqLat,
        lng: reqLng,
        pickupAddress: req.pickupAddress,
        category: req.categoryId,
        volumeTier: req.volumeTier,
        notes: req.notes || '',
        gatePhotoBase64: null,
        pocPhotoBase64: null,
        gatePhotoUrl: req.gatePhotoUrl || null,
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
        residentPhoneNumber: req.residentPhoneNumber || null,
        collectorPhoneNumber: null,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      };

      tx.set(docRef, writeDoc);
      return { status: 'SUCCESS', requestRef: req.requestRef };
    });
  } catch (err) {
    if (err.code === 'permission-denied' || (err.message && err.message.includes('PERMISSION_DENIED'))) {
      return { status: 'REJECTED', errorCode: 'PERMISSION_DENIED' };
    }
    throw err;
  }
}

describe('WasteLink Module 2 Phase 1C — Live Firestore Emulator Integration Tests (12 Scenarios)', () => {
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

    // Seed active users, categories, and service zones
    await seedWithRulesDisabled(async (db) => {
      // Users
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

      // Categories
      await setDoc(doc(db, 'waste_categories/cat_general'), {
        id: 'cat_general',
        name: 'General Waste',
        isActive: true
      });
      await setDoc(doc(db, 'waste_categories/cat_inactive'), {
        id: 'cat_inactive',
        name: 'Decommissioned Category',
        isActive: false
      });

      // Service Zones
      await setDoc(doc(db, 'service_zones/zone_kinondoni'), {
        id: 'zone_kinondoni',
        name: 'Kinondoni Municipal',
        minLat: -6.8200,
        maxLat: -6.7000,
        minLng: 39.1800,
        maxLng: 39.2600,
        isActive: true
      });
      await setDoc(doc(db, 'service_zones/zone_inactive'), {
        id: 'zone_inactive',
        name: 'Decommissioned Zone',
        minLat: -6.9500,
        maxLat: -6.8900,
        minLng: 39.2000,
        maxLng: 39.3000,
        isActive: false
      });
      await setDoc(doc(db, 'service_zones/zone_malformed'), {
        id: 'zone_malformed',
        name: 'Malformed Geometry Zone',
        minLat: null,
        maxLat: -6.7000,
        minLng: 39.1800,
        maxLng: 39.2600,
        isActive: true
      });
    });
  });

  const sampleRequest = {
    id: 'req_1001',
    requestRef: 'WL-2611-7X9F2B',
    categoryId: 'cat_general',
    serviceZoneId: 'zone_kinondoni',
    volumeTier: 'STANDARD_BIN',
    pickupAddress: 'Masaki, Dar es Salaam',
    coordinates: { lat: -6.7924, lng: 39.2083 },
    notes: 'Near the white gate',
    gatePhotoUrl: 'https://storage.googleapis.com/wastelink/gate_1.jpg',
    residentPhoneNumber: '+255712345678'
  };

  // Scenario 1: Atomic creation
  it('1. Atomic creation: active resident creates waste request via transaction', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const result = await executeWasteRequestTransaction(ownerDb, 'res_owner', sampleRequest);

    assert.equal(result.status, 'SUCCESS');
    assert.equal(result.requestRef, 'WL-2611-7X9F2B');

    const createdSnap = await getDoc(doc(ownerDb, 'waste_requests/req_1001'));
    assert.equal(createdSnap.exists(), true);
    assert.equal(createdSnap.data().status, 'PENDING_DISPATCH');
    assert.equal(createdSnap.data().clientUid, 'res_owner');
  });

  // Scenario 2: Repeated submission of identical request
  it('2. Repeated submission: identical request submission returns ALREADY_EXISTS idempotently', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();

    const first = await executeWasteRequestTransaction(ownerDb, 'res_owner', sampleRequest);
    assert.equal(first.status, 'SUCCESS');

    const second = await executeWasteRequestTransaction(ownerDb, 'res_owner', sampleRequest);
    assert.equal(second.status, 'ALREADY_EXISTS');
    assert.equal(second.requestRef, 'WL-2611-7X9F2B');
  });

  // Scenario 3: Concurrent submissions using same request ID
  it('3. Concurrent submissions: concurrent submissions with same request ID reconcile safely', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();

    const [r1, r2] = await Promise.all([
      executeWasteRequestTransaction(ownerDb, 'res_owner', sampleRequest),
      executeWasteRequestTransaction(ownerDb, 'res_owner', sampleRequest)
    ]);

    const statuses = [r1.status, r2.status].sort();
    assert.deepEqual(statuses, ['ALREADY_EXISTS', 'SUCCESS']);
  });

  // Scenario 4: Existing document owned by another user
  it('4. Existing document owned by another user: rejects with PERMISSION_DENIED', async () => {
    const otherDb = userContext('res_other', 'other@wastelink.tz', true).firestore();
    await executeWasteRequestTransaction(otherDb, 'res_other', sampleRequest);

    // Attacker attempts to create/overwrite with same doc ID
    const attackerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const result = await executeWasteRequestTransaction(attackerDb, 'res_owner', sampleRequest);

    assert.equal(result.status, 'REJECTED');
    assert.equal(result.errorCode, 'PERMISSION_DENIED');
  });

  // Scenario 5: Existing document with conflicting payload
  it('5. Existing document with conflicting payload: rejects with CONFLICTING_EXISTING_DOCUMENT', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    await executeWasteRequestTransaction(ownerDb, 'res_owner', sampleRequest);

    const conflicting = { ...sampleRequest, notes: 'Different conflicting note' };
    const result = await executeWasteRequestTransaction(ownerDb, 'res_owner', conflicting);

    assert.equal(result.status, 'REJECTED');
    assert.equal(result.errorCode, 'CONFLICTING_EXISTING_DOCUMENT');
  });

  // Scenario 6: Unknown category
  it('6. Unknown category: rejects with UNKNOWN_CATEGORY when category does not exist', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const req = { ...sampleRequest, categoryId: 'cat_non_existent' };

    const result = await executeWasteRequestTransaction(ownerDb, 'res_owner', req);
    assert.equal(result.status, 'REJECTED');
    assert.equal(result.errorCode, 'UNKNOWN_CATEGORY');
  });

  // Scenario 7: Inactive category
  it('7. Inactive category: rejects with INACTIVE_CATEGORY when category is decommissioned', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const req = { ...sampleRequest, categoryId: 'cat_inactive' };

    const result = await executeWasteRequestTransaction(ownerDb, 'res_owner', req);
    assert.equal(result.status, 'REJECTED');
    assert.equal(result.errorCode, 'INACTIVE_CATEGORY');
  });

  // Scenario 8: Unknown service zone
  it('8. Unknown service zone: rejects with UNKNOWN_SERVICE_ZONE when zone does not exist', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const req = { ...sampleRequest, serviceZoneId: 'zone_mars_colony' };

    const result = await executeWasteRequestTransaction(ownerDb, 'res_owner', req);
    assert.equal(result.status, 'REJECTED');
    assert.equal(result.errorCode, 'UNKNOWN_SERVICE_ZONE');
  });

  // Scenario 9: Inactive service zone
  it('9. Inactive service zone: rejects with INACTIVE_SERVICE_ZONE when zone is decommissioned', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const req = { ...sampleRequest, serviceZoneId: 'zone_inactive' };

    const result = await executeWasteRequestTransaction(ownerDb, 'res_owner', req);
    assert.equal(result.status, 'REJECTED');
    assert.equal(result.errorCode, 'INACTIVE_SERVICE_ZONE');
  });

  // Scenario 10: Missing or malformed service-zone geometry (fail-closed)
  it('10. Fail-closed geometry: rejects missing bounds or coordinates outside boundary', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();

    // 10a. Missing geometry bounds
    const reqMalformed = { ...sampleRequest, id: 'req_malformed', serviceZoneId: 'zone_malformed' };
    const resMalformed = await executeWasteRequestTransaction(ownerDb, 'res_owner', reqMalformed);
    assert.equal(resMalformed.status, 'REJECTED');
    assert.equal(resMalformed.errorCode, 'MALFORMED_SERVICE_ZONE_GEOMETRY');

    // 10b. Coordinates outside service zone
    const reqOutside = {
      ...sampleRequest,
      id: 'req_outside',
      coordinates: { lat: -6.1630, lng: 35.7516 } // Dodoma coords, outside Kinondoni
    };
    const resOutside = await executeWasteRequestTransaction(ownerDb, 'res_owner', reqOutside);
    assert.equal(resOutside.status, 'REJECTED');
    assert.equal(resOutside.errorCode, 'OUTSIDE_SERVICE_ZONE');
  });

  // Scenario 11: Server timestamp resolution
  it('11. Server timestamp resolution: committed document has valid resolved server timestamps', async () => {
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    await executeWasteRequestTransaction(ownerDb, 'res_owner', sampleRequest);

    const snap = await getDoc(doc(ownerDb, 'waste_requests/req_1001'));
    assert.equal(snap.exists(), true);
    const data = snap.data();

    assert.ok(data.createdAt, 'createdAt must not be null');
    assert.ok(data.updatedAt, 'updatedAt must not be null');
    assert.equal(typeof data.createdAt.toMillis, 'function', 'createdAt must resolve to Firestore Timestamp');
    assert.ok(data.createdAt.toMillis() > 0, 'createdAt must resolve to valid epoch milliseconds > 0');
  });

  // Scenario 12: Firestore Security Rules permission denial
  it('12. Firestore Security Rules permission denial: forgery, suspended, bad status, hard-delete blocked', async () => {
    const otherDb = userContext('res_other', 'other@wastelink.tz', true).firestore();
    const suspendedDb = userContext('res_suspended', 'suspended@wastelink.tz', true).firestore();
    const ownerDb = userContext('res_owner', 'owner@wastelink.tz', true).firestore();
    const adminDb = userContext('admin_user', 'admin@wastelink.tz', true, { role: 'admin' }).firestore();

    // 12a. Resident cannot create request for another resident UID (forgery)
    await assertFails(
      setDoc(doc(otherDb, 'waste_requests/req_forged'), buildValidWasteRequestDoc('req_forged', 'res_owner'))
    );

    // 12b. Unrelated resident cannot read private waste request
    await seedWithRulesDisabled(async (db) => {
      await setDoc(doc(db, 'waste_requests/req_private'), buildValidWasteRequestDoc('req_private', 'res_owner'));
    });
    await assertFails(getDoc(doc(otherDb, 'waste_requests/req_private')));

    // 12c. Suspended resident cannot create waste requests
    await assertFails(
      setDoc(doc(suspendedDb, 'waste_requests/req_susp'), buildValidWasteRequestDoc('req_susp', 'res_suspended'))
    );

    // 12d. Invalid initial status (e.g. COMPLETED) rejected by security rules
    await assertFails(
      setDoc(doc(ownerDb, 'waste_requests/req_bad_status'), {
        ...buildValidWasteRequestDoc('req_bad_status', 'res_owner'),
        status: 'COMPLETED'
      })
    );

    // 12e. Hard-delete blocked: even Admin cannot delete requests
    await assertFails(deleteDoc(doc(adminDb, 'waste_requests/req_private')));
  });
});
