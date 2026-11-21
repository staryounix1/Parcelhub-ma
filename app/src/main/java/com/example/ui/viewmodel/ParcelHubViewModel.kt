package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.carriers.LabelResult
import com.example.data.repository.ParcelHubRepository
import com.example.data.whatsapp.WhatsAppSendOutcome
import com.example.domain.model.BreakdownMetric
import com.example.domain.model.CarrierAccountConfig
import com.example.domain.model.CarrierCode
import com.example.domain.model.CodBatchSummary
import com.example.domain.model.DashboardKpis
import com.example.domain.model.DeadLetterEvent
import com.example.domain.model.MerchantSession
import com.example.domain.model.NotificationLog
import com.example.domain.model.ParcelOrder
import com.example.domain.model.StoreConnection
import com.example.domain.model.StorePlatform
import com.example.domain.model.UnifiedParcelStatus
import com.example.domain.model.WhatsAppScenario
import com.example.domain.model.WhatsAppTemplate
import com.example.domain.util.CasablancaTime
import com.example.domain.util.MoroccanPhoneNormalizer
import com.example.ui.theme.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ParcelHubUiState(
    val language: AppLanguage = AppLanguage.AR,
    val session: MerchantSession = MerchantSession("merch_atlas_01", "atlas.shop@parcelhub.ma", "Atlas Commerce MA", "PRO", false),
    val allOrders: List<ParcelOrder> = emptyList(),
    val filteredOrders: List<ParcelOrder> = emptyList(),
    val delayedOrders: List<ParcelOrder> = emptyList(),
    val blacklistedOrders: List<ParcelOrder> = emptyList(),
    val kpis: DashboardKpis = DashboardKpis(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0),
    val cityBreakdown: List<BreakdownMetric> = emptyList(),
    val carrierBreakdown: List<BreakdownMetric> = emptyList(),
    val codBatches: List<CodBatchSummary> = emptyList(),
    val stores: List<StoreConnection> = emptyList(),
    val carrierAccounts: List<CarrierAccountConfig> = emptyList(),
    val templates: List<WhatsAppTemplate> = emptyList(),
    val notificationLogs: List<NotificationLog> = emptyList(),
    val deadLetters: List<DeadLetterEvent> = emptyList(),
    val searchQuery: String = "",
    val selectedStatusFilter: UnifiedParcelStatus? = null,
    val selectedCarrierFilter: CarrierCode? = null,
    val activeLabelPreview: LabelResult? = null,
    val bannerMessage: String? = null,
    val isBusy: Boolean = false,
    val isAuthDialogOpen: Boolean = false,
    val authErrorMessage: String? = null
)

class ParcelHubViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ParcelHubRepository(application.applicationContext)

    init {
        viewModelScope.launch {
            repository.restoreSessionFromPrefs()
        }
    }

    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow<UnifiedParcelStatus?>(null)
    private val carrierFilter = MutableStateFlow<CarrierCode?>(null)
    private val labelPreview = MutableStateFlow<LabelResult?>(null)
    private val bannerMessage = MutableStateFlow<String?>(null)
    private val isBusy = MutableStateFlow(false)
    private val isAuthDialogOpen = MutableStateFlow(false)
    private val authErrorMessage = MutableStateFlow<String?>(null)

    private data class FilterState(
        val query: String,
        val status: UnifiedParcelStatus?,
        val carrier: CarrierCode?,
        val label: LabelResult?,
        val message: String?,
        val busy: Boolean,
        val authOpen: Boolean,
        val authError: String?
    )

    private val queryFiltersFlow = combine(
        searchQuery,
        statusFilter,
        carrierFilter
    ) { query, status, carrier ->
        Triple(query, status, carrier)
    }

    private val uiOverlayFlow = combine(
        labelPreview,
        bannerMessage,
        isBusy
    ) { label, message, busy ->
        Triple(label, message, busy)
    }

    private val authOverlayFlow = combine(
        isAuthDialogOpen,
        authErrorMessage
    ) { open, error ->
        open to error
    }

    private val filterFlow = combine(
        queryFiltersFlow,
        uiOverlayFlow,
        authOverlayFlow
    ) { qf, ov, auth ->
        FilterState(
            query = qf.first,
            status = qf.second,
            carrier = qf.third,
            label = ov.first,
            message = ov.second,
            busy = ov.third,
            authOpen = auth.first,
            authError = auth.second
        )
    }

    val uiState: StateFlow<ParcelHubUiState> = combine(
        repository.languageFlow,
        repository.snapshot,
        filterFlow
    ) { lang, snap, filters ->
        val normalizedQueryPhone = MoroccanPhoneNormalizer.normalize(filters.query)
        val filtered = snap.orders.filter { order ->
            val matchesStatus = filters.status == null || order.status == filters.status
            val matchesCarrier = filters.carrier == null || order.carrierCode == filters.carrier
            val q = filters.query.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                order.customer.fullName.lowercase().contains(q) ||
                order.customer.city.lowercase().contains(q) ||
                order.storeOrderRef.lowercase().contains(q) ||
                (order.trackingNumber?.lowercase()?.contains(q) == true) ||
                order.customer.phoneNormalized.contains(normalizedQueryPhone) ||
                order.customer.phoneNormalized.contains(q)
            matchesStatus && matchesCarrier && matchesQuery
        }

        val delayed = snap.orders.filter {
            !it.status.isTerminal &&
                it.status != UnifiedParcelStatus.CREATED &&
                CasablancaTime.daysElapsed(it.createdAtMillis) >= 3L
        }
        val blacklisted = snap.orders.filter { it.customer.isBlacklisted }

        ParcelHubUiState(
            language = lang,
            session = snap.session,
            allOrders = snap.orders,
            filteredOrders = filtered,
            delayedOrders = delayed,
            blacklistedOrders = blacklisted,
            kpis = repository.computeKpis(snap.orders),
            cityBreakdown = repository.computeCityBreakdown(snap.orders),
            carrierBreakdown = repository.computeCarrierBreakdown(snap.orders),
            codBatches = repository.computeCodBatches(snap.orders),
            stores = snap.stores,
            carrierAccounts = snap.carrierAccounts,
            templates = snap.templates,
            notificationLogs = snap.notificationLogs,
            deadLetters = snap.deadLetters,
            searchQuery = filters.query,
            selectedStatusFilter = filters.status,
            selectedCarrierFilter = filters.carrier,
            activeLabelPreview = filters.label,
            bannerMessage = filters.message,
            isBusy = filters.busy,
            isAuthDialogOpen = filters.authOpen,
            authErrorMessage = filters.authError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ParcelHubUiState()
    )

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (uiState.value.language == AppLanguage.AR) AppLanguage.FR else AppLanguage.AR
            repository.setLanguage(next)
        }
    }

    fun syncWithSupabase() {
        viewModelScope.launch {
            isBusy.value = true
            bannerMessage.value = repository.syncWithSupabase()
            isBusy.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectStatusFilter(status: UnifiedParcelStatus?) {
        statusFilter.value = status
    }

    fun selectCarrierFilter(carrier: CarrierCode?) {
        carrierFilter.value = carrier
    }

    fun clearBanner() {
        bannerMessage.value = null
    }

    fun dismissLabelPreview() {
        labelPreview.value = null
    }

    fun openLabelPreview(order: ParcelOrder) {
        viewModelScope.launch {
            labelPreview.value = repository.getLabelPreview(order)
        }
    }

    fun openAuthDialog() {
        authErrorMessage.value = null
        isAuthDialogOpen.value = true
    }

    fun dismissAuthDialog() {
        authErrorMessage.value = null
        isAuthDialogOpen.value = false
    }

    fun clearAuthError() {
        authErrorMessage.value = null
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            isBusy.value = true
            authErrorMessage.value = null
            val res = repository.signInSupabase(email, password)
            res.fold(
                onSuccess = { msg ->
                    isAuthDialogOpen.value = false
                    bannerMessage.value = msg
                },
                onFailure = { err ->
                    authErrorMessage.value = err.message ?: "فشل تسجيل الدخول"
                }
            )
            isBusy.value = false
        }
    }

    fun signUp(storeBrandName: String, email: String, password: String) {
        viewModelScope.launch {
            isBusy.value = true
            authErrorMessage.value = null
            val res = repository.signUpSupabase(email, password, storeBrandName)
            res.fold(
                onSuccess = { msg ->
                    isAuthDialogOpen.value = false
                    bannerMessage.value = msg
                },
                onFailure = { err ->
                    authErrorMessage.value = err.message ?: "فشل إنشاء الحساب"
                }
            )
            isBusy.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            isAuthDialogOpen.value = false
            authErrorMessage.value = null
            bannerMessage.value = if (uiState.value.language == AppLanguage.AR) {
                "تم تسجيل الخروج من الجلسة الحالية"
            } else {
                "Déconnexion effectuée"
            }
        }
    }

    fun createManualOrder(
        customerName: String,
        rawPhone: String,
        city: String,
        address: String,
        productSummary: String,
        codAmountMad: Double,
        platform: StorePlatform
    ) {
        viewModelScope.launch {
            val res = repository.createManualOrder(
                customerName, rawPhone, city, address, productSummary, codAmountMad, platform
            )
            bannerMessage.value = res.fold(
                onSuccess = { "تم إنشاء الطلب ${it.storeOrderRef} (${it.customer.phoneNormalized})" },
                onFailure = { it.message ?: "خطأ في إدخال الطلب" }
            )
        }
    }

    fun importSimulatedStoreOrder(platform: StorePlatform) {
        viewModelScope.launch {
            val order = repository.importSimulatedStoreOrder(platform)
            bannerMessage.value = "تم استيراد الطلب ${order.storeOrderRef} من ${platform.displayName}"
        }
    }

    fun dispatchToCarrier(orderId: String, carrierCode: CarrierCode) {
        viewModelScope.launch {
            isBusy.value = true
            val useDarija = uiState.value.language == AppLanguage.AR
            val res = repository.dispatchOrderToCarrier(orderId, carrierCode, sendWhatsAppOnShip = true, useDarija = useDarija)
            res.fold(
                onSuccess = { label ->
                    labelPreview.value = label
                    bannerMessage.value = "تم الشحن عبر ${carrierCode.displayName} • ${label.trackingNumber}"
                },
                onFailure = { err ->
                    bannerMessage.value = err.message ?: "تعذر إنشاء الشحنة"
                }
            )
            isBusy.value = false
        }
    }

    fun cancelShipment(orderId: String) {
        viewModelScope.launch {
            val res = repository.cancelOrderShipment(orderId)
            bannerMessage.value = res.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "تعذر الإلغاء" }
            )
        }
    }

    fun syncOrderToStore(orderId: String) {
        viewModelScope.launch {
            bannerMessage.value = repository.syncOrderBackToStore(orderId)
        }
    }

    fun simulateWebhook(
        orderId: String,
        rawStatus: String,
        externalEventId: String,
        note: String,
        validSignature: Boolean = true,
        staleTimestamp: Boolean = false
    ) {
        viewModelScope.launch {
            val ts = if (staleTimestamp) System.currentTimeMillis() - 86_400_000L * 5 else System.currentTimeMillis()
            val res = repository.processCarrierWebhook(
                orderId = orderId,
                rawStatus = rawStatus,
                externalEventId = externalEventId,
                note = note,
                validSignature = validSignature,
                eventEpochMillis = ts
            )
            bannerMessage.value = res.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "تعذر تحديث حالة الطرد" }
            )
        }
    }

    fun runFallbackPolling() {
        viewModelScope.launch {
            isBusy.value = true
            val count = repository.runFallbackPollingCron()
            bannerMessage.value = "تم تحديث تتبع $count طرود نشطة"
            isBusy.value = false
        }
    }

    fun testCarrierAdapter(carrierCode: CarrierCode) {
        viewModelScope.launch {
            bannerMessage.value = repository.testCarrierAdapter(carrierCode)
        }
    }

    fun toggleStoreActive(storeId: String) {
        repository.toggleStoreActive(storeId)
    }

    fun toggleCarrierActive(carrierCode: CarrierCode) {
        repository.toggleCarrierActive(carrierCode)
    }

    fun retryDeadLetter(dlqId: String) {
        repository.retryDeadLetter(dlqId)
        bannerMessage.value = "تمت إعادة جدولة تحديث الطرد بنجاح"
    }

    fun sendWhatsApp(orderId: String, scenario: WhatsAppScenario, useDarija: Boolean) {
        viewModelScope.launch {
            val outcome = repository.sendWhatsAppNotification(orderId, scenario, useDarija)
            bannerMessage.value = when (outcome) {
                is WhatsAppSendOutcome.Sent -> "تم إرسال رسالة واتساب إلى ${outcome.log.customerPhone}"
                is WhatsAppSendOutcome.BlockedOptOut -> "لم يتم الإرسال: الزبون ${outcome.phone} أوقف الإشعارات"
                is WhatsAppSendOutcome.BlockedDuplicate -> "تم إرسال هذه الرسالة مسبقاً لنفس الطلب"
                is WhatsAppSendOutcome.Failed -> "تعذر إرسال الرسالة: ${outcome.reason}"
            }
        }
    }

    fun simulateCustomerReply(orderId: String, confirmed: Boolean) {
        repository.simulateCustomerConfirmationReply(orderId, confirmed)
        bannerMessage.value = if (confirmed) {
            "تأكيد الزبون: الطلب جاهز للشحن"
        } else {
            "إلغاء الزبون عبر واتساب: تم توفير مصاريف Retour"
        }
    }

    fun toggleOptOut(customerId: String) {
        repository.toggleCustomerOptOut(customerId)
    }

    fun toggleBlacklist(customerId: String) {
        repository.toggleCustomerBlacklist(customerId)
    }

    fun reconcileCod(carrierCode: CarrierCode) {
        repository.reconcileCarrierCod(carrierCode)
        bannerMessage.value = "تمت مطابقة تحويل COD مع ${carrierCode.displayName}"
    }

    fun selectPlan(planId: String) {
        repository.selectSubscriptionPlan(planId)
        bannerMessage.value = "تم تفعيل باقة $planId بنجاح"
    }
}
