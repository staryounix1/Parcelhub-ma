package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.CarrierCode
import com.example.domain.model.ParcelOrder
import com.example.domain.model.StorePlatform
import com.example.domain.model.UnifiedParcelStatus
import com.example.domain.util.CasablancaTime
import com.example.domain.util.MoroccanPhoneNormalizer
import com.example.ui.components.ParcelStatusBadge
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRetour
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.strings
import com.example.ui.viewmodel.ParcelHubUiState

@Composable
fun ShipmentsScreen(
    state: ParcelHubUiState,
    onSearchChange: (String) -> Unit,
    onSelectStatus: (UnifiedParcelStatus?) -> Unit,
    onSelectCarrier: (CarrierCode?) -> Unit,
    onCreateOrder: (String, String, String, String, String, Double, StorePlatform) -> Unit,
    onDispatchCarrier: (String, CarrierCode) -> Unit,
    onCancelShipment: (String) -> Unit,
    onSyncToStore: (String) -> Unit,
    onOpenLabel: (ParcelOrder) -> Unit,
    onSimulateWebhook: (String, String, String, String, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    var showCreateOrderDialog by remember { mutableStateOf(false) }
    var orderToDispatch by remember { mutableStateOf<ParcelOrder?>(null) }
    var orderForTimeline by remember { mutableStateOf<ParcelOrder?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateOrderDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_order_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = s.newOrderBtn)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(s.searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shipments_search_input")
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedStatusFilter == null,
                        onClick = { onSelectStatus(null) },
                        label = { Text(s.allStatuses) },
                        modifier = Modifier.testTag("filter_status_all")
                    )
                    UnifiedParcelStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.selectedStatusFilter == status,
                            onClick = {
                                onSelectStatus(if (state.selectedStatusFilter == status) null else status)
                            },
                            label = { Text(status.label(isArabic)) },
                            modifier = Modifier.testTag("filter_status_${status.name.lowercase()}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedCarrierFilter == null,
                        onClick = { onSelectCarrier(null) },
                        label = { Text(s.allCarriers) },
                        modifier = Modifier.testTag("filter_carrier_all")
                    )
                    CarrierCode.entries.forEach { carrier ->
                        FilterChip(
                            selected = state.selectedCarrierFilter == carrier,
                            onClick = {
                                onSelectCarrier(if (state.selectedCarrierFilter == carrier) null else carrier)
                            },
                            label = { Text(carrier.displayName) },
                            modifier = Modifier.testTag("filter_carrier_${carrier.name.lowercase()}")
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.filteredOrders, key = { it.id }) { order ->
                    ParcelOrderCard(
                        order = order,
                        isArabic = isArabic,
                        onDispatchClick = { orderToDispatch = order },
                        onLabelClick = { onOpenLabel(order) },
                        onTimelineClick = { orderForTimeline = order }
                    )
                }
            }
        }
    }

    if (showCreateOrderDialog) {
        CreateOrderDialog(
            onDismiss = { showCreateOrderDialog = false },
            onSubmit = { name, phone, city, addr, prod, cod, platform ->
                onCreateOrder(name, phone, city, addr, prod, cod, platform)
                showCreateOrderDialog = false
            }
        )
    }

    orderToDispatch?.let { order ->
        DispatchCarrierDialog(
            order = order,
            onDismiss = { orderToDispatch = null },
            onSelectCarrier = { carrier ->
                onDispatchCarrier(order.id, carrier)
                orderToDispatch = null
            }
        )
    }

    orderForTimeline?.let { order ->
        val latest = state.allOrders.find { it.id == order.id } ?: order
        ParcelTrackingDialog(
            order = latest,
            onDismiss = { orderForTimeline = null },
            onUpdateStatus = onSimulateWebhook,
            onCancelShipment = onCancelShipment,
            onSyncToStore = onSyncToStore
        )
    }
}

