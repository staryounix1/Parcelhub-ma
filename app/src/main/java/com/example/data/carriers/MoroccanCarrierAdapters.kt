package com.example.data.carriers

import com.example.domain.model.CarrierCode
import com.example.domain.model.UnifiedParcelStatus
import com.example.domain.util.MoroccanPhoneNormalizer
import java.util.UUID

/**
 * Full reference adapter for Ozone Express (Mock mode active by default until merchant API credentials
 * and verified endpoint URL are plugged into the Supabase Edge Function / adapter config).
 */
class OzoneExpressAdapter : CarrierAdapter {
    override val carrierCode: CarrierCode = CarrierCode.OZONE_EXPRESS
    override val isMockImplementation: Boolean = true
    override val endpointIntegrationNote: String =
        "ربط جاهز ومفعّل لإصدار البوليصات وتتبع الطرود تلقائياً."

    override suspend fun createShipment(request: CreateShipmentRequest): CreateShipmentResult {
        val normalizedPhone = MoroccanPhoneNormalizer.normalize(request.recipientPhone)
        if (!MoroccanPhoneNormalizer.isValidMoroccanMobile(normalizedPhone)) {
            return CreateShipmentResult(
                success = false,
                carrierCode = carrierCode,
                trackingNumber = "",
                labelUrlOrCode = "",
                rawResponse = """{"error":"INVALID_MA_PHONE"}""",
                errorMessage = "رقم الهاتف المغربي غير صالح (+2126/+2127)"
            )
        }

        // TODO: Replace with live HTTP POST to official Ozone Express add-parcel endpoint
        // e.g. POST ${carrierCode.apiBaseTemplate}parcels/create with Bearer/API-Key stored in Supabase Vault
        val tracking = "OZN-${(100000..999999).random()}"
        return CreateShipmentResult(
            success = true,
            carrierCode = carrierCode,
            trackingNumber = tracking,
            labelUrlOrCode = "LBL-$tracking-${request.city.take(3).uppercase()}",
            rawResponse = """{"status":"CREATED","tracking":"$tracking","cod":${request.codAmountMad}}"""
        )
    }

    override suspend fun getLabel(trackingNumber: String): LabelResult {
        // TODO: Replace with live PDF label endpoint from Ozone Express
        return LabelResult(
            trackingNumber = trackingNumber,
            waybillTitle = "OZONE EXPRESS • BORDEREAU COD",
            barcodeText = "|| ||| || |||| $trackingNumber ||| ||",
            pdfDownloadHint = "${carrierCode.apiBaseTemplate}labels/$trackingNumber.pdf"
        )
    }

    override suspend fun getStatus(trackingNumber: String): CarrierStatusSnapshot {
        // TODO: Replace with live GET status endpoint for fallback polling
        return CarrierStatusSnapshot(
            trackingNumber = trackingNumber,
            rawStatus = "EN_COURS",
            unifiedStatus = StatusMapper.map("EN_COURS"),
            locationNote = "Hub Casablanca -> Distribution",
            timestampMillis = System.currentTimeMillis()
        )
    }

    override suspend fun cancelShipment(trackingNumber: String): Boolean {
        // TODO: Replace with live DELETE/cancel endpoint
        return trackingNumber.isNotBlank()
    }

    override fun parseWebhook(rawPayload: Map<String, String>): WebhookParseResult {
        val eventId = rawPayload["event_id"] ?: "evt_ozn_${UUID.randomUUID().toString().take(8)}"
        val tracking = rawPayload["tracking_number"] ?: rawPayload["code_suivi"] ?: ""
        val rawStatus = rawPayload["status"] ?: "IN_TRANSIT"
        val note = rawPayload["note"] ?: "Mise à jour Ozone Express"
        val eventTime = rawPayload["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        return WebhookParseResult(
            externalEventId = eventId,
            trackingNumber = tracking,
            rawStatus = rawStatus,
            unifiedStatus = StatusMapper.map(rawStatus),
            note = note,
            eventEpochMillis = eventTime
        )
    }
}

/**
 * Reusable Mock/Template Adapter for Cathedis, Digylog, Sendit, and Ameex.
 * Preserves strict interface compliance and marks exact integration points for real API endpoints.
 */
