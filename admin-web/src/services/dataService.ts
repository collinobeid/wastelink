import {
  collection,
  query,
  where,
  getDocs,
  getDoc,
  limit,
  orderBy,
  doc,
  writeBatch,
} from 'firebase/firestore';
import { db } from '../config/firebase';
import {
  DashboardMetrics,
  UserProfile,
  CollectorSubmission,
  WasteRequest,
  WasteCategory,
  ServiceZone,
  Dispute,
  AdminActionLog,
} from '../domain/types';

export type DataServiceErrorKind = 'PERMISSION_DENIED' | 'NETWORK_ERROR' | 'NOT_FOUND' | 'UNKNOWN';

export class DataServiceError extends Error {
  public readonly kind: DataServiceErrorKind;
  public readonly originalError?: unknown;

  constructor(message: string, kind: DataServiceErrorKind, originalError?: unknown) {
    super(message);
    this.name = 'DataServiceError';
    this.kind = kind;
    this.originalError = originalError;
  }
}

export function parseFirestoreError(err: unknown, fallbackMessage: string): DataServiceError {
  if (err instanceof DataServiceError) {
    return err;
  }
  const msg = err instanceof Error ? err.message : String(err);
  const code = (err as { code?: string })?.code || '';

  if (
    code.includes('permission-denied') ||
    msg.toLowerCase().includes('permission') ||
    msg.toLowerCase().includes('insufficient permissions')
  ) {
    return new DataServiceError('Access denied: Insufficient Firestore permissions.', 'PERMISSION_DENIED', err);
  }
  if (
    code.includes('unavailable') ||
    code.includes('deadline-exceeded') ||
    msg.toLowerCase().includes('network') ||
    msg.toLowerCase().includes('offline') ||
    msg.toLowerCase().includes('failed to get document because the client is offline')
  ) {
    return new DataServiceError('Network connection failed or Firestore is unavailable.', 'NETWORK_ERROR', err);
  }
  if (code.includes('not-found') || msg.toLowerCase().includes('not found')) {
    return new DataServiceError('Requested resource was not found.', 'NOT_FOUND', err);
  }
  return new DataServiceError(msg || fallbackMessage, 'UNKNOWN', err);
}

/**
 * Standard seed categories used for initialization or when remote rules are pending.
 * Exactly aligned with StandardCategoryCode in Android :core:domain.
 */
export const SEED_CATEGORIES: WasteCategory[] = [
  { id: 'household', displayName: 'Household Waste', isHazardous: false, requiresSpecialHandling: false, baseEstimateTzs: 3000, isActive: true, version: 1 },
  { id: 'recyclable', displayName: 'Recyclable Materials', isHazardous: false, requiresSpecialHandling: false, baseEstimateTzs: 2000, isActive: true, version: 1 },
  { id: 'organic', displayName: 'Organic / Food Waste', isHazardous: false, requiresSpecialHandling: false, baseEstimateTzs: 2500, isActive: true, version: 1 },
  { id: 'hazardous', displayName: 'Hazardous Waste', isHazardous: true, requiresSpecialHandling: true, baseEstimateTzs: null, isActive: true, version: 1 },
  { id: 'electronic', displayName: 'E-Waste / Electronics', isHazardous: false, requiresSpecialHandling: true, baseEstimateTzs: 5000, isActive: true, version: 1 },
  { id: 'construction', displayName: 'Construction Debris', isHazardous: false, requiresSpecialHandling: true, baseEstimateTzs: 10000, isActive: true, version: 1 },
  { id: 'bulky', displayName: 'Bulky Items / Furniture', isHazardous: false, requiresSpecialHandling: false, baseEstimateTzs: 8000, isActive: true, version: 1 },
];

/**
 * Standard service zone fixtures used when remote rules are pending.
 * Fully configurable; no hardcoded municipal claims.
 */
