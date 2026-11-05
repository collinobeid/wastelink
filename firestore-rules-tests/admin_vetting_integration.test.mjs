import { before, after, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc,
  setDoc,
  collection,
  writeBatch,
  setLogLevel,
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
    ...extraClaims,
  });
}

function buildValidCollectorDoc(uid) {
  return {
    id: uid,
    uid: uid,
    fullName: 'Juma Mwambao',
    name: 'Juma Mwambao',
    email: 'collector@wastelink.go.tz',
    phoneNumber: '+255712999888',
    phone: '+255712999888',
    role: 'collector',
    accountStatus: 'ACTIVE',
    status: 'ACTIVE',
    statusReason: null,
    suspendedUntilEpochMs: null,
    onboardingStatus: 'COMPLETED',
    isVerified: false,
    collectorVerificationStatus: 'PENDING_REVIEW',
    collectorRejectionReason: null,
    defaultZoneId: 'zone_kinondoni',
    vehicleType: 'Compactor Truck',
    vehiclePlateNumber: 'T 123 ABC',
    preferredLanguage: 'sw',
    createdAtEpochMs: 1730000000000,
    updatedAtEpochMs: 1730000000000,
  };
}

function buildValidSubmissionDoc(uid, attempt = 1) {
  return {
    userId: uid,
    vehicleType: 'Compactor Truck',
    vehiclePlateNumber: 'T 123 ABC',
    licenseNumber: 'TZ-DL-998877',
    serviceZoneId: 'zone_kinondoni',
    status: 'PENDING_REVIEW',
    submissionAttempt: attempt,
    submittedAtEpochMs: 1730000100000,
    reviewedByUid: null,
    reviewedAtEpochMs: null,
    rejectionReason: null,
    adminNotes: null,
    lastRejectionReason: null,
    lastReviewedByUid: null,
    lastReviewedAtEpochMs: null,
  };
}

