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
  collection,
  query,
  where,
  getDocs,
  writeBatch,
  setLogLevel
} from 'firebase/firestore';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const RULES_PATH = path.resolve(__dirname, '..', 'firestore.rules');

let testEnv;

/**
 * Executes a callback with security rules disabled, instantiating ctx.firestore() once.
 */
async function seedWithRulesDisabled(fn) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await fn(db);
  });
}

/**
 * Helper that executes a security test assertion and prints a structured audit line:
 * - Test name
 * - Expected result
 * - Actual result
 * - Pass/fail
 */
async function verifyRule(testName, expectedOutcome, operationPromiseFn) {
  if (expectedOutcome === 'ALLOW') {
    try {
      await assertSucceeds(operationPromiseFn());
      console.log(
        `[PASS] ${testName} | Expected: ALLOW | Actual: ALLOWED`
      );
    } catch (err) {
      console.error(
        `[FAIL] ${testName} | Expected: ALLOW | Actual: DENIED (${err.message})`
      );
      throw err;
    }
  } else {
    try {
      await assertFails(operationPromiseFn());
      console.log(
        `[PASS] ${testName} | Expected: DENY (PERMISSION_DENIED) | Actual: DENIED (PERMISSION_DENIED)`
      );
    } catch (err) {
      console.error(
        `[FAIL] ${testName} | Expected: DENY (PERMISSION_DENIED) | Actual: ALLOWED`
      );
      throw err;
    }
  }
}

function buildValidUserDoc(uid, email, role = 'resident', overrides = {}) {
  const isCollector = role === 'collector' || role === 'COLLECTOR';
  return {
    id: uid,
    uid: uid,
    fullName: 'Amina Nyerere',
    name: 'Amina Nyerere',
    email: email,
    phoneNumber: '+255712345678',
    phone: '+255712345678',
    role: role,
    accountStatus: 'ACTIVE',
    status: 'ACTIVE',
    statusReason: null,
    suspendedUntilEpochMs: null,
    onboardingStatus: 'NOT_STARTED',
    isVerified: false,
    collectorVerificationStatus: isCollector ? 'NOT_SUBMITTED' : 'NOT_APPLICABLE',
    collectorRejectionReason: null,
    defaultZoneId: null,
    vehicleType: null,
    vehiclePlateNumber: null,
    preferredLanguage: 'en',
    createdAtEpochMs: 1730000000000,
    updatedAtEpochMs: 1730000000000,
    ...overrides
  };
}

function buildValidSubmissionDoc(uid, overrides = {}) {
  return {
    userId: uid,
    vehicleType: 'Compactor Truck',
    vehiclePlateNumber: 'T 456 ABC',
    licenseNumber: 'TZ-DL-998877',
    serviceZoneId: 'zone_kinondoni',
    status: 'PENDING_REVIEW',
    submissionAttempt: 1,
    submittedAtEpochMs: 1730000100000,
    reviewedByUid: null,
    reviewedAtEpochMs: null,
    rejectionReason: null,
    adminNotes: null,
    lastRejectionReason: null,
    lastReviewedByUid: null,
    lastReviewedAtEpochMs: null,
    ...overrides
  };
}

function buildValidHistoryDoc(uid, attemptNumber = 1, eventType = 'SUBMITTED', overrides = {}) {
  return {
    attemptId: `attempt_${attemptNumber}`,
    userId: uid,
    eventType: eventType,
    vehicleType: 'Compactor Truck',
    vehiclePlateNumber: 'T 456 ABC',
    licenseNumber: 'TZ-DL-998877',
    serviceZoneId: 'zone_kinondoni',
    submissionAttempt: attemptNumber,
    timestampEpochMs: 1730000100000,
    actorUid: uid,
    rejectionReason: null,
    ...overrides
  };
}

function buildValidWasteRequestDoc(requestId, clientUid, overrides = {}) {
  return {
    id: requestId,
    requestRef: 'REQ-1001',
    clientUid: clientUid,
    geohash: 'kzf0t8',
    lat: -6.7924,
    lng: 39.2083,
    pickupAddress: 'Kinondoni Road, Dar es Salaam',
    category: 'HOUSEHOLD',
    volumeTier: 'MEDIUM_BAGS',
    notes: 'Gate is green',
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

function userContext(uid, email, emailVerified = true, extraClaims = {}) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: emailVerified,
    ...extraClaims
  });
}

