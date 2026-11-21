package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.carriers.CarrierRegistry
import com.example.domain.model.CarrierCode
import com.example.domain.model.CodSettlementStatus
import com.example.domain.model.StorePlatform
import com.example.domain.util.CasablancaTime
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRetour
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.strings
import com.example.ui.viewmodel.ParcelHubUiState

@Composable
fun CodAndIntegrationsScreen(
    state: ParcelHubUiState,
    onReconcileCod: (CarrierCode) -> Unit,
    onImportStoreOrder: (StorePlatform) -> Unit,
    onToggleStoreActive: (String) -> Unit,
    onToggleCarrierActive: (CarrierCode) -> Unit,
    onTestCarrierAdapter: (CarrierCode) -> Unit,
    onRefreshTracking: () -> Unit,
    onRetryFailedSync: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // 1. COD Reconciliation Ledger
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = EmeraldPrimary)
                Text(
                    text = s.codReconciliationTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(state.codBatches, key = { it.id }) { batch ->
            val isRemitted = batch.settlementStatus == CodSettlementStatus.REMITTED_TO_MERCHANT
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
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
                        Text(
                            text = "${batch.carrierCode.displayName} (${batch.parcelCount} ${if (isArabic) "طرود" else "colis"})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = if (isRemitted) EmeraldContainer else AmberContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = batch.settlementStatus.label(isArabic),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isRemitted) OnEmeraldContainer else OnAmberContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isArabic) "إجمالي COD" else "COD Brut",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("${batch.grossCodMad.toInt()} MAD", fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text(
                                text = if (isArabic) "عمولة التوصيل" else "Frais livraison",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("-${batch.carrierFeesMad.toInt()} MAD", color = CoralRetour)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isArabic) "الصافي للتاجر" else "Net Marchand",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${batch.netPayableMad.toInt()} MAD",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                        }
                    }

                    if (!isRemitted) {
                        Button(
                            onClick = { onReconcileCod(batch.carrierCode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("reconcile_cod_${batch.carrierCode.name.lowercase()}")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.markRemittedBtn)
                        }
                    }
                }
            }
        }

        // 2. Connected Stores (YouCan, Shopify, WooCommerce, Google Sheets)
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = s.storeConnectionsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(state.stores, key = { it.id }) { store ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${store.platform.displayName} • ${store.storeName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${store.importedOrdersCount} ${if (isArabic) "طلب مستورد" else "commandes importées"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = store.isActive,
                            onCheckedChange = { onToggleStoreActive(store.id) },
                            modifier = Modifier.testTag("toggle_store_${store.id}")
                        )
                        FilledTonalButton(
                            onClick = { onImportStoreOrder(store.platform) },
                            enabled = store.isActive,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("import_store_${store.platform.name.lowercase()}")
                        ) {
                            Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "استيراد" else "Importer")
                        }
                    }
                }
            }
        }

        // 3. 5 Moroccan Carriers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = s.carrierConnectionsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onRefreshTracking,
                    modifier = Modifier.testTag("integrations_refresh_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isArabic) "تحديث الكل" else "Actualiser")
                }
            }
        }

        items(state.carrierAccounts, key = { it.carrierCode.name }) { cfg ->
            val adapter = CarrierRegistry.get(cfg.carrierCode)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cfg.carrierCode.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (cfg.carrierCode.supportsWebhooks) {
                                        if (isArabic) "ربط مباشر" else "Temps réel"
                                    } else {
                                        if (isArabic) "مزامنة تلقائية" else "Sync auto"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnEmeraldContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Switch(
                                checked = cfg.isActive,
                                onCheckedChange = { onToggleCarrierActive(cfg.carrierCode) },
                                modifier = Modifier.testTag("toggle_carrier_${cfg.carrierCode.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = if (isArabic) "اتصال مشفر ومؤمّن • رسوم ${cfg.carrierCode.defaultFeeMad.toInt()} درهم" else "Connexion sécurisée • Frais ${cfg.carrierCode.defaultFeeMad.toInt()} MAD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = adapter.endpointIntegrationNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { onTestCarrierAdapter(cfg.carrierCode) },
                        enabled = cfg.isActive,
                        modifier = Modifier.testTag("test_carrier_${cfg.carrierCode.name.lowercase()}")
                    ) {
                        Text(if (isArabic) "فحص الاتصال" else "Tester la connexion")
                    }
                }
            }
        }

        // 4. Failed Syncs Retry Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = CoralRetour)
                Text(
                    text = s.failedSyncsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (state.deadLetters.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text(
                        text = if (isArabic) "جميع التحديثات متزامنة بنجاح" else "Toutes les mises à jour sont synchronisées",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(state.deadLetters, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CoralContainer.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${item.source} • ${if (isArabic) "محاولة" else "Tentative"} #${item.retryCount}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnCoralContainer
                        )
                        Text(text = item.payloadSummary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${item.failureReason} • ${CasablancaTime.formatDateTime(item.createdAtMillis, isArabic)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        OutlinedButton(
                            onClick = { onRetryFailedSync(item.id) },
                            modifier = Modifier.testTag("retry_sync_${item.id}")
                        ) {
                            Text(s.retryBtn)
                        }
                    }
                }
            }
        }
    }
}