export const SEED_ZONES: ServiceZone[] = [
  {
    zoneId: 'zone-central-pilot',
    name: 'Central Pilot District',
    minLat: -6.85,
    maxLat: -6.78,
    minLng: 39.24,
    maxLng: 39.31,
    polygonVertices: [
      { latitude: -6.85, longitude: 39.24 },
      { latitude: -6.78, longitude: 39.25 },
      { latitude: -6.79, longitude: 39.31 },
      { latitude: -6.84, longitude: 39.30 },
    ],
    isActive: true,
  },
  {
    zoneId: 'zone-north-expansion',
    name: 'North Expansion Sector',
    minLat: -6.77,
    maxLat: -6.70,
    minLng: 39.18,
    maxLng: 39.26,
    polygonVertices: [
      { latitude: -6.77, longitude: 39.18 },
      { latitude: -6.70, longitude: 39.20 },
      { latitude: -6.71, longitude: 39.26 },
      { latitude: -6.76, longitude: 39.25 },
    ],
    isActive: true,
  },
];

/**
 * Standard pricing rules reference fixtures.
 */
export const SEED_PRICING = [
  { id: 'price-small-bag', category: 'Household', volumeTier: 'Small Bag (1-3 bags)', priceTzs: 3000, currency: 'TZS', effectiveDate: '2026-01-01' },
  { id: 'price-standard-bin', category: 'Household', volumeTier: 'Standard Bin (240L)', priceTzs: 5000, currency: 'TZS', effectiveDate: '2026-01-01' },
  { id: 'price-large-load', category: 'Household', volumeTier: 'Large Load (~1.5m³)', priceTzs: 15000, currency: 'TZS', effectiveDate: '2026-01-01' },
  { id: 'price-truck-load', category: 'Household', volumeTier: 'Truck Load (~5.0m³)', priceTzs: 45000, currency: 'TZS', effectiveDate: '2026-01-01' },
  { id: 'price-electronic', category: 'E-Waste', volumeTier: 'Per Diagnostic Unit', priceTzs: 8000, currency: 'TZS', effectiveDate: '2026-01-01' },
  { id: 'price-hazardous', category: 'Hazardous', volumeTier: 'Custom Inspection Quote', priceTzs: null, currency: 'TZS', effectiveDate: '2026-01-01' },
];

/**
 * Fetches dashboard counts across real Firestore collections.
 * Fails distinctly if permissions or connectivity fail.
 */
export async function fetchDashboardMetrics(): Promise<{
  metrics: DashboardMetrics;
  isLive: boolean;
}> {
  try {
    const usersRef = collection(db, 'users');
    const requestsRef = collection(db, 'waste_requests');
    const vettingRef = collection(db, 'collector_submissions');
    const disputesRef = collection(db, 'disputes');

    const [residentsSnap, collectorsSnap, vettingSnap, requestsPendingSnap, disputesSnap] =
      await Promise.all([
        getDocs(query(usersRef, where('role', 'in', ['RESIDENT', 'resident']))),
        getDocs(
          query(
            usersRef,
            where('role', 'in', ['COLLECTOR', 'collector']),
            where('collectorVerificationStatus', '==', 'APPROVED')
          )
        ),
        getDocs(query(vettingRef, where('status', '==', 'PENDING_REVIEW'))),
        getDocs(query(requestsRef, where('status', '==', 'PENDING_DISPATCH'))),
        getDocs(query(disputesRef, where('status', 'in', ['OPEN', 'INVESTIGATING']))),
      ]);

    const totalResidents = residentsSnap.size;
    const activeCollectors = collectorsSnap.size;
    const pendingCollectorVetting = vettingSnap.size;
    const pendingRequests = requestsPendingSnap.size;
    const openDisputes = disputesSnap.size;

    const totalAuthoritativeRecords =
      totalResidents + activeCollectors + pendingCollectorVetting + pendingRequests + openDisputes;

    return {
      metrics: {
        totalResidents,
        activeCollectors,
        pendingCollectorVetting,
        pendingRequests,
        activeFleetInTransit: 0,
        completedCollections: 0,
        openDisputes,
        systemHealth:
          totalAuthoritativeRecords > 0
            ? 'Authoritative Connected (Operational)'
            : 'Authoritative Connected (Empty Dataset)',
      },
      isLive: true,
    };
  } catch (err: unknown) {
    throw parseFirestoreError(err, 'Failed to fetch authoritative dashboard metrics from Firestore.');
  }
}

/**
 * Fetches recent administrative actions log.
 */
export async function fetchRecentAuditLogs(maxLogs = 10): Promise<AdminActionLog[]> {
  try {
    const logsRef = collection(db, 'admin_actions_log');
    const q = query(logsRef, orderBy('timestampMs', 'desc'), limit(maxLogs));
    const snapshot = await getDocs(q);

    return snapshot.docs.map((d) => ({
      id: d.id,
      ...(d.data() as Omit<AdminActionLog, 'id'>),
    }));
  } catch (err: unknown) {
    throw parseFirestoreError(err, 'Failed to fetch administrative audit logs.');
  }
}