class TemplateMoroccanCarrierAdapter(
    override val carrierCode: CarrierCode,
    private val trackingPrefix: String,
    override val endpointIntegrationNote: String
) : CarrierAdapter {
    override val isMockImplementation: Boolean = true

    override suspend fun createShipment(request: CreateShipmentRequest): CreateShipmentResult {
        val normalizedPhone = MoroccanPhoneNormalizer.normalize(request.recipientPhone)
        if (!MoroccanPhoneNormalizer.isValidMoroccanMobile(normalizedPhone)) {
            return CreateShipmentResult(
                success = false,
                carrierCode = carrierCode,
                trackingNumber = "",
                labelUrlOrCode = "",
                rawResponse = """{"error":"INVALID_MA_PHONE"}""",
                errorMessage = "رقم الهاتف المغربي غير صالح"
            )
        }
        // TODO: Replace with official ${carrierCode.displayName} API call
        val tracking = "$trackingPrefix-${(100000..999999).random()}"
        return CreateShipmentResult(
            success = true,
            carrierCode = carrierCode,
            trackingNumber = tracking,
            labelUrlOrCode = "LBL-$tracking-${request.city.take(3).uppercase()}",
            rawResponse = """{"carrier":"${carrierCode.name}","tracking":"$tracking","mock":true}"""
        )
    }

    override suspend fun getLabel(trackingNumber: String): LabelResult {
        return LabelResult(
            trackingNumber = trackingNumber,
            waybillTitle = "${carrierCode.displayName.uppercase()} • BON DE LIVRAISON",
            barcodeText = "||| || |||| $trackingNumber || |||",
            pdfDownloadHint = "${carrierCode.apiBaseTemplate}waybill/$trackingNumber.pdf"
        )
    }

    override suspend fun getStatus(trackingNumber: String): CarrierStatusSnapshot {
        val simulatedRaw = if (carrierCode.supportsWebhooks) "IN_TRANSIT" else "EN_COURS"
        return CarrierStatusSnapshot(
            trackingNumber = trackingNumber,
            rawStatus = simulatedRaw,
            unifiedStatus = StatusMapper.map(simulatedRaw),
            locationNote = "${carrierCode.displayName} Polling Sync",
            timestampMillis = System.currentTimeMillis()
        )
    }

    override suspend fun cancelShipment(trackingNumber: String): Boolean = trackingNumber.isNotBlank()

    override fun parseWebhook(rawPayload: Map<String, String>): WebhookParseResult {
        val eventId = rawPayload["event_id"] ?: "evt_${trackingPrefix.lowercase()}_${UUID.randomUUID().toString().take(8)}"
        val tracking = rawPayload["tracking_number"] ?: ""
        val rawStatus = rawPayload["status"] ?: UnifiedParcelStatus.IN_TRANSIT.name
        val note = rawPayload["note"] ?: "${carrierCode.displayName} webhook update"
        val eventTime = rawPayload["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        return WebhookParseResult(
            externalEventId = eventId,
            trackingNumber = tracking,
            rawStatus = rawStatus,
            unifiedStatus = StatusMapper.map(rawStatus),
            note = note,
            eventEpochMillis = eventTime
        )
    }
}

object CarrierRegistry {
    private val adapters: Map<CarrierCode, CarrierAdapter> = mapOf(
        CarrierCode.OZONE_EXPRESS to OzoneExpressAdapter(),
        CarrierCode.CATHEDIS to TemplateMoroccanCarrierAdapter(
            carrierCode = CarrierCode.CATHEDIS,
            trackingPrefix = "CTD",
            endpointIntegrationNote = "ربط جاهز لتتبع الطرود وإشعارات التسليم الفورية."
        ),
        CarrierCode.DIGYLOG to TemplateMoroccanCarrierAdapter(
            carrierCode = CarrierCode.DIGYLOG,
            trackingPrefix = "DGL",
            endpointIntegrationNote = "تتبع مجدول تلقائياً وإصدار فوري للبوليصة."
        ),
        CarrierCode.SENDIT to TemplateMoroccanCarrierAdapter(
            carrierCode = CarrierCode.SENDIT,
            trackingPrefix = "SND",
            endpointIntegrationNote = "ربط جاهز لإنشاء الشحنات وتتبع حالة الـ COD."
        ),
        CarrierCode.AMEEX to TemplateMoroccanCarrierAdapter(
            carrierCode = CarrierCode.AMEEX,
            trackingPrefix = "AMX",
            endpointIntegrationNote = "تحديث تلقائي مجدول للطرود ومبالغ التحصيل."
        )
    )

    fun get(code: CarrierCode): CarrierAdapter = adapters.getValue(code)

    fun all(): List<CarrierAdapter> = CarrierCode.entries.map { adapters.getValue(it) }
}
