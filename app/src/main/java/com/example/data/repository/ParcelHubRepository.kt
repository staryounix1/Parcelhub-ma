package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.carriers.CarrierRegistry
import com.example.data.carriers.CreateShipmentRequest
import com.example.data.carriers.LabelResult
import com.example.data.carriers.StatusMapper
import com.example.data.carriers.WebhookSignatureVerifier
import com.example.data.stores.IncomingStoreWebhook
import com.example.data.stores.StoreWebhookProcessor
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseCustomerRow
import com.example.data.supabase.SupabaseNotificationLogRow
import com.example.data.supabase.SupabaseOrderRow
import com.example.data.supabase.SupabaseShipmentRow
import com.example.data.supabase.SupabaseSyncResult
import com.example.data.supabase.SupabaseTrackingLogRow
import com.example.data.supabase.SupabaseUserRow
import com.example.data.whatsapp.WhatsAppAutomationEngine
import com.example.data.whatsapp.WhatsAppSendOutcome
import com.example.domain.model.BreakdownMetric
import com.example.domain.model.CarrierAccountConfig
import com.example.domain.model.CarrierCode
import com.example.domain.model.CodBatchSummary
import com.example.domain.model.CodSettlementStatus
import com.example.domain.model.Customer
import com.example.domain.model.DashboardKpis
import com.example.domain.model.DeadLetterEvent
import com.example.domain.model.MerchantSession
import com.example.domain.model.NotificationLog
import com.example.domain.model.ParcelOrder
import com.example.domain.model.StoreConnection
import com.example.domain.model.StorePlatform
import com.example.domain.model.TrackingLog
import com.example.domain.model.UnifiedParcelStatus
import com.example.domain.model.WhatsAppScenario
import com.example.domain.model.WhatsAppTemplate
import com.example.domain.util.CasablancaTime
import com.example.domain.util.MoroccanPhoneNormalizer
import com.example.ui.theme.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "parcelhub_prefs")

data class ParcelHubSnapshot(
    val session: MerchantSession,
    val orders: List<ParcelOrder>,
    val stores: List<StoreConnection>,
    val carrierAccounts: List<CarrierAccountConfig>,
    val templates: List<WhatsAppTemplate>,
    val notificationLogs: List<NotificationLog>,
    val deadLetters: List<DeadLetterEvent>
)

class ParcelHubRepository(private val context: Context) {

