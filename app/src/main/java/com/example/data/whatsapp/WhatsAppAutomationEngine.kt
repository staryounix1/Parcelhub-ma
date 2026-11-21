package com.example.data.whatsapp

import com.example.BuildConfig
import com.example.domain.model.NotificationLog
import com.example.domain.model.ParcelOrder
import com.example.domain.model.WhatsAppScenario
import com.example.domain.model.WhatsAppTemplate
import com.example.domain.util.MoroccanPhoneNormalizer
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.UUID

@JsonClass(generateAdapter = true)
data class WhatsAppCloudRequest(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "template",
    val template: WhatsAppCloudTemplatePayload
)

@JsonClass(generateAdapter = true)
data class WhatsAppCloudTemplatePayload(
    val name: String,
    val language: WhatsAppCloudLanguage,
    val components: List<WhatsAppCloudComponent>
)

@JsonClass(generateAdapter = true)
data class WhatsAppCloudLanguage(val code: String)

@JsonClass(generateAdapter = true)
data class WhatsAppCloudComponent(
    val type: String = "body",
    val parameters: List<WhatsAppCloudParameter>
)

@JsonClass(generateAdapter = true)
data class WhatsAppCloudParameter(
    val type: String = "text",
    val text: String
)

interface WhatsAppCloudApiService {
    @POST("v21.0/{phoneNumberId}/messages")
    suspend fun sendTemplateMessage(
        @Path("phoneNumberId") phoneNumberId: String,
        @Header("Authorization") bearerToken: String,
        @Body request: WhatsAppCloudRequest
    ): Response<Map<String, Any>>
}

sealed class WhatsAppSendOutcome {
    data class Sent(val log: NotificationLog, val viaLiveMetaApi: Boolean) : WhatsAppSendOutcome()
    data class BlockedOptOut(val phone: String) : WhatsAppSendOutcome()
    data class BlockedDuplicate(val dedupKey: String) : WhatsAppSendOutcome()
    data class Failed(val reason: String) : WhatsAppSendOutcome()
}

object WhatsAppAutomationEngine {
    val defaultTemplates: List<WhatsAppTemplate> = listOf(
        WhatsAppTemplate(
            id = "tpl_confirm",
            scenario = WhatsAppScenario.ORDER_CONFIRMATION,
            bodyDarija = "السلام عليكم {name} 👋 توصلنا بطلبك رقم {tracking}. المبلغ عند الاستلام هو {cod_amount} درهم. واش نأكدو ليك الإرسال؟ جاوب بـ [نعم] أو [لا].",
            bodyFrench = "Bonjour {name} 👋 Nous avons bien reçu votre commande {tracking}. Montant COD : {cod_amount} MAD. Confirmez-vous l'expédition ? Répondez OUI ou NON."
        ),
        WhatsAppTemplate(
            id = "tpl_shipped",
            scenario = WhatsAppScenario.SHIPPED,
            bodyDarija = "مبروك {name} 📦 الطلب ديالك تشحن برقم التتبع {tracking}. المبلغ المطلوب عند التسليم: {cod_amount} درهم. غادي يتواصل معاك الموزع قريباً.",
            bodyFrench = "Bonne nouvelle {name} 📦 Votre colis {tracking} est expédié. Montant à payer à la livraison : {cod_amount} MAD."
        ),
        WhatsAppTemplate(
            id = "tpl_ofd",
            scenario = WhatsAppScenario.OUT_FOR_DELIVERY,
            bodyDarija = "صباح الخير {name} 🚚 الموزع خرج يوصل ليك الكوليا {tracking} اليوم. عافاك خلي الهاتف شاعل ووجد {cod_amount} درهم.",
            bodyFrench = "Bonjour {name} 🚚 Votre colis {tracking} est en cours de livraison aujourd'hui. Merci de garder votre téléphone joignable ({cod_amount} MAD)."
        ),
        WhatsAppTemplate(
            id = "tpl_failed",
            scenario = WhatsAppScenario.FAILED_ATTEMPT,
            bodyDarija = "أهلاً {name} ⚠️ حاول الموزع يتصل بيك بخصوص الكوليا {tracking} ({cod_amount} درهم) وماكانش جواب. جاوبنا بالوقت المناسب لإعادة التوصيل.",
            bodyFrench = "Bonjour {name} ⚠️ Le livreur n'a pas pu vous joindre pour le colis {tracking} ({cod_amount} MAD). Indiquez-nous votre disponibilité."
        ),
        WhatsAppTemplate(
            id = "tpl_delivered",
            scenario = WhatsAppScenario.DELIVERED,
            bodyDarija = "شكراً ليك {name} ✅ تم تسليم الكوليا {tracking} بنجاح ({cod_amount} درهم). نتمنى يعجبك المنتج! لإلغاء الإشعارات كتب STOP.",
            bodyFrench = "Merci {name} ✅ Votre colis {tracking} a été livré ({cod_amount} MAD). Pour ne plus recevoir de messages, répondez STOP."
        )
    )