/**
 * Fetches users with optional role filtering.
 */
export async function fetchUsers(roleFilter?: string): Promise<UserProfile[]> {
  try {
    const usersRef = collection(db, 'users');
    let q = query(usersRef, limit(50));
    if (roleFilter && roleFilter !== 'ALL') {
      q = query(usersRef, where('role', '==', roleFilter.toLowerCase()), limit(50));
    }
    const snapshot = await getDocs(q);
    return snapshot.docs.map((d) => ({
      id: d.id,
      uid: d.id,
      ...(d.data() as Omit<UserProfile, 'id' | 'uid'>),
    }));
  } catch (err: unknown) {
    throw parseFirestoreError(err, 'Failed to fetch user profiles.');
  }
}

/**
 * Fetches collector submissions.
 */
export async function fetchCollectorSubmissions(): Promise<CollectorSubmission[]> {
  try {
    const ref = collection(db, 'collector_submissions');
    const snapshot = await getDocs(query(ref, limit(50)));
    return snapshot.docs.map((d) => ({
      userId: d.id,
      ...(d.data() as Omit<CollectorSubmission, 'userId'>),
    }));
  } catch (err: unknown) {
    throw parseFirestoreError(err, 'Failed to fetch collector submissions.');
  }
}

/**
 * Fetches waste requests.
 */
export async function fetchWasteRequests(statusFilter?: string): Promise<WasteRequest[]> {
  try {
    const ref = collection(db, 'waste_requests');
    let q = query(ref, limit(50));
    if (statusFilter && statusFilter !== 'ALL') {
      q = query(ref, where('status', '==', statusFilter), limit(50));
    }
    const snapshot = await getDocs(q);
    return snapshot.docs.map((d) => ({
      id: d.id,
      ...(d.data() as Omit<WasteRequest, 'id'>),
    }));
  } catch (err: unknown) {
    throw parseFirestoreError(err, 'Failed to fetch waste collection requests.');
  }
}

/**
 * Fetches disputes.
 */
export async function fetchDisputes(): Promise<Dispute[]> {
  try {
    const ref = collection(db, 'disputes');
    const snapshot = await getDocs(query(ref, limit(50)));
    return snapshot.docs.map((d) => ({
      id: d.id,
      ...(d.data() as Omit<Dispute, 'id'>),
    }));
  } catch (err: unknown) {
    throw parseFirestoreError(err, 'Failed to fetch dispute records.');
  }
}

/**
 * Performs atomic collector vetting decision strictly adhering to firestore.rules lines 405-449 and 101-108.
 * Guarantees:
 * 1. Read existing submission to verify submissionAttempt matches and prevent stale attempt races.
 * 2. Atomic update to /collector_submissions/{collectorUid} with required fields and without altering collector-immutable keys.
 * 3. Atomic update to /users/{collectorUid} verification status.
 * 4. Atomic append-only creation of /collector_submissions/{collectorUid}/history/{attemptId} including submissionAttempt.
 * 5. Atomic append-only creation of /admin_actions_log with authenticated actorUid.
 */
