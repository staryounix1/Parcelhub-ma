package com.example.data.stores

import com.example.domain.model.CodSettlementStatus
import com.example.domain.model.Customer
import com.example.domain.model.ParcelOrder
import com.example.domain.model.StorePlatform
import com.example.domain.model.UnifiedParcelStatus
import com.example.domain.util.MoroccanPhoneNormalizer
import java.util.UUID

data class IncomingStoreWebhook(
    val platform: StorePlatform,
    val externalOrderRef: String,
    val customerName: String,
    val rawPhone: String,
    val city: String,
    val address: String,
    val productSummary: String,
    val codAmountMad: Double
)

object StoreWebhookProcessor {
    private val sampleMoroccanCities = listOf(
        "Casablanca", "Rabat", "Marrakech", "Tanger", "Agadir", "Fès", "Meknès", "Oujda"
    )

    private val sampleProducts = listOf(
        "ساعة ذكية Pro + سماعات",
        "حقيبة جلدية فاخرة",
        "مجموعة العناية بالبشرة (Pack 3)",
        "حذاء رياضي Running Air",
        "خلاط محمول USB"
    )

    private val sampleNames = listOf(
        "سلمى بنعلي", "يوسف العلمي", "خديجة الإدريسي", "أمين التازي", "مريم السقاط", "حمزة المنصوري"
    )

    fun toParcelOrder(
        merchantId: String,
        webhook: IncomingStoreWebhook,
        existingCustomers: List<Customer>
    ): ParcelOrder {
        val now = System.currentTimeMillis()
        val normalizedPhone = MoroccanPhoneNormalizer.normalize(webhook.rawPhone)
        val existingCustomer = existingCustomers.find { it.phoneNormalized == normalizedPhone }

        val customer = existingCustomer?.copy(
            totalOrders = existingCustomer.totalOrders + 1
        ) ?: Customer(
            id = "cust_${UUID.randomUUID().toString().take(8)}",
            fullName = webhook.customerName,
            phoneNormalized = normalizedPhone,
            city = webhook.city,
            address = webhook.address
        )

        return ParcelOrder(
            id = "ord_${UUID.randomUUID().toString().take(8)}",
            merchantId = merchantId,
            storeOrderRef = webhook.externalOrderRef,
            storePlatform = webhook.platform,
            customer = customer,
            productSummary = webhook.productSummary,
            codAmountMad = webhook.codAmountMad,
            status = UnifiedParcelStatus.CREATED,
            carrierCode = null,
            trackingNumber = null,
            labelPreviewCode = null,
            codSettlementStatus = CodSettlementStatus.PENDING_CARRIER,
            storeSynced = true,
            whatsappConfirmed = null,
            createdAtMillis = now,
            updatedAtMillis = now
        )
    }

    fun generateSimulatedStoreOrder(platform: StorePlatform): IncomingStoreWebhook {
        val city = sampleMoroccanCities.random()
        val rawPrefix = if (listOf(true, false).random()) "06" else "07"
        val rawNumber = "$rawPrefix${(10000000..99999999).random()}"
        val refPrefix = when (platform) {
            StorePlatform.YOUCAN -> "YC"
            StorePlatform.SHOPIFY -> "SHP"
            StorePlatform.WOOCOMMERCE -> "WC"
            StorePlatform.GOOGLE_SHEETS -> "GS"
        }
        return IncomingStoreWebhook(
            platform = platform,
            externalOrderRef = "$refPrefix-${(1040..9999).random()}",
            customerName = sampleNames.random(),
            rawPhone = rawNumber,
            city = city,
            address = "حي السلام، شارع الحسن الثاني، $city",
            productSummary = sampleProducts.random(),
            codAmountMad = listOf(199.0, 249.0, 299.0, 349.0, 420.0).random()
        )
    }
}
