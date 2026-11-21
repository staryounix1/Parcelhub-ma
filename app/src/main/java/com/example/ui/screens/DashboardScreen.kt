package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.BreakdownMetric
import com.example.domain.util.CasablancaTime
import com.example.ui.components.ParcelStatusBadge
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRetour
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnCoralContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.strings
import com.example.ui.viewmodel.ParcelHubUiState
import java.util.Locale

@Composable
fun DashboardScreen(
    state: ParcelHubUiState,
    onImportYouCanTest: () -> Unit,
    onRefreshTracking: () -> Unit,
    onToggleBlacklist: (String) -> Unit,
    onInspectOrder: (String) -> Unit,
    onOpenShipments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Hero COD Cashflow Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(EmeraldPrimary, EmeraldPrimaryLight)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = state.session.storeBrandName,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = s.codCollected,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${state.kpis.totalParcels} ${if (isArabic) "طرد" else "colis"}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Text(
                        text = "${state.kpis.codCollectedMad.toInt()} ${s.madUnit}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = s.codPending,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${state.kpis.codPendingMad.toInt()} ${s.madUnit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = s.avgDeliveryTime,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", state.kpis.avgDeliveryHours)} ${s.hoursUnit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 2. Delivery & Retour KPIs Pair
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMiniCard(
                    title = s.deliveryRate,
                    value = "${String.format(Locale.US, "%.1f", state.kpis.deliveryRatePercent)}%",
                    subtitle = "${state.kpis.deliveredCount} ${if (isArabic) "مُسلّم" else "livrés"}",
                    icon = Icons.Filled.CheckCircle,
                    containerColor = EmeraldContainer,
                    contentColor = OnEmeraldContainer,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = s.retourRate,
                    value = "${String.format(Locale.US, "%.1f", state.kpis.retourRatePercent)}%",
                    subtitle = "${state.kpis.retourCount} ${if (isArabic) "مرتجع" else "retours"}",
                    icon = Icons.Filled.TrendingDown,
                    containerColor = CoralContainer,
                    contentColor = OnCoralContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Quick Operations Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onImportYouCanTest,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("quick_import_youcan_button")
                ) {
                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "استيراد YouCan" else "Import YouCan",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onRefreshTracking,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("quick_refresh_tracking_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.refreshTrackingBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // 4. Retour Comparison by City
        item {
            BreakdownCard(
                title = s.retourByCity,
                metrics = state.cityBreakdown,
                accentColor = AmberAccent
            )
        }

        // 5. Retour Comparison by Carrier
        item {
            BreakdownCard(
                title = s.retourByCarrier,
                metrics = state.carrierBreakdown,
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        // 6. Smart Alerts: Delayed +3 Days & Blacklist
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberAccent)
                    Text(
                        text = s.smartAlerts,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onOpenShipments,
                    modifier = Modifier.testTag("alerts_view_shipments_button")
                ) {
                    Text(s.navShipments)
                }
            }
        }

        if (state.delayedOrders.isEmpty() && state.blacklistedOrders.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldPrimary)
                        Text(s.noAlertsMessage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            items(state.delayedOrders, key = { "delay_${it.id}" }) { order ->
                val days = CasablancaTime.daysElapsed(order.createdAtMillis)
                Card(
                    onClick = { onInspectOrder(order.trackingNumber ?: order.storeOrderRef) },
                    colors = CardDefaults.cardColors(containerColor = AmberContainer.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delayed_alert_card_${order.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = OnAmberContainer, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${s.delayedParcelsTitle} • $days ${if (isArabic) "أيام" else "j"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OnAmberContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${order.customer.fullName} (${order.customer.city}) • ${order.trackingNumber ?: order.storeOrderRef}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${order.carrierCode?.displayName ?: "-"} • ${order.codAmountMad.toInt()} ${s.madUnit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ParcelStatusBadge(status = order.status)
                    }
                }
            }

            items(state.blacklistedOrders, key = { "blk_${it.id}" }) { order ->
                Card(
                    onClick = { onInspectOrder(order.customer.phoneNormalized) },
                    colors = CardDefaults.cardColors(containerColor = CoralContainer.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blacklist_alert_card_${order.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.GppBad, contentDescription = null, tint = CoralRetour, modifier = Modifier.size(16.dp))
                                Text(
                                    text = s.blacklistTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OnCoralContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${order.customer.fullName} • ${order.customer.phoneNormalized}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isArabic) {
                                    "عدد المرتجعات: ${order.customer.returnCount} من ${order.customer.totalOrders} طلبات"
                                } else {
                                    "Retours : ${order.customer.returnCount} sur ${order.customer.totalOrders} commandes"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onToggleBlacklist(order.customer.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRetour),
                            modifier = Modifier.testTag("toggle_blacklist_${order.customer.id}")
                        ) {
                            Text(if (isArabic) "إزالة" else "Retirer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
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
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerColor)
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    metrics: List<BreakdownMetric>,
    accentColor: Color
) {
    val isArabic = LocalAppLanguage.current == AppLanguage.AR
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (metrics.isEmpty()) {
                Text(
                    text = if (isArabic) "لا توجد بيانات كافية بعد" else "Données insuffisantes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                metrics.take(5).forEach { metric ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = metric.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.0f", metric.retourRatePercent)}% (${metric.retourCount}/${metric.totalCount})",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (metric.retourRatePercent >= 30.0) CoralRetour else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (metric.retourRatePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                            color = if (metric.retourRatePercent >= 30.0) CoralRetour else accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}
