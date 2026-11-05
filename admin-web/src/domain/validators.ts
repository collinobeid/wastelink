import { GeoCoordinates, ServiceZone, RequestLifecycleStatus } from './types';

/**
 * Checks whether coordinates fall within an axis-aligned bounding box.
 */
export function isPointInBoundingBox(pt: GeoCoordinates, zone: ServiceZone): boolean {
  return (
    pt.latitude >= zone.minLat &&
    pt.latitude <= zone.maxLat &&
    pt.longitude >= zone.minLng &&
    pt.longitude <= zone.maxLng
  );
}

/**
 * Checks whether point [pt] lies directly on the segment between [v1] and [v2].
 */
export function isPointOnSegment(
  pt: GeoCoordinates,
  v1: GeoCoordinates,
  v2: GeoCoordinates,
  epsilon = 1e-9
): boolean {
  const minLat = Math.min(v1.latitude, v2.latitude) - epsilon;
  const maxLat = Math.max(v1.latitude, v2.latitude) + epsilon;
  const minLng = Math.min(v1.longitude, v2.longitude) - epsilon;
  const maxLng = Math.max(v1.longitude, v2.longitude) + epsilon;

  if (pt.latitude < minLat || pt.latitude > maxLat || pt.longitude < minLng || pt.longitude > maxLng) {
    return false;
  }

  // Cross product determines collinearity
  const crossProduct =
    (pt.longitude - v1.longitude) * (v2.latitude - v1.latitude) -
    (pt.latitude - v1.latitude) * (v2.longitude - v1.longitude);

  return Math.abs(crossProduct) <= epsilon;
}

/**
 * Boundary-inclusive 2D polygon containment verification using Jordan Curve Theorem (Ray Casting).
 * Aligned with Android pure Kotlin ServiceZone.isPointInPolygon.
 */
export function isPointInPolygon(
  pt: GeoCoordinates,
  vertices: GeoCoordinates[],
  epsilon = 1e-9
): boolean {
  if (vertices.length < 3) return false;

  // Step 1: Boundary check
  let j = vertices.length - 1;
  for (let i = 0; i < vertices.length; i++) {
    if (isPointOnSegment(pt, vertices[j], vertices[i], epsilon)) {
      return true;
    }
    j = i;
  }

  // Step 2: Ray casting
  let inside = false;
  j = vertices.length - 1;
  for (let i = 0; i < vertices.length; i++) {
    const vi = vertices[i];
    const vj = vertices[j];
    const xi = vi.longitude;
    const yi = vi.latitude;
    const xj = vj.longitude;
    const yj = vj.latitude;

    const intersect =
      yi > pt.latitude !== yj > pt.latitude &&
      pt.longitude < ((xj - xi) * (pt.latitude - yi)) / (yj - yi) + xi;

    if (intersect) {
      inside = !inside;
    }
    j = i;
  }

  return inside;
}

/**
 * Verifies whether coordinates fall within a configured ServiceZone.
 */
export function isCoordinatesInZone(pt: GeoCoordinates, zone: ServiceZone): boolean {
  if (!zone.isActive) return false;
  if (!isPointInBoundingBox(pt, zone)) return false;

  if (!zone.polygonVertices || zone.polygonVertices.length < 3) {
    return true; // Bounding-box mode
  }

  return isPointInPolygon(pt, zone.polygonVertices);
}

/**
 * Permitted operational state transitions for Administrators.
 * Aligned with RequestStateTransitionValidator and firestore.rules lines 705–723.
 */
export function isValidAdminRequestTransition(
  current: RequestLifecycleStatus,
  target: RequestLifecycleStatus
): boolean {
  if (current === target) return false;

  switch (target) {
    case 'ASSIGNED_TO_FLEET':
      return current === 'PENDING_DISPATCH' || current === 'FLAGGED_FOR_REVIEW';
    case 'FLAGGED_FOR_REVIEW':
      return current === 'PENDING_DISPATCH';
    case 'PENDING_DISPATCH':
      return current === 'FLAGGED_FOR_REVIEW' || current === 'ASSIGNED_TO_FLEET';
    case 'CANCELLED':
      return (
        current === 'PENDING_DISPATCH' ||
        current === 'FLAGGED_FOR_REVIEW' ||
        current === 'ASSIGNED_TO_FLEET'
      );
    case 'RESOLVED':
    case 'DISMISSED':
      return current === 'DISPUTED';
    default:
      return false;
  }
}
