package com.waste.link.data.repository

import androidx.annotation.VisibleForTesting
import com.waste.link.data.remote.FirebaseErrorMapper
import com.waste.link.data.remote.AuthRemoteDataSource
import com.waste.link.data.remote.RemoteDocumentResult
import com.waste.link.domain.model.AuthError
import com.waste.link.domain.model.CollectorSubmission
import com.waste.link.domain.model.CollectorSubmissionHistoryEntry
import com.waste.link.domain.model.CollectorSubmissionInput
import com.waste.link.domain.model.CollectorSubmissionMapper
import com.waste.link.domain.model.CollectorSubmissionOutcome
import com.waste.link.domain.model.CollectorVerificationStatus
import com.waste.link.domain.model.FieldValidationFailure
import com.waste.link.domain.model.OnboardingStatus
import com.waste.link.domain.model.UserRole
import com.waste.link.domain.validation.AuthValidator
import com.waste.link.domain.validation.ValidationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectorVerificationRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val authRepositoryProvider: () -> AuthRepository,
    private val clock: () -> Long
) : CollectorVerificationRepository {

    constructor(
        remoteDataSource: AuthRemoteDataSource,
        authRepository: AuthRepository,
        clock: () -> Long = { System.currentTimeMillis() }
    ) : this(
        remoteDataSource = remoteDataSource,
        authRepositoryProvider = { authRepository },
        clock = clock
    )

    @Inject
    constructor(
        remoteDataSource: AuthRemoteDataSource,
        authRepositoryProvider: javax.inject.Provider<AuthRepository>
    ) : this(
        remoteDataSource = remoteDataSource,
        authRepositoryProvider = { authRepositoryProvider.get() },
        clock = { System.currentTimeMillis() }
    )

    private val authRepository: AuthRepository get() = authRepositoryProvider()

    private val submissionMutex = Mutex()

    override suspend fun submitInitialVerification(
        input: CollectorSubmissionInput
    ): CollectorSubmissionOutcome = submissionMutex.withLock {
        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return@withLock CollectorSubmissionOutcome.Failure(AuthError.Unauthenticated())

        if (!authUser.isEmailVerified) {
            return@withLock CollectorSubmissionOutcome.Failure(AuthError.EmailNotVerified())
        }

        val currentUser = authRepository.getCurrentUser()
            ?: return@withLock CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Server-verified user session required.")
            )

        if (currentUser.role != UserRole.COLLECTOR) {
            return@withLock CollectorSubmissionOutcome.Failure(
                AuthError.UnauthorizedRoleSelection("Only accounts with role COLLECTOR can submit fleet verification.")
            )
        }

        // Validate input fields before touching network
        val failures = mutableListOf<FieldValidationFailure>()
        val vehicleTypeRes = AuthValidator.validateVehicleType(input.vehicleType)
        if (vehicleTypeRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("vehicleType", vehicleTypeRes.error, vehicleTypeRes.message))
        }
        val plateRes = AuthValidator.validateVehiclePlateNumber(input.vehiclePlateNumber)
        if (plateRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("vehiclePlateNumber", plateRes.error, plateRes.message))
        }
        val licenseRes = AuthValidator.validateLicenseNumber(input.licenseNumber)
        if (licenseRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("licenseNumber", licenseRes.error, licenseRes.message))
        }
        val zoneRes = AuthValidator.validateServiceZoneId(input.serviceZoneId)
        if (zoneRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("serviceZoneId", zoneRes.error, zoneRes.message))
        }

        if (failures.isNotEmpty()) {
            return@withLock CollectorSubmissionOutcome.Failure(AuthError.ValidationFailed(failures))
        }

        val normalizedVehicleType = (vehicleTypeRes as ValidationResult.Valid).value
        val normalizedPlate = (plateRes as ValidationResult.Valid).value
        val normalizedLicense = (licenseRes as ValidationResult.Valid).value
        val normalizedZone = (zoneRes as ValidationResult.Valid).value

        // Check server document state for idempotency & concurrency protection
        val existingResult = try {
            remoteDataSource.fetchCollectorSubmission(authUser.uid)
        } catch (_: Exception) {
            RemoteDocumentResult.NotFound(isFromCache = false)
        }

        if (existingResult is RemoteDocumentResult.Found) {
            val existingData = existingResult.data
            val existingStatusStr = existingData["status"] as? String
            val existingStatus = CollectorVerificationStatus.fromFirestoreValue(existingStatusStr, UserRole.COLLECTOR)
            val existingAttempt = (existingData["submissionAttempt"] as? Number)?.toInt() ?: 1

            when (existingStatus) {
                CollectorVerificationStatus.PENDING_REVIEW -> {
                    // Check if this is an idempotent retry of an already-submitted initial application
                    val existingVType = existingData["vehicleType"] as? String
                    val existingPlate = existingData["vehiclePlateNumber"] as? String
                    val existingLicense = existingData["licenseNumber"] as? String
                    val existingZone = existingData["serviceZoneId"] as? String

                    if (existingAttempt == 1 &&
                        existingVType == normalizedVehicleType &&
                        existingPlate == normalizedPlate &&
                        existingLicense == normalizedLicense &&
                        existingZone == normalizedZone
                    ) {
                        val submission = CollectorSubmissionMapper.fromFirestoreMap(existingData)
                            ?: return@withLock CollectorSubmissionOutcome.Failure(
                                AuthError.PermissionDenied("Corrupted existing submission data.")
                            )
                        val updatedUser = currentUser.copy(
                            collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW,
                            onboardingStatus = OnboardingStatus.COMPLETED,
                            vehicleType = normalizedVehicleType,
                            vehiclePlateNumber = normalizedPlate,
                            defaultZoneId = normalizedZone
                        )
                        authRepository.applyServerVerifiedUserToSession(updatedUser)
                        return@withLock CollectorSubmissionOutcome.Success(submission, updatedUser)
                    } else {
                        return@withLock CollectorSubmissionOutcome.Failure(
                            AuthError.PermissionDenied("A verification application is already pending review.")
                        )
                    }
                }
                CollectorVerificationStatus.APPROVED -> {
                    return@withLock CollectorSubmissionOutcome.Failure(
                        AuthError.PermissionDenied("Collector credentials have already been approved.")
                    )
                }
                CollectorVerificationStatus.REJECTED -> {
                    return@withLock CollectorSubmissionOutcome.Failure(
                        AuthError.PermissionDenied("Application was previously rejected. Please use resubmit to provide updated credentials.")
                    )
                }
                else -> Unit
            }
        }

        if (currentUser.collectorVerificationStatus != CollectorVerificationStatus.NOT_SUBMITTED) {
            return@withLock CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Collector verification has already been submitted or processed.")
            )
        }

        val now = maxOf(clock(), currentUser.updatedAtEpochMs + 1L)

        val submissionData = mapOf<String, Any?>(
            "userId" to authUser.uid,
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "licenseNumber" to normalizedLicense,
            "serviceZoneId" to normalizedZone,
            "status" to "PENDING_REVIEW",
            "submissionAttempt" to 1,
            "submittedAtEpochMs" to now,
            "reviewedByUid" to null,
            "reviewedAtEpochMs" to null,
            "rejectionReason" to null,
            "adminNotes" to null,
            "lastRejectionReason" to null,
            "lastReviewedByUid" to null,
            "lastReviewedAtEpochMs" to null
        )

        val historyData = mapOf<String, Any?>(
            "attemptId" to "attempt_1",
            "userId" to authUser.uid,
            "eventType" to "SUBMITTED",
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "licenseNumber" to normalizedLicense,
            "serviceZoneId" to normalizedZone,
            "submissionAttempt" to 1,
            "timestampEpochMs" to now,
            "actorUid" to authUser.uid,
            "rejectionReason" to null
        )

        val userUpdateFields = mapOf<String, Any?>(
            "collectorVerificationStatus" to "PENDING_REVIEW",
            "onboardingStatus" to OnboardingStatus.COMPLETED.name,
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "defaultZoneId" to normalizedZone,
            "updatedAtEpochMs" to now
        )

        return@withLock try {
            remoteDataSource.executeCollectorInitialSubmissionBatch(
                uid = authUser.uid,
                submissionData = submissionData,
                historyData = historyData,
                attemptId = "attempt_1",
                userUpdateFields = userUpdateFields
            )

            val updatedUser = currentUser.copy(
                collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW,
                onboardingStatus = OnboardingStatus.COMPLETED,
                vehicleType = normalizedVehicleType,
                vehiclePlateNumber = normalizedPlate,
                defaultZoneId = normalizedZone,
                updatedAtEpochMs = now
            )
            authRepository.applyServerVerifiedUserToSession(updatedUser)

            val submission = CollectorSubmission(
                userId = authUser.uid,
                vehicleType = normalizedVehicleType,
                vehiclePlateNumber = normalizedPlate,
                licenseNumber = normalizedLicense,
                serviceZoneId = normalizedZone,
                status = CollectorVerificationStatus.PENDING_REVIEW,
                submissionAttempt = 1,
                submittedAtEpochMs = now
            )
            CollectorSubmissionOutcome.Success(submission, updatedUser)
        } catch (e: Throwable) {
            CollectorSubmissionOutcome.Failure(
                FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = authUser.email,
                    uidContext = authUser.uid
                )
            )
        }
    }

    override suspend fun resubmitVerification(
        input: CollectorSubmissionInput
    ): CollectorSubmissionOutcome = submissionMutex.withLock {
        val authUser = remoteDataSource.getCurrentAuthUser()
            ?: return@withLock CollectorSubmissionOutcome.Failure(AuthError.Unauthenticated())

        if (!authUser.isEmailVerified) {
            return@withLock CollectorSubmissionOutcome.Failure(AuthError.EmailNotVerified())
        }

        val currentUser = authRepository.getCurrentUser()
            ?: return@withLock CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Server-verified user session required.")
            )

        if (currentUser.role != UserRole.COLLECTOR) {
            return@withLock CollectorSubmissionOutcome.Failure(
                AuthError.UnauthorizedRoleSelection("Only accounts with role COLLECTOR can resubmit fleet verification.")
            )
        }

        // Validate input fields before touching network
        val failures = mutableListOf<FieldValidationFailure>()
        val vehicleTypeRes = AuthValidator.validateVehicleType(input.vehicleType)
        if (vehicleTypeRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("vehicleType", vehicleTypeRes.error, vehicleTypeRes.message))
        }
        val plateRes = AuthValidator.validateVehiclePlateNumber(input.vehiclePlateNumber)
        if (plateRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("vehiclePlateNumber", plateRes.error, plateRes.message))
        }
        val licenseRes = AuthValidator.validateLicenseNumber(input.licenseNumber)
        if (licenseRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("licenseNumber", licenseRes.error, licenseRes.message))
        }
        val zoneRes = AuthValidator.validateServiceZoneId(input.serviceZoneId)
        if (zoneRes is ValidationResult.Invalid) {
            failures.add(FieldValidationFailure("serviceZoneId", zoneRes.error, zoneRes.message))
        }

        if (failures.isNotEmpty()) {
            return@withLock CollectorSubmissionOutcome.Failure(AuthError.ValidationFailed(failures))
        }

        val normalizedVehicleType = (vehicleTypeRes as ValidationResult.Valid).value
        val normalizedPlate = (plateRes as ValidationResult.Valid).value
        val normalizedLicense = (licenseRes as ValidationResult.Valid).value
        val normalizedZone = (zoneRes as ValidationResult.Valid).value

        val existingResult = remoteDataSource.fetchCollectorSubmission(authUser.uid)
        val existingData = (existingResult as? RemoteDocumentResult.Found)?.data
            ?: return@withLock CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Previous submission record not found.")
            )

        val existingStatusStr = existingData["status"] as? String
        val existingStatus = CollectorVerificationStatus.fromFirestoreValue(existingStatusStr, UserRole.COLLECTOR)
        val previousAttempt = (existingData["submissionAttempt"] as? Number)?.toInt() ?: 1

        // Check for idempotent retry or invalid status
        if (existingStatus == CollectorVerificationStatus.PENDING_REVIEW) {
            val existingVType = existingData["vehicleType"] as? String
            val existingPlate = existingData["vehiclePlateNumber"] as? String
            val existingLicense = existingData["licenseNumber"] as? String
            val existingZone = existingData["serviceZoneId"] as? String

            if (previousAttempt > 1 &&
                existingVType == normalizedVehicleType &&
                existingPlate == normalizedPlate &&
                existingLicense == normalizedLicense &&
                existingZone == normalizedZone
            ) {
                val submission = CollectorSubmissionMapper.fromFirestoreMap(existingData)
                    ?: return@withLock CollectorSubmissionOutcome.Failure(
                        AuthError.PermissionDenied("Corrupted existing submission data.")
                    )
                val updatedUser = currentUser.copy(
                    collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW,
                    collectorRejectionReason = null,
                    vehicleType = normalizedVehicleType,
                    vehiclePlateNumber = normalizedPlate,
                    defaultZoneId = normalizedZone
                )
                authRepository.applyServerVerifiedUserToSession(updatedUser)
                return@withLock CollectorSubmissionOutcome.Success(submission, updatedUser)
            } else {
                return@withLock CollectorSubmissionOutcome.Failure(
                    AuthError.PermissionDenied("Application is already pending review.")
                )
            }
        }

        if (existingStatus != CollectorVerificationStatus.REJECTED) {
            return@withLock CollectorSubmissionOutcome.Failure(
                AuthError.PermissionDenied("Resubmission is only permitted for REJECTED applications.")
            )
        }

        val newAttempt = previousAttempt + 1
        val attemptId = "attempt_$newAttempt"
        val now = maxOf(clock(), currentUser.updatedAtEpochMs + 1L)

        val submissionUpdateData = mapOf<String, Any?>(
            "userId" to authUser.uid,
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "licenseNumber" to normalizedLicense,
            "serviceZoneId" to normalizedZone,
            "status" to "PENDING_REVIEW",
            "submissionAttempt" to newAttempt,
            "submittedAtEpochMs" to now,
            "reviewedByUid" to null,
            "reviewedAtEpochMs" to null,
            "rejectionReason" to null,
            "adminNotes" to existingData["adminNotes"],
            "lastRejectionReason" to existingData["rejectionReason"],
            "lastReviewedByUid" to existingData["reviewedByUid"],
            "lastReviewedAtEpochMs" to existingData["reviewedAtEpochMs"]
        )

        val historyData = mapOf<String, Any?>(
            "attemptId" to attemptId,
            "userId" to authUser.uid,
            "eventType" to "RESUBMITTED",
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "licenseNumber" to normalizedLicense,
            "serviceZoneId" to normalizedZone,
            "submissionAttempt" to newAttempt,
            "timestampEpochMs" to now,
            "actorUid" to authUser.uid,
            "rejectionReason" to null
        )

        val userUpdateFields = mapOf<String, Any?>(
            "collectorVerificationStatus" to "PENDING_REVIEW",
            "collectorRejectionReason" to null,
            "vehicleType" to normalizedVehicleType,
            "vehiclePlateNumber" to normalizedPlate,
            "defaultZoneId" to normalizedZone,
            "updatedAtEpochMs" to now
        )

        return@withLock try {
            remoteDataSource.executeCollectorResubmissionBatch(
                uid = authUser.uid,
                submissionUpdateData = submissionUpdateData,
                historyData = historyData,
                attemptId = attemptId,
                userUpdateFields = userUpdateFields
            )

            val updatedUser = currentUser.copy(
                collectorVerificationStatus = CollectorVerificationStatus.PENDING_REVIEW,
                collectorRejectionReason = null,
                vehicleType = normalizedVehicleType,
                vehiclePlateNumber = normalizedPlate,
                defaultZoneId = normalizedZone,
                updatedAtEpochMs = now
            )
            authRepository.applyServerVerifiedUserToSession(updatedUser)

            val submission = CollectorSubmission(
                userId = authUser.uid,
                vehicleType = normalizedVehicleType,
                vehiclePlateNumber = normalizedPlate,
                licenseNumber = normalizedLicense,
                serviceZoneId = normalizedZone,
                status = CollectorVerificationStatus.PENDING_REVIEW,
                submissionAttempt = newAttempt,
                submittedAtEpochMs = now,
                lastRejectionReason = existingData["rejectionReason"] as? String,
                lastReviewedByUid = existingData["reviewedByUid"] as? String,
                lastReviewedAtEpochMs = (existingData["reviewedAtEpochMs"] as? Number)?.toLong()
            )
            CollectorSubmissionOutcome.Success(submission, updatedUser)
        } catch (e: Throwable) {
            CollectorSubmissionOutcome.Failure(
                FirebaseErrorMapper.toAuthError(
                    throwable = e,
                    emailContext = authUser.email,
                    uidContext = authUser.uid
                )
            )
        }
    }

    override suspend fun getSubmission(uid: String): Result<CollectorSubmission?> {
        return try {
            when (val res = remoteDataSource.fetchCollectorSubmission(uid)) {
                is RemoteDocumentResult.Found -> {
                    val submission = CollectorSubmissionMapper.fromFirestoreMap(res.data)
                    Result.success(submission)
                }
                is RemoteDocumentResult.NotFound -> Result.success(null)
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun getSubmissionHistory(uid: String): Result<List<CollectorSubmissionHistoryEntry>> {
        return try {
            val list = remoteDataSource.fetchCollectorSubmissionHistory(uid)
            val entries = list.mapNotNull { CollectorSubmissionMapper.fromHistoryFirestoreMap(it) }
                .sortedBy { it.submissionAttempt }
            Result.success(entries)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    @VisibleForTesting
    override suspend fun adminApproveCollectorSubmission(
        collectorUid: String,
        adminUid: String
    ): Result<Unit> {
        return try {
            val currentAuth = remoteDataSource.getCurrentAuthUser()
            if (currentAuth == null || !currentAuth.hasAdminClaim) {
                return Result.failure(SecurityException("Administrator claim required to approve collector submissions."))
            }

            val subResult = remoteDataSource.fetchCollectorSubmission(collectorUid)
            val subData = (subResult as? RemoteDocumentResult.Found)?.data
                ?: return Result.failure(IllegalStateException("No submission found for $collectorUid"))

            val attempt = (subData["submissionAttempt"] as? Number)?.toInt() ?: 1
            val attemptId = "attempt_${attempt}_approved"
            val now = clock()

            val historyData = mapOf<String, Any?>(
                "attemptId" to attemptId,
                "userId" to collectorUid,
                "eventType" to "APPROVED",
                "vehicleType" to (subData["vehicleType"] as? String ?: ""),
                "vehiclePlateNumber" to (subData["vehiclePlateNumber"] as? String ?: ""),
                "licenseNumber" to (subData["licenseNumber"] as? String ?: ""),
                "serviceZoneId" to (subData["serviceZoneId"] as? String ?: ""),
                "submissionAttempt" to attempt,
                "timestampEpochMs" to now,
                "actorUid" to adminUid,
                "rejectionReason" to null
            )

            val submissionUpdateData = mapOf<String, Any?>(
                "status" to "APPROVED",
                "reviewedByUid" to adminUid,
                "reviewedAtEpochMs" to now,
                "rejectionReason" to null
            )

            val userUpdateFields = mapOf<String, Any?>(
                "collectorVerificationStatus" to "APPROVED",
                "onboardingStatus" to "COMPLETED",
                "isVerified" to true,
                "status" to "ACTIVE",
                "collectorRejectionReason" to null,
                "updatedAtEpochMs" to now
            )

            remoteDataSource.executeAdminCollectorApprovalBatch(
                uid = collectorUid,
                attemptId = attemptId,
                historyData = historyData,
                submissionUpdateData = submissionUpdateData,
                userUpdateFields = userUpdateFields
            )
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    @VisibleForTesting
    override suspend fun adminRejectCollectorSubmission(
        collectorUid: String,
        adminUid: String,
        rejectionReason: String,
        adminNotes: String?
    ): Result<Unit> {
        return try {
            val currentAuth = remoteDataSource.getCurrentAuthUser()
            if (currentAuth == null || !currentAuth.hasAdminClaim) {
                return Result.failure(SecurityException("Administrator claim required to reject collector submissions."))
            }
            if (rejectionReason.isBlank()) {
                return Result.failure(IllegalArgumentException("Rejection reason must not be blank."))
            }

            val subResult = remoteDataSource.fetchCollectorSubmission(collectorUid)
            val subData = (subResult as? RemoteDocumentResult.Found)?.data
                ?: return Result.failure(IllegalStateException("No submission found for $collectorUid"))

            val attempt = (subData["submissionAttempt"] as? Number)?.toInt() ?: 1
            val attemptId = "attempt_${attempt}_rejected"
            val now = clock()

            val historyData = mapOf<String, Any?>(
                "attemptId" to attemptId,
                "userId" to collectorUid,
                "eventType" to "REJECTED",
                "vehicleType" to (subData["vehicleType"] as? String ?: ""),
                "vehiclePlateNumber" to (subData["vehiclePlateNumber"] as? String ?: ""),
                "licenseNumber" to (subData["licenseNumber"] as? String ?: ""),
                "serviceZoneId" to (subData["serviceZoneId"] as? String ?: ""),
                "submissionAttempt" to attempt,
                "timestampEpochMs" to now,
                "actorUid" to adminUid,
                "rejectionReason" to rejectionReason
            )

            val submissionUpdateData = mutableMapOf<String, Any?>(
                "status" to "REJECTED",
                "reviewedByUid" to adminUid,
                "reviewedAtEpochMs" to now,
                "rejectionReason" to rejectionReason
            )
            if (adminNotes != null) {
                submissionUpdateData["adminNotes"] = adminNotes
            }

            val userUpdateFields = mapOf<String, Any?>(
                "collectorVerificationStatus" to "REJECTED",
                "isVerified" to false,
                "collectorRejectionReason" to rejectionReason,
                "updatedAtEpochMs" to now
            )

            remoteDataSource.executeAdminCollectorRejectionBatch(
                uid = collectorUid,
                attemptId = attemptId,
                historyData = historyData,
                submissionUpdateData = submissionUpdateData,
                userUpdateFields = userUpdateFields
            )
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
