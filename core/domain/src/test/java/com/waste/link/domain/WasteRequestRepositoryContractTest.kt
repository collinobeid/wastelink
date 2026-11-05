package com.waste.link.domain

import com.waste.link.domain.model.GeoCoordinates
import com.waste.link.domain.model.RequestLifecycleStatus
import com.waste.link.domain.model.VolumeTier
import com.waste.link.domain.model.WasteRequest
import com.waste.link.domain.repository.WasteRequestCancellationOutcome
import com.waste.link.domain.repository.WasteRequestCreationOutcome
import com.waste.link.domain.repository.WasteRequestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WasteRequestRepositoryContractTest {

    private fun sampleRequest(): WasteRequest {
        val id = UUID.randomUUID().toString()
        return WasteRequest(
            id = id,
            clientGeneratedId = id,
            clientUid = "resident_uid_123",
            coordinates = GeoCoordinates(-6.7924, 39.2083),
            geohash = "kygck5q",
            serviceZoneId = "zone_kinondoni_01",
            pickupAddress = "Plot 42, Ali Hassan Rd",
            categoryId = "household",
            volumeTier = VolumeTier.STANDARD_BIN,
            status = RequestLifecycleStatus.PENDING_DISPATCH
        )
    }

    @Test
    fun createWasteRequest_successContract() = runTest {
        val req = sampleRequest()
        val mockRepo = object : WasteRequestRepository {
            override suspend fun createWasteRequest(request: WasteRequest): WasteRequestCreationOutcome {
                return WasteRequestCreationOutcome.Success(request.copy(requestRef = "WL-2611-7X9F2B"))
            }

            override suspend fun cancelWasteRequest(requestId: String, actorUid: String): WasteRequestCancellationOutcome {
                return WasteRequestCancellationOutcome.Success
            }

            override fun watchWasteRequest(requestId: String): Flow<WasteRequest?> = flowOf(req)
            override fun watchResidentRequests(clientUid: String): Flow<List<WasteRequest>> = flowOf(listOf(req))
        }

        val outcome = mockRepo.createWasteRequest(req)
        assertTrue(outcome is WasteRequestCreationOutcome.Success)
        val success = outcome as WasteRequestCreationOutcome.Success
        assertEquals("WL-2611-7X9F2B", success.request.requestRef)
    }

    @Test
    fun createWasteRequest_queuedOfflineContract() = runTest {
        val req = sampleRequest()
        val mockRepo = object : WasteRequestRepository {
            override suspend fun createWasteRequest(request: WasteRequest): WasteRequestCreationOutcome {
                return WasteRequestCreationOutcome.QueuedOffline(request.clientGeneratedId)
            }

            override suspend fun cancelWasteRequest(requestId: String, actorUid: String): WasteRequestCancellationOutcome {
                return WasteRequestCancellationOutcome.Success
            }

            override fun watchWasteRequest(requestId: String): Flow<WasteRequest?> = flowOf(null)
            override fun watchResidentRequests(clientUid: String): Flow<List<WasteRequest>> = flowOf(emptyList())
        }

        val outcome = mockRepo.createWasteRequest(req)
        assertTrue(outcome is WasteRequestCreationOutcome.QueuedOffline)
        assertEquals(req.clientGeneratedId, (outcome as WasteRequestCreationOutcome.QueuedOffline).clientGeneratedId)
    }

    @Test
    fun cancelWasteRequest_successContract() = runTest {
        val mockRepo = object : WasteRequestRepository {
            override suspend fun createWasteRequest(request: WasteRequest): WasteRequestCreationOutcome =
                WasteRequestCreationOutcome.QueuedOffline(request.clientGeneratedId)

            override suspend fun cancelWasteRequest(requestId: String, actorUid: String): WasteRequestCancellationOutcome =
                WasteRequestCancellationOutcome.Success

            override fun watchWasteRequest(requestId: String): Flow<WasteRequest?> = flowOf(null)
            override fun watchResidentRequests(clientUid: String): Flow<List<WasteRequest>> = flowOf(emptyList())
        }

        val outcome = mockRepo.cancelWasteRequest("req_123", "user_123")
        assertTrue(outcome is WasteRequestCancellationOutcome.Success)
    }
}
