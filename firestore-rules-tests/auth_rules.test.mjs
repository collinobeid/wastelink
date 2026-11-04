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

function userContext(uid, email, emailVerified = true, extraClaims = {}) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: emailVerified,
    ...extraClaims
  });
}

describe('WasteLink Module 1 — Firestore Security Rules & Emulator Suite', () => {
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
  // 1. AUTHENTICATION & PRIVACY ISOLATION
  // ===========================================================================
  describe('1. Authentication & Ownership Isolation', () => {
    it('SEC-AUTH-01: Unauthenticated user cannot read private profiles', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz'));
      });

      const unauthedDb = testEnv.unauthenticatedContext().firestore();
      await verifyRule(
        'SEC-AUTH-01: Unauthenticated user cannot read /users/res_1',
        'DENY',
        () => getDoc(doc(unauthedDb, 'users/res_1'))
      );
    });

    it('SEC-AUTH-02: Unauthenticated user cannot create profiles', async () => {
      const unauthedDb = testEnv.unauthenticatedContext().firestore();
      await verifyRule(
        'SEC-AUTH-02: Unauthenticated user cannot create /users/res_1',
        'DENY',
        () => setDoc(doc(unauthedDb, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz'))
      );
    });

    it('SEC-AUTH-03: Authenticated user can read their own permitted profile', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz'));
      });

      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', false).firestore();
      await verifyRule(
        'SEC-AUTH-03: Authenticated owner can read own /users/res_1 profile',
        'ALLOW',
        () => getDoc(doc(res1Db, 'users/res_1'))
      );
    });

    it('SEC-AUTH-04: Authenticated user cannot read another resident private profile', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz'));
        await setDoc(doc(db, 'users/res_2'), buildValidUserDoc('res_2', 'res2@wastelink.co.tz'));
      });

      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-AUTH-04: Authenticated user res_1 cannot read /users/res_2',
        'DENY',
        () => getDoc(doc(res1Db, 'users/res_2'))
      );
    });

    it('SEC-AUTH-05: Ordinary authenticated user cannot list /users collection', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-AUTH-05: Ordinary authenticated user cannot list /users',
        'DENY',
        () => getDocs(collection(res1Db, 'users'))
      );
    });
  });

  // ===========================================================================
  // 2. PROFILE INTEGRITY & IMMUTABLE FIELD ENFORCEMENT
  // ===========================================================================
  describe('2. Profile Creation & Integrity', () => {
    it('SEC-PROF-01: Resident can create own valid profile and Collector can create own valid profile', async () => {
      const resDb = userContext('res_1', 'res1@wastelink.co.tz', false).firestore();
      await verifyRule(
        'SEC-PROF-01a: Resident creates valid /users/res_1 profile',
        'ALLOW',
        () => setDoc(doc(resDb, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident'))
      );

      const colDb = userContext('col_1', 'col1@wastelink.co.tz', false).firestore();
      await verifyRule(
        'SEC-PROF-01b: Collector creates valid /users/col_1 profile (NOT_SUBMITTED)',
        'ALLOW',
        () => setDoc(doc(colDb, 'users/col_1'), buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector'))
      );
    });

    it('SEC-PROF-02: Resident or Collector cannot create ADMIN profile', async () => {
      const attackerDb = userContext('attacker_1', 'attacker@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-02a: Client cannot create profile with role = ADMIN',
        'DENY',
        () => setDoc(
          doc(attackerDb, 'users/attacker_1'),
          buildValidUserDoc('attacker_1', 'attacker@wastelink.co.tz', 'ADMIN')
        )
      );
      await verifyRule(
        'SEC-PROF-02b: Client cannot create profile with role = admin',
        'DENY',
        () => setDoc(
          doc(attackerDb, 'users/attacker_1'),
          buildValidUserDoc('attacker_1', 'attacker@wastelink.co.tz', 'admin')
        )
      );
    });

    it('SEC-PROF-03: User cannot create profile for another UID or with mismatched email or pre-approved collector status', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-03a: User cannot create profile under another UID',
        'DENY',
        () => setDoc(doc(res1Db, 'users/res_2'), buildValidUserDoc('res_2', 'res1@wastelink.co.tz'))
      );
      await verifyRule(
        'SEC-PROF-03b: User cannot create profile with spoofed email',
        'DENY',
        () => setDoc(doc(res1Db, 'users/res_1'), buildValidUserDoc('res_1', 'spoofed@wastelink.co.tz'))
      );
      await verifyRule(
        'SEC-PROF-03c: Collector cannot create profile with collectorVerificationStatus = APPROVED',
        'DENY',
        () => setDoc(
          doc(res1Db, 'users/res_1'),
          buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'APPROVED'
          })
        )
      );
    });

    it('SEC-PROF-04: User cannot change their established role after creation', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_1'), buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident'));
      });

      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-04a: Resident cannot change role to collector',
        'DENY',
        () => updateDoc(doc(res1Db, 'users/res_1'), { role: 'collector' })
      );
      await verifyRule(
        'SEC-PROF-04b: Resident cannot escalate role to admin',
        'DENY',
        () => updateDoc(doc(res1Db, 'users/res_1'), { role: 'admin' })
      );
    });

    it('SEC-PROF-05: User cannot mark email/phone as verified or set isVerified = true', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/col_1'), buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector'));
      });

      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-05a: User cannot inject isEmailVerified = true into Firestore profile',
        'DENY',
        () => updateDoc(doc(col1Db, 'users/col_1'), { isEmailVerified: true })
      );
      await verifyRule(
        'SEC-PROF-05b: User cannot mutate isVerified = true on Firestore profile',
        'DENY',
        () => updateDoc(doc(col1Db, 'users/col_1'), { isVerified: true })
      );
    });

    it('SEC-PROF-06: User cannot modify account restriction status or admin-only fields', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(
          doc(db, 'users/res_restricted'),
          buildValidUserDoc('res_restricted', 'restricted@wastelink.co.tz', 'resident', {
            accountStatus: 'RESTRICTED',
            status: 'RESTRICTED',
            statusReason: 'Repeated missed pickups'
          })
        );
        await setDoc(
          doc(db, 'users/res_active'),
          buildValidUserDoc('res_active', 'active@wastelink.co.tz', 'resident')
        );
      });

      const restrictedDb = userContext('res_restricted', 'restricted@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-06a: Restricted user cannot self-clear accountStatus to ACTIVE',
        'DENY',
        () => updateDoc(doc(restrictedDb, 'users/res_restricted'), {
          accountStatus: 'ACTIVE',
          status: 'ACTIVE'
        })
      );

      const activeDb = userContext('res_active', 'active@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-06b: Active user cannot modify createdAtEpochMs or collectorRejectionReason',
        'DENY',
        () => updateDoc(doc(activeDb, 'users/res_active'), {
          createdAtEpochMs: 9999999999999
        })
      );
    });

    it('SEC-PROF-07: Unknown profile fields and invalid field types are rejected', async () => {
      const res1Db = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-PROF-07a: Unknown field isAdmin=true rejected on profile creation',
        'DENY',
        () => setDoc(
          doc(res1Db, 'users/res_1'),
          buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident', { isAdmin: true })
        )
      );
      await verifyRule(
        'SEC-PROF-07b: Invalid field type (numeric fullName / short name) rejected on creation',
        'DENY',
        () => setDoc(
          doc(res1Db, 'users/res_1'),
          buildValidUserDoc('res_1', 'res1@wastelink.co.tz', 'resident', { fullName: 'A', name: 'A' })
        )
      );
    });
  });

  // ===========================================================================
  // 3. EMAIL VERIFICATION SECURITY
  // ===========================================================================
  describe('3. Email Verification Token Authority', () => {
    it('SEC-EMAIL-01: Unverified authenticated user cannot perform protected profile/onboarding updates', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_unv'), buildValidUserDoc('res_unv', 'unv@wastelink.co.tz', 'resident'));
      });

      const unverifiedDb = userContext('res_unv', 'unv@wastelink.co.tz', false).firestore();
      await verifyRule(
        'SEC-EMAIL-01: Unverified authenticated resident cannot complete onboarding',
        'DENY',
        () => updateDoc(doc(unverifiedDb, 'users/res_unv'), {
          defaultZoneId: 'zone_ilala',
          onboardingStatus: 'COMPLETED',
          updatedAtEpochMs: 1730000200000
        })
      );
    });

    it('SEC-EMAIL-02: Verified authenticated user can perform permitted self-service onboarding/profile updates', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/res_ver'), buildValidUserDoc('res_ver', 'ver@wastelink.co.tz', 'resident'));
      });

      const verifiedDb = userContext('res_ver', 'ver@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-EMAIL-02: Verified active resident can complete onboarding and update contact info',
        'ALLOW',
        () => updateDoc(doc(verifiedDb, 'users/res_ver'), {
          fullName: 'Amina Nyerere Updated',
          name: 'Amina Nyerere Updated',
          phoneNumber: '+255754998877',
          phone: '+255754998877',
          defaultZoneId: 'zone_ilala',
          onboardingStatus: 'COMPLETED',
          updatedAtEpochMs: 1730000200000
        })
      );
    });
  });

  // ===========================================================================
  // 4. COLLECTOR SUBMISSIONS, ATOMIC BATCHES & IMMUTABLE HISTORY
  // ===========================================================================
  describe('4. Collector Submissions & Atomic WriteBatch Enforcement', () => {
    it('SEC-COL-01: Verified Collector can create own valid submission via atomic WriteBatch', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/col_1'), buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector'));
      });

      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COL-01: Atomic WriteBatch creating submission + history/attempt_1 + parent PENDING_REVIEW succeeds',
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
    });

    it('SEC-COL-02: Collector cannot submit for another UID or approve themselves', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/col_1'), buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector'));
        await setDoc(doc(db, 'users/col_2'), buildValidUserDoc('col_2', 'col2@wastelink.co.tz', 'collector'));
      });

      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COL-02a: Collector col_1 cannot create submission for col_2',
        'DENY',
        () => setDoc(doc(col1Db, 'collector_submissions/col_2'), buildValidSubmissionDoc('col_2'))
      );

      await verifyRule(
        'SEC-COL-02b: Collector col_1 cannot self-approve submission (status = APPROVED)',
        'DENY',
        async () => {
          const batch = writeBatch(col1Db);
          batch.set(
            doc(col1Db, 'collector_submissions/col_1'),
            buildValidSubmissionDoc('col_1', { status: 'APPROVED' })
          );
          batch.set(
            doc(col1Db, 'collector_submissions/col_1/history/attempt_1'),
            buildValidHistoryDoc('col_1', 1, 'SUBMITTED')
          );
          batch.update(doc(col1Db, 'users/col_1'), {
            collectorVerificationStatus: 'APPROVED',
            updatedAtEpochMs: 1730000100000
          });
          await batch.commit();
        }
      );
    });

    it('SEC-COL-03: Collector cannot alter reviewer info, adminNotes, or modify while PENDING_REVIEW', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(
          doc(db, 'users/col_1'),
          buildValidUserDoc('col_1', 'col1@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'PENDING_REVIEW'
          })
        );
        await setDoc(
          doc(db, 'collector_submissions/col_1'),
          buildValidSubmissionDoc('col_1', { status: 'PENDING_REVIEW' })
        );
      });

      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COL-03a: Collector cannot modify submission while status is PENDING_REVIEW',
        'DENY',
        () => updateDoc(doc(col1Db, 'collector_submissions/col_1'), {
          vehiclePlateNumber: 'T 999 ZZZ'
        })
      );

      await verifyRule(
        'SEC-COL-03b: Collector cannot forge reviewedByUid or adminNotes',
        'DENY',
        () => updateDoc(doc(col1Db, 'collector_submissions/col_1'), {
          status: 'APPROVED',
          reviewedByUid: 'col_1',
          adminNotes: 'Self approved'
        })
      );
    });

    it('SEC-COL-04: Unauthorized users cannot read other collectors submissions, and history cannot be modified/deleted', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(
          doc(db, 'collector_submissions/col_1'),
          buildValidSubmissionDoc('col_1')
        );
        await setDoc(
          doc(db, 'collector_submissions/col_1/history/attempt_1'),
          buildValidHistoryDoc('col_1', 1, 'SUBMITTED')
        );
      });

      const otherUserDb = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COL-04a: Other user cannot read /collector_submissions/col_1',
        'DENY',
        () => getDoc(doc(otherUserDb, 'collector_submissions/col_1'))
      );

      const col1Db = userContext('col_1', 'col1@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COL-04b: Collector cannot update history entry /collector_submissions/col_1/history/attempt_1',
        'DENY',
        () => updateDoc(doc(col1Db, 'collector_submissions/col_1/history/attempt_1'), {
          vehiclePlateNumber: 'TAMPERED'
        })
      );

      const adminDb = userContext('admin_1', 'admin@wastelink.co.tz', true, { admin: true }).firestore();
      await verifyRule(
        'SEC-COL-04c: Even Admin cannot delete immutable history entry /collector_submissions/col_1/history/attempt_1',
        'DENY',
        () => deleteDoc(doc(adminDb, 'collector_submissions/col_1/history/attempt_1'))
      );
    });

    it('SEC-COL-05: Rejected collector can validly resubmit via atomic WriteBatch preserving rejection history', async () => {
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

      // First verify that resubmitting while wiping adminNotes or tampering with lastRejectionReason FAILS
      await verifyRule(
        'SEC-COL-05a: Resubmission that alters adminNotes or omits lastRejectionReason is rejected',
        'DENY',
        async () => {
          const badBatch = writeBatch(col1Db);
          badBatch.update(doc(col1Db, 'collector_submissions/col_1'), {
            vehiclePlateNumber: 'T 456 CORRECT',
            status: 'PENDING_REVIEW',
            submissionAttempt: 2,
            submittedAtEpochMs: 1730000900000,
            lastRejectionReason: 'Tampered reason',
            lastReviewedByUid: 'admin_1',
            lastReviewedAtEpochMs: 1730000500000,
            reviewedByUid: null,
            reviewedAtEpochMs: null,
            rejectionReason: null,
            adminNotes: null // Attempting to wipe adminNotes!
          });
          badBatch.set(
            doc(col1Db, 'collector_submissions/col_1/history/attempt_2'),
            buildValidHistoryDoc('col_1', 2, 'RESUBMITTED', { vehiclePlateNumber: 'T 456 CORRECT' })
          );
          badBatch.update(doc(col1Db, 'users/col_1'), {
            collectorVerificationStatus: 'PENDING_REVIEW',
            vehiclePlateNumber: 'T 456 CORRECT',
            updatedAtEpochMs: 1730000900000
          });
          await badBatch.commit();
        }
      );

      // Now verify that a compliant atomic resubmission succeeds
      await verifyRule(
        'SEC-COL-05b: Compliant atomic resubmission from REJECTED -> PENDING_REVIEW succeeds',
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
            vehiclePlateNumber: 'T 456 CORRECT',
            updatedAtEpochMs: 1730000900000
          });
          await validBatch.commit();
        }
      );
    });

    it('SEC-BATCH-01: Atomic batch failure modes (missing history doc, inconsistent parent state, partial unauthorized write)', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(doc(db, 'users/col_batch'), buildValidUserDoc('col_batch', 'batch@wastelink.co.tz', 'collector'));
      });

      const colDb = userContext('col_batch', 'batch@wastelink.co.tz', true).firestore();

      // 1. Missing history document
      await verifyRule(
        'SEC-BATCH-01a: Batch without /history/attempt_1 document fails atomically',
        'DENY',
        async () => {
          const batch = writeBatch(colDb);
          batch.set(doc(colDb, 'collector_submissions/col_batch'), buildValidSubmissionDoc('col_batch'));
          batch.update(doc(colDb, 'users/col_batch'), {
            collectorVerificationStatus: 'PENDING_REVIEW',
            updatedAtEpochMs: 1730000100000
          });
          await batch.commit();
        }
      );

      // 2. Inconsistent parent state (submission created, but parent /users/col_batch not updated to PENDING_REVIEW)
      await verifyRule(
        'SEC-BATCH-01b: Batch with inconsistent parent state fails atomically',
        'DENY',
        async () => {
          const batch = writeBatch(colDb);
          batch.set(doc(colDb, 'collector_submissions/col_batch'), buildValidSubmissionDoc('col_batch'));
          batch.set(
            doc(colDb, 'collector_submissions/col_batch/history/attempt_1'),
            buildValidHistoryDoc('col_batch', 1, 'SUBMITTED')
          );
          await batch.commit();
        }
      );

      // 3. Updating parent /users/col_batch to PENDING_REVIEW without creating /collector_submissions/col_batch
      await verifyRule(
        'SEC-BATCH-01c: Updating user collectorVerificationStatus to PENDING_REVIEW without submission doc fails',
        'DENY',
        () => updateDoc(doc(colDb, 'users/col_batch'), {
          collectorVerificationStatus: 'PENDING_REVIEW',
          updatedAtEpochMs: 1730000100000
        })
      );

      // Verify that none of the rejected batches left partial data in Firestore
      await seedWithRulesDisabled(async (db) => {
        const subSnap = await getDoc(doc(db, 'collector_submissions/col_batch'));
        const userSnap = await getDoc(doc(db, 'users/col_batch'));
        assert.equal(subSnap.exists(), false, 'Submission document must not exist after failed batches');
        assert.equal(userSnap.data().collectorVerificationStatus, 'NOT_SUBMITTED');
      });
    });
  });

  // ===========================================================================
  // 5. ADMINISTRATOR CUSTOM CLAIM SECURITY & PRIVATE MODERATION DATA
  // ===========================================================================
  describe('5. Administrator Custom Claim & Private Moderation Security', () => {
    it('SEC-ADMIN-01: Fake client-side ADMIN role in Firestore without token.admin custom claim has ZERO admin privilege', async () => {
      await seedWithRulesDisabled(async (db) => {
        // User whose Firestore doc claims role = 'ADMIN', but who lacks custom claim token.admin == true
        await setDoc(
          doc(db, 'users/fake_admin'),
          buildValidUserDoc('fake_admin', 'fakeadmin@wastelink.co.tz', 'ADMIN')
        );
        await setDoc(
          doc(db, 'users/col_target'),
          buildValidUserDoc('col_target', 'target@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'PENDING_REVIEW'
          })
        );
        await setDoc(
          doc(db, 'collector_submissions/col_target'),
          buildValidSubmissionDoc('col_target')
        );
      });

      // Authenticated WITHOUT `admin: true` claim
      const fakeAdminDb = userContext('fake_admin', 'fakeadmin@wastelink.co.tz', true).firestore();

      await verifyRule(
        'SEC-ADMIN-01a: Fake ADMIN without custom claim cannot read other users',
        'DENY',
        () => getDoc(doc(fakeAdminDb, 'users/col_target'))
      );

      await verifyRule(
        'SEC-ADMIN-01b: Fake ADMIN without custom claim cannot approve collectors',
        'DENY',
        async () => {
          const batch = writeBatch(fakeAdminDb);
          batch.update(doc(fakeAdminDb, 'collector_submissions/col_target'), {
            status: 'APPROVED',
            reviewedByUid: 'fake_admin',
            reviewedAtEpochMs: 1730000500000,
            rejectionReason: null
          });
          batch.update(doc(fakeAdminDb, 'users/col_target'), {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true
          });
          await batch.commit();
        }
      );

      await verifyRule(
        'SEC-ADMIN-01c: Fake ADMIN without custom claim cannot write /user_moderation',
        'DENY',
        () => setDoc(doc(fakeAdminDb, 'user_moderation/col_target'), {
          userId: 'col_target',
          action: 'BANNED',
          accountStatus: 'BANNED',
          reason: 'Unauthorized ban',
          adminNotes: 'Internal note',
          suspendedUntilEpochMs: null,
          bannedUntil: null,
          contractUntil: null,
          deletedAt: null,
          moderatedByUid: 'fake_admin',
          moderatedAtEpochMs: 1730000500000
        })
      );
    });

    it('SEC-ADMIN-02: Trusted Administrator with token.admin == true can approve collectors, moderate users, and write append-only audit logs', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(
          doc(db, 'users/col_target'),
          buildValidUserDoc('col_target', 'target@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'PENDING_REVIEW'
          })
        );
        await setDoc(
          doc(db, 'collector_submissions/col_target'),
          buildValidSubmissionDoc('col_target')
        );
      });

      const realAdminDb = userContext('real_admin', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      await verifyRule(
        'SEC-ADMIN-02a: Trusted Admin with custom claim can atomically approve collector submission',
        'ALLOW',
        async () => {
          const batch = writeBatch(realAdminDb);
          batch.update(doc(realAdminDb, 'collector_submissions/col_target'), {
            status: 'APPROVED',
            reviewedByUid: 'real_admin',
            reviewedAtEpochMs: 1730000600000,
            rejectionReason: null
          });
          batch.set(
            doc(realAdminDb, 'collector_submissions/col_target/history/attempt_1_approved'),
            buildValidHistoryDoc('col_target', 1, 'APPROVED', {
              attemptId: 'attempt_1_approved',
              actorUid: 'real_admin'
            })
          );
          batch.update(doc(realAdminDb, 'users/col_target'), {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true,
            status: 'ACTIVE'
          });
          await batch.commit();
        }
      );

      await verifyRule(
        'SEC-ADMIN-02b: Trusted Admin can write private /user_moderation/col_target record',
        'ALLOW',
        () => setDoc(doc(realAdminDb, 'user_moderation/col_target'), {
          userId: 'col_target',
          action: 'SUSPENDED',
          accountStatus: 'SUSPENDED',
          reason: 'Safety inspection overdue',
          adminNotes: 'Confidential fleet inspection note #402',
          suspendedUntilEpochMs: 1730600000000,
          bannedUntil: 1730600000000,
          contractUntil: null,
          deletedAt: null,
          moderatedByUid: 'real_admin',
          moderatedAtEpochMs: 1730000700000
        })
      );

      // Ordinary user (even the target user col_target) cannot read /user_moderation/col_target
      const targetUserDb = userContext('col_target', 'target@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-ADMIN-02c: Target user col_target cannot read confidential /user_moderation/col_target',
        'DENY',
        () => getDoc(doc(targetUserDb, 'user_moderation/col_target'))
      );

      // Admin can create append-only /admin_actions_log record, but cannot update or delete it
      await verifyRule(
        'SEC-ADMIN-02d: Trusted Admin can create /admin_actions_log/log_1',
        'ALLOW',
        () => setDoc(doc(realAdminDb, 'admin_actions_log/log_1'), {
          actorId: 'real_admin',
          targetId: 'col_target',
          action: 'APPROVED_COLLECTOR',
          reason: 'Verified license and plate',
          timestampMs: 1730000600000
        })
      );

      await verifyRule(
        'SEC-ADMIN-02e: Administrative audit log /admin_actions_log/log_1 cannot be altered after creation',
        'DENY',
        () => updateDoc(doc(realAdminDb, 'admin_actions_log/log_1'), {
          reason: 'Retroactively altered reason'
        })
      );

      await verifyRule(
        'SEC-ADMIN-02f: Administrative audit log /admin_actions_log/log_1 cannot be deleted',
        'DENY',
        () => deleteDoc(doc(realAdminDb, 'admin_actions_log/log_1'))
      );
    });
  });

  // ===========================================================================
  // 6. EXISTING COLLECTIONS & DEFAULT-DENY FALLBACK
  // ===========================================================================
  describe('6. Existing Collections Hardening & Default-Deny Fallback', () => {
    it('SEC-COLL-01: Unapproved collector cannot publish live location; approved active collector can', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(
          doc(db, 'users/col_pending'),
          buildValidUserDoc('col_pending', 'pending@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'PENDING_REVIEW',
            isVerified: false
          })
        );
        await setDoc(
          doc(db, 'users/col_approved'),
          buildValidUserDoc('col_approved', 'approved@wastelink.co.tz', 'collector', {
            collectorVerificationStatus: 'APPROVED',
            isVerified: true
          })
        );
      });

      const pendingColDb = userContext('col_pending', 'pending@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COLL-01a: Unapproved collector (PENDING_REVIEW) cannot write /live_locations/col_pending',
        'DENY',
        () => setDoc(doc(pendingColDb, 'live_locations/col_pending'), {
          collectorId: 'col_pending',
          lat: -6.7924,
          lng: 39.2083,
          heading: 90.0
        })
      );

      const approvedColDb = userContext('col_approved', 'approved@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COLL-01b: Approved active collector can write own /live_locations/col_approved',
        'ALLOW',
        () => setDoc(doc(approvedColDb, 'live_locations/col_approved'), {
          collectorId: 'col_approved',
          lat: -6.7924,
          lng: 39.2083,
          heading: 90.0
        })
      );
    });

    it('SEC-COLL-02: Restricted resident cannot create new waste request; active verified resident can', async () => {
      await seedWithRulesDisabled(async (db) => {
        await setDoc(
          doc(db, 'users/res_restricted'),
          buildValidUserDoc('res_restricted', 'rest@wastelink.co.tz', 'resident', {
            accountStatus: 'RESTRICTED',
            status: 'RESTRICTED'
          })
        );
        await setDoc(
          doc(db, 'users/res_active'),
          buildValidUserDoc('res_active', 'act@wastelink.co.tz', 'resident', {
            accountStatus: 'ACTIVE',
            status: 'ACTIVE'
          })
        );
      });

      const restrictedDb = userContext('res_restricted', 'rest@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COLL-02a: Restricted resident cannot create new /waste_requests document',
        'DENY',
        () => setDoc(doc(restrictedDb, 'waste_requests/req_1'), {
          clientUid: 'res_restricted',
          status: 'PENDING_DISPATCH',
          pickupAddress: 'Kinondoni Road'
        })
      );

      const activeDb = userContext('res_active', 'act@wastelink.co.tz', true).firestore();
      await verifyRule(
        'SEC-COLL-02b: Active verified resident can create own /waste_requests document',
        'ALLOW',
        () => setDoc(doc(activeDb, 'waste_requests/req_2'), {
          clientUid: 'res_active',
          status: 'PENDING_DISPATCH',
          pickupAddress: 'Kinondoni Road'
        })
      );
    });

    it('SEC-COLL-03: Default-deny blocks all reads and writes to unrecognized paths', async () => {
      const authedDb = userContext('res_1', 'res1@wastelink.co.tz', true).firestore();
      const adminDb = userContext('admin_1', 'admin@wastelink.co.tz', true, { admin: true }).firestore();

      await verifyRule(
        'SEC-COLL-03a: Authenticated user denied on unrecognized path /unknown_collection/doc_1',
        'DENY',
        () => setDoc(doc(authedDb, 'unknown_collection/doc_1'), { foo: 'bar' })
      );
      await verifyRule(
        'SEC-COLL-03b: Even Admin denied on unrecognized path /unknown_collection/doc_1',
        'DENY',
        () => setDoc(doc(adminDb, 'unknown_collection/doc_1'), { foo: 'bar' })
      );
    });
  });
});
