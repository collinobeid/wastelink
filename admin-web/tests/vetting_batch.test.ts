import { describe, it, expect, vi, beforeEach } from 'vitest';
import { reviewCollectorVetting } from '../src/services/dataService';
import * as Firestore from 'firebase/firestore';

vi.mock('firebase/firestore', async () => {
  const actual = await vi.importActual<typeof Firestore>('firebase/firestore');
  return {
    ...actual,
    doc: vi.fn((collOrDb, ...parts) => {
      if (parts.length === 0 && collOrDb && typeof collOrDb === 'object' && 'path' in collOrDb) {
        return { path: `${collOrDb.path}/auto-id`, id: 'auto-id' };
      }
      return { path: parts.join('/'), id: parts[parts.length - 1] };
    }),
    collection: vi.fn((_db, path) => ({ path, id: 'mock-log-id' })),
    getDoc: vi.fn(),
    writeBatch: vi.fn(),
  };
});

describe('Collector Vetting Batch Remediation Suite', () => {
  let mockBatch: {
    update: ReturnType<typeof vi.fn>;
    set: ReturnType<typeof vi.fn>;
    commit: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    vi.clearAllMocks();

    mockBatch = {
      update: vi.fn(),
      set: vi.fn(),
      commit: vi.fn().mockResolvedValue(undefined),
    };
    vi.mocked(Firestore.writeBatch).mockReturnValue(mockBatch as any);
  });

  // Test 7: Collector approval batch satisfies all required Firestore history fields
  it('7. Collector approval batch satisfies all required Firestore history fields', async () => {
    const adminUid = 'admin_vetter_1';
    const collectorUid = 'col_123';
    const attempt = 1;

    vi.mocked(Firestore.getDoc).mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        userId: collectorUid,
        status: 'PENDING_REVIEW',
        submissionAttempt: 1,
        vehicleType: 'Compactor Truck',
        vehiclePlateNumber: 'T 123 ABC',
        licenseNumber: 'TZ-DL-9999',
        serviceZoneId: 'zone_central',
        submittedAtEpochMs: 1730000000000,
      }),
    } as any);

    const result = await reviewCollectorVetting(adminUid, collectorUid, 'APPROVED', attempt);

    expect(result.success).toBe(true);

    // Verify atomic submission update
    expect(mockBatch.update).toHaveBeenCalledWith(
      expect.objectContaining({ path: 'collector_submissions/col_123' }),
      expect.objectContaining({
        status: 'APPROVED',
        reviewedByUid: adminUid,
        rejectionReason: null,
      })
    );

    // Verify atomic user update
    expect(mockBatch.update).toHaveBeenCalledWith(
      expect.objectContaining({ path: 'users/col_123' }),
      expect.objectContaining({
        collectorVerificationStatus: 'APPROVED',
        isVerified: true,
        collectorRejectionReason: null,
      })
    );

    // Verify history document satisfies all required fields including submissionAttempt
    expect(mockBatch.set).toHaveBeenCalledWith(
      expect.objectContaining({ path: 'collector_submissions/col_123/history/attempt_1_approved' }),
      expect.objectContaining({
        attemptId: 'attempt_1_approved',
        userId: collectorUid,
        eventType: 'APPROVED',
        submissionAttempt: 1, // MANDATORY requirement for firestore.rules
        actorUid: adminUid,
        rejectionReason: null,
        vehicleType: 'Compactor Truck',
        vehiclePlateNumber: 'T 123 ABC',
        licenseNumber: 'TZ-DL-9999',
        serviceZoneId: 'zone_central',
      })
    );

    // Verify append-only audit log creation
    expect(mockBatch.set).toHaveBeenCalledWith(
      expect.objectContaining({ path: 'admin_actions_log/auto-id' }),
      expect.objectContaining({
        adminId: adminUid,
        actorId: adminUid,
        targetUserId: collectorUid,
        action: 'COLLECTOR_APPROVED',
      })
    );

    expect(mockBatch.commit).toHaveBeenCalledTimes(1);
  });

  // Test 8: Collector rejection batch satisfies all required Firestore history fields and reason validation
  it('8. Collector rejection batch satisfies all required Firestore history fields and rejection reason validation', async () => {
    const adminUid = 'admin_vetter_1';
    const collectorUid = 'col_456';
    const attempt = 2;

    // Subcase 8a: Rejection reason too short or missing fails validation without batch write
    const invalidResult = await reviewCollectorVetting(adminUid, collectorUid, 'REJECTED', attempt, 'no');
    expect(invalidResult.success).toBe(false);
    expect(invalidResult.error).toMatch(/between 3 and 500 characters/i);
    expect(mockBatch.commit).not.toHaveBeenCalled();

    // Subcase 8b: Valid rejection reason writes atomic batch
    vi.mocked(Firestore.getDoc).mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        userId: collectorUid,
        status: 'PENDING_REVIEW',
        submissionAttempt: 2,
        vehicleType: 'Tricycle',
        vehiclePlateNumber: 'T 999 XYZ',
        licenseNumber: 'TZ-DL-7777',
        serviceZoneId: 'zone_north',
        submittedAtEpochMs: 1730000000000,
      }),
    } as any);

    const validReason = 'Driver license document is illegible and expired.';
    const validResult = await reviewCollectorVetting(adminUid, collectorUid, 'REJECTED', attempt, validReason);

    expect(validResult.success).toBe(true);

    // Verify submission update has rejectionReason
    expect(mockBatch.update).toHaveBeenCalledWith(
      expect.objectContaining({ path: 'collector_submissions/col_456' }),
      expect.objectContaining({
        status: 'REJECTED',
        reviewedByUid: adminUid,
        rejectionReason: validReason,
      })
    );

    // Verify user profile update has rejection status and reason
    expect(mockBatch.update).toHaveBeenCalledWith(
      expect.objectContaining({ path: 'users/col_456' }),
      expect.objectContaining({
        collectorVerificationStatus: 'REJECTED',
        isVerified: false,
        collectorRejectionReason: validReason,
      })
    );

    // Verify history document includes submissionAttempt and exact rejectionReason
    expect(mockBatch.set).toHaveBeenCalledWith(
      expect.objectContaining({ path: 'collector_submissions/col_456/history/attempt_2_rejected' }),
      expect.objectContaining({
        attemptId: 'attempt_2_rejected',
        userId: collectorUid,
        eventType: 'REJECTED',
        submissionAttempt: 2,
        actorUid: adminUid,
        rejectionReason: validReason,
      })
    );

    expect(mockBatch.commit).toHaveBeenCalledTimes(1);
  });

  // Test 9: Stale submission attempt fails safely
  it('9. Stale submission attempt fails safely without committing mutations', async () => {
    const adminUid = 'admin_vetter_1';
    const collectorUid = 'col_789';

    // Server record is currently on attempt #3 (collector already resubmitted)
    vi.mocked(Firestore.getDoc).mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        userId: collectorUid,
        status: 'PENDING_REVIEW',
        submissionAttempt: 3,
      }),
    } as any);

    // Admin UI attempted to review based on stale cached attempt #1
    const result = await reviewCollectorVetting(adminUid, collectorUid, 'APPROVED', 1);

    expect(result.success).toBe(false);
    expect(result.error).toMatch(/Stale submission attempt/i);
    expect(mockBatch.commit).not.toHaveBeenCalled();

    // Non-integer attempt fails immediately
    const invalidAttemptResult = await reviewCollectorVetting(adminUid, collectorUid, 'APPROVED', -1);
    expect(invalidAttemptResult.success).toBe(false);
    expect(invalidAttemptResult.error).toMatch(/Invalid submission attempt/i);
  });
});
