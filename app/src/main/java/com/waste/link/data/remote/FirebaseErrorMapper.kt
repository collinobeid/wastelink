package com.waste.link.data.remote

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.waste.link.domain.model.AuthError
import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * Maps raw Firebase Auth, Firestore, network, and [RemoteAuthException] throwables into
 * strongly typed [AuthError] domain errors so raw SDK exceptions never leak to UI consumers.
 */
object FirebaseErrorMapper {

    fun toAuthError(
        throwable: Throwable,
        emailContext: String? = null,
        uidContext: String? = null
    ): AuthError {
        return when (throwable) {
            is RemoteAuthException -> mapRemoteCode(
                code = throwable.code,
                message = throwable.message,
                emailContext = emailContext,
                uidContext = uidContext
            )

            is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword(
                message = throwable.reason?.takeIf { it.isNotBlank() }
                    ?: "Password does not meet minimum security requirements."
            )

            is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse(
                email = emailContext?.trim()?.lowercase().orEmpty()
            )

            is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCredentials()

            is FirebaseAuthInvalidUserException -> when (throwable.errorCode) {
                "ERROR_USER_DISABLED" -> AuthError.UserDisabled()
                "ERROR_USER_TOKEN_EXPIRED",
                "ERROR_INVALID_USER_TOKEN" -> AuthError.SessionRevoked()
                "ERROR_USER_NOT_FOUND" -> AuthError.AccountNotFound()
                else -> AuthError.InvalidCredentials()
            }

            is FirebaseAuthException -> when (throwable.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE",
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> AuthError.EmailAlreadyInUse(
                    email = emailContext?.trim()?.lowercase().orEmpty()
                )
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_EMAIL" -> AuthError.InvalidCredentials()
                "ERROR_USER_NOT_FOUND" -> AuthError.AccountNotFound()
                "ERROR_USER_DISABLED" -> AuthError.UserDisabled()
                "ERROR_USER_TOKEN_EXPIRED",
                "ERROR_INVALID_USER_TOKEN" -> AuthError.SessionRevoked()
                "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword()
                "ERROR_TOO_MANY_REQUESTS" -> AuthError.RateLimited()
                "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NetworkFailure(isRetryable = true)
                else -> AuthError.UnknownError(
                    message = throwable.message ?: "Authentication error (${throwable.errorCode})."
                )
            }

            is FirebaseTooManyRequestsException -> AuthError.RateLimited()

            is FirebaseNetworkException,
            is IOException,
            is TimeoutException -> AuthError.NetworkFailure(isRetryable = true)

            is FirebaseFirestoreException -> when (throwable.code.name) {
                "PERMISSION_DENIED" -> AuthError.PermissionDenied()
                "UNAUTHENTICATED" -> AuthError.SessionRevoked()
                "UNAVAILABLE",
                "DEADLINE_EXCEEDED",
                "ABORTED",
                "INTERNAL" -> AuthError.NetworkFailure(isRetryable = true)
                "RESOURCE_EXHAUSTED" -> AuthError.RateLimited()
                "NOT_FOUND" -> AuthError.ProfileNotFound(
                    uid = uidContext.orEmpty(),
                    email = emailContext.orEmpty()
                )
                else -> AuthError.UnknownError(
                    message = throwable.message ?: "Database error (${throwable.code.name})."
                )
            }

            else -> AuthError.UnknownError(
                message = throwable.message ?: "An unexpected error occurred."
            )
        }
    }

    fun isTransientNetworkError(throwable: Throwable): Boolean {
        return toAuthError(throwable) is AuthError.NetworkFailure
    }

    fun isSessionRevokedOrDisabled(error: AuthError): Boolean {
        return error is AuthError.UserDisabled ||
            error is AuthError.SessionRevoked ||
            error is AuthError.AccountNotFound
    }

    private fun mapRemoteCode(
        code: RemoteAuthErrorCode,
        message: String,
        emailContext: String?,
        uidContext: String?
    ): AuthError {
        return when (code) {
            RemoteAuthErrorCode.INVALID_CREDENTIALS -> AuthError.InvalidCredentials(message)
            RemoteAuthErrorCode.USER_NOT_FOUND -> AuthError.AccountNotFound(message)
            RemoteAuthErrorCode.EMAIL_ALREADY_IN_USE -> AuthError.EmailAlreadyInUse(
                email = emailContext?.trim()?.lowercase().orEmpty(),
                message = message
            )
            RemoteAuthErrorCode.WEAK_PASSWORD -> AuthError.WeakPassword(message)
            RemoteAuthErrorCode.USER_DISABLED -> AuthError.UserDisabled(message)
            RemoteAuthErrorCode.TOKEN_EXPIRED -> AuthError.SessionRevoked(message)
            RemoteAuthErrorCode.UNAUTHENTICATED -> AuthError.Unauthenticated(message)
            RemoteAuthErrorCode.TOO_MANY_REQUESTS -> AuthError.RateLimited(message = message)
            RemoteAuthErrorCode.NETWORK_UNAVAILABLE -> AuthError.NetworkFailure(
                message = message,
                isRetryable = true
            )
            RemoteAuthErrorCode.PERMISSION_DENIED -> AuthError.PermissionDenied(message)
            RemoteAuthErrorCode.DOCUMENT_NOT_FOUND -> AuthError.ProfileNotFound(
                uid = uidContext.orEmpty(),
                email = emailContext.orEmpty(),
                message = message
            )
            RemoteAuthErrorCode.UNKNOWN -> AuthError.UnknownError(message)
        }
    }
}
