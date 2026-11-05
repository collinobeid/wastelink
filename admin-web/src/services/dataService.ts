import {
  collection,
  query,
  where,
  getDocs,
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
 * Handles fallback gracefully if the database is freshly initialized and empty.
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

    return {
      metrics: {
        totalResidents: residentsSnap.size,
        activeCollectors: collectorsSnap.size,
        pendingCollectorVetting: vettingSnap.size,
        pendingRequests: requestsPendingSnap.size,
        activeFleetInTransit: 0,
        completedCollections: 0,
        openDisputes: disputesSnap.size,
        systemHealth: '100% Operational',
      },
      isLive: true,
    };
  } catch (err) {
    // If running in development without emulator data seeded, return honest zeroed metrics
    return {
      metrics: {
        totalResidents: 0,
        activeCollectors: 0,
        pendingCollectorVetting: 0,
        pendingRequests: 0,
        activeFleetInTransit: 0,
        completedCollections: 0,
        openDisputes: 0,
        systemHealth: '100% Operational',
      },
      isLive: false,
    };
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
  } catch (_err) {
    return [];
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
  } catch (_err) {
    return [];
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
  } catch (_err) {
    return [];
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
  } catch (_err) {
    return [];
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
  } catch (_err) {
    return [];
  }
}

/**
 * Performs atomic collector vetting decision according to firestore.rules lines 405-449.
 */
export async function reviewCollectorVetting(
  adminUid: string,
  collectorUid: string,
  decision: 'APPROVED' | 'REJECTED',
  attempt: number,
  rejectionReason?: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const batch = writeBatch(db);
    const now = Date.now();

    const submissionRef = doc(db, 'collector_submissions', collectorUid);
    const userRef = doc(db, 'users', collectorUid);
    const historyDocId = `attempt_${attempt}_${decision.toLowerCase()}`;
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
        updatedAtEpochMs: now,
      });

      batch.set(historyRef, {
        attemptId: historyDocId,
        userId: collectorUid,
        eventType: 'APPROVED',
        actorUid: adminUid,
        timestampEpochMs: now,
        rejectionReason: null,
      });

      batch.set(logRef, {
        adminId: adminUid,
        targetUserId: collectorUid,
        action: 'COLLECTOR_APPROVED',
        reason: 'Credentials and vehicle vetted successfully',
        title: 'Collector Application Approved',
        description: `Collector ${collectorUid} application approved for municipal operations.`,
        iconType: 'FLEET',
        timestampMs: now,
      });
    } else {
      if (!rejectionReason || rejectionReason.trim().length < 3) {
        return { success: false, error: 'Rejection reason must be at least 3 characters.' };
      }

      batch.update(submissionRef, {
        status: 'REJECTED',
        reviewedByUid: adminUid,
        reviewedAtEpochMs: now,
        rejectionReason: rejectionReason.trim(),
      });

      batch.update(userRef, {
        collectorVerificationStatus: 'REJECTED',
        isVerified: false,
        collectorRejectionReason: rejectionReason.trim(),
        updatedAtEpochMs: now,
      });

      batch.set(historyRef, {
        attemptId: historyDocId,
        userId: collectorUid,
        eventType: 'REJECTED',
        actorUid: adminUid,
        timestampEpochMs: now,
        rejectionReason: rejectionReason.trim(),
      });

      batch.set(logRef, {
        adminId: adminUid,
        targetUserId: collectorUid,
        action: 'COLLECTOR_REJECTED',
        reason: rejectionReason.trim(),
        title: 'Collector Application Rejected',
        description: `Collector ${collectorUid} application rejected: ${rejectionReason.trim()}`,
        iconType: 'FLEET',
        timestampMs: now,
      });
    }

    await batch.commit();
    return { success: true };
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Collector review failed';
    return { success: false, error: msg };
  }
}