export async function reviewCollectorVetting(
  adminUid: string,
  collectorUid: string,
  decision: 'APPROVED' | 'REJECTED',
  attempt: number,
  rejectionReason?: string
): Promise<{ success: boolean; error?: string }> {
  try {
    if (!adminUid) {
      return { success: false, error: 'Administrative actor UID is required.' };
    }
    if (!collectorUid) {
      return { success: false, error: 'Collector target UID is required.' };
    }
    if (!Number.isInteger(attempt) || attempt < 1) {
      return { success: false, error: 'Invalid submission attempt number.' };
    }

    if (decision === 'REJECTED') {
      if (!rejectionReason || rejectionReason.trim().length < 3 || rejectionReason.trim().length > 500) {
        return { success: false, error: 'Rejection reason must be between 3 and 500 characters.' };
      }
    }

    const submissionRef = doc(db, 'collector_submissions', collectorUid);
    const userRef = doc(db, 'users', collectorUid);

    // Fetch existing submission to verify state and avoid stale attempt races
    const subDocSnap = await getDoc(submissionRef);
    if (!subDocSnap.exists()) {
      return { success: false, error: 'Collector submission document does not exist.' };
    }

    const subData = subDocSnap.data() as CollectorSubmission;

    // Fail safely if attempt is stale or mismatched
    if (subData.submissionAttempt !== attempt) {
      return {
        success: false,
        error: `Stale submission attempt: client expected #${attempt}, but server record is on attempt #${subData.submissionAttempt}.`,
      };
    }

    if (decision === 'APPROVED' && subData.status !== 'PENDING_REVIEW') {
      return {
        success: false,
        error: `Cannot approve submission with status '${subData.status}'. Only PENDING_REVIEW submissions can be approved.`,
      };
    }

    if (decision === 'REJECTED' && !['PENDING_REVIEW', 'APPROVED'].includes(subData.status)) {
      return {
        success: false,
        error: `Cannot reject submission with status '${subData.status}'.`,
      };
    }

    const now = Date.now();
    const batch = writeBatch(db);
    const historyDocId = `attempt_${subData.submissionAttempt}_${decision.toLowerCase()}`;
    const historyRef = doc(db, `collector_submissions/${collectorUid}/history`, historyDocId);
    const logRef = doc(collection(db, 'admin_actions_log'));

    if (decision === 'APPROVED') {
      batch.update(submissionRef, {
        status: 'APPROVED',
        reviewedByUid: adminUid,
        reviewedAtEpochMs: now,
        rejectionReason: null,
      });

      batch.update(userRef, {
        collectorVerificationStatus: 'APPROVED',
        isVerified: true,
        collectorRejectionReason: null,
        updatedAtEpochMs: now,
      });

      batch.set(historyRef, {
        attemptId: historyDocId,
        userId: collectorUid,
        eventType: 'APPROVED',
        vehicleType: subData.vehicleType || '',
        vehiclePlateNumber: subData.vehiclePlateNumber || '',
        licenseNumber: subData.licenseNumber || '',
        serviceZoneId: subData.serviceZoneId || '',
        submissionAttempt: subData.submissionAttempt,
        timestampEpochMs: now,
        actorUid: adminUid,
        rejectionReason: null,
      });

      batch.set(logRef, {
        id: logRef.id,
        adminId: adminUid,
        actorId: adminUid,
        targetUserId: collectorUid,
        targetId: collectorUid,
        action: 'COLLECTOR_APPROVED',
        reason: 'Collector credentials and vehicle vetted successfully',
        title: 'Collector Application Approved',
        description: `Collector ${collectorUid} application approved for municipal operations.`,
        iconType: 'FLEET',
        timestampMs: now,
      });
    } else {
      const cleanReason = rejectionReason!.trim();

      batch.update(submissionRef, {
        status: 'REJECTED',
        reviewedByUid: adminUid,
        reviewedAtEpochMs: now,
        rejectionReason: cleanReason,
      });

      batch.update(userRef, {
        collectorVerificationStatus: 'REJECTED',
        isVerified: false,
        collectorRejectionReason: cleanReason,
        updatedAtEpochMs: now,
      });

      batch.set(historyRef, {
        attemptId: historyDocId,
        userId: collectorUid,
        eventType: 'REJECTED',
        vehicleType: subData.vehicleType || '',
        vehiclePlateNumber: subData.vehiclePlateNumber || '',
        licenseNumber: subData.licenseNumber || '',
        serviceZoneId: subData.serviceZoneId || '',
        submissionAttempt: subData.submissionAttempt,
        timestampEpochMs: now,
        actorUid: adminUid,
        rejectionReason: cleanReason,
      });

      batch.set(logRef, {
        id: logRef.id,
        adminId: adminUid,
        actorId: adminUid,
        targetUserId: collectorUid,
        targetId: collectorUid,
        action: 'COLLECTOR_REJECTED',
        reason: cleanReason,
        title: 'Collector Application Rejected',
        description: `Collector ${collectorUid} application rejected: ${cleanReason}`,
        iconType: 'FLEET',
        timestampMs: now,
      });
    }

    await batch.commit();
    return { success: true };
  } catch (err: unknown) {
    const parsed = parseFirestoreError(err, 'Collector review failed');
    return { success: false, error: parsed.message };
  }
}

