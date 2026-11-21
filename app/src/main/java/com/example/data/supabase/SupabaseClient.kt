package com.example.data.supabase

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class SupabaseAuthRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthUser(
    val id: String,
    val email: String?
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    val user: SupabaseAuthUser? = null,
    val id: String? = null,
    val email: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseUserRow(
    val id: String,
    val email: String,
    @Json(name = "brand_name") val brandName: String,
    @Json(name = "preferred_lang") val preferredLang: String = "ar"
)

@JsonClass(generateAdapter = true)
data class SupabaseCustomerRow(
    val id: String,
    @Json(name = "merchant_id") val merchantId: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "phone_e164") val phoneE164: String,
    val city: String,
    val address: String? = null,
    @Json(name = "total_orders") val totalOrders: Int = 1,
    @Json(name = "return_count") val returnCount: Int = 0,
    @Json(name = "is_blacklisted") val isBlacklisted: Boolean = false,
    @Json(name = "whatsapp_opt_out") val whatsappOptOut: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SupabaseOrderRow(
    val id: String,
    @Json(name = "merchant_id") val merchantId: String,
    @Json(name = "customer_id") val customerId: String,
    @Json(name = "store_order_ref") val storeOrderRef: String,
    @Json(name = "store_platform") val storePlatform: String,
    @Json(name = "product_summary") val productSummary: String,
    @Json(name = "cod_amount_mad") val codAmountMad: Double,
    @Json(name = "unified_status") val unifiedStatus: String,
    @Json(name = "whatsapp_confirmed") val whatsappConfirmed: Boolean? = null,
    @Json(name = "store_synced") val storeSynced: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SupabaseShipmentRow(
    val id: String,
    @Json(name = "merchant_id") val merchantId: String,
    @Json(name = "order_id") val orderId: String,
    val carrier: String,
    @Json(name = "tracking_number") val trackingNumber: String,
    @Json(name = "label_url") val labelUrl: String? = null,
    @Json(name = "carrier_fee_mad") val carrierFeeMad: Double = 35.0,
    @Json(name = "cod_settlement_status") val codSettlementStatus: String = "PENDING_CARRIER"
)

@JsonClass(generateAdapter = true)
data class SupabaseTrackingLogRow(
    val id: String,
    @Json(name = "merchant_id") val merchantId: String,
    @Json(name = "shipment_id") val shipmentId: String,
    @Json(name = "external_event_id") val externalEventId: String,
    @Json(name = "raw_status") val rawStatus: String,
    @Json(name = "unified_status") val unifiedStatus: String,
    val note: String? = null,
    val source: String
)

@JsonClass(generateAdapter = true)
data class SupabaseNotificationLogRow(
    val id: String,
    @Json(name = "merchant_id") val merchantId: String,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "customer_phone") val customerPhone: String,
    val scenario: String,
    @Json(name = "dedup_key") val dedupKey: String,
    @Json(name = "provider_status") val providerStatus: String,
    @Json(name = "cost_mad") val costMad: Double = 0.16
)

interface SupabaseRestApi {
    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseAuthRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") apiKey: String,
        @Body body: SupabaseAuthRequest
    ): Response<SupabaseAuthResponse>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/users")
    suspend fun upsertUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body row: SupabaseUserRow
    ): Response<List<SupabaseUserRow>>

    @GET("rest/v1/customers")
    suspend fun fetchCustomers(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseCustomerRow>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/customers")
    suspend fun upsertCustomer(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body row: SupabaseCustomerRow
    ): Response<List<SupabaseCustomerRow>>

    @GET("rest/v1/orders")
    suspend fun fetchOrders(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<SupabaseOrderRow>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/orders")
    suspend fun upsertOrder(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body row: SupabaseOrderRow
    ): Response<List<SupabaseOrderRow>>

    @GET("rest/v1/shipments")
    suspend fun fetchShipments(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseShipmentRow>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/shipments")
    suspend fun upsertShipment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body row: SupabaseShipmentRow
    ): Response<List<SupabaseShipmentRow>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/tracking_logs")
    suspend fun upsertTrackingLog(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body row: SupabaseTrackingLogRow
    ): Response<List<SupabaseTrackingLogRow>>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/notification_logs")
    suspend fun upsertNotificationLog(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body row: SupabaseNotificationLogRow
    ): Response<List<SupabaseNotificationLogRow>>
}

sealed class SupabaseSyncResult<out T> {
    data class Success<T>(val data: T) : SupabaseSyncResult<T>()
    data class SandboxFallback(val reason: String) : SupabaseSyncResult<Nothing>()
    data class Error(val message: String) : SupabaseSyncResult<Nothing>()
}

data class SupabaseRemoteBundle(
    val customers: List<SupabaseCustomerRow>,
    val orders: List<SupabaseOrderRow>,
    val shipments: List<SupabaseShipmentRow>
)

object SupabaseClient {
    fun isConfigured(): Boolean {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        return url.startsWith("https://") &&
            !url.contains("demo-project.supabase.co") &&
            key.isNotBlank() &&
            !key.startsWith("demo_")
    }

    private val api: SupabaseRestApi? by lazy {
        if (!isConfigured()) return@lazy null
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/') + "/"
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseRestApi::class.java)
    }

    private fun extractErrorReason(rawBody: String?, fallback: String): String {
        if (rawBody.isNullOrBlank()) return fallback
        return try {
            val json = org.json.JSONObject(rawBody)
            json.optString("msg").takeIf { it.isNotBlank() }
                ?: json.optString("error_description").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
                ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    suspend fun signIn(email: String, password: String): SupabaseSyncResult<SupabaseAuthResponse> {
        val service = api ?: return SupabaseSyncResult.SandboxFallback(
            "Local merchant session mode"
        )
        return try {
            val res = service.signInWithPassword(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                body = SupabaseAuthRequest(email.trim(), password)
            )
            if (res.isSuccessful && res.body() != null) {
                SupabaseSyncResult.Success(res.body()!!)
            } else {
                val reason = extractErrorReason(res.errorBody()?.string(), "HTTP ${res.code()}")
                SupabaseSyncResult.Error(reason)
            }
        } catch (e: Exception) {
            SupabaseSyncResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        brandName: String? = null
    ): SupabaseSyncResult<SupabaseAuthResponse> {
        val service = api ?: return SupabaseSyncResult.SandboxFallback(
            "Local merchant session mode"
        )
        val metadata = brandName?.trim()?.takeIf { it.isNotBlank() }?.let {
            mapOf("brand_name" to it)
        }
        return try {
            val res = service.signUp(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                body = SupabaseAuthRequest(email.trim(), password, metadata)
            )
            if (res.isSuccessful && res.body() != null) {
                SupabaseSyncResult.Success(res.body()!!)
            } else {
                val reason = extractErrorReason(res.errorBody()?.string(), "HTTP ${res.code()}")
                SupabaseSyncResult.Error(reason)
            }
        } catch (e: Exception) {
            SupabaseSyncResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun syncMerchantProfile(
        accessToken: String?,
        userRow: SupabaseUserRow
    ): SupabaseSyncResult<Unit> {
        val service = api ?: return SupabaseSyncResult.SandboxFallback("Sandbox mode")
        val token = accessToken?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPABASE_ANON_KEY
        return try {
            val res = service.upsertUser(BuildConfig.SUPABASE_ANON_KEY, "Bearer $token", userRow)
            if (res.isSuccessful) SupabaseSyncResult.Success(Unit)
            else SupabaseSyncResult.Error("Users HTTP ${res.code()}")
        } catch (e: Exception) {
            SupabaseSyncResult.Error(e.localizedMessage ?: "Sync error")
        }
    }

    suspend fun fetchRemoteBundle(accessToken: String?): SupabaseSyncResult<SupabaseRemoteBundle> {
        val service = api ?: return SupabaseSyncResult.SandboxFallback("Sandbox mode")
        val token = accessToken?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPABASE_ANON_KEY
        val bearer = "Bearer $token"
        return try {
            val customersRes = service.fetchCustomers(BuildConfig.SUPABASE_ANON_KEY, bearer)
            val ordersRes = service.fetchOrders(BuildConfig.SUPABASE_ANON_KEY, bearer)
            val shipmentsRes = service.fetchShipments(BuildConfig.SUPABASE_ANON_KEY, bearer)
            if (ordersRes.isSuccessful) {
                SupabaseSyncResult.Success(
                    SupabaseRemoteBundle(
                        customers = customersRes.body().orEmpty(),
                        orders = ordersRes.body().orEmpty(),
                        shipments = shipmentsRes.body().orEmpty()
                    )
                )
            } else {
                SupabaseSyncResult.Error("Fetch HTTP ${ordersRes.code()}")
            }
        } catch (e: Exception) {
            SupabaseSyncResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun pushOrderBundle(
        accessToken: String?,
        customer: SupabaseCustomerRow,
        order: SupabaseOrderRow,
        shipment: SupabaseShipmentRow? = null,
        latestTrackingLog: SupabaseTrackingLogRow? = null
    ): SupabaseSyncResult<Unit> {
        val service = api ?: return SupabaseSyncResult.SandboxFallback("Sandbox mode")
        val token = accessToken?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPABASE_ANON_KEY
        val bearer = "Bearer $token"
        return try {
            service.upsertCustomer(BuildConfig.SUPABASE_ANON_KEY, bearer, customer)
            val ordRes = service.upsertOrder(BuildConfig.SUPABASE_ANON_KEY, bearer, order)
            if (shipment != null) {
                service.upsertShipment(BuildConfig.SUPABASE_ANON_KEY, bearer, shipment)
            }
            if (latestTrackingLog != null) {
                service.upsertTrackingLog(BuildConfig.SUPABASE_ANON_KEY, bearer, latestTrackingLog)
            }
            if (ordRes.isSuccessful) SupabaseSyncResult.Success(Unit)
            else SupabaseSyncResult.Error("PostgREST HTTP ${ordRes.code()}")
        } catch (e: Exception) {
            SupabaseSyncResult.Error(e.localizedMessage ?: "Sync error")
        }
    }

    suspend fun pushNotificationLog(
        accessToken: String?,
        row: SupabaseNotificationLogRow
    ): SupabaseSyncResult<Unit> {
        val service = api ?: return SupabaseSyncResult.SandboxFallback("Sandbox mode")
        val token = accessToken?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPABASE_ANON_KEY
        return try {
            val res = service.upsertNotificationLog(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                bearer = "Bearer $token",
                row = row
            )
            if (res.isSuccessful) SupabaseSyncResult.Success(Unit)
            else SupabaseSyncResult.Error("NotifLog HTTP ${res.code()}")
        } catch (e: Exception) {
            SupabaseSyncResult.Error(e.localizedMessage ?: "Sync error")
        }
    }
}
