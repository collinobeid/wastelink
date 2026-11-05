package com.waste.link.domain.model

/**
 * Domain exception thrown when an unrecognized waste category ID is queried.
 * Strict domain invariant: Silent fallback to any default category is strictly prohibited.
 */
class UnknownWasteCategoryException(message: String) : IllegalArgumentException(message)

/**
 * Domain exception thrown when attempting to select or process a waste category that has been deactivated.
 */
class InactiveCategoryException(message: String) : IllegalStateException(message)

/**
 * Domain exception thrown when an unrecognized volume tier ID is queried.
 * Strict domain invariant: Silent fallback to any default volume tier is strictly prohibited.
 */
class UnknownVolumeTierException(message: String) : IllegalArgumentException(message)

/**
 * Domain exception thrown when geographic coordinates fall outside standard WGS 84 bounds.
 */
class InvalidCoordinatesException(message: String) : IllegalArgumentException(message)

/**
 * Domain exception thrown when coordinates fall outside any active municipal service zone.
 */
class OutsideServiceZoneException(message: String) : IllegalStateException(message)

/**
 * Domain exception thrown when a request lifecycle status transition is illegal or out of sequence.
 */
class InvalidStateTransitionException(message: String) : IllegalStateException(message)

/**
 * Domain exception thrown when an actor (Resident, Collector, Admin) attempts an operation
 * or state transition for which their authenticated role or ownership claim is unauthorized.
 */
class UnauthorizedTransitionException(message: String) : SecurityException(message)

/**
 * Domain exception thrown when a collector attempts to transition to COLLECTION_VERIFIED
 * without supplying mandatory Proof of Collection (PoC) photographic evidence.
 */
class MissingProofOfCollectionException(message: String) : IllegalStateException(message)

/**
 * Domain exception thrown when a collector transitions to FAILED without mandatory failure evidence
 * (photo and descriptive reason).
 */
class MissingFailureEvidenceException(message: String) : IllegalStateException(message)

/**
 * Domain exception thrown when a resident attempts to cancel a request that has already been
 * assigned to fleet or transitioned beyond PENDING_DISPATCH.
 */
class CancellationLockedException(message: String) : IllegalStateException(message)

/**
 * Domain exception thrown when a resident attempts to file a dispute after the 48-hour
 * grievance window has elapsed since completion or failure.
 */
class DisputeWindowExpiredException(message: String) : IllegalStateException(message)

/**
 * Domain exception thrown when standard instant quotation is requested for hazardous waste.
 * Hazardous waste requires special municipal inspection and dispatcher quotation.
 */
class HazardousQuotationRequiredException(message: String) : IllegalStateException(message)

/**
 * Domain exception encapsulating one or more field-level validation failures during request drafting.
 */
class RequestValidationException(val errors: List<String>) : IllegalArgumentException(errors.joinToString("; "))