@Composable
private fun ParcelOrderCard(
    order: ParcelOrder,
    isArabic: Boolean,
    onDispatchClick: () -> Unit,
    onLabelClick: () -> Unit,
    onTimelineClick: () -> Unit
) {
    val s = strings()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_card_${order.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = order.trackingNumber ?: order.storeOrderRef,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = order.storePlatform.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${order.customer.fullName} • ${order.customer.city}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                ParcelStatusBadge(status = order.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.customer.phoneNormalized,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${order.codAmountMad.toInt()} ${s.madUnit}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = order.productSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (order.carrierCode != null) {
                    Surface(
                        color = EmeraldContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = order.carrierCode.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnEmeraldContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (order.customer.isBlacklisted) {
                    Surface(color = CoralContainer, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = if (isArabic) "قائمة سوداء" else "Liste noire",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnCoralContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (order.storeSynced) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = "Synced",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (order.whatsappConfirmed == true) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "WhatsApp Confirmed",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = CasablancaTime.formatDateTime(order.updatedAtMillis, isArabic),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (order.carrierCode == null && order.status == UnifiedParcelStatus.CREATED) {
                    Button(
                        onClick = onDispatchClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("dispatch_button_${order.id}")
                    ) {
                        Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.dispatchCarrierBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onLabelClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("label_button_${order.id}")
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.viewLabelBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onTimelineClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("update_status_button_${order.id}")
                    ) {
                        Icon(Icons.Filled.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.updateStatusBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateOrderDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String, Double, StorePlatform) -> Unit
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR
    var name by remember { mutableStateOf("") }
    var rawPhone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Casablanca") }
    var address by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("") }
    var codStr by remember { mutableStateOf("299") }
    var platform by remember { mutableStateOf(StorePlatform.YOUCAN) }

    val normalized = MoroccanPhoneNormalizer.normalize(rawPhone)
    val isPhoneValid = MoroccanPhoneNormalizer.isValidMoroccanMobile(normalized)
    val codValue = codStr.toDoubleOrNull()
    val isCodValid = codValue != null && codValue > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.newOrderBtn) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isArabic) "اسم الزبون" else "Nom du client") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_order_name_input")
                )
                OutlinedTextField(
                    value = rawPhone,
                    onValueChange = { rawPhone = it },
                    label = { Text(if (isArabic) "الهاتف (06/07)" else "Téléphone (06/07)") },
                    supportingText = {
                        if (rawPhone.isNotBlank()) {
                            Text(
                                text = if (isPhoneValid) "✓ $normalized" else "Ex: 0661223344",
                                color = if (isPhoneValid) EmeraldPrimary else CoralRetour
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_order_phone_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text(if (isArabic) "المدينة" else "Ville") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = codStr,
                        onValueChange = { codStr = it },
                        label = { Text("COD (MAD)") },
                        isError = codStr.isNotBlank() && !isCodValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("new_order_cod_input")
                    )
                }
                OutlinedTextField(
                    value = product,
                    onValueChange = { product = it },
                    label = { Text(if (isArabic) "المنتج" else "Produit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(if (isArabic) "العنوان" else "Adresse") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StorePlatform.entries.forEach { p ->
                        FilterChip(
                            selected = platform == p,
                            onClick = { platform = p },
                            label = { Text(p.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = codValue ?: return@Button
                    onSubmit(name, rawPhone, city, address, product.ifBlank { "طلب COD" }, amount, platform)
                },
                enabled = name.isNotBlank() && isPhoneValid && isCodValid,
                modifier = Modifier.testTag("submit_new_order_button")
            ) {
                Text(s.saveBtn)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancelBtn) }
        }
    )
}

@Composable
private fun DispatchCarrierDialog(
    order: ParcelOrder,
    onDismiss: () -> Unit,
    onSelectCarrier: (CarrierCode) -> Unit
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${s.dispatchCarrierBtn} (${order.storeOrderRef})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${order.customer.fullName} • ${order.customer.city} (${order.codAmountMad.toInt()} MAD)",
                    style = MaterialTheme.typography.bodyMedium
                )
                CarrierCode.entries.forEach { carrier ->
                    Card(
                        onClick = { onSelectCarrier(carrier) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("select_carrier_${carrier.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(carrier.displayName, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (carrier.supportsWebhooks) {
                                        if (isArabic) "تتبع فوري ومباشر" else "Suivi en temps réel"
                                    } else {
                                        if (isArabic) "تحديث تلقائي مجدول" else "Suivi actualisé automatiquement"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${carrier.defaultFeeMad.toInt()} MAD",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancelBtn) }
        }
    )
}

@Composable
private fun ParcelTrackingDialog(
    order: ParcelOrder,
    onDismiss: () -> Unit,
    onUpdateStatus: (String, String, String, String, Boolean, Boolean) -> Unit,
    onCancelShipment: (String) -> Unit,
    onSyncToStore: (String) -> Unit
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${order.trackingNumber ?: order.storeOrderRef} • ${order.carrierCode?.displayName ?: ""}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isArabic) "تحديث حالة الشحنة:" else "Mettre à jour le statut :",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            onUpdateStatus(
                                order.id,
                                "EN_COURS",
                                "evt_ofd_${order.id}",
                                "خرج الموزع للتوصيل",
                                true,
                                false
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("status_ofd_button")
                    ) { Text(if (isArabic) "للتوصيل" else "En livr.") }
                    Button(
                        onClick = {
                            onUpdateStatus(
                                order.id,
                                "LIVRE",
                                "evt_del_${order.id}",
                                "تم التسليم وتحصيل COD",
                                true,
                                false
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("status_delivered_button")
                    ) { Text(if (isArabic) "مُسلّم" else "Livré") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            onUpdateStatus(
                                order.id,
                                "RETOUR",
                                "evt_ret_${order.id}",
                                "رفض الزبون الاستلام",
                                true,
                                false
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("status_retour_button")
                    ) { Text("Retour") }
                    OutlinedButton(
                        onClick = { onSyncToStore(order.id) },
                        modifier = Modifier.weight(1f).testTag("sync_to_store_button")
                    ) {
                        Text(s.syncToStoreBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (!order.status.isTerminal) {
                    OutlinedButton(
                        onClick = { onCancelShipment(order.id) },
                        modifier = Modifier.fillMaxWidth().testTag("cancel_shipment_button")
                    ) {
                        Text(
                            text = if (isArabic) "إلغاء الشحنة" else "Annuler le colis",
                            color = CoralRetour
                        )
                    }
                }

                HorizontalDivider()
                Text(
                    text = if (isArabic) "سجل التتبع:" else "Historique du colis :",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (order.trackingLogs.isEmpty()) {
                    Text(
                        text = if (isArabic) "لا توجد تحديثات بعد" else "Aucune mise à jour",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    order.trackingLogs.take(4).forEach { log ->
                        Text(
                            text = "• ${log.unifiedStatus.label(isArabic)} — ${log.note}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.closeBtn) }
        }
    )
}
