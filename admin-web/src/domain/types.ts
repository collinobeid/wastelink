/**
 * Canonical domain types for WasteLink Admin Web.
 * Strictly aligned with Firestore Security Rules and Android :core:domain models.
 */

export type UserRole = 'resident' | 'collector' | 'admin';

export type AccountStatus = 'ACTIVE' | 'RESTRICTED' | 'SUSPENDED' | 'BANNED';

export type OnboardingStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

export type CollectorVerificationStatus =
  | 'NOT_APPLICABLE'
  | 'NOT_SUBMITTED'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED';

export type RequestLifecycleStatus =
  | 'PENDING_DISPATCH'
  | 'FLAGGED_FOR_REVIEW'
  | 'ASSIGNED_TO_FLEET'
  | 'EN_ROUTE'
  | 'ARRIVED_AT_LOCATION'
  | 'COLLECTION_VERIFIED'
  | 'IN_TRANSIT_TO_DUMP'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'DISPUTED'
  | 'RESOLVED'
  | 'DISMISSED';

export type VolumeTier = 'small_bag' | 'standard_bin' | 'large_load' | 'truck_load';

export interface GeoCoordinates {
  latitude: number;
  longitude: number;
}

export interface UserProfile {
  id: string;
  uid: string;
  fullName: string;
  name?: string;
  email: string;
  phoneNumber: string;
  phone?: string;
  role: UserRole;
  accountStatus: AccountStatus;
  status?: string;
  statusReason?: string | null;
  suspendedUntilEpochMs?: number | null;
  onboardingStatus: OnboardingStatus;
  isVerified: boolean;
  collectorVerificationStatus: CollectorVerificationStatus;
  collectorRejectionReason?: string | null;
  defaultZoneId?: string | null;
  vehicleType?: string | null;
  vehiclePlateNumber?: string | null;
  preferredLanguage: string;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  fcmToken?: string | null;
}

export interface CollectorSubmission {
  userId: string;
  vehicleType: string;
  vehiclePlateNumber: string;
  licenseNumber: string;
  serviceZoneId: string;
  status: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
  submissionAttempt: number;
  submittedAtEpochMs: number;
  reviewedByUid?: string | null;
  reviewedAtEpochMs?: number | null;
  rejectionReason?: string | null;
  adminNotes?: string | null;
  lastRejectionReason?: string | null;
  lastReviewedByUid?: string | null;
  lastReviewedAtEpochMs?: number | null;
}

export interface WasteRequest {
  id: string;
  clientGeneratedId?: string;
  requestRef?: string | null;
  clientUid: string;
  pickupAddress: string;
  category: string;
  volumeTier: VolumeTier;
  lat: number;
  lng: number;
  geohash?: string;
  serviceZoneId?: string;
  notes?: string;
  gatePhotoUrl?: string | null;
  gatePhotoBase64?: string | null;
  pocPhotoUrl?: string | null;
  pocPhotoBase64?: string | null;
  status: RequestLifecycleStatus;
  assignedTruckId?: string | null;
  assignedCollectorUid?: string | null;
  assignedVehicleId?: string | null;
  assignedAt?: number | null;
  enRouteAt?: number | null;
  arrivedAt?: number | null;
  collectedAt?: number | null;
  completedAt?: number | null;
  failedAt?: number | null;
  failureReason?: string | null;
  failurePhotoUrl?: string | null;
  agreedPrice?: number | null;
  priceTzs?: number | null;
  currency?: string;
  residentPhoneNumber?: string | null;
  collectorPhoneNumber?: string | null;
  disputeReason?: string | null;
  adminResolutionNote?: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface WasteCategory {
  id: string;
  displayName: string;
  isHazardous: boolean;
  requiresSpecialHandling: boolean;
  baseEstimateTzs?: number | null;
  isActive: boolean;
  version: number;
}

export interface ServiceZone {
  zoneId: string;
  name: string;
  minLat: number;
  maxLat: number;
  minLng: number;
  maxLng: number;
  polygonVertices?: GeoCoordinates[];
  isActive: boolean;
}

export interface Dispute {
  id: string;
  requestId: string;
  residentId: string;
  collectorId: string;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'DISMISSED';
  reason: string;
  resolutionOutcome?: string | null;
  resolvedByUid?: string | null;
  resolvedAtEpochMs?: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface AdminActionLog {
  id: string;
  adminId?: string;
  actorId?: string;
  targetId?: string;
  targetUserId?: string;
  action: string;
  reason: string;
  title: string;
  description: string;
  iconType?: string;
  timestampMs: number;
}

export interface DashboardMetrics {
  totalResidents: number;
  activeCollectors: number;
  pendingCollectorVetting: number;
  pendingRequests: number;
  activeFleetInTransit: number;
  completedCollections: number;
  openDisputes: number;
  systemHealth: string;
}
