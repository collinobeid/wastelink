import { describe, it, expect } from 'vitest';
import {
  isPointInBoundingBox,
  isPointOnSegment,
  isPointInPolygon,
  isCoordinatesInZone,
  isValidAdminRequestTransition,
} from '../src/domain/validators';
import { ServiceZone } from '../src/domain/types';

describe('Spatial Containment & Boundary Validators', () => {
  const testZone: ServiceZone = {
    zoneId: 'test-zone-1',
    name: 'Municipal Test District',
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
  };

  it('correctly evaluates points inside and outside axis-aligned bounding box', () => {
    // Inside bounding box
    expect(isPointInBoundingBox({ latitude: -6.81, longitude: 39.27 }, testZone)).toBe(true);
    // Outside latitude limits
    expect(isPointInBoundingBox({ latitude: -6.90, longitude: 39.27 }, testZone)).toBe(false);
    expect(isPointInBoundingBox({ latitude: -6.70, longitude: 39.27 }, testZone)).toBe(false);
    // Outside longitude limits
    expect(isPointInBoundingBox({ latitude: -6.81, longitude: 39.20 }, testZone)).toBe(false);
    expect(isPointInBoundingBox({ latitude: -6.81, longitude: 39.35 }, testZone)).toBe(false);
  });

  it('confirms collinearity on polygon edge segments', () => {
    const v1 = { latitude: 0.0, longitude: 0.0 };
    const v2 = { latitude: 10.0, longitude: 10.0 };
    // Exact midpoint
    expect(isPointOnSegment({ latitude: 5.0, longitude: 5.0 }, v1, v2)).toBe(true);
    // Non-collinear point
    expect(isPointOnSegment({ latitude: 5.0, longitude: 5.5 }, v1, v2)).toBe(false);
    // Point beyond segment
    expect(isPointOnSegment({ latitude: 15.0, longitude: 15.0 }, v1, v2)).toBe(false);
  });

  it('evaluates boundary-inclusive Jordan curve polygon containment', () => {
    // Interior point
    expect(isPointInPolygon({ latitude: -6.81, longitude: 39.27 }, testZone.polygonVertices!)).toBe(true);
    // Vertex point (boundary inclusive)
    expect(isPointInPolygon({ latitude: -6.85, longitude: 39.24 }, testZone.polygonVertices!)).toBe(true);
    // Clearly exterior point (within AABB but outside polygon wedge)
    expect(isPointInPolygon({ latitude: -6.781, longitude: 39.309 }, testZone.polygonVertices!)).toBe(false);
  });

  it('rejects coordinates when service zone is deactivated', () => {
    const inactiveZone: ServiceZone = { ...testZone, isActive: false };
    expect(isCoordinatesInZone({ latitude: -6.81, longitude: 39.27 }, inactiveZone)).toBe(false);
  });
});

describe('Admin Operational Transition Guards', () => {
  it('permits authorized admin transitions', () => {
    expect(isValidAdminRequestTransition('PENDING_DISPATCH', 'ASSIGNED_TO_FLEET')).toBe(true);
    expect(isValidAdminRequestTransition('PENDING_DISPATCH', 'FLAGGED_FOR_REVIEW')).toBe(true);
    expect(isValidAdminRequestTransition('FLAGGED_FOR_REVIEW', 'PENDING_DISPATCH')).toBe(true);
    expect(isValidAdminRequestTransition('PENDING_DISPATCH', 'CANCELLED')).toBe(true);
    expect(isValidAdminRequestTransition('ASSIGNED_TO_FLEET', 'CANCELLED')).toBe(true);
    expect(isValidAdminRequestTransition('DISPUTED', 'RESOLVED')).toBe(true);
    expect(isValidAdminRequestTransition('DISPUTED', 'DISMISSED')).toBe(true);
  });

  it('blocks unauthorized and illegal state transitions', () => {
    // Self-transition is prohibited
    expect(isValidAdminRequestTransition('PENDING_DISPATCH', 'PENDING_DISPATCH')).toBe(false);
    // Terminal state cannot be cancelled
    expect(isValidAdminRequestTransition('COMPLETED', 'CANCELLED')).toBe(false);
    // Admin cannot mark en-route directly to completed without hauling transit
    expect(isValidAdminRequestTransition('EN_ROUTE', 'COMPLETED')).toBe(false);
    // Cannot arbitrate undisputed request
    expect(isValidAdminRequestTransition('PENDING_DISPATCH', 'RESOLVED')).toBe(false);
  });
});