    private val KEY_LANGUAGE = stringPreferencesKey("app_language")
    private val KEY_EMAIL = stringPreferencesKey("merchant_email")
    private val KEY_USER_ID = stringPreferencesKey("merchant_user_id")
    private val KEY_STORE_NAME = stringPreferencesKey("merchant_store_name")
    private val KEY_ACCESS_TOKEN = stringPreferencesKey("supabase_access_token")
    private val KEY_LOCAL_ACCOUNTS = stringPreferencesKey("local_merchant_accounts")

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_LANGUAGE]) {
            AppLanguage.FR.code -> AppLanguage.FR
            else -> AppLanguage.AR
        }
    }

    private val _snapshot = MutableStateFlow(createInitialSnapshot())
    val snapshot: StateFlow<ParcelHubSnapshot> = _snapshot.asStateFlow()

    suspend fun restoreSessionFromPrefs() {
        val prefs = context.dataStore.data.first()
        val email = prefs[KEY_EMAIL]
        val userId = prefs[KEY_USER_ID]
        val storeName = prefs[KEY_STORE_NAME]
        val token = prefs[KEY_ACCESS_TOKEN]
        if (!email.isNullOrBlank() && !userId.isNullOrBlank()) {
            val isRemoteToken = !token.isNullOrBlank() && !token.startsWith("local_session_")
            _snapshot.update {
                it.copy(
                    session = it.session.copy(
                        userId = userId,
                        email = email,
                        storeBrandName = storeName?.takeIf { name -> name.isNotBlank() } ?: it.session.storeBrandName,
                        accessToken = token,
                        isSupabaseLive = SupabaseClient.isConfigured() && isRemoteToken
                    )
                )
            }
            if (SupabaseClient.isConfigured() && isRemoteToken) {
                syncWithSupabase()
            }
        }
    }

    suspend fun setLanguage(lang: AppLanguage) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang.code }
    }

    private fun hashLocalPassword(email: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("parcelhub_ma:$email:$password".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun translateAuthError(raw: String, isArabic: Boolean, isSignUp: Boolean): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("already registered") || lower.contains("already exists") ->
                if (isArabic) "هذا البريد الإلكتروني مسجل مسبقاً، يرجى تسجيل الدخول"
                else "Cet email est déjà utilisé, veuillez vous connecter"
            lower.contains("invalid login credentials") ->
                if (isArabic) "البريد الإلكتروني أو كلمة المرور غير صحيحة"
                else "Email ou mot de passe incorrect"
            lower.contains("email not confirmed") ->
                if (isArabic) "يرجى تفعيل بريدك الإلكتروني عبر الرابط المرسل إليك أولاً"
                else "Veuillez confirmer votre email via le lien reçu"
            lower.contains("password") || lower.contains("6 characters") ->
                if (isArabic) "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل"
                else "Le mot de passe doit contenir au moins 6 caractères"
            lower.contains("email") && (lower.contains("invalid") || lower.contains("unable")) ->
                if (isArabic) "صيغة البريد الإلكتروني غير صالحة"
                else "Adresse email invalide"
            lower.contains("rate limit") || lower.contains("too many") || lower.contains("429") ->
                if (isArabic) "محاولات كثيرة متتالية، يرجى الانتظار قليلاً ثم إعادة المحاولة"
                else "Trop de tentatives, veuillez patienter un instant"
            isSignUp ->
                if (isArabic) "تعذر إنشاء الحساب: تأكد من صحة البيانات واتصالك بالإنترنت"
                else "Impossible de créer le compte : vérifiez vos informations et votre connexion"
            else ->
                if (isArabic) "تعذر تسجيل الدخول: تأكد من البريد الإلكتروني وكلمة المرور"
                else "Connexion impossible : vérifiez votre email et mot de passe"
        }
    }

    suspend fun signInSupabase(email: String, password: String): Result<String> {
        val isArabic = languageFlow.first() == AppLanguage.AR
        val cleanEmail = email.trim().lowercase()
        if (!EMAIL_REGEX.matches(cleanEmail)) {
            return Result.failure(
                IllegalArgumentException(
                    if (isArabic) "يرجى إدخال بريد إلكتروني صالح" else "Veuillez saisir un email valide"
                )
            )
        }
        if (password.length < 6) {
            return Result.failure(
                IllegalArgumentException(
                    if (isArabic) "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل"
                    else "Le mot de passe doit contenir au moins 6 caractères"
                )
            )
        }

        return when (val res = SupabaseClient.signIn(cleanEmail, password)) {
            is SupabaseSyncResult.Success -> {
                val userId = res.data.user?.id ?: res.data.id ?: "merch_${UUID.randomUUID().toString().take(8)}"
                val token = res.data.accessToken
                val brand = _snapshot.value.session.storeBrandName
                context.dataStore.edit {
                    it[KEY_EMAIL] = cleanEmail
                    it[KEY_USER_ID] = userId
                    it[KEY_STORE_NAME] = brand
                    if (token != null) it[KEY_ACCESS_TOKEN] = token
                }
                _snapshot.update {
                    it.copy(
                        session = it.session.copy(
                            userId = userId,
                            email = cleanEmail,
                            isSupabaseLive = token != null,
                            accessToken = token
                        )
                    )
                }
                if (token != null) {
                    SupabaseClient.syncMerchantProfile(
                        token,
                        SupabaseUserRow(userId, cleanEmail, brand)
                    )
                    syncWithSupabase()
                }
                Result.success(if (isArabic) "تم تسجيل الدخول بنجاح" else "Connexion réussie")
            }
            is SupabaseSyncResult.SandboxFallback -> {
                val prefs = context.dataStore.data.first()
                val accountsJson = runCatching {
                    JSONObject(prefs[KEY_LOCAL_ACCOUNTS] ?: "{}")
                }.getOrDefault(JSONObject())

                val existing = accountsJson.optJSONObject(cleanEmail)
                val (resolvedUserId, resolvedBrand) = if (existing != null) {
                    val expectedHash = existing.optString("passwordHash")
                    val actualHash = hashLocalPassword(cleanEmail, password)
                    if (expectedHash.isNotBlank() && expectedHash != actualHash) {
                        return Result.failure(
                            IllegalStateException(
                                if (isArabic) "كلمة المرور غير صحيحة" else "Mot de passe incorrect"
                            )
                        )
                    }
                    existing.optString("userId", "merch_${UUID.randomUUID().toString().take(8)}") to
                        existing.optString("storeName", "Atlas Commerce MA")
                } else if (cleanEmail == "atlas.shop@parcelhub.ma") {
                    "merch_atlas_01" to "Atlas Commerce MA"
                } else {
                    return Result.failure(
                        IllegalStateException(
                            if (isArabic) "لا يوجد حساب مسجل بهذا البريد الإلكتروني، اختر «حساب جديد» لإنشائه"
                            else "Aucun compte trouvé pour cet email. Choisissez « Créer un compte »"
                        )
                    )
                }

                val localToken = "local_session_${UUID.randomUUID().toString().take(8)}"
                context.dataStore.edit {
                    it[KEY_EMAIL] = cleanEmail
                    it[KEY_USER_ID] = resolvedUserId
                    it[KEY_STORE_NAME] = resolvedBrand
                    it[KEY_ACCESS_TOKEN] = localToken
                }
                _snapshot.update {
                    it.copy(
                        session = it.session.copy(
                            userId = resolvedUserId,
                            email = cleanEmail,
                            storeBrandName = resolvedBrand,
                            isSupabaseLive = false,
                            accessToken = localToken
                        )
                    )
                }
                Result.success(
                    if (isArabic) "تم تسجيل الدخول إلى «$resolvedBrand» بنجاح"
                    else "Connecté à « $resolvedBrand »"
                )
            }
            is SupabaseSyncResult.Error -> {
                Result.failure(IllegalStateException(translateAuthError(res.message, isArabic, isSignUp = false)))
            }
        }
    }

    suspend fun signUpSupabase(
        email: String,
        password: String,
        storeBrandName: String = ""
    ): Result<String> {
        val isArabic = languageFlow.first() == AppLanguage.AR
        val cleanEmail = email.trim().lowercase()
        val cleanBrand = storeBrandName.trim().ifBlank {
            cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "My Store MA" }
        }
        if (!EMAIL_REGEX.matches(cleanEmail)) {
            return Result.failure(
                IllegalArgumentException(
                    if (isArabic) "يرجى إدخال بريد إلكتروني صالح" else "Veuillez saisir un email valide"
                )
            )
        }
        if (password.length < 6) {
            return Result.failure(
                IllegalArgumentException(
                    if (isArabic) "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل"
                    else "Le mot de passe doit contenir au moins 6 caractères"
                )
            )
        }

        return when (val res = SupabaseClient.signUp(cleanEmail, password, cleanBrand)) {
            is SupabaseSyncResult.Success -> {
                val userId = res.data.user?.id ?: res.data.id ?: "merch_${UUID.randomUUID().toString().take(8)}"
                val token = res.data.accessToken
                context.dataStore.edit {
                    it[KEY_EMAIL] = cleanEmail
                    it[KEY_USER_ID] = userId
                    it[KEY_STORE_NAME] = cleanBrand
                    if (token != null) it[KEY_ACCESS_TOKEN] = token else it.remove(KEY_ACCESS_TOKEN)
                }
                _snapshot.update {
                    it.copy(
                        session = it.session.copy(
                            userId = userId,
                            email = cleanEmail,
                            storeBrandName = cleanBrand,
                            isSupabaseLive = token != null,
                            accessToken = token
                        )
                    )
                }
                if (token != null) {
                    SupabaseClient.syncMerchantProfile(
                        token,
                        SupabaseUserRow(userId, cleanEmail, cleanBrand)
                    )
                    Result.success(
                        if (isArabic) "تم إنشاء حساب متجرك «$cleanBrand» بنجاح"
                        else "Compte boutique « $cleanBrand » créé avec succès"
                    )
                } else {
                    Result.success(
                        if (isArabic) "تم إنشاء الحساب! يرجى تأكيد بريدك الإلكتروني عبر الرابط المرسل إليك"
                        else "Compte créé ! Veuillez confirmer votre email via le lien reçu"
                    )
                }
            }
            is SupabaseSyncResult.SandboxFallback -> {
                val prefs = context.dataStore.data.first()
                val accountsJson = runCatching {
                    JSONObject(prefs[KEY_LOCAL_ACCOUNTS] ?: "{}")
                }.getOrDefault(JSONObject())

                if (accountsJson.has(cleanEmail)) {
                    return Result.failure(
                        IllegalStateException(
                            if (isArabic) "هذا البريد الإلكتروني مسجل مسبقاً، يرجى تسجيل الدخول"
                            else "Cet email est déjà utilisé, veuillez vous connecter"
                        )
                    )
                }

                val newUserId = "merch_${UUID.randomUUID().toString().take(8)}"
                val localToken = "local_session_${UUID.randomUUID().toString().take(8)}"
                val accountObj = JSONObject().apply {
                    put("userId", newUserId)
                    put("storeName", cleanBrand)
                    put("passwordHash", hashLocalPassword(cleanEmail, password))
                }
                accountsJson.put(cleanEmail, accountObj)

                context.dataStore.edit {
                    it[KEY_LOCAL_ACCOUNTS] = accountsJson.toString()
                    it[KEY_EMAIL] = cleanEmail
                    it[KEY_USER_ID] = newUserId
                    it[KEY_STORE_NAME] = cleanBrand
                    it[KEY_ACCESS_TOKEN] = localToken
                }
                _snapshot.update {
                    it.copy(
                        session = it.session.copy(
                            userId = newUserId,
                            email = cleanEmail,
                            storeBrandName = cleanBrand,
                            isSupabaseLive = false,
                            accessToken = localToken
                        )
                    )
                }
                Result.success(
                    if (isArabic) "تم فتح حساب متجرك «$cleanBrand» بنجاح"
                    else "Compte boutique « $cleanBrand » créé avec succès"
                )
            }
            is SupabaseSyncResult.Error -> {
                Result.failure(IllegalStateException(translateAuthError(res.message, isArabic, isSignUp = true)))
            }
        }
    }

    suspend fun signOut() {
        context.dataStore.edit {
            it.remove(KEY_EMAIL)
            it.remove(KEY_USER_ID)
            it.remove(KEY_STORE_NAME)
            it.remove(KEY_ACCESS_TOKEN)
        }
        _snapshot.update {
            it.copy(
                session = it.session.copy(
                    userId = "merch_atlas_01",
                    email = "atlas.shop@parcelhub.ma",
                    storeBrandName = "Atlas Commerce MA",
                    isSupabaseLive = SupabaseClient.isConfigured(),
                    accessToken = null
                )
            )
        }
    }

    suspend fun syncWithSupabase(): String {
        val session = _snapshot.value.session
        if (!SupabaseClient.isConfigured()) {
            return "تم تحديث قائمة الطلبات"
        }
        return when (val bundleRes = SupabaseClient.fetchRemoteBundle(session.accessToken)) {
            is SupabaseSyncResult.Success -> {
                val bundle = bundleRes.data
                if (bundle.orders.isEmpty()) {
                    _snapshot.value.orders.forEach { pushOrderToSupabaseIfConfigured(it) }
                    "تم حفظ ${_snapshot.value.orders.size} طلبات بنجاح"
                } else {
                    val customerMap = bundle.customers.associateBy { it.id }
                    val shipmentMap = bundle.shipments.associateBy { it.orderId }
                    val now = System.currentTimeMillis()
                    val remoteOrders = bundle.orders.map { row ->
                        val cust = customerMap[row.customerId]
                        val ship = shipmentMap[row.id]
                        ParcelOrder(
                            id = row.id,
                            merchantId = row.merchantId,
                            storeOrderRef = row.storeOrderRef,
                            storePlatform = runCatching { StorePlatform.valueOf(row.storePlatform) }
                                .getOrDefault(StorePlatform.YOUCAN),
                            customer = Customer(
                                id = row.customerId,
                                fullName = cust?.fullName ?: "زبون",
                                phoneNormalized = cust?.phoneE164 ?: "+212600000000",
                                city = cust?.city ?: "Casablanca",
                                address = cust?.address ?: "",
                                totalOrders = cust?.totalOrders ?: 1,
                                returnCount = cust?.returnCount ?: 0,
                                isBlacklisted = cust?.isBlacklisted ?: false,
                                whatsappOptOut = cust?.whatsappOptOut ?: false
                            ),
                            productSummary = row.productSummary,
                            codAmountMad = row.codAmountMad,
                            status = runCatching { UnifiedParcelStatus.valueOf(row.unifiedStatus) }
                                .getOrDefault(UnifiedParcelStatus.CREATED),
                            carrierCode = ship?.carrier?.let { c ->
                                runCatching { CarrierCode.valueOf(c) }.getOrNull()
                            },
                            trackingNumber = ship?.trackingNumber,
                            labelPreviewCode = ship?.labelUrl,
                            codSettlementStatus = ship?.codSettlementStatus?.let { cs ->
                                runCatching { CodSettlementStatus.valueOf(cs) }
                                    .getOrDefault(CodSettlementStatus.PENDING_CARRIER)
                            } ?: CodSettlementStatus.PENDING_CARRIER,
                            storeSynced = row.storeSynced,
                            whatsappConfirmed = row.whatsappConfirmed,
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                    }
                    _snapshot.update { it.copy(orders = remoteOrders) }
                    "تم تحديث ${remoteOrders.size} طلبات"
                }
            }
            is SupabaseSyncResult.SandboxFallback -> "تم تحديث قائمة الطلبات"
            is SupabaseSyncResult.Error -> "تعذر التحديث حالياً، يرجى المحاولة لاحقاً"
        }
    }

    suspend fun createManualOrder(
        customerName: String,
        rawPhone: String,
        city: String,
        address: String,
        productSummary: String,
        codAmountMad: Double,
        platform: StorePlatform
    ): Result<ParcelOrder> {
        val normalized = MoroccanPhoneNormalizer.normalize(rawPhone)
        if (!MoroccanPhoneNormalizer.isValidMoroccanMobile(normalized)) {
            return Result.failure(IllegalArgumentException("رقم الهاتف يجب أن يكون مغربياً صحيحاً (06/07 -> +212)"))
        }
        val state = _snapshot.value
        val existingCustomers = state.orders.map { it.customer }
        val order = StoreWebhookProcessor.toParcelOrder(
            merchantId = state.session.userId,
            webhook = IncomingStoreWebhook(
                platform = platform,
                externalOrderRef = "MAN-${(1000..9999).random()}",
                customerName = customerName.trim(),
                rawPhone = normalized,
                city = city.trim(),
                address = address.trim().ifEmpty { city.trim() },
                productSummary = productSummary.trim(),
                codAmountMad = codAmountMad
            ),
            existingCustomers = existingCustomers
        )
        _snapshot.update { current ->
            current.copy(orders = listOf(order) + current.orders)
        }
        pushOrderToSupabaseIfConfigured(order)
        return Result.success(order)
    }

    suspend fun importSimulatedStoreOrder(platform: StorePlatform): ParcelOrder {
        val webhook = StoreWebhookProcessor.generateSimulatedStoreOrder(platform)
        val state = _snapshot.value
        val order = StoreWebhookProcessor.toParcelOrder(
            merchantId = state.session.userId,
            webhook = webhook,
            existingCustomers = state.orders.map { it.customer }
        )
        _snapshot.update { current ->
            val updatedStores = current.stores.map { store ->
                if (store.platform == platform) {
                    store.copy(
                        importedOrdersCount = store.importedOrdersCount + 1,
                        lastSyncMillis = System.currentTimeMillis()
                    )
                } else {
                    store
                }
            }
            current.copy(
                orders = listOf(order) + current.orders,
                stores = updatedStores
            )
        }
        pushOrderToSupabaseIfConfigured(order)
        return order
    }

    suspend fun dispatchOrderToCarrier(
        orderId: String,
        carrierCode: CarrierCode,
        sendWhatsAppOnShip: Boolean = true,
        useDarija: Boolean = true
    ): Result<LabelResult> {
        val currentState = _snapshot.value
        val order = currentState.orders.find { it.id == orderId }
            ?: return Result.failure(IllegalArgumentException("Order not found"))

        val adapter = CarrierRegistry.get(carrierCode)
        val result = adapter.createShipment(
            CreateShipmentRequest(
                orderId = order.id,
                recipientName = order.customer.fullName,
                recipientPhone = order.customer.phoneNormalized,
                city = order.customer.city,
                address = order.customer.address,
                productSummary = order.productSummary,
                codAmountMad = order.codAmountMad,
                merchantReference = order.storeOrderRef
            )
        )

        if (!result.success) {
            return Result.failure(IllegalStateException(result.errorMessage ?: "Carrier error"))
        }

        val duplicateTracking = currentState.orders.any {
            it.id != orderId && it.trackingNumber == result.trackingNumber
        }
        val finalTracking = if (duplicateTracking) {
            "${result.trackingNumber}-R"
        } else {
            result.trackingNumber
        }

        val label = adapter.getLabel(finalTracking)
        val now = System.currentTimeMillis()
        val initialLog = TrackingLog(
            id = "log_${UUID.randomUUID().toString().take(8)}",
            shipmentId = order.id,
            externalEventId = "create_$finalTracking",
            rawStatus = "PICKED_UP",
            unifiedStatus = UnifiedParcelStatus.PICKED_UP,
            note = "تم إرسال الطرد إلى ${carrierCode.displayName}",
            eventEpochMillis = now,
            source = "API"
        )

        val updatedOrder = order.copy(
            carrierCode = carrierCode,
            trackingNumber = finalTracking,
            labelPreviewCode = label.barcodeText,
            status = UnifiedParcelStatus.PICKED_UP,
            updatedAtMillis = now,
            storeSynced = true,
            trackingLogs = listOf(initialLog) + order.trackingLogs
        )

        _snapshot.update { state ->
            state.copy(
                orders = state.orders.map { if (it.id == orderId) updatedOrder else it },
                carrierAccounts = state.carrierAccounts.map { cfg ->
                    if (cfg.carrierCode == carrierCode) {
                        cfg.copy(activeShipmentsCount = cfg.activeShipmentsCount + 1)
                    } else {
                        cfg
                    }
                }
            )
        }

        if (sendWhatsAppOnShip) {
            sendWhatsAppNotification(orderId, WhatsAppScenario.SHIPPED, useDarija)
        }
        pushOrderToSupabaseIfConfigured(updatedOrder)
        return Result.success(label)
    }

    suspend fun cancelOrderShipment(orderId: String): Result<String> {
        val order = _snapshot.value.orders.find { it.id == orderId }
            ?: return Result.failure(IllegalArgumentException("Order not found"))
        if (order.status.isTerminal) {
            return Result.failure(IllegalStateException("لا يمكن إلغاء شحنة في حالة نهائية (${order.status.name})"))
        }

        val carrier = order.carrierCode
        val tracking = order.trackingNumber
        val cancelledOnCarrier = if (carrier != null && tracking != null) {
            CarrierRegistry.get(carrier).cancelShipment(tracking)
        } else {
            true
        }

        if (!cancelledOnCarrier) {
            return Result.failure(IllegalStateException("رفضت شركة التوصيل إلغاء الشحنة"))
        }

        val now = System.currentTimeMillis()
        val log = TrackingLog(
            id = "cancel_${UUID.randomUUID().toString().take(8)}",
            shipmentId = order.id,
            externalEventId = "cancel_${order.id}_$now",
            rawStatus = "CANCELLED",
            unifiedStatus = UnifiedParcelStatus.CANCELLED,
            note = "تم إلغاء الشحنة من التاجر",
            eventEpochMillis = now,
            source = "MANUAL"
        )
        val updatedOrder = order.copy(
            status = UnifiedParcelStatus.CANCELLED,
            updatedAtMillis = now,
            storeSynced = true,
            trackingLogs = listOf(log) + order.trackingLogs
        )
        _snapshot.update { current ->
            current.copy(orders = current.orders.map { if (it.id == orderId) updatedOrder else it })
        }
        pushOrderToSupabaseIfConfigured(updatedOrder)
        return Result.success("تم إلغاء الشحنة ومزامنة الإلغاء مع المتجر")
    }

    suspend fun syncOrderBackToStore(orderId: String): String {
        val order = _snapshot.value.orders.find { it.id == orderId } ?: return "الطلب غير موجود"
        val updated = order.copy(storeSynced = true, updatedAtMillis = System.currentTimeMillis())
        _snapshot.update { current ->
            current.copy(orders = current.orders.map { if (it.id == orderId) updated else it })
        }
        pushOrderToSupabaseIfConfigured(updated)
        return "تمت مزامنة حالة ${updated.status.name} إلى متجر ${updated.storePlatform.displayName}"
    }

    suspend fun getLabelPreview(order: ParcelOrder): LabelResult? {
        val carrier = order.carrierCode ?: return null
        val tracking = order.trackingNumber ?: return null
        return CarrierRegistry.get(carrier).getLabel(tracking)
    }

    suspend fun processCarrierWebhook(
        orderId: String,
        rawStatus: String,
        externalEventId: String,
        note: String,
        validSignature: Boolean = true,
        eventEpochMillis: Long = System.currentTimeMillis()
    ): Result<String> {
        val state = _snapshot.value
        val order = state.orders.find { it.id == orderId }
            ?: return Result.failure(IllegalArgumentException("Order not found"))
        val carrier = order.carrierCode
            ?: return Result.failure(IllegalStateException("يجب إرسال الطرد لشركة توصيل أولاً"))

        val payloadString = "${order.trackingNumber}|$rawStatus|$externalEventId"
        val demoSecret = "whsec_parcelhub_ma"
        val sig = if (validSignature) {
            WebhookSignatureVerifier.computeHmacSha256(demoSecret, payloadString)
        } else {
            "invalid_signature_000"
        }

        if (!WebhookSignatureVerifier.verifySignature(demoSecret, payloadString, sig)) {
            val dlq = DeadLetterEvent(
                id = "dlq_${UUID.randomUUID().toString().take(6)}",
                source = carrier.displayName,
                payloadSummary = "طرد ${order.trackingNumber} -> $rawStatus",
                failureReason = "تعذر التحقق من صحة التحديث — في انتظار إعادة المحاولة",
                retryCount = 1,
                nextRetryMillis = System.currentTimeMillis() + 60_000L,
                createdAtMillis = System.currentTimeMillis()
            )
            _snapshot.update { it.copy(deadLetters = listOf(dlq) + it.deadLetters) }
            return Result.failure(SecurityException("تعذر التحقق من التحديث وتمت إضافته لإعادة المحاولة"))
        }

        if (order.trackingLogs.any { it.externalEventId == externalEventId }) {
            return Result.success("تم تسجيل هذا التحديث مسبقاً")
        }

        val parsed = CarrierRegistry.get(carrier).parseWebhook(
            mapOf(
                "event_id" to externalEventId,
                "tracking_number" to (order.trackingNumber ?: ""),
                "status" to rawStatus,
                "note" to note,
                "timestamp" to eventEpochMillis.toString()
            )
        )

        if (!StatusMapper.shouldApplyTransition(
                currentStatus = order.status,
                incomingStatus = parsed.unifiedStatus,
                currentUpdatedMillis = order.updatedAtMillis,
                incomingEventMillis = parsed.eventEpochMillis
            )
        ) {
            return Result.success("لا يمكن الرجوع لحالة سابقة لطرد في حالة (${order.status.labelAr})")
        }

        val log = TrackingLog(
            id = "log_${UUID.randomUUID().toString().take(8)}",
            shipmentId = order.id,
            externalEventId = parsed.externalEventId,
            rawStatus = parsed.rawStatus,
            unifiedStatus = parsed.unifiedStatus,
            note = parsed.note,
            eventEpochMillis = parsed.eventEpochMillis,
            source = "WEBHOOK"
        )

        val newCodStatus = when (parsed.unifiedStatus) {
            UnifiedParcelStatus.DELIVERED -> CodSettlementStatus.COLLECTED_BY_CARRIER
            UnifiedParcelStatus.RETURNED, UnifiedParcelStatus.CANCELLED -> CodSettlementStatus.PENDING_CARRIER
            else -> order.codSettlementStatus
        }

        val updatedCustomer = if (parsed.unifiedStatus == UnifiedParcelStatus.RETURNED &&
            order.status != UnifiedParcelStatus.RETURNED
        ) {
            val newReturns = order.customer.returnCount + 1
            order.customer.copy(
                returnCount = newReturns,
                isBlacklisted = newReturns >= 2
            )
        } else {
            order.customer
        }

        val updatedOrder = order.copy(
            status = parsed.unifiedStatus,
            codSettlementStatus = newCodStatus,
            customer = updatedCustomer,
            updatedAtMillis = parsed.eventEpochMillis,
            deliveredAtMillis = if (parsed.unifiedStatus == UnifiedParcelStatus.DELIVERED) parsed.eventEpochMillis else order.deliveredAtMillis,
            storeSynced = true,
            trackingLogs = listOf(log) + order.trackingLogs
        )

        _snapshot.update { current ->
            current.copy(
                orders = current.orders.map { if (it.id == orderId) updatedOrder else it }
            )
        }
        pushOrderToSupabaseIfConfigured(updatedOrder)
        return Result.success("تم تحديث الحالة إلى ${parsed.unifiedStatus.name} ومزامنة المتجر")
    }

    suspend fun runFallbackPollingCron(): Int {
        val state = _snapshot.value
        val now = System.currentTimeMillis()
        var polledCount = 0
        val updatedOrders = state.orders.map { order ->
            val carrier = order.carrierCode
            val tracking = order.trackingNumber
            if (carrier != null && tracking != null && !carrier.supportsWebhooks && !order.status.isTerminal) {
                val snap = CarrierRegistry.get(carrier).getStatus(tracking)
                polledCount++
                val log = TrackingLog(
                    id = "poll_${UUID.randomUUID().toString().take(8)}",
                    shipmentId = order.id,
                    externalEventId = "poll_${now / 1000}_${order.id}",
                    rawStatus = snap.rawStatus,
                    unifiedStatus = snap.unifiedStatus,
                    note = snap.locationNote,
                    eventEpochMillis = now,
                    source = "POLLING"
                )
                val shouldUpdate = StatusMapper.shouldApplyTransition(
                    order.status, snap.unifiedStatus, order.updatedAtMillis, now
                )
                if (shouldUpdate) {
                    order.copy(
                        status = snap.unifiedStatus,
                        updatedAtMillis = now,
                        trackingLogs = listOf(log) + order.trackingLogs
                    )
                } else {
                    order.copy(trackingLogs = listOf(log) + order.trackingLogs)
                }
            } else {
                order
            }
        }

        _snapshot.update { current ->
            current.copy(
                orders = updatedOrders,
                carrierAccounts = current.carrierAccounts.map {
                    if (!it.carrierCode.supportsWebhooks) it.copy(lastPollMillis = now) else it
                }
            )
        }
        return polledCount
    }

    suspend fun testCarrierAdapter(carrierCode: CarrierCode): String {
        val adapter = CarrierRegistry.get(carrierCode)
        val sampleTracking = _snapshot.value.orders.firstOrNull { it.carrierCode == carrierCode }?.trackingNumber
            ?: "${carrierCode.name.take(3)}-TEST-01"
        val snap = adapter.getStatus(sampleTracking)
        _snapshot.update { current ->
            current.copy(
                carrierAccounts = current.carrierAccounts.map {
                    if (it.carrierCode == carrierCode) it.copy(lastPollMillis = System.currentTimeMillis()) else it
                }
            )
        }
        return "${carrierCode.displayName}: الاتصال نشط (${snap.locationNote})"
    }

    fun toggleStoreActive(storeId: String) {
        _snapshot.update { current ->
            current.copy(
                stores = current.stores.map {
                    if (it.id == storeId) it.copy(isActive = !it.isActive) else it
                }
            )
        }
    }

    fun toggleCarrierActive(carrierCode: CarrierCode) {
        _snapshot.update { current ->
            current.copy(
                carrierAccounts = current.carrierAccounts.map {
                    if (it.carrierCode == carrierCode) it.copy(isActive = !it.isActive) else it
                }
            )
        }
    }

    fun retryDeadLetter(dlqId: String) {
        _snapshot.update { current ->
            val target = current.deadLetters.find { it.id == dlqId } ?: return@update current
            if (target.retryCount >= 2) {
                current.copy(deadLetters = current.deadLetters.filterNot { it.id == dlqId })
            } else {
                val backoffMs = (1L shl target.retryCount) * 60_000L
                current.copy(
                    deadLetters = current.deadLetters.map {
                        if (it.id == dlqId) {
                            it.copy(
                                retryCount = it.retryCount + 1,
                                nextRetryMillis = System.currentTimeMillis() + backoffMs
                            )
                        } else it
                    }
                )
            }
        }
    }

    suspend fun sendWhatsAppNotification(
        orderId: String,
        scenario: WhatsAppScenario,
        useDarija: Boolean
    ): WhatsAppSendOutcome {
        val state = _snapshot.value
        val order = state.orders.find { it.id == orderId }
            ?: return WhatsAppSendOutcome.Failed("Order not found")

        val outcome = WhatsAppAutomationEngine.sendScenarioMessage(
            order = order,
            scenario = scenario,
            useDarija = useDarija,
            templates = state.templates,
            existingLogs = state.notificationLogs
        )

        if (outcome is WhatsAppSendOutcome.Sent) {
            _snapshot.update { current ->
                current.copy(notificationLogs = listOf(outcome.log) + current.notificationLogs)
            }
            if (SupabaseClient.isConfigured()) {
                SupabaseClient.pushNotificationLog(
                    accessToken = state.session.accessToken,
                    row = SupabaseNotificationLogRow(
                        id = outcome.log.id,
                        merchantId = state.session.userId,
                        orderId = outcome.log.orderId,
                        customerPhone = outcome.log.customerPhone,
                        scenario = outcome.log.scenario.templateKey,
                        dedupKey = outcome.log.dedupKey,
                        providerStatus = outcome.log.status,
                        costMad = outcome.log.costMad
                    )
                )
            }
        }
        return outcome
    }

    fun simulateCustomerConfirmationReply(orderId: String, confirmed: Boolean) {
        _snapshot.update { current ->
            current.copy(
                orders = current.orders.map { order ->
                    if (order.id != orderId) order
                    else {
                        val nextStatus = if (!confirmed && order.status == UnifiedParcelStatus.CREATED) {
                            UnifiedParcelStatus.CANCELLED
                        } else order.status
                        order.copy(
                            whatsappConfirmed = confirmed,
                            status = nextStatus,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    }
                }
            )
        }
    }

    fun toggleCustomerOptOut(customerId: String) {
        _snapshot.update { current ->
            current.copy(
                orders = current.orders.map { order ->
                    if (order.customer.id == customerId) {
                        order.copy(customer = order.customer.copy(whatsappOptOut = !order.customer.whatsappOptOut))
                    } else order
                }
            )
        }
    }

    fun toggleCustomerBlacklist(customerId: String) {
        _snapshot.update { current ->
            current.copy(
                orders = current.orders.map { order ->
                    if (order.customer.id == customerId) {
                        order.copy(customer = order.customer.copy(isBlacklisted = !order.customer.isBlacklisted))
                    } else order
                }
            )
        }
    }

    fun reconcileCarrierCod(carrierCode: CarrierCode) {
        val now = System.currentTimeMillis()
        _snapshot.update { current ->
            current.copy(
                orders = current.orders.map { order ->
                    if (order.carrierCode == carrierCode &&
                        order.codSettlementStatus == CodSettlementStatus.COLLECTED_BY_CARRIER
                    ) {
                        order.copy(
                            codSettlementStatus = CodSettlementStatus.REMITTED_TO_MERCHANT,
                            updatedAtMillis = now
                        )
                    } else order
                }
            )
        }
    }

    fun selectSubscriptionPlan(planId: String) {
        _snapshot.update { current ->
            current.copy(session = current.session.copy(currentPlanId = planId))
        }
    }

    fun computeKpis(orders: List<ParcelOrder>): DashboardKpis {
        val shipped = orders.filter { it.status != UnifiedParcelStatus.CREATED && it.status != UnifiedParcelStatus.CANCELLED }
        val delivered = orders.filter { it.status == UnifiedParcelStatus.DELIVERED }
        val retours = orders.filter { it.status == UnifiedParcelStatus.RETURNED || it.status == UnifiedParcelStatus.REFUSED }
        val denom = shipped.size.coerceAtLeast(1)

        val codCollected = delivered.sumOf { it.codAmountMad }
        val codPending = shipped
            .filter { it.status != UnifiedParcelStatus.DELIVERED && it.status != UnifiedParcelStatus.RETURNED }
            .sumOf { it.codAmountMad }

        val deliveryHours = delivered.mapNotNull { order ->
            order.deliveredAtMillis?.let { CasablancaTime.hoursElapsed(order.createdAtMillis, it) }
        }
        val avgHours = if (deliveryHours.isNotEmpty()) deliveryHours.average() else 34.0

        return DashboardKpis(
            totalParcels = orders.size,
            deliveredCount = delivered.size,
            retourCount = retours.size,
            deliveryRatePercent = (delivered.size * 100.0) / denom,
            retourRatePercent = (retours.size * 100.0) / denom,
            codCollectedMad = codCollected,
            codPendingMad = codPending,
            avgDeliveryHours = avgHours
        )
    }

    fun computeCityBreakdown(orders: List<ParcelOrder>): List<BreakdownMetric> {
        return orders.groupBy { it.customer.city }.map { (city, list) ->
            val retours = list.count {
                it.status == UnifiedParcelStatus.RETURNED || it.status == UnifiedParcelStatus.REFUSED
            }
            BreakdownMetric(
                label = city,
                totalCount = list.size,
                retourCount = retours,
                retourRatePercent = (retours * 100.0) / list.size.coerceAtLeast(1)
            )
        }.sortedByDescending { it.retourRatePercent }
    }

    fun computeCarrierBreakdown(orders: List<ParcelOrder>): List<BreakdownMetric> {
        return orders.filter { it.carrierCode != null }.groupBy { it.carrierCode!! }.map { (carrier, list) ->
            val retours = list.count {
                it.status == UnifiedParcelStatus.RETURNED || it.status == UnifiedParcelStatus.REFUSED
            }
            BreakdownMetric(
                label = carrier.displayName,
                totalCount = list.size,
                retourCount = retours,
                retourRatePercent = (retours * 100.0) / list.size.coerceAtLeast(1)
            )
        }.sortedByDescending { it.retourRatePercent }
    }

    fun computeCodBatches(orders: List<ParcelOrder>): List<CodBatchSummary> {
        val now = System.currentTimeMillis()
        return CarrierCode.entries.mapNotNull { carrier ->
            val deliveredForCarrier = orders.filter {
                it.carrierCode == carrier && it.status == UnifiedParcelStatus.DELIVERED
            }
            if (deliveredForCarrier.isEmpty()) return@mapNotNull null
            val pendingCollection = deliveredForCarrier.filter {
                it.codSettlementStatus == CodSettlementStatus.COLLECTED_BY_CARRIER
            }
            val targetList = pendingCollection.ifEmpty { deliveredForCarrier }
            val gross = targetList.sumOf { it.codAmountMad }
            val fees = targetList.size * carrier.defaultFeeMad
            val status = if (pendingCollection.isNotEmpty()) {
                CodSettlementStatus.COLLECTED_BY_CARRIER
            } else {
                CodSettlementStatus.REMITTED_TO_MERCHANT
            }
            CodBatchSummary(
                id = "batch_${carrier.name}",
                carrierCode = carrier,
                parcelCount = targetList.size,
                grossCodMad = gross,
                carrierFeesMad = fees,
                netPayableMad = (gross - fees).coerceAtLeast(0.0),
                settlementStatus = status,
                updatedAtMillis = now
            )
        }
    }

    private suspend fun pushOrderToSupabaseIfConfigured(order: ParcelOrder) {
        val session = _snapshot.value.session
        if (!SupabaseClient.isConfigured()) return

        val customerRow = SupabaseCustomerRow(
            id = order.customer.id,
            merchantId = session.userId,
            fullName = order.customer.fullName,
            phoneE164 = order.customer.phoneNormalized,
            city = order.customer.city,
            address = order.customer.address,
            totalOrders = order.customer.totalOrders,
            returnCount = order.customer.returnCount,
            isBlacklisted = order.customer.isBlacklisted,
            whatsappOptOut = order.customer.whatsappOptOut
        )

        val orderRow = SupabaseOrderRow(
            id = order.id,
            merchantId = session.userId,
            customerId = order.customer.id,
            storeOrderRef = order.storeOrderRef,
            storePlatform = order.storePlatform.name,
            productSummary = order.productSummary,
            codAmountMad = order.codAmountMad,
            unifiedStatus = order.status.name,
            whatsappConfirmed = order.whatsappConfirmed,
            storeSynced = order.storeSynced
        )

        val shipmentRow = if (order.carrierCode != null && order.trackingNumber != null) {
            SupabaseShipmentRow(
                id = "ship_${order.id}",
                merchantId = session.userId,
                orderId = order.id,
                carrier = order.carrierCode.name,
                trackingNumber = order.trackingNumber,
                labelUrl = order.labelPreviewCode,
                carrierFeeMad = order.carrierCode.defaultFeeMad,
                codSettlementStatus = order.codSettlementStatus.name
            )
        } else null

        val latestLog = order.trackingLogs.firstOrNull()?.let { log ->
            SupabaseTrackingLogRow(
                id = log.id,
                merchantId = session.userId,
                shipmentId = "ship_${order.id}",
                externalEventId = log.externalEventId,
                rawStatus = log.rawStatus,
                unifiedStatus = log.unifiedStatus.name,
                note = log.note,
                source = log.source
            )
        }

        SupabaseClient.pushOrderBundle(
            accessToken = session.accessToken,
            customer = customerRow,
            order = orderRow,
            shipment = shipmentRow,
            latestTrackingLog = latestLog
        )
    }

    private fun createInitialSnapshot(): ParcelHubSnapshot {
        val now = System.currentTimeMillis()
        val hour = TimeUnit.HOURS.toMillis(1)
        val day = TimeUnit.DAYS.toMillis(1)
        val merchantId = "merch_atlas_01"

        val c1 = Customer("c1", "كريم العمراني", "+212661482910", "Casablanca", "المعاريف، زنقة جبل العياشي", 4, 0)
        val c2 = Customer("c2", "ندى التوزاني", "+212708193455", "Rabat", "أكدال، شارع فرنسا", 2, 0)
        val c3 = Customer("c3", "سفيان بركات", "+212655901234", "Marrakech", "كليز، إقامة النخيل", 3, 2, isBlacklisted = true)
        val c4 = Customer("c4", "حنان الزروالي", "+212612334455", "Tanger", "طنجة البالية، تجزئة الأمل", 1, 0)
        val c5 = Customer("c5", "رشيد أيت بنعلي", "+212771220099", "Agadir", "تالبورجت، شارع الجيش الملكي", 2, 1)
        val c6 = Customer("c6", "إيمان الفاسي", "+212663998877", "Fès", "طريق إيموزار، حي السعادة", 1, 0, whatsappOptOut = true)
        val c7 = Customer("c7", "مهدي بنجلون", "+212670112233", "Casablanca", "عين السبع، قرب المحطة", 5, 0)

        val orders = listOf(
            ParcelOrder(
                id = "ord_101",
                merchantId = merchantId,
                storeOrderRef = "YC-8841",
                storePlatform = StorePlatform.YOUCAN,
                customer = c1,
                productSummary = "ساعة ذكية Ultra + حزامين",
                codAmountMad = 349.0,
                status = UnifiedParcelStatus.DELIVERED,
                carrierCode = CarrierCode.OZONE_EXPRESS,
                trackingNumber = "OZN-849201",
                labelPreviewCode = "|| ||| || OZN-849201 |||",
                codSettlementStatus = CodSettlementStatus.COLLECTED_BY_CARRIER,
                storeSynced = true,
                whatsappConfirmed = true,
                createdAtMillis = now - 48 * hour,
                updatedAtMillis = now - 14 * hour,
                deliveredAtMillis = now - 14 * hour,
                trackingLogs = listOf(
                    TrackingLog("t1", "ord_101", "evt_101_3", "LIVRE", UnifiedParcelStatus.DELIVERED, "تم تسليم الطرد وتحصيل 349 MAD", now - 14 * hour, "WEBHOOK"),
                    TrackingLog("t2", "ord_101", "evt_101_2", "EN_COURS", UnifiedParcelStatus.OUT_FOR_DELIVERY, "خرج مع الموزع بالدار البيضاء", now - 20 * hour, "WEBHOOK")
                )
            ),
            ParcelOrder(
                id = "ord_102",
                merchantId = merchantId,
                storeOrderRef = "SHP-4410",
                storePlatform = StorePlatform.SHOPIFY,
                customer = c2,
                productSummary = "مجموعة العناية Argan Gold",
                codAmountMad = 280.0,
                status = UnifiedParcelStatus.DELIVERED,
                carrierCode = CarrierCode.CATHEDIS,
                trackingNumber = "CTD-332190",
                labelPreviewCode = "|| ||| || CTD-332190 |||",
                codSettlementStatus = CodSettlementStatus.REMITTED_TO_MERCHANT,
                storeSynced = true,
                whatsappConfirmed = true,
                createdAtMillis = now - 72 * hour,
                updatedAtMillis = now - 36 * hour,
                deliveredAtMillis = now - 42 * hour,
                trackingLogs = listOf(
                    TrackingLog("t3", "ord_102", "evt_102_2", "DELIVERED", UnifiedParcelStatus.DELIVERED, "Livré à Agdal Rabat", now - 42 * hour, "WEBHOOK")
                )
            ),
            ParcelOrder(
                id = "ord_103",
                merchantId = merchantId,
                storeOrderRef = "WC-2091",
                storePlatform = StorePlatform.WOOCOMMERCE,
                customer = c3,
                productSummary = "حذاء رياضي Air Pro",
                codAmountMad = 399.0,
                status = UnifiedParcelStatus.RETURNED,
                carrierCode = CarrierCode.DIGYLOG,
                trackingNumber = "DGL-550120",
                labelPreviewCode = "|| ||| || DGL-550120 |||",
                codSettlementStatus = CodSettlementStatus.PENDING_CARRIER,
                storeSynced = true,
                whatsappConfirmed = false,
                createdAtMillis = now - 5 * day,
                updatedAtMillis = now - 1 * day,
                trackingLogs = listOf(
                    TrackingLog("t4", "ord_103", "evt_103_2", "RETOUR", UnifiedParcelStatus.RETURNED, "الزبون رفض الاستلام بعد 3 محاولات", now - 1 * day, "POLLING")
                )
            ),
            ParcelOrder(
                id = "ord_104",
                merchantId = merchantId,
                storeOrderRef = "YC-8855",
                storePlatform = StorePlatform.YOUCAN,
                customer = c4,
                productSummary = "كاميرا سيارة DashCam HD",
                codAmountMad = 450.0,
                status = UnifiedParcelStatus.IN_TRANSIT,
                carrierCode = CarrierCode.AMEEX,
                trackingNumber = "AMX-774012",
                labelPreviewCode = "|| ||| || AMX-774012 |||",
                codSettlementStatus = CodSettlementStatus.PENDING_CARRIER,
                storeSynced = true,
                whatsappConfirmed = true,
                createdAtMillis = now - 4 * day,
                updatedAtMillis = now - 2 * day,
                trackingLogs = listOf(
                    TrackingLog("t5", "ord_104", "evt_104_1", "EN_TRANSIT", UnifiedParcelStatus.IN_TRANSIT, "في مستودع طنجة (تأخر +3 أيام)", now - 2 * day, "POLLING")
                )
            ),
            ParcelOrder(
                id = "ord_105",
                merchantId = merchantId,
                storeOrderRef = "GS-104",
                storePlatform = StorePlatform.GOOGLE_SHEETS,
                customer = c5,
                productSummary = "خلاط رياضي قابل للشحن",
                codAmountMad = 220.0,
                status = UnifiedParcelStatus.OUT_FOR_DELIVERY,
                carrierCode = CarrierCode.SENDIT,
                trackingNumber = "SND-910443",
                labelPreviewCode = "|| ||| || SND-910443 |||",
                codSettlementStatus = CodSettlementStatus.PENDING_CARRIER,
                storeSynced = true,
                whatsappConfirmed = true,
                createdAtMillis = now - 4 * day,
                updatedAtMillis = now - 6 * hour,
                trackingLogs = listOf(
                    TrackingLog("t6", "ord_105", "evt_105_1", "EN_LIVRAISON", UnifiedParcelStatus.OUT_FOR_DELIVERY, "خرج للتوصيل في أكادير", now - 6 * hour, "WEBHOOK")
                )
            ),
            ParcelOrder(
                id = "ord_106",
                merchantId = merchantId,
                storeOrderRef = "YC-8862",
                storePlatform = StorePlatform.YOUCAN,
                customer = c6,
                productSummary = "طقم حقائب سفر (3 قطع)",
                codAmountMad = 590.0,
                status = UnifiedParcelStatus.CREATED,
                carrierCode = null,
                trackingNumber = null,
                labelPreviewCode = null,
                codSettlementStatus = CodSettlementStatus.PENDING_CARRIER,
                storeSynced = false,
                whatsappConfirmed = null,
                createdAtMillis = now - 2 * hour,
                updatedAtMillis = now - 2 * hour
            ),
            ParcelOrder(
                id = "ord_107",
                merchantId = merchantId,
                storeOrderRef = "SHP-4429",
                storePlatform = StorePlatform.SHOPIFY,
                customer = c7,
                productSummary = "مصباح مكتب ذكي LED",
                codAmountMad = 189.0,
                status = UnifiedParcelStatus.DELIVERED,
                carrierCode = CarrierCode.OZONE_EXPRESS,
                trackingNumber = "OZN-849980",
                labelPreviewCode = "|| ||| || OZN-849980 |||",
                codSettlementStatus = CodSettlementStatus.COLLECTED_BY_CARRIER,
                storeSynced = true,
                whatsappConfirmed = true,
                createdAtMillis = now - 30 * hour,
                updatedAtMillis = now - 5 * hour,
                deliveredAtMillis = now - 5 * hour
            )
        )

        val stores = listOf(
            StoreConnection("st_1", StorePlatform.YOUCAN, "Atlas Gadgets MA", "https://api.parcelhub.ma/webhooks/youcan", true, 142, now - 15 * 60_000),
            StoreConnection("st_2", StorePlatform.SHOPIFY, "Darija Beauty Store", "https://api.parcelhub.ma/webhooks/shopify", true, 89, now - 40 * 60_000),
            StoreConnection("st_3", StorePlatform.WOOCOMMERCE, "Casablanca Sneakers", "https://api.parcelhub.ma/webhooks/woo", true, 54, now - 2 * hour),
            StoreConnection("st_4", StorePlatform.GOOGLE_SHEETS, "Call Center Sheet", "https://api.parcelhub.ma/webhooks/sheets", true, 31, now - 3 * hour)
        )

        val carriers = CarrierCode.entries.map { code ->
            CarrierAccountConfig(
                carrierCode = code,
                isActive = true,
                isMockMode = true,
                encryptedKeyPreview = "vault:aes256:••••${code.name.take(3)}",
                lastPollMillis = now - 25 * 60_000,
                activeShipmentsCount = orders.count { it.carrierCode == code }
            )
        }

        val initialLogs = listOf(
            NotificationLog(
                id = "wa_init_1",
                orderId = "ord_101",
                trackingNumber = "OZN-849201",
                customerPhone = c1.phoneNormalized,
                scenario = WhatsAppScenario.DELIVERED,
                languageTag = "Darija (ar_MA)",
                renderedMessage = "شكراً ليك كريم العمراني ✅ تم تسليم الكوليا OZN-849201 بنجاح (349 درهم).",
                status = "SENT",
                dedupKey = "ord_101_parcel_delivered",
                costMad = 0.16,
                sentAtMillis = now - 14 * hour
            )
        )

        val dlq = listOf(
            DeadLetterEvent(
                id = "dlq_init_1",
                source = "Sendit",
                payloadSummary = "طرد SND-910443 — خرج للتوصيل",
                failureReason = "انقطاع مؤقت في الاتصال بشركة التوصيل — جاهز لإعادة المحاولة",
                retryCount = 1,
                nextRetryMillis = now + 120_000,
                createdAtMillis = now - 10 * 60_000
            )
        )

        return ParcelHubSnapshot(
            session = MerchantSession(
                userId = merchantId,
                email = "atlas.shop@parcelhub.ma",
                storeBrandName = "Atlas Commerce MA",
                currentPlanId = "PRO",
                isSupabaseLive = SupabaseClient.isConfigured()
            ),
            orders = orders,
            stores = stores,
            carrierAccounts = carriers,
            templates = WhatsAppAutomationEngine.defaultTemplates,
            notificationLogs = initialLogs,
            deadLetters = dlq
        )
    }
}
