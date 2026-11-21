package com.example.data.carriers

import com.example.domain.model.CarrierCode
import com.example.domain.model.UnifiedParcelStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class CreateShipmentRequest(
    val orderId: String,
    val recipientName: String,
    val recipientPhone: String,
    val city: String,
    val address: String,
    val productSummary: String,
    val codAmountMad: Double,
    val merchantReference: String
)

data class CreateShipmentResult(
    val success: Boolean,
    val carrierCode: CarrierCode,
    val trackingNumber: String,
    val labelUrlOrCode: String,
    val rawResponse: String,
    val errorMessage: String? = null
)

data class LabelResult(
    val trackingNumber: String,
    val waybillTitle: String,
    val barcodeText: String,
    val pdfDownloadHint: String
)

data class CarrierStatusSnapshot(
    val trackingNumber: String,
    val rawStatus: String,
    val unifiedStatus: UnifiedParcelStatus,
    val locationNote: String,
    val timestampMillis: Long
)

data class WebhookParseResult(
    val externalEventId: String,
    val trackingNumber: String,
    val rawStatus: String,
    val unifiedStatus: UnifiedParcelStatus,
    val note: String,
    val eventEpochMillis: Long
)

/**
 * Unified interface implemented by all Moroccan delivery carriers.
 * Each adapter maps carrier-specific payloads and statuses into ParcelHub MA's unified model.
 */
interface CarrierAdapter {
    val carrierCode: CarrierCode
    val isMockImplementation: Boolean
    val endpointIntegrationNote: String

    suspend fun createShipment(request: CreateShipmentRequest): CreateShipmentResult
    suspend fun getLabel(trackingNumber: String): LabelResult
    suspend fun getStatus(trackingNumber: String): CarrierStatusSnapshot
    suspend fun cancelShipment(trackingNumber: String): Boolean
    fun parseWebhook(rawPayload: Map<String, String>): WebhookParseResult
}

object StatusMapper {
    private val rawToUnified: Map<String, UnifiedParcelStatus> = mapOf(
        // Created / New
        "CREATED" to UnifiedParcelStatus.CREATED,
        "NOUVEAU" to UnifiedParcelStatus.CREATED,
        "NEW_PARCEL" to UnifiedParcelStatus.CREATED,
        "EN_ATTENTE_RAMASSAGE" to UnifiedParcelStatus.CREATED,
        // Picked up
        "PICKED_UP" to UnifiedParcelStatus.PICKED_UP,
        "RAMASSE" to UnifiedParcelStatus.PICKED_UP,
        "COLLECTED" to UnifiedParcelStatus.PICKED_UP,
        // In transit
        "IN_TRANSIT" to UnifiedParcelStatus.IN_TRANSIT,
        "EXPEDIE" to UnifiedParcelStatus.IN_TRANSIT,
        "EN_TRANSIT" to UnifiedParcelStatus.IN_TRANSIT,
        "HUB_ARRIVAL" to UnifiedParcelStatus.IN_TRANSIT,
        // Out for delivery
        "OUT_FOR_DELIVERY" to UnifiedParcelStatus.OUT_FOR_DELIVERY,
        "EN_COURS" to UnifiedParcelStatus.OUT_FOR_DELIVERY,
        "EN_LIVRAISON" to UnifiedParcelStatus.OUT_FOR_DELIVERY,
        "DISTRIBUTION" to UnifiedParcelStatus.OUT_FOR_DELIVERY,
        // Delivered
        "DELIVERED" to UnifiedParcelStatus.DELIVERED,
        "LIVRE" to UnifiedParcelStatus.DELIVERED,
        "LIVRÉ" to UnifiedParcelStatus.DELIVERED,
        // Refused
        "REFUSED" to UnifiedParcelStatus.REFUSED,
        "REFUSE" to UnifiedParcelStatus.REFUSED,
        "INJOIGNABLE" to UnifiedParcelStatus.REFUSED,
        "NO_ANSWER" to UnifiedParcelStatus.REFUSED,
        // Returned
        "RETURNED" to UnifiedParcelStatus.RETURNED,
        "RETOUR" to UnifiedParcelStatus.RETURNED,
        "RETOURNE" to UnifiedParcelStatus.RETURNED,
        // Cancelled
        "CANCELLED" to UnifiedParcelStatus.CANCELLED,
        "ANNULE" to UnifiedParcelStatus.CANCELLED
    )

    fun map(rawStatus: String): UnifiedParcelStatus {
        val key = rawStatus.trim().uppercase().replace(" ", "_")
        return rawToUnified[key] ?: UnifiedParcelStatus.IN_TRANSIT
    }

    /**
     * Prevents out-of-order or stale webhook events from regressing terminal or later states.
     */
    fun shouldApplyTransition(
        currentStatus: UnifiedParcelStatus,
        incomingStatus: UnifiedParcelStatus,
        currentUpdatedMillis: Long,
        incomingEventMillis: Long
    ): Boolean {
        if (currentStatus.isTerminal && incomingStatus != currentStatus) {
            return false
        }
        if (incomingStatus.rank < currentStatus.rank && incomingEventMillis <= currentUpdatedMillis) {
            return false
        }
        return true
    }
}

object WebhookSignatureVerifier {
    fun computeHmacSha256(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val raw = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }
    }

    fun verifySignature(secret: String, payload: String, providedSignature: String): Boolean {
        if (secret.isBlank() || providedSignature.isBlank()) return false
        val expected = computeHmacSha256(secret, payload)
        return MessageDigest.isEqual(
            expected.lowercase().toByteArray(StandardCharsets.UTF_8),
            providedSignature.trim().lowercase().toByteArray(StandardCharsets.UTF_8)
        )
    }
}