describe('WasteLink Admin Web — Collector Vetting Security Rules Integration Suite', () => {
  before(async () => {
    setLogLevel('silent');
    const rules = fs.readFileSync(RULES_PATH, 'utf8');
    testEnv = await initializeTestEnvironment({
      projectId: 'demo-wastelink',
      firestore: {
        rules,
        host: '127.0.0.1',
        port: 8080,
      },
    });
  });

  after(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();

    // Seed baseline collector profile and pending submission
    await seedWithRulesDisabled(async (db) => {
      await setDoc(doc(db, 'users/col_target'), buildValidCollectorDoc('col_target'));
      await setDoc(doc(db, 'collector_submissions/col_target'), buildValidSubmissionDoc('col_target', 1));
    });
  });

  // Test 7 (Emulator integration): Complete approval batch with submissionAttempt in history succeeds
  it('SEC-ADMIN-VET-01: Admin atomic approval batch with submissionAttempt in history succeeds against firestore.rules', async () => {
    const adminDb = userContext('admin_auditor', 'admin@wastelink.go.tz', true, { admin: true }).firestore();
    const now = 1730000500000;
    const batch = writeBatch(adminDb);

    // 1. Update collector submission
    batch.update(doc(adminDb, 'collector_submissions/col_target'), {
      status: 'APPROVED',
      reviewedByUid: 'admin_auditor',
      reviewedAtEpochMs: now,
      rejectionReason: null,
    });

    // 2. Update user profile
    batch.update(doc(adminDb, 'users/col_target'), {
      collectorVerificationStatus: 'APPROVED',
      isVerified: true,
      status: 'ACTIVE',
    });

    // 3. Create history document satisfying firestore.rules lines 101-108 and 480-498
    batch.set(doc(adminDb, 'collector_submissions/col_target/history/attempt_1_approved'), {
      attemptId: 'attempt_1_approved',
      userId: 'col_target',
      eventType: 'APPROVED',
      vehicleType: 'Compactor Truck',
      vehiclePlateNumber: 'T 123 ABC',
      licenseNumber: 'TZ-DL-998877',
      serviceZoneId: 'zone_kinondoni',
      submissionAttempt: 1, // MANDATORY: required by hasValidAdminDecisionHistoryAfter
      timestampEpochMs: now,
      actorUid: 'admin_auditor',
      rejectionReason: null,
    });

    // 4. Create admin action log satisfying lines 539-558
    batch.set(doc(collection(adminDb, 'admin_actions_log')), {
      adminId: 'admin_auditor',
      actorId: 'admin_auditor',
      targetUserId: 'col_target',
      targetId: 'col_target',
      action: 'COLLECTOR_APPROVED',
      reason: 'Collector credentials and vehicle vetted successfully',
      title: 'Collector Application Approved',
      description: 'Collector vetted for municipal service zone.',
      iconType: 'FLEET',
      timestampMs: now,
    });

    await assertSucceeds(batch.commit());
  });

  // Test: Incomplete approval batch omitting submissionAttempt in history is DENIED by firestore.rules
  it('SEC-ADMIN-VET-02: Admin approval batch omitting submissionAttempt in history is strictly DENIED by firestore.rules', async () => {
    const adminDb = userContext('admin_auditor', 'admin@wastelink.go.tz', true, { admin: true }).firestore();
    const now = 1730000500000;
    const badBatch = writeBatch(adminDb);

    badBatch.update(doc(adminDb, 'collector_submissions/col_target'), {
      status: 'APPROVED',
      reviewedByUid: 'admin_auditor',
      reviewedAtEpochMs: now,
      rejectionReason: null,
    });

    badBatch.update(doc(adminDb, 'users/col_target'), {
      collectorVerificationStatus: 'APPROVED',
      isVerified: true,
      status: 'ACTIVE',
    });

    // History document OMITTING submissionAttempt (the defect that was fixed)
    badBatch.set(doc(adminDb, 'collector_submissions/col_target/history/attempt_1_approved'), {
      attemptId: 'attempt_1_approved',
      userId: 'col_target',
      eventType: 'APPROVED',
      actorUid: 'admin_auditor',
      timestampEpochMs: now,
      rejectionReason: null,
      // submissionAttempt IS MISSING!
    });

    await assertFails(badBatch.commit());
  });

  // Test 8 (Emulator integration): Complete rejection batch with mandatory rejectionReason succeeds
  it('SEC-ADMIN-VET-03: Admin atomic rejection batch with valid rejectionReason succeeds against firestore.rules', async () => {
    const adminDb = userContext('admin_auditor', 'admin@wastelink.go.tz', true, { admin: true }).firestore();
    const now = 1730000500000;
    const batch = writeBatch(adminDb);
    const reason = 'Vehicle inspection report expired and plate blurry';

    // 1. Update submission
    batch.update(doc(adminDb, 'collector_submissions/col_target'), {
      status: 'REJECTED',
      reviewedByUid: 'admin_auditor',
      reviewedAtEpochMs: now,
      rejectionReason: reason,
    });

    // 2. Update user profile
    batch.update(doc(adminDb, 'users/col_target'), {
      collectorVerificationStatus: 'REJECTED',
      isVerified: false,
      collectorRejectionReason: reason,
      status: 'ACTIVE',
    });

    // 3. Create history document
    batch.set(doc(adminDb, 'collector_submissions/col_target/history/attempt_1_rejected'), {
      attemptId: 'attempt_1_rejected',
      userId: 'col_target',
      eventType: 'REJECTED',
      vehicleType: 'Compactor Truck',
      vehiclePlateNumber: 'T 123 ABC',
      licenseNumber: 'TZ-DL-998877',
      serviceZoneId: 'zone_kinondoni',
      submissionAttempt: 1,
      timestampEpochMs: now,
      actorUid: 'admin_auditor',
      rejectionReason: reason,
    });

    // 4. Create admin action log
    batch.set(doc(collection(adminDb, 'admin_actions_log')), {
      adminId: 'admin_auditor',
      actorId: 'admin_auditor',
      targetUserId: 'col_target',
      targetId: 'col_target',
      action: 'COLLECTOR_REJECTED',
      reason: reason,
      title: 'Collector Application Rejected',
      description: `Collector rejected: ${reason}`,
      iconType: 'FLEET',
      timestampMs: now,
    });

    await assertSucceeds(batch.commit());
  });

  // Test: Rejection with empty rejectionReason is DENIED by firestore.rules
  it('SEC-ADMIN-VET-04: Admin rejection with empty or too-short rejection reason is DENIED by firestore.rules', async () => {
    const adminDb = userContext('admin_auditor', 'admin@wastelink.go.tz', true, { admin: true }).firestore();
    const now = 1730000500000;
    const badBatch = writeBatch(adminDb);

    badBatch.update(doc(adminDb, 'collector_submissions/col_target'), {
      status: 'REJECTED',
      reviewedByUid: 'admin_auditor',
      reviewedAtEpochMs: now,
      rejectionReason: 'no', // Less than 3 characters (invalid)
    });

    badBatch.update(doc(adminDb, 'users/col_target'), {
      collectorVerificationStatus: 'REJECTED',
      isVerified: false,
      collectorRejectionReason: 'no',
      status: 'ACTIVE',
    });

    badBatch.set(doc(adminDb, 'collector_submissions/col_target/history/attempt_1_rejected'), {
      attemptId: 'attempt_1_rejected',
      userId: 'col_target',
      eventType: 'REJECTED',
      submissionAttempt: 1,
      timestampEpochMs: now,
      actorUid: 'admin_auditor',
      rejectionReason: 'no',
    });

    await assertFails(badBatch.commit());
  });

  // Test 9 (Emulator integration): Submission attempt mismatch is DENIED by firestore.rules
  it('SEC-ADMIN-VET-05: Submission attempt mismatch in history document is strictly DENIED by firestore.rules', async () => {
    const adminDb = userContext('admin_auditor', 'admin@wastelink.go.tz', true, { admin: true }).firestore();
    const now = 1730000500000;
    const badBatch = writeBatch(adminDb);

    badBatch.update(doc(adminDb, 'collector_submissions/col_target'), {
      status: 'APPROVED',
      reviewedByUid: 'admin_auditor',
      reviewedAtEpochMs: now,
      rejectionReason: null,
    });

    badBatch.update(doc(adminDb, 'users/col_target'), {
      collectorVerificationStatus: 'APPROVED',
      isVerified: true,
      status: 'ACTIVE',
    });

    // Submission is on attempt 1, but history claims attempt 999
    badBatch.set(doc(adminDb, 'collector_submissions/col_target/history/attempt_999_approved'), {
      attemptId: 'attempt_999_approved',
      userId: 'col_target',
      eventType: 'APPROVED',
      submissionAttempt: 999, // Mismatched! Expected: 1
      timestampEpochMs: now,
      actorUid: 'admin_auditor',
      rejectionReason: null,
    });

    await assertFails(badBatch.commit());
  });
});
