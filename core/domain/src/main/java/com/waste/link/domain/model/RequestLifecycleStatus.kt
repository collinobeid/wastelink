package com.waste.link.domain.model

/**
 * Server-authoritative operational request lifecycle states stored in Cloud Firestore under field `status`.
 *
 * Operational Separation Guarantee:
 * - This enum represents strictly the municipal field operations and dispatch state machine.
 * - Local synchronization metadata (e.g. `LOCAL_DRAFT`, `QUEUED_OFFLINE`, `UPLOADING`, `SYNCED`)
 *   belongs exclusively to `LocalSyncStatus` in Room DB and must NEVER be mixed into this enum.
 */
enum class RequestLifecycleStatus(val firestoreValue: String) {
    /** Request committed to Firestore; awaiting fleet assignment. Cancellation permitted. */
    PENDING_DISPATCH("PENDING_DISPATCH"),

    /** Request quarantined due to spam/cooldown violation or account restriction. */
    FLAGGED_FOR_REVIEW("FLAGGED_FOR_REVIEW"),

    /** A specific collector UID and vehicle ID are allocated. Cancellation locks immediately. */
    ASSIGNED_TO_FLEET("ASSIGNED_TO_FLEET"),

    /** Collector started route and is traveling toward resident pickup coordinates. */
    EN_ROUTE("EN_ROUTE"),

    /** Collector has arrived at pickup coordinates (< 50m) or manual gate override. */
    ARRIVED_AT_LOCATION("ARRIVED_AT_LOCATION"),

    /** Waste custody transferred; mandatory Proof of Collection (PoC) photographic evidence verified. */
    COLLECTION_VERIFIED("COLLECTION_VERIFIED"),

    /** Driver has departed pickup location and is hauling waste to municipal disposal facility. */
    IN_TRANSIT_TO_DUMP("IN_TRANSIT_TO_DUMP"),

    /** Waste successfully offloaded at municipal disposal site. Terminal state. GreenPoints awarded. */
    COMPLETED("COMPLETED"),

    /** Request cancelled prior to fleet allocation by resident owner or administrator. Terminal state. */
    CANCELLED("CANCELLED"),

    /** Pickup aborted due to inaccessible location, gate locked, or vehicle breakdown. Mandatory photo proof. */
    FAILED("FAILED"),

    /** Resident filed formal grievance within 48 hours of completion or failure. Awaiting arbitration. */
    DISPUTED("DISPUTED"),

    /** Dispute settled by administrator with refund or re-service. Terminal state. */
    RESOLVED("RESOLVED"),

    /** Dispute dismissed by administrator with written justification. Terminal state. */
    DISMISSED("DISMISSED");

    /**
     * True if the request has reached an irreversible terminal state.
     */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == RESOLVED || this == DISMISSED

    /**
     * True only while the request is in [PENDING_DISPATCH], during which the resident owner
     * is permitted to cancel the request.
     */
    val isCancellableByResident: Boolean
        get() = this == PENDING_DISPATCH

    /**
     * True if the request state permits filing a service grievance (within 48 hours).
     */
    val isDisputable: Boolean
        get() = this == COMPLETED || this == FAILED

    /**
     * True if the request is actively undergoing fleet execution in the field.
     */
    val isActiveFleetExecution: Boolean
        get() = this in setOf(ASSIGNED_TO_FLEET, EN_ROUTE, ARRIVED_AT_LOCATION, COLLECTION_VERIFIED, IN_TRANSIT_TO_DUMP)

    companion object {
        /**
         * Resolves canonical [RequestLifecycleStatus] from Firestore strings.
         */
        fun fromFirestoreValue(value: String?): RequestLifecycleStatus? {
            if (value.isNullOrBlank()) return null
            return entries.find { it.firestoreValue.equals(value.trim(), ignoreCase = true) }
        }

        /**
         * Reconciles legacy status vocabulary and historical database records to the canonical enum.
         */
        fun fromLegacyStatus(value: String?): RequestLifecycleStatus? {
            if (value.isNullOrBlank()) return null
            return when (value.trim().uppercase()) {
                "PENDING", "NEW", "PENDING_DISPATCH" -> PENDING_DISPATCH
                "ASSIGNED", "ASSIGNED_TO_FLEET" -> ASSIGNED_TO_FLEET
                "EN_ROUTE", "STARTED" -> EN_ROUTE
                "ARRIVED", "AT_GATE", "ARRIVED_AT_LOCATION" -> ARRIVED_AT_LOCATION
                "VERIFIED", "PICKED_UP", "COLLECTION_VERIFIED" -> COLLECTION_VERIFIED
                "IN_TRANSIT", "HAULING", "IN_TRANSIT_TO_DUMP" -> IN_TRANSIT_TO_DUMP
                "COMPLETED", "DONE" -> COMPLETED
                "CANCELLED", "USER_CANCELLED" -> CANCELLED
                "FAILED", "FAILED_NO_SHOW", "ABORTED" -> FAILED
                "DISPUTED", "IN_DISPUTE" -> DISPUTED
                "RESOLVED" -> RESOLVED
                "DISMISSED" -> DISMISSED
                "FLAGGED", "SHADOW_BANNED", "FLAGGED_FOR_REVIEW" -> FLAGGED_FOR_REVIEW
                else -> entries.find { it.name.equals(value.trim(), ignoreCase = true) }
            }
        }
    }
}