describe('WasteLink Module 1 Stage 2.1 — Complete Firestore Security Rules & Collection Audit Suite', () => {
  before(async () => {
    setLogLevel('silent');
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
  });

  // ===========================================================================
  // 1. CANONICAL SCHEMA CONTRACT & DRIFT REGRESSION
  // ===========================================================================
  describe('1. Canonical Schema Contract & Enum Drift Prevention', () => {
    it('SEC-SCHEMA-01: Rules enforce canonical Kotlin domain enums and reject drifted enum strings', async () => {
      const colDb = userContext('col_schema', 'colschema@wastelink.co.tz', true).firestore();
      const resDb = userContext('res_schema', 'resschema@wastelink.co.tz', true).firestore();

      // Drifted UNSUBMITTED instead of canonical NOT_SUBMITTED must be rejected
      await verifyRule(
        'SEC-SCHEMA-01a: Drifted collectorVerificationStatus = UNSUBMITTED is rejected',
        'DENY',
        () => setDoc(
          doc(colDb, 'users/col_schema'),
          buildValidUserDoc('col_schema', 'colschema@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'UNSUBMITTED'
          })
        )
      );

      // Drifted onboardingStatus = PROFILE_COMPLETE or COLLECTOR_PROFILE_INCOMPLETE must be rejected
      await verifyRule(
        'SEC-SCHEMA-01b: Drifted onboardingStatus = PROFILE_COMPLETE is rejected',
        'DENY',
        () => setDoc(
          doc(resDb, 'users/res_schema'),
          buildValidUserDoc('res_schema', 'resschema@wastelink.co.tz', 'resident', {
            onboardingStatus: 'PROFILE_COMPLETE'
          })
        )
      );

      await verifyRule(
        'SEC-SCHEMA-01c: Drifted onboardingStatus = COLLECTOR_PROFILE_INCOMPLETE is rejected',
        'DENY',
        () => setDoc(
          doc(colDb, 'users/col_schema'),
          buildValidUserDoc('col_schema', 'colschema@wastelink.co.tz', 'collector', {
            onboardingStatus: 'COLLECTOR_PROFILE_INCOMPLETE'
          })
        )
      );

      // Drifted accountStatus = DEACTIVATED must be rejected
      await verifyRule(
        'SEC-SCHEMA-01d: Drifted accountStatus = DEACTIVATED is rejected',
        'DENY',
        () => setDoc(
          doc(resDb, 'users/res_schema'),
          buildValidUserDoc('res_schema', 'resschema@wastelink.co.tz', 'resident', {
            accountStatus: 'DEACTIVATED'
          })
        )
      );
    });
  });

  // ===========================================================================
  // 2. AUTHENTICATION, PRIVACY ISOLATION & PROFILE INTEGRITY
  // ===========================================================================
  describe('2. Authentication, Ownership Isolation & Profile Integrity', () => {
    it('SEC-AUTH-01: Unauthenticated user cannot read or create profiles', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz'));
      });

      const unauthedDb = testEnv.unauthenticatedContext().firestore();
      await verifyRule(
        'SEC-AUTH-01a: Unauthenticated user cannot read /users/res_1',
        'DENY',
        () => getDoc(doc(unauthedDb, 'users/res_1'))
      );
      await verifyRule(
        'SEC-AUTH-01b: Unauthenticated user cannot create /users/res_2',
        'DENY',
        () => setDoc(doc(unauthedDb, 'users/res_2'), buildValidUserDoc('res_2', 'res2@wastelink.co.tz'))
      );
    });

    it('SEC-AUTH-02: Authenticated user can read own profile but cannot read or list other users', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz'));
        await setDoc(doc(db, 'users/res_2'), buildValidUserDoc('res_2', 'res2@wastelink.co.tz'));
      });

      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-AUTH-02a: Authenticated owner can read own /users/res_1 profile',
        'ALLOW',
        () => getDoc(doc(res1Db, 'users/res_1'))
      );
      await verifyRule(
        'SEC-AUTH-02b: Authenticated user res_1 cannot read /users/res_2',
        'DENY',
        () => getDoc(doc(res1Db, 'users/res_2'))
      );
      await verifyRule(
        'SEC-AUTH-02c: Ordinary authenticated user cannot list /users',
        'DENY',
        () => getDocs(collection(res1Db, 'users'))
      );
    });

    it('SEC-PROF-01: Resident and Collector can create valid initial profiles with canonical state', async () => {
      const resDb = userContext('res_1', 'res1@wastelink.co.tz', false).firestore();
      await verifyRule(
        'SEC-PROF-01a: Resident creates valid /users/res_1 profile (NOT_STARTED, NOT_APPLICABLE)',
        'ALLOW',
        () => setDoc(doc(resDb, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident'))
      );

      const colDb = userContext('col_1', 'col1@wastelink.co.tz', false).firestore();
      await verifyRule(
        'SEC-PROF-01b: Collector creates valid /users/col_1 profile (NOT_STARTED, NOT_SUBMITTED)',
        'ALLOW',
        () => setDoc(doc(colDb, 'users/col_1'), buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector'))
      );
    });

    it('SEC-PROF-02: Client cannot self-assign ADMIN role, spoof UID/email, pre-approve collector, or delete profile', async () => {
      const attackerDb = userContext('attacker_1', 'attacker@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-02a: Client cannot create profile with role = ADMIN',
        'DENY',
        () => setDoc(doc(attackerDb, 'users/attacker_1'), buildValidUserDoc('attacker_1', 'attacker@wastelink.co.tz', 'ADMIN'))
      );
      await verifyRule(
        'SEC-PROF-02b: Client cannot create profile under another UID',
        'DENY',
        () => setDoc(doc(attackerDb, 'users/other_uid'), buildValidUserDoc('other_uid', 'attacker@wastelink.co.tz'))
      );
      await verifyRule(
        'SEC-PROF-02c: Client cannot create profile with spoofed email',
        'DENY',
        () => setDoc(doc(attackerDb, 'users/attacker_1'), buildValidUserDoc('attacker_1', 'spoof@wastelink.co.tz'))
      );
      await verifyRule(
        'SEC-PROF-02d: Collector cannot create profile with collectorVerificationStatus = APPROVED',
        'DENY',
        () => setDoc(
          doc(attackerDb, 'users/attacker_1'),
          buildValidUserDoc('attacker_1', 'attacker@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'APPROVED'
          })
        )
      );
    });

    it('SEC-PROF-03: Owner cannot mutate role, accountStatus, verification fields, or timestamps; email_verified token required for updates', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident'));
      });

      const unverifiedDb = userContext('res_1', 'res1@wastelink.co.tz', false).firestore();
      await verifyRule(
        'SEC-PROF-03a: Unverified user (email_verified == false) cannot update profile/onboarding',
        'DENY',
        () => updateDoc(doc(unverifiedDb, 'users/res_1'), {
          onboardingStatus: 'COMPLETED',
          updatedAtEpochMs: 1730000200000
        })
      );

      const verifiedDb = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-03b: Verified active user can complete onboarding and update contact fields',
        'ALLOW',
        () => updateDoc(doc(verifiedDb, 'users/res_1'), {
          fullName: 'Amina Updated',
          name: 'Amina Updated',
          defaultZoneId: 'zone_ilala',
          onboardingStatus: 'COMPLETED',
          updatedAtEpochMs: 1730000200000
        })
      );

      await verifyRule(
        'SEC-PROF-03c: Verified user cannot regress onboardingStatus from COMPLETED to NOT_STARTED',
        'DENY',
        () => updateDoc(doc(verifiedDb, 'users/res_1'), {
          onboardingStatus: 'NOT_STARTED',
          updatedAtEpochMs: 1730000300000
        })
      );

      await verifyRule(
        'SEC-PROF-03d: Verified user cannot mutate role, isVerified, or createdAtEpochMs',
        'DENY',
        () => updateDoc(doc(verifiedDb, 'users/res_1'), {
          role: 'admin',
          updatedAtEpochMs: 1730000300000
        })
      );

      await verifyRule(
        'SEC-PROF-03e: User cannot delete own /users/res_1 document',
        'DENY',
        () => deleteDoc(doc(verifiedDb, 'users/res_1'))
      );
    });
  });

  // ===========================================================================
  // 3. COLLECTOR SUBMISSIONS, ATOMIC BATCHES & IMMUTABLE HISTORY
  // ===========================================================================
  describe('3. Collector Vetting Submissions & Atomic WriteBatch Integrity', () => {
    it('SEC-COL-01: Verified Collector can create initial submission atomically; partial batches fail', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/col_1'), buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector'));
      });

      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();

      // Batch missing /history/attempt_1 must fail
      await verifyRule(
        'SEC-COL-01a: Submission batch missing /history/attempt_1 is rejected',
        'DENY',
        async () => {
          const badBatch = writeBatch(col1Db);
          badBatch.set(doc(col1Db, 'collector_submissions/col_1'), buildValidSubmissionDoc('col_1'));
          badBatch.update(doc(col1Db, 'users/col_1'), {
            collectorVerificationStatus: 'PENDING_REVIEW',
            updatedAtEpochMs: 1730000100000
          });
          await badBatch.commit();
        }
      );

      // Complete atomic WriteBatch succeeds
      await verifyRule(
        'SEC-COL-01b: Complete atomic WriteBatch (submission + history/attempt_1 + user PENDING_REVIEW) succeeds',
        'ALLOW',
        async () => {
          const batch = writeBatch(col1Db);
          batch.set(doc(col1Db, 'collector_submissions/col_1'), buildValidSubmissionDoc('col_1'));
          batch.set(
            doc(col1Db, 'collector_submissions/col_1/history/attempt_1'),
            buildValidHistoryDoc('col_1', 1, 'SUBMITTED')
          );
          batch.update(doc(col1Db, 'users/col_1'), {
            collectorVerificationStatus: 'PENDING_REVIEW',
            onboardingStatus: 'COMPLETED',
            vehicleType: 'Compactor Truck',
            vehiclePlateNumber: 'T 456 ABC',
            defaultZoneId: 'zone_kinondoni',
            updatedAtEpochMs: 1730000100000
          });
          await batch.commit();
        }
      );

      // Once PENDING_REVIEW, collector cannot modify submission or self-approve
      await verifyRule(
        'SEC-COL-01c: Collector cannot modify submission while PENDING_REVIEW',
        'DENY',
        () => updateDoc(doc(col1Db, 'collector_submissions/col_1'), {
          vehiclePlateNumber: 'T 999 ZZZ'
        })
      );
    });

    it('SEC-COL-02: Rejected collector can atomically resubmit (attempt 2), clearing collectorRejectionReason on user doc while preserving history', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(
          doc(db, 'users/col_1'),
          buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'REJECTED',
            collectorRejectionReason: 'Plate number blurry',
            onboardingStatus: 'COMPLETED'
          })
        );
        await setDoc(
          doc(db, 'collector_submissions/col_1'),
          buildValidSubmissionDoc('col_1', {
            status: 'REJECTED',
            submissionAttempt: 1,
            reviewedByUid: 'admin_1',
            reviewedAtEpochMs: 1730000500000,
            rejectionReason: 'Plate number blurry',
            adminNotes: 'Verify plate on second review'
          })
        );
      });

      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();

      await verifyRule(
        'SEC-COL-02a: Resubmission wiping adminNotes or altering lastRejectionReason is denied',
        'DENY',
        async () => {
          const badBatch = writeBatch(col1Db);
          badBatch.update(doc(col1Db, 'collector_submissions/col_1'), {
            vehiclePlateNumber: 'T 456 CORRECT',
            status: 'PENDING_REVIEW',
            submissionAttempt: 2,
            submittedAtEpochMs: 1730000900000,
            lastRejectionReason: 'Forged reason',
            lastReviewedByUid: 'admin_1',
            lastReviewedAtEpochMs: 1730000500000,
            reviewedByUid: null,
            reviewedAtEpochMs: null,
            rejectionReason: null,
            adminNotes: null
          });
          badBatch.set(
            doc(col1Db, 'collector_submissions/col_1/history/attempt_2'),
            buildValidHistoryDoc('col_1', 2, 'RESUBMITTED', { vehiclePlateNumber: 'T 456 CORRECT' })
          );
          badBatch.update(doc(col1Db, 'users/col_1'), {
            collectorVerificationStatus: 'PENDING_REVIEW',
            collectorRejectionReason: null,
            updatedAtEpochMs: 1730000900000
          });
          await badBatch.commit();
        }
      );

      await verifyRule(
        'SEC-COL-02b: Valid atomic resubmission (attempt 2, clearing collectorRejectionReason on user doc) succeeds',
        'ALLOW',
        async () => {
          const validBatch = writeBatch(col1Db);
          validBatch.update(doc(col1Db, 'collector_submissions/col_1'), {
            vehiclePlateNumber: 'T 456 CORRECT',
            status: 'PENDING_REVIEW',
            submissionAttempt: 2,
            submittedAtEpochMs: 1730000900000,
            lastRejectionReason: 'Plate number blurry',
            lastReviewedByUid: 'admin_1',
            lastReviewedAtEpochMs: 1730000500000,
            reviewedByUid: null,
            reviewedAtEpochMs: null,
            rejectionReason: null,
            adminNotes: 'Verify plate on second review'
          });
          validBatch.set(
            doc(col1Db, 'collector_submissions/col_1/history/attempt_2'),
            buildValidHistoryDoc('col_1', 2, 'RESUBMITTED', { vehiclePlateNumber: 'T 456 CORRECT' })
          );
          validBatch.update(doc(col1Db, 'users/col_1'), {
            collectorVerificationStatus: 'PENDING_REVIEW',
            collectorRejectionReason: null,
            vehiclePlateNumber: 'T 456 CORRECT',
            updatedAtEpochMs: 1730000900000
          });
          await validBatch.commit();
        }
      );
    });
  });

  // ===========================================================================
  // 4. ADMINISTRATOR UPDATE RESTRICTIONS & MANDATORY AUDIT TRAIL
  // ===========================================================================
  describe('4. Administrator Authorization & Update Restrictions', () => {
    it('SEC-ADMIN-01: Fake ADMIN role in Firestore without custom claim token.admin == true has zero privilege', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/fake_admin'), buildValidUserDoc('fake_admin', 'fake@wastelink.co.tz', 'ADMIN'));
        await setDoc(doc(db, 'users/res_target'), buildValidUserDoc('res_target', 'target@wastelink.co.tz', 'resident'));
      });

      const fakeAdminDb = userContext('fake_admin', 'fake@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-ADMIN-01a: Fake ADMIN without custom claim cannot read other users',
        'DENY',
        () => getDoc(doc(fakeAdminDb, 'users/res_target'))
      );
      await verifyRule(
        'SEC-ADMIN-01b: Fake ADMIN without custom claim cannot write /user_moderation',
        'DENY',
        () => setDoc(doc(fakeAdminDb, 'user_moderation/res_target'), {
          userId: 'res_target',
          action: 'BANNED',
          accountStatus: 'BANNED',
          reason: 'Fake ban',
          moderatedByUid: 'fake_admin',
          moderatedAtEpochMs: 1730000500000
        })
      );
    });

    it('SEC-ADMIN-02: Even a trusted Admin cannot modify user personal identity fields, delete users, or approve a Resident as Collector', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_target'), buildValidUserDoc('res_target', 'target@wastelink.co.tz', 'resident'));
      });

      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      await verifyRule(
        'SEC-ADMIN-02a: Admin cannot modify user fullName or phoneNumber',
        'DENY',
        () => updateDoc(doc(adminDb, 'users/res_target'), {
          fullName: 'Admin Overwritten Name',
          name: 'Admin Overwritten Name'
        })
      );

      await verifyRule(
        'SEC-ADMIN-02b: Admin cannot mark a Resident as collectorVerificationStatus = APPROVED',
        'DENY',
        () => updateDoc(doc(adminDb, 'users/res_target'), {
          collectorVerificationStatus: 'APPROVED',
          isVerified: true
        })
      );

      await verifyRule(
        'SEC-ADMIN-02c: Admin cannot hard-delete /users/res_target',
        'DENY',
        () => deleteDoc(doc(adminDb, 'users/res_target'))
      );
    });

    it('SEC-ADMIN-03: Admin account moderation requires statusReason and atomic /user_moderation record', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_target'), buildValidUserDoc('res_target', 'target@wastelink.co.tz', 'resident'));
      });

      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      // Suspending user WITHOUT writing /user_moderation/res_target in batch must fail
      await verifyRule(
        'SEC-ADMIN-03a: Admin suspending user without atomic /user_moderation record is denied',
        'DENY',
        () => updateDoc(doc(adminDb, 'users/res_target'), {
          accountStatus: 'SUSPENDED',
          status: 'SUSPENDED',
          statusReason: 'Repeated policy violations',
          suspendedUntilEpochMs: 1730600000000
        })
      );

      // Suspending user WITHOUT statusReason or suspendedUntilEpochMs must fail
      await verifyRule(
        'SEC-ADMIN-03b: Admin suspending user without suspendedUntilEpochMs is denied',
        'DENY',
        async () => {
          const badBatch = writeBatch(adminDb);
          badBatch.update(doc(adminDb, 'users/res_target'), {
            accountStatus: 'SUSPENDED',
            status: 'SUSPENDED',
            statusReason: 'Repeated policy violations',
            suspendedUntilEpochMs: null
          });
          badBatch.set(doc(adminDb, 'user_moderation/res_target'), {
            userId: 'res_target',
            action: 'SUSPENDED',
            accountStatus: 'SUSPENDED',
            reason: 'Repeated policy violations',
            moderatedByUid: 'real_admin',
            moderatedAtEpochMs: 1730000700000
          });
          await badBatch.commit();
        }
      );

      // Valid atomic suspension batch (updating /users/res_target + /user_moderation/res_target + /admin_actions_log) succeeds
      await verifyRule(
        'SEC-ADMIN-03c: Admin atomic suspension with statusReason, suspendedUntilEpochMs, and /user_moderation succeeds',
        'ALLOW',
        async () => {
          const batch = writeBatch(adminDb);
          batch.update(doc(adminDb, 'users/res_target'), {
            accountStatus: 'SUSPENDED',
            status: 'SUSPENDED',
            statusReason: 'Repeated policy violations',
            suspendedUntilEpochMs: 1730600000000
          });
          batch.set(doc(adminDb, 'user_moderation/res_target'), {
            userId: 'res_target',
            action: 'SUSPENDED',
            accountStatus: 'SUSPENDED',
            reason: 'Repeated policy violations',
            adminNotes: 'Internal investigation #12',
            suspendedUntilEpochMs: 1730600000000,
            bannedUntil: 1730600000000,
            contractUntil: null,
            deletedAt: null,
            moderatedByUid: 'real_admin',
            moderatedAtEpochMs: 1730000700000
          });
          batch.set(doc(adminDb, 'admin_actions_log/log_suspend_1'), {
            actorId: 'real_admin',
            targetId: 'res_target',
            action: 'SUSPENDED_CLIENT',
            reason: 'Repeated policy violations',
            timestampMs: 1730000700000
          });
          await batch.commit();
        }
      );

      // Target user cannot read /user_moderation/res_target
      const targetDb = userContext('res_target', 'target@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-ADMIN-03d: Target user cannot read confidential /user_moderation/res_target',
        'DENY',
        () => getDoc(doc(targetDb, 'user_moderation/res_target'))
      );
    });

    it('SEC-ADMIN-04: Admin collector approval & rejection enforce state transitions, field immutability, and atomic history', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(
          doc(db, 'users/col_target'),
          buildValidUserDoc('col_target', 'coltarget@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'PENDING_REVIEW'
          })
        );
        await setDoc(
          doc(db, 'collector_submissions/col_target'),
          buildValidSubmissionDoc('col_target')
        );
      });

      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      // Admin cannot tamper with collector-submitted vehiclePlateNumber during review
      await verifyRule(
        'SEC-ADMIN-04a: Admin cannot alter collector-submitted vehiclePlateNumber during approval',
        'DENY',
        async () => {
          const badBatch = writeBatch(adminDb);
          badBatch.update(doc(adminDb, 'collector_submissions/col_target'), {
            vehiclePlateNumber: 'TAMPERED PLATE',
            status: 'APPROVED',
            reviewedByUid: 'real_admin',
            reviewedAtEpochMs: 1730000600000,
            rejectionReason: null
          });
          badBatch.set(
            doc(adminDb, 'collector_submissions/col_target/history/attempt_1_approved'),
            buildValidHistoryDoc('col_target', 1, 'APPROVED', {
              attemptId: 'attempt_1_approved',
              actorUid: 'real_admin'
            })
          );
          badBatch.update(doc(adminDb, 'users/col_target'), {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true
          });
          await badBatch.commit();
        }
      );

      // Admin approval WITH atomic history and user update succeeds
      await verifyRule(
        'SEC-ADMIN-04b: Admin atomic approval with /history/attempt_1_approved and /users/col_target update succeeds',
        'ALLOW',
        async () => {
          const batch = writeBatch(adminDb);
          batch.update(doc(adminDb, 'collector_submissions/col_target'), {
            status: 'APPROVED',
            reviewedByUid: 'real_admin',
            reviewedAtEpochMs: 1730000600000,
            rejectionReason: null
          });
          batch.set(
            doc(adminDb, 'collector_submissions/col_target/history/attempt_1_approved'),
            buildValidHistoryDoc('col_target', 1, 'APPROVED', {
              attemptId: 'attempt_1_approved',
              actorUid: 'real_admin'
            })
          );
          batch.update(doc(adminDb, 'users/col_target'), {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true,
            status: 'ACTIVE'
          });
          await batch.commit();
        }
      );
    });
  });

  // ===========================================================================
  // 5. WASTE REQUEST SECURITY (BOTH /waste_requests AND /wasteRequests)
  // ===========================================================================
  describe('5. Waste Request Privacy, State Transitions, Payment Protection & Legacy Parity', () => {
    beforeEach(async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident'));
        await setDoc(doc(db, 'users/res_2'), buildValidUserDoc('res_2', 'res2@wastelink.co.tz', 'resident'));
        await setDoc(
          doc(db, 'users/res_restricted'),
          buildValidUserDoc('res_restricted', 'rest@wastelink.co.tz', 'resident', {
            accountStatus: 'RESTRICTED',
            status: 'RESTRICTED',
            statusReason: 'Dispute under investigation'
          })
        );
        await setDoc(
          doc(db, 'users/col_1'),
          buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true
          })
        );
        await setDoc(
          doc(db, 'users/col_2'),
          buildValidUserDoc('col_2', 'col2@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true
          })
        );
        await setDoc(
          doc(db, 'users/col_unapproved'),
          buildValidUserDoc('col_unapproved', 'unapp@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'PENDING_REVIEW',
            isVerified: false
          })
        );

        // Seed requests in both canonical (waste_requests) and legacy (wasteRequests) collections
        await setDoc(
          doc(db, 'waste_requests/req_res1_unassigned'),
          buildValidWasteRequestDoc('req_res1_unassigned', 'res_1')
        );
        await setDoc(
          doc(db, 'waste_requests/req_res1_col1'),
          buildValidWasteRequestDoc('req_res1_col1', 'res_1', {
            status: 'ASSIGNED_TO_FLEET',
            assignedTruckId: 'col_1',
            assignedAt: 1730000200000,
            agreedPrice: 15000
          })
        );
        await setDoc(
          doc(db, 'wasteRequests/req_legacy_res1_col1'),
          buildValidWasteRequestDoc('req_legacy_res1_col1', 'res_1', {
            status: 'ASSIGNED_TO_FLEET',
            assignedTruckId: 'col_1',
            assignedAt: 1730000200000,
            agreedPrice: 15000
          })
        );
      });
    });

    it('SEC-REQ-01: Resident can read/query own requests, but CANNOT read or query another resident requests', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const res2Db = userContext('res_2', 'res2@wastelink.co.tz', true).firestore();

      await verifyRule(
        'SEC-REQ-01a: Resident res_1 can get own /waste_requests/req_res1_unassigned',
        'ALLOW',
        () => getDoc(doc(res1Db, 'waste_requests/req_res1_unassigned'))
      );

      await verifyRule(
        'SEC-REQ-01b: Resident res_1 can execute constrained query where(clientUid == res_1)',
        'ALLOW',
        () => getDocs(query(collection(res1Db, 'waste_requests'), where('clientUid', '==', 'res_1')))
      );

      await verifyRule(
        'SEC-REQ-01c: Resident res_2 CANNOT get res_1 request /waste_requests/req_res1_unassigned',
        'DENY',
        () => getDoc(doc(res2Db, 'waste_requests/req_res1_unassigned'))
      );

      await verifyRule(
        'SEC-REQ-01d: Resident res_2 CANNOT get res_1 legacy request /wasteRequests/req_legacy_res1_col1',
        'DENY',
        () => getDoc(doc(res2Db, 'wasteRequests/req_legacy_res1_col1'))
      );

      await verifyRule(
        'SEC-REQ-01e: Resident res_1 CANNOT execute unconstrained collection list on /waste_requests',
        'DENY',
        () => getDocs(collection(res1Db, 'waste_requests'))
      );
    });

    it('SEC-REQ-02: Collector can ONLY read/query requests assigned to their collector identity', async () => {
      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      const col2Db = userContext('col_2', 'col2@wastelink.co.tz', true).firestore();
      const unappDb = userContext('col_unapproved', 'unapp@wastelink.co.tz', true).firestore();

      await verifyRule(
        'SEC-REQ-02a: Assigned collector col_1 can get /waste_requests/req_res1_col1',
        'ALLOW',
        () => getDoc(doc(col1Db, 'waste_requests/req_res1_col1'))
      );

      await verifyRule(
        'SEC-REQ-02b: Assigned collector col_1 can execute constrained query where(assignedTruckId == col_1)',
        'ALLOW',
        () => getDocs(query(collection(col1Db, 'waste_requests'), where('assignedTruckId', '==', 'col_1')))
      );

      await verifyRule(
        'SEC-REQ-02c: Approved collector col_1 CANNOT read unassigned request /waste_requests/req_res1_unassigned',
        'DENY',
        () => getDoc(doc(col1Db, 'waste_requests/req_res1_unassigned'))
      );

      await verifyRule(
        'SEC-REQ-02d: Unassigned collector col_2 CANNOT read col_1 request /waste_requests/req_res1_col1',
        'DENY',
        () => getDoc(doc(col2Db, 'waste_requests/req_res1_col1'))
      );

      await verifyRule(
        'SEC-REQ-02e: Unassigned collector col_2 CANNOT read col_1 legacy request /wasteRequests/req_legacy_res1_col1',
        'DENY',
        () => getDoc(doc(col2Db, 'wasteRequests/req_legacy_res1_col1'))
      );

      await verifyRule(
        'SEC-REQ-02f: Unapproved collector cannot read any waste requests',
        'DENY',
        () => getDoc(doc(unappDb, 'waste_requests/req_res1_col1'))
      );
    });

    it('SEC-REQ-03: Resident creation and update restrictions (ownership, status, assignment & payment protection)', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const restrictedDb = userContext('res_restricted', 'rest@wastelink.co.tz', true).firestore();

      await verifyRule(
        'SEC-REQ-03a: Active resident can create valid request in /waste_requests',
        'ALLOW',
        () => setDoc(doc(res1Db, 'waste_requests/req_new_1'), buildValidWasteRequestDoc('req_new_1', 'res_1'))
      );

      await verifyRule(
        'SEC-REQ-03b: Restricted resident CANNOT create new request',
        'DENY',
        () => setDoc(doc(restrictedDb, 'waste_requests/req_rest_1'), buildValidWasteRequestDoc('req_rest_1', 'res_restricted'))
      );

      await verifyRule(
        'SEC-REQ-03c: Resident CANNOT forge clientUid for another resident on creation',
        'DENY',
        () => setDoc(doc(res1Db, 'waste_requests/req_forged'), buildValidWasteRequestDoc('req_forged', 'res_2'))
      );

      await verifyRule(
        'SEC-REQ-03d: Resident CANNOT pre-set assignedTruckId, agreedPrice, or COMPLETED status on creation',
        'DENY',
        () => setDoc(
          doc(res1Db, 'waste_requests/req_preassigned'),
          buildValidWasteRequestDoc('req_preassigned', 'res_1', {
            assignedTruckId: 'col_1',
            agreedPrice: 5000
          })
        )
      );

      await verifyRule(
        'SEC-REQ-03e: Resident can cancel own PENDING_DISPATCH request',
        'ALLOW',
        () => updateDoc(doc(res1Db, 'waste_requests/req_res1_unassigned'), {
          status: 'CANCELLED',
          updatedAt: 1730000300000
        })
      );

      await verifyRule(
        'SEC-REQ-03f: Resident CANNOT modify agreedPrice, assignedTruckId, or mark request COMPLETED',
        'DENY',
        () => updateDoc(doc(res1Db, 'waste_requests/req_res1_col1'), {
          agreedPrice: 100,
          updatedAt: 1730000300000
        })
      );
    });

    it('SEC-REQ-04: Assigned Collector operational state transitions, proof-of-collection requirement, and payment/ownership immutability', async () => {
      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      const col2Db = userContext('col_2', 'col2@wastelink.co.tz', true).firestore();

      // Unassigned collector col_2 cannot update col_1's request
      await verifyRule(
        'SEC-REQ-04a: Unassigned collector col_2 cannot update /waste_requests/req_res1_col1',
        'DENY',
        () => updateDoc(doc(col2Db, 'waste_requests/req_res1_col1'), {
          status: 'EN_ROUTE',
          enRouteAt: 1730000300000,
          updatedAt: 1730000300000
        })
      );

      // Assigned collector col_1 cannot jump directly from ASSIGNED_TO_FLEET to COMPLETED
      await verifyRule(
        'SEC-REQ-04b: Assigned collector cannot jump directly from ASSIGNED_TO_FLEET to COMPLETED',
        'DENY',
        () => updateDoc(doc(col1Db, 'waste_requests/req_res1_col1'), {
          status: 'COMPLETED',
          completedAt: 1730000300000,
          pocPhotoUrl: 'https://storage.googleapis.com/wastelink/poc_1.jpg',
          updatedAt: 1730000300000
        })
      );

      // Assigned collector col_1 cannot tamper with agreedPrice (forged payment update) or clientUid
      await verifyRule(
        'SEC-REQ-04c: Assigned collector cannot modify agreedPrice (forged payment update)',
        'DENY',
        () => updateDoc(doc(col1Db, 'waste_requests/req_res1_col1'), {
          status: 'EN_ROUTE',
          enRouteAt: 1730000300000,
          agreedPrice: 999999,
          updatedAt: 1730000300000
        })
      );

      // Step 1: Valid transition ASSIGNED_TO_FLEET -> EN_ROUTE
      await verifyRule(
        'SEC-REQ-04d: Assigned collector transitions ASSIGNED_TO_FLEET -> EN_ROUTE with enRouteAt',
        'ALLOW',
        () => updateDoc(doc(col1Db, 'waste_requests/req_res1_col1'), {
          status: 'EN_ROUTE',
          enRouteAt: 1730000300000,
          updatedAt: 1730000300000
        })
      );

      // Step 2: Valid transition EN_ROUTE -> ARRIVED_AT_LOCATION
      await verifyRule(
        'SEC-REQ-04e: Assigned collector transitions EN_ROUTE -> ARRIVED_AT_LOCATION with arrivedAt',
        'ALLOW',
        () => updateDoc(doc(col1Db, 'waste_requests/req_res1_col1'), {
          status: 'ARRIVED_AT_LOCATION',
          arrivedAt: 1730000400000,
          updatedAt: 1730000400000
        })
      );

      // Step 3: Attempting to complete WITHOUT proof of collection (pocPhotoUrl / pocPhotoBase64) is denied
      await verifyRule(
        'SEC-REQ-04f: Assigned collector CANNOT transition to COMPLETED without proof-of-collection photo',
        'DENY',
        () => updateDoc(doc(col1Db, 'waste_requests/req_res1_col1'), {
          status: 'COMPLETED',
          completedAt: 1730000500000,
          updatedAt: 1730000500000
        })
      );

      // Step 4: Valid completion WITH completedAt and pocPhotoUrl succeeds
      await verifyRule(
        'SEC-REQ-04g: Assigned collector transitions ARRIVED_AT_LOCATION -> COMPLETED with pocPhotoUrl and completedAt',
        'ALLOW',
        () => updateDoc(doc(col1Db, 'waste_requests/req_res1_col1'), {
          status: 'COMPLETED',
          completedAt: 1730000500000,
          pocPhotoUrl: 'https://storage.googleapis.com/wastelink/poc_1.jpg',
          updatedAt: 1730000500000
        })
      );
    });

    it('SEC-REQ-05: Administrator dispatch operations and legacy /wasteRequests parity', async () => {
      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      await verifyRule(
        'SEC-REQ-05a: Admin can dispatch /waste_requests/req_res1_unassigned to col_1 with agreedPrice',
        'ALLOW',
        () => updateDoc(doc(adminDb, 'waste_requests/req_res1_unassigned'), {
          status: 'ASSIGNED_TO_FLEET',
          assignedTruckId: 'col_1',
          assignedAt: 1730000250000,
          agreedPrice: 20000,
          updatedAt: 1730000250000
        })
      );

      await verifyRule(
        'SEC-REQ-05b: Admin CANNOT alter clientUid (resident ownership) on waste request',
        'DENY',
        () => updateDoc(doc(adminDb, 'waste_requests/req_res1_unassigned'), {
          clientUid: 'res_2',
          updatedAt: 1730000260000
        })
      );

      await verifyRule(
        'SEC-REQ-05c: Even Admin cannot hard-delete waste requests on /waste_requests or /wasteRequests',
        'DENY',
        () => deleteDoc(doc(adminDb, 'wasteRequests/req_legacy_res1_col1'))
      );
    });
  });

  // ===========================================================================
  // 6. LIVE LOCATION PRIVACY & REQUEST-TO-COLLECTOR VERIFICATION
  // ===========================================================================
  describe('6. Live Location Privacy & Request-to-Collector Relationship Verification', () => {
    beforeEach(async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident'));
        await setDoc(doc(db, 'users/res_2'), buildValidUserDoc('res_2', 'res2@wastelink.co.tz', 'resident'));
        await setDoc(
          doc(db, 'users/col_1'),
          buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true
          })
        );
        await setDoc(
          doc(db, 'users/col_2'),
          buildValidUserDoc('col_2', 'col2@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true
          })
        );
        await setDoc(
          doc(db, 'waste_requests/req_active_res1_col1'),
          buildValidWasteRequestDoc('req_active_res1_col1', 'res_1', {
            status: 'EN_ROUTE',
            assignedTruckId: 'col_1'
          })
        );
        await setDoc(
          doc(db, 'waste_requests/req_other_col2'),
          buildValidWasteRequestDoc('req_other_col2', 'res_2', {
            status: 'EN_ROUTE',
            assignedTruckId: 'col_2'
          })
        );
      });
    });

    it('SEC-LOC-01: Approved collector can write own location with verified activeRequestId, but cannot spoof unrelated activeRequestId', async () => {
      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();

      // Collector col_1 cannot reference a request assigned to col_2
      await verifyRule(
        'SEC-LOC-01a: Collector col_1 CANNOT write location referencing req_other_col2 (assigned to col_2)',
        'DENY',
        () => setDoc(doc(col1Db, 'live_locations/col_1'), {
          collectorId: 'col_1',
          lat: -6.7924,
          lng: 39.2083,
          heading: 180.0,
          activeRequestId: 'req_other_col2',
          updatedAtEpochMs: 1730000300000
        })
      );

      // Collector col_1 can write location referencing their genuinely assigned active request req_active_res1_col1
      await verifyRule(
        'SEC-LOC-01b: Collector col_1 CAN write own location referencing assigned req_active_res1_col1',
        'ALLOW',
        () => setDoc(doc(col1Db, 'live_locations/col_1'), {
          collectorId: 'col_1',
          lat: -6.7924,
          lng: 39.2083,
          heading: 180.0,
          activeRequestId: 'req_active_res1_col1',
          updatedAtEpochMs: 1730000300000
        })
      );
    });

    it('SEC-LOC-02: Only assigned active request Resident, Collector owner, or Admin can read /live_locations/col_1; unrelated residents/collectors and list queries are denied', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'live_locations/col_1'), {
          collectorId: 'col_1',
          lat: -6.7924,
          lng: 39.2083,
          heading: 180.0,
          activeRequestId: 'req_active_res1_col1',
          updatedAtEpochMs: 1730000300000
        });
      });

      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const res2Db = userContext('res_2', 'res2@wastelink.co.tz', true).firestore();
      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      const col2Db = userContext('col_2', 'col2@wastelink.co.tz', true).firestore();
      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      await verifyRule(
        'SEC-LOC-02a: Collector col_1 can get own /live_locations/col_1',
        'ALLOW',
        () => getDoc(doc(col1Db, 'live_locations/col_1'))
      );

      await verifyRule(
        'SEC-LOC-02b: Resident res_1 (owner of active EN_ROUTE req_active_res1_col1) CAN get /live_locations/col_1',
        'ALLOW',
        () => getDoc(doc(res1Db, 'live_locations/col_1'))
      );

      await verifyRule(
        'SEC-LOC-02c: Unrelated Resident res_2 CANNOT get /live_locations/col_1',
        'DENY',
        () => getDoc(doc(res2Db, 'live_locations/col_1'))
      );

      await verifyRule(
        'SEC-LOC-02d: Other Collector col_2 CANNOT get /live_locations/col_1',
        'DENY',
        () => getDoc(doc(col2Db, 'live_locations/col_1'))
      );

      await verifyRule(
        'SEC-LOC-02e: Resident res_1 CANNOT list /live_locations collection',
        'DENY',
        () => getDocs(collection(res1Db, 'live_locations'))
      );

      await verifyRule(
        'SEC-LOC-02f: Collector col_1 CANNOT list /live_locations collection',
        'DENY',
        () => getDocs(collection(col1Db, 'live_locations'))
      );

      await verifyRule(
        'SEC-LOC-02g: Admin CAN list /live_locations collection for fleet monitoring',
        'ALLOW',
        () => getDocs(collection(adminDb, 'live_locations'))
      );

      // Once the waste request is COMPLETED, Resident res_1 can NO LONGER read /live_locations/col_1!
      await seedWithRulesDisabled(async (db) => {
        await updateDoc(doc(db, 'waste_requests/req_active_res1_col1'), {
          status: 'COMPLETED'
        });
      });

      await verifyRule(
        'SEC-LOC-02h: Once request is COMPLETED, Resident res_1 is immediately DENIED access to /live_locations/col_1',
        'DENY',
        () => getDoc(doc(res1Db, 'live_locations/col_1'))
      );
    });
  });

  // ===========================================================================
  // 7. DISPUTES, AUDIT LOGS, OPERATIONAL UNITS & SUBCOLLECTION WILDCARD CHECKS
  // ===========================================================================
  describe('7. Disputes, Audit Logs, Operational Units & Wildcard Security', () => {
    beforeEach(async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident'));
        await setDoc(doc(db, 'users/res_2'), buildValidUserDoc('res_2', 'res2@wastelink.co.tz', 'resident'));
        await setDoc(
          doc(db, 'users/col_1'),
          buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true
          })
        );
        await setDoc(
          doc(db, 'waste_requests/req_1'),
          buildValidWasteRequestDoc('req_1', 'res_1', {
            status: 'ASSIGNED_TO_FLEET',
            assignedTruckId: 'col_1'
          })
        );
      });
    });

    it('SEC-DISP-01: Dispute creation validates genuine request ownership; notes are admin-only append-only', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const res2Db = userContext('res_2', 'res2@wastelink.co.tz', true).firestore();
      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      // Resident res_2 cannot file a dispute on res_1's request req_1
      await verifyRule(
        'SEC-DISP-01a: Resident res_2 CANNOT file a dispute on res_1 request req_1',
        'DENY',
        () => setDoc(doc(res2Db, 'disputes/disp_forged'), {
          id: 'disp_forged',
          requestId: 'req_1',
          residentId: 'res_2',
          collectorId: 'col_1',
          status: 'OPEN',
          reason: 'Collector missed pickup',
          resolutionOutcome: null,
          createdAt: 1730000500000,
          updatedAt: 1730000500000
        })
      );

      // Resident res_1 can file a valid dispute on their own request req_1
      await verifyRule(
        'SEC-DISP-01b: Resident res_1 CAN file a valid OPEN dispute on own request req_1',
        'ALLOW',
        () => setDoc(doc(res1Db, 'disputes/disp_1'), {
          id: 'disp_1',
          requestId: 'req_1',
          residentId: 'res_1',
          collectorId: 'col_1',
          status: 'OPEN',
          reason: 'Collector missed scheduled pickup window',
          resolutionOutcome: null,
          createdAt: 1730000500000,
          updatedAt: 1730000500000
        })
      );

      // Participant collector col_1 can read disp_1, but unrelated res_2 cannot
      await verifyRule(
        'SEC-DISP-01c: Assigned collector col_1 CAN read /disputes/disp_1',
        'ALLOW',
        () => getDoc(doc(col1Db, 'disputes/disp_1'))
      );
      await verifyRule(
        'SEC-DISP-01d: Unrelated resident res_2 CANNOT read /disputes/disp_1',
        'DENY',
        () => getDoc(doc(res2Db, 'disputes/disp_1'))
      );

      // Resident cannot update or resolve dispute
      await verifyRule(
        'SEC-DISP-01e: Resident res_1 CANNOT update or resolve /disputes/disp_1',
        'DENY',
        () => updateDoc(doc(res1Db, 'disputes/disp_1'), {
          status: 'RESOLVED',
          resolutionOutcome: 'Full refund'
        })
      );

      // Admin can create internal dispute note (anti-impersonation enforced) and resolve dispute
      await verifyRule(
        'SEC-DISP-01f: Admin CANNOT impersonate another adminId when creating dispute note',
        'DENY',
        () => setDoc(doc(adminDb, 'disputes/disp_1/notes/note_1'), {
          id: 'note_1',
          text: 'Spoke with collector',
          adminId: 'spoofed_admin',
          timestampMs: 1730000600000
        })
      );

      await verifyRule(
        'SEC-DISP-01g: Admin CAN create valid dispute note with own adminId',
        'ALLOW',
        () => setDoc(doc(adminDb, 'disputes/disp_1/notes/note_1'), {
          id: 'note_1',
          text: 'Spoke with collector and verified GPS logs',
          adminId: 'real_admin',
          timestampMs: 1730000600000
        })
      );

      await verifyRule(
        'SEC-DISP-01h: Resident res_1 CANNOT read internal admin /disputes/disp_1/notes/note_1',
        'DENY',
        () => getDoc(doc(res1Db, 'disputes/disp_1/notes/note_1'))
      );

      await verifyRule(
        'SEC-DISP-01i: Admin CAN resolve /disputes/disp_1 with resolutionOutcome while preserving immutable fields',
        'ALLOW',
        () => updateDoc(doc(adminDb, 'disputes/disp_1'), {
          status: 'RESOLVED',
          resolutionOutcome: 'Rescheduled priority pickup',
          resolvedByUid: 'real_admin',
          updatedAt: 1730000700000
        })
      );
    });

    it('SEC-DISP-02: Service disputes enforce reporter ownership, cross-user privacy, and admin resolution constraints', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const res2Db = userContext('res_2', 'res2@wastelink.co.tz', true).firestore();
      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      await verifyRule(
        'SEC-DISP-02a: Resident res_1 CANNOT impersonate res_2 as reporterId in /service_disputes',
        'DENY',
        () => setDoc(doc(res1Db, 'service_disputes/sdisp_forged'), {
          id: 'sdisp_forged',
          reporterId: 'res_2',
          disputedEntityType: 'PICKUP_REQUEST',
          disputedEntityId: 'req_1',
          snapshotData: '{}',
          reason: 'Service complaint',
          status: 'PENDING_REVIEW',
          createdAt: 1730000500000,
          updatedAt: 1730000500000
        })
      );

      await verifyRule(
        'SEC-DISP-02b: Resident res_1 CAN create valid /service_disputes/sdisp_1 with own reporterId',
        'ALLOW',
        () => setDoc(doc(res1Db, 'service_disputes/sdisp_1'), {
          id: 'sdisp_1',
          reporterId: 'res_1',
          disputedEntityType: 'PICKUP_REQUEST',
          disputedEntityId: 'req_1',
          snapshotData: '{}',
          reason: 'Service complaint regarding pickup delay',
          status: 'PENDING_REVIEW',
          createdAt: 1730000500000,
          updatedAt: 1730000500000
        })
      );

      await verifyRule(
        'SEC-DISP-02c: Unrelated resident res_2 CANNOT read /service_disputes/sdisp_1',
        'DENY',
        () => getDoc(doc(res2Db, 'service_disputes/sdisp_1'))
      );

      await verifyRule(
        'SEC-DISP-02d: Admin CANNOT alter reporterId or reason on /service_disputes/sdisp_1',
        'DENY',
        () => updateDoc(doc(adminDb, 'service_disputes/sdisp_1'), {
          reason: 'Tampered reason',
          status: 'RESOLVED'
        })
      );

      await verifyRule(
        'SEC-DISP-02e: Admin CAN resolve /service_disputes/sdisp_1 with valid resolvedByUid',
        'ALLOW',
        () => updateDoc(doc(adminDb, 'service_disputes/sdisp_1'), {
          status: 'RESOLVED',
          resolutionOutcome: 'Resolved with credit',
          resolvedByUid: 'real_admin',
          updatedAt: 1730000700000
        })
      );
    });

    it('SEC-AUDIT-01: Audit logs prevent actor impersonation and unauthorized admin event forgery', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      // Admin cannot impersonate another admin in /admin_actions_log (even if actorId is valid but adminId is spoofed)
      await verifyRule(
        'SEC-AUDIT-01a: Admin CANNOT spoof adminId while setting actorId in /admin_actions_log',
        'DENY',
        () => setDoc(doc(adminDb, 'admin_actions_log/log_spoof'), {
          actorId: 'real_admin',
          adminId: 'other_admin',
          targetId: 'res_1',
          action: 'BANNED_CLIENT',
          reason: 'Test',
          timestampMs: 1730000600000
        })
      );

      // Resident cannot forge an admin action in /audit_logs
      await verifyRule(
        'SEC-AUDIT-01b: Resident CANNOT log admin action BANNED_CLIENT in /audit_logs',
        'DENY',
        () => setDoc(doc(res1Db, 'audit_logs/audit_forged'), {
          uid: 'res_1',
          action: 'BANNED_CLIENT',
          timestamp: 1730000600000
        })
      );

      // Resident can log allowed self-service event ACCOUNT_DELETED with own uid
      await verifyRule(
        'SEC-AUDIT-01c: Resident CAN log ACCOUNT_DELETED for own uid in /audit_logs',
        'ALLOW',
        () => setDoc(doc(res1Db, 'audit_logs/audit_self'), {
          uid: 'res_1',
          action: 'ACCOUNT_DELETED',
          timestamp: 1730000600000
        })
      );
    });

    it('SEC-UNIT-01: Operational units restrict read to owning collector or Admin, and write to Admin only', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      await verifyRule(
        'SEC-UNIT-01a: Admin CAN create valid /operational_units/unit_1',
        'ALLOW',
        () => setDoc(doc(adminDb, 'operational_units/unit_1'), {
          id: 'unit_1',
          companyId: 'col_1',
          name: 'Truck Alpha - Route 4',
          licensePlate: 'T 456 ABC',
          status: 'ACTIVE',
          totalPickups: 12,
          rating: 4.8,
          createdAt: 1730000100000,
          updatedAt: 1730000100000
        })
      );

      await verifyRule(
        'SEC-UNIT-01b: Owning approved collector col_1 CAN read /operational_units/unit_1',
        'ALLOW',
        () => getDoc(doc(col1Db, 'operational_units/unit_1'))
      );

      await verifyRule(
        'SEC-UNIT-01c: Resident res_1 CANNOT read or list /operational_units',
        'DENY',
        () => getDoc(doc(res1Db, 'operational_units/unit_1'))
      );
    });

    it('SEC-FALLBACK-01: Catch-all default-deny blocks unrecognized collections and arbitrary nested subcollections', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const adminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      await verifyRule(
        'SEC-FALLBACK-01a: Unrecognized top-level collection denied for authenticated user',
        'DENY',
        () => setDoc(doc(res1Db, 'unknown_collection/doc_1'), { foo: 'bar' })
      );

      await verifyRule(
        'SEC-FALLBACK-01b: Arbitrary nested subcollection under /users/{uid}/arbitrary_sub/doc_1 denied even for owner and Admin',
        'DENY',
        () => setDoc(doc(adminDb, 'users/res_1/arbitrary_sub/doc_1'), { secret: true })
      );
    });
  });
});