    private val apiService: WhatsAppCloudApiService by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl("https://graph.facebook.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(WhatsAppCloudApiService::class.java)
    }

    fun isLiveMetaConfigured(): Boolean {
        val token = BuildConfig.WHATSAPP_CLOUD_TOKEN
        val phoneId = BuildConfig.WHATSAPP_PHONE_NUMBER_ID
        return token.isNotBlank() && !token.startsWith("demo_") &&
            phoneId.isNotBlank() && !phoneId.startsWith("demo_")
    }

    fun renderBody(
        template: WhatsAppTemplate,
        order: ParcelOrder,
        useDarija: Boolean
    ): String {
        val raw = if (useDarija) template.bodyDarija else template.bodyFrench
        val trackingOrRef = order.trackingNumber ?: order.storeOrderRef
        return raw
            .replace("{name}", order.customer.fullName)
            .replace("{tracking}", trackingOrRef)
            .replace("{cod_amount}", order.codAmountMad.toInt().toString())
    }

    suspend fun sendScenarioMessage(
        order: ParcelOrder,
        scenario: WhatsAppScenario,
        useDarija: Boolean,
        templates: List<WhatsAppTemplate>,
        existingLogs: List<NotificationLog>
    ): WhatsAppSendOutcome {
        val normalizedPhone = MoroccanPhoneNormalizer.normalize(order.customer.phoneNormalized)
        if (order.customer.whatsappOptOut) {
            return WhatsAppSendOutcome.BlockedOptOut(normalizedPhone)
        }

        val dedupKey = "${order.id}_${scenario.templateKey}"
        if (existingLogs.any { it.dedupKey == dedupKey }) {
            return WhatsAppSendOutcome.BlockedDuplicate(dedupKey)
        }

        val template = templates.firstOrNull { it.scenario == scenario }
            ?: return WhatsAppSendOutcome.Failed("Template not found")
        val rendered = renderBody(template, order, useDarija)
        val trackingOrRef = order.trackingNumber ?: order.storeOrderRef
        val langTag = if (useDarija) "Darija (ar_MA)" else "Français (fr)"

        var sentViaMeta = false
        if (isLiveMetaConfigured()) {
            try {
                val payload = WhatsAppCloudRequest(
                    to = normalizedPhone.removePrefix("+"),
                    template = WhatsAppCloudTemplatePayload(
                        name = scenario.templateKey,
                        language = WhatsAppCloudLanguage(if (useDarija) "ar" else "fr"),
                        components = listOf(
                            WhatsAppCloudComponent(
                                parameters = listOf(
                                    WhatsAppCloudParameter(text = order.customer.fullName),
                                    WhatsAppCloudParameter(text = trackingOrRef),
                                    WhatsAppCloudParameter(text = order.codAmountMad.toInt().toString())
                                )
                            )
                        )
                    )
                )
                val response = apiService.sendTemplateMessage(
                    phoneNumberId = BuildConfig.WHATSAPP_PHONE_NUMBER_ID,
                    bearerToken = "Bearer ${BuildConfig.WHATSAPP_CLOUD_TOKEN}",
                    request = payload
                )
                sentViaMeta = response.isSuccessful
            } catch (_: Exception) {
                sentViaMeta = false
            }
        }

        val log = NotificationLog(
            id = "wa_${UUID.randomUUID().toString().take(8)}",
            orderId = order.id,
            trackingNumber = trackingOrRef,
            customerPhone = normalizedPhone,
            scenario = scenario,
            languageTag = langTag,
            renderedMessage = rendered,
            status = "SENT",
            dedupKey = dedupKey,
            costMad = scenario.estimatedCostMad,
            sentAtMillis = System.currentTimeMillis()
        )
        return WhatsAppSendOutcome.Sent(log, sentViaMeta)
    }
}
