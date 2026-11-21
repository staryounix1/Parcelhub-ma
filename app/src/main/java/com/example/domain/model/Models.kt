package com.example.domain.model

enum class UnifiedParcelStatus(
    val rank: Int,
    val isTerminal: Boolean,
    val labelAr: String,
    val labelFr: String
) {
    CREATED(1, false, "تم الإنشاء", "Créé"),
    PICKED_UP(2, false, "تم الاستلام من التاجر", "Ramassé"),
    IN_TRANSIT(3, false, "في الطريق", "En transit"),
    OUT_FOR_DELIVERY(4, false, "خرج للتوصيل", "En livraison"),
    DELIVERED(5, true, "تم التسليم", "Livré"),
    REFUSED(5, false, "مرفوض من الزبون", "Refusé"),
    RETURNED(6, true, "مرتجع (Retour)", "Retourné"),
    CANCELLED(6, true, "ملغى", "Annulé");

    fun label(isArabic: Boolean): String = if (isArabic) labelAr else labelFr
}

enum class CarrierCode(
    val displayName: String,
    val supportsWebhooks: Boolean,
    val defaultFeeMad: Double,
    val apiBaseTemplate: String
) {
    OZONE_EXPRESS("Ozone Express", true, 35.0, "https://api.ozoneexpress.ma/v1/"),
    CATHEDIS("Cathedis", true, 30.0, "https://api.cathedis.ma/v2/"),
    DIGYLOG("Digylog", false, 32.0, "https://api.digylog.com/v1/"),
    SENDIT("Sendit", true, 30.0, "https://app.sendit.ma/api/v1/"),
    AMEEX("Ameex", false, 35.0, "https://api.ameex.app/customer/v1/")
}

enum class StorePlatform(val displayName: String, val webhookHeader: String) {
    YOUCAN("YouCan", "X-YouCan-Signature"),
    SHOPIFY("Shopify", "X-Shopify-Hmac-Sha256"),
    WOOCOMMERCE("WooCommerce", "X-WC-Webhook-Signature"),
    GOOGLE_SHEETS("Google Sheets", "X-Sheets-Sync-Token")
}

enum class CodSettlementStatus(val labelAr: String, val labelFr: String) {
    PENDING_CARRIER("في انتظار التحصيل", "En attente"),
    COLLECTED_BY_CARRIER("محصّل لدى الشركة", "Encaissé transporteur"),
    REMITTED_TO_MERCHANT("محوّل للتاجر", "Viré au marchand"),
    DISPUTED("نزاع / مراجعة", "Litige");

    fun label(isArabic: Boolean): String = if (isArabic) labelAr else labelFr
}

enum class WhatsAppScenario(
    val templateKey: String,
    val titleAr: String,
    val titleFr: String,
    val metaCategory: String,
    val estimatedCostMad: Double
) {
    ORDER_CONFIRMATION(
        "order_confirm_cod",
        "تأكيد الطلب (نعم/لا)",
        "Confirmation COD (Oui/Non)",
        "UTILITY",
        0.16
    ),
    SHIPPED(
        "parcel_shipped",
        "تم الشحن",
        "Colis expédié",
        "UTILITY",
        0.16
    ),
    OUT_FOR_DELIVERY(
        "out_for_delivery",
        "خرج للتوصيل",
        "En cours de livraison",
        "UTILITY",
        0.16
    ),
    FAILED_ATTEMPT(
        "failed_attempt",
        "محاولة فاشلة",
        "Tentative échouée",
        "UTILITY",
        0.16
    ),
    DELIVERED(
        "parcel_delivered",
        "تم التسليم",
        "Colis livré",
        "UTILITY",
        0.16
    );

    fun title(isArabic: Boolean): String = if (isArabic) titleAr else titleFr
}

data class Customer(
    val id: String,
    val fullName: String,
    val phoneNormalized: String,
    val city: String,
    val address: String,
    val totalOrders: Int = 1,
    val returnCount: Int = 0,
    val isBlacklisted: Boolean = false,
    val whatsappOptOut: Boolean = false
)

data class TrackingLog(
    val id: String,
    val shipmentId: String,
    val externalEventId: String,
    val rawStatus: String,
    val unifiedStatus: UnifiedParcelStatus,
    val note: String,
    val eventEpochMillis: Long,
    val source: String
)

data class ParcelOrder(
    val id: String,
    val merchantId: String,
    val storeOrderRef: String,
    val storePlatform: StorePlatform,
    val customer: Customer,
    val productSummary: String,
    val codAmountMad: Double,
    val status: UnifiedParcelStatus,
    val carrierCode: CarrierCode?,
    val trackingNumber: String?,
    val labelPreviewCode: String?,
    val codSettlementStatus: CodSettlementStatus,
    val storeSynced: Boolean,
    val whatsappConfirmed: Boolean?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deliveredAtMillis: Long? = null,
    val trackingLogs: List<TrackingLog> = emptyList()
)

data class WhatsAppTemplate(
    val id: String,
    val scenario: WhatsAppScenario,
    val bodyDarija: String,
    val bodyFrench: String,
    val isApprovedMeta: Boolean = true
)

data class NotificationLog(
    val id: String,
    val orderId: String,
    val trackingNumber: String,
    val customerPhone: String,
    val scenario: WhatsAppScenario,
    val languageTag: String,
    val renderedMessage: String,
    val status: String,
    val dedupKey: String,
    val costMad: Double,
    val sentAtMillis: Long
)

data class StoreConnection(
    val id: String,
    val platform: StorePlatform,
    val storeName: String,
    val webhookUrl: String,
    val isActive: Boolean,
    val importedOrdersCount: Int,
    val lastSyncMillis: Long
)

data class CarrierAccountConfig(
    val carrierCode: CarrierCode,
    val isActive: Boolean,
    val isMockMode: Boolean,
    val encryptedKeyPreview: String,
    val lastPollMillis: Long,
    val activeShipmentsCount: Int
)

data class CodBatchSummary(
    val id: String,
    val carrierCode: CarrierCode,
    val parcelCount: Int,
    val grossCodMad: Double,
    val carrierFeesMad: Double,
    val netPayableMad: Double,
    val settlementStatus: CodSettlementStatus,
    val updatedAtMillis: Long
)

data class DeadLetterEvent(
    val id: String,
    val source: String,
    val payloadSummary: String,
    val failureReason: String,
    val retryCount: Int,
    val nextRetryMillis: Long,
    val createdAtMillis: Long
)

data class SubscriptionTier(
    val id: String,
    val nameAr: String,
    val nameFr: String,
    val priceMadMonthly: Int,
    val monthlyParcelLimit: Int,
    val storesLimit: Int,
    val carriersLimit: Int,
    val featuresAr: List<String>,
    val featuresFr: List<String>,
    val isRecommended: Boolean = false
)

data class DashboardKpis(
    val totalParcels: Int,
    val deliveredCount: Int,
    val retourCount: Int,
    val deliveryRatePercent: Double,
    val retourRatePercent: Double,
    val codCollectedMad: Double,
    val codPendingMad: Double,
    val avgDeliveryHours: Double
)

data class BreakdownMetric(
    val label: String,
    val totalCount: Int,
    val retourCount: Int,
    val retourRatePercent: Double
)

data class MerchantSession(
    val userId: String,
    val email: String,
    val storeBrandName: String,
    val currentPlanId: String,
    val isSupabaseLive: Boolean,
    val accessToken: String? = null
)
