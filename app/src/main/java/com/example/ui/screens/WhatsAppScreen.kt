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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.whatsapp.WhatsAppAutomationEngine
import com.example.domain.model.WhatsAppScenario
import com.example.domain.util.CasablancaTime
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.CoralRetour
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.WhatsAppContainer
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.strings
import com.example.ui.viewmodel.ParcelHubUiState

@Composable
fun WhatsAppScreen(
    state: ParcelHubUiState,
    onSendWhatsApp: (String, WhatsAppScenario, Boolean) -> Unit,
    onSimulateReply: (String, Boolean) -> Unit,
    onToggleOptOut: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    var useDarija by remember { mutableStateOf(true) }
    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    var selectedScenario by remember { mutableStateOf(WhatsAppScenario.ORDER_CONFIRMATION) }

    val selectedOrder = state.allOrders.find { it.id == selectedOrderId } ?: state.allOrders.firstOrNull()
    val selectedTemplate = state.templates.find { it.scenario == selectedScenario }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // 1. WhatsApp Cost & Automation Info Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = WhatsAppContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Forum, contentDescription = null, tint = WhatsAppGreen)
                        Text(
                            text = if (isArabic) "أتمتة إشعارات واتساب (WhatsApp Business)" else "Automatisation WhatsApp Business",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isArabic) {
                            "• تأكيد الطلب قبل الشحن يقلل نسبة الـ Retour بشكل ملموس.\n• التكلفة التقديرية عبر القوالب الرسمية ~0.16 درهم لكل محادثة (مع منع التكرار تلقائياً واحترام طلبات إيقاف الإشعارات)."
                        } else {
                            "• La confirmation avant expédition réduit nettement les retours.\n• Coût estimé ~0,16 MAD par conversation avec protection anti-doublon et respect des désabonnements."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 2. Language & Target Order Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "لغة الرسالة:" else "Langue du message :",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = useDarija,
                                onClick = { useDarija = true },
                                label = { Text(s.darijaLabel) },
                                modifier = Modifier.testTag("whatsapp_darija_chip")
                            )
                            FilterChip(
                                selected = !useDarija,
                                onClick = { useDarija = false },
                                label = { Text(s.frenchLabel) },
                                modifier = Modifier.testTag("whatsapp_french_chip")
                            )
                        }
                    }

                    Text(
                        text = if (isArabic) "اختر الطلب ونوع الإشعار:" else "Commande & scénario :",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.allOrders.forEach { order ->
                            FilterChip(
                                selected = selectedOrder?.id == order.id,
                                onClick = { selectedOrderId = order.id },
                                label = { Text("${order.storeOrderRef} (${order.customer.fullName.substringBefore(" ")})") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WhatsAppScenario.entries.forEach { scenario ->
                            FilterChip(
                                selected = selectedScenario == scenario,
                                onClick = { selectedScenario = scenario },
                                label = { Text(scenario.title(isArabic)) },
                                modifier = Modifier.testTag("wa_scenario_${scenario.name.lowercase()}")
                            )
                        }
                    }

                    if (selectedOrder != null && selectedTemplate != null) {
                        val previewText = WhatsAppAutomationEngine.renderBody(
                            template = selectedTemplate,
                            order = selectedOrder,
                            useDarija = useDarija
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = selectedOrder.customer.phoneNormalized,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = WhatsAppGreen
                                )
                                Text(text = previewText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Block,
                                    contentDescription = null,
                                    tint = if (selectedOrder.customer.whatsappOptOut) CoralRetour else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(s.optOutLabel, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = selectedOrder.customer.whatsappOptOut,
                                onCheckedChange = { onToggleOptOut(selectedOrder.customer.id) },
                                modifier = Modifier.testTag("whatsapp_opt_out_switch")
                            )
                        }

                        Button(
                            onClick = { onSendWhatsApp(selectedOrder.id, selectedScenario, useDarija) },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("send_whatsapp_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.sendWhatsAppBtn)
                        }

                        if (selectedScenario == WhatsAppScenario.ORDER_CONFIRMATION) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onSimulateReply(selectedOrder.id, true) },
                                    modifier = Modifier.weight(1f).testTag("wa_reply_yes_button")
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.customerConfirmYes, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = { onSimulateReply(selectedOrder.id, false) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRetour),
                                    modifier = Modifier.weight(1f).testTag("wa_reply_no_button")
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.customerConfirmNo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Notification Logs
        item {
            Text(
                text = if (isArabic) "سجل الرسائل المرسلة" else "Historique des messages envoyés",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(state.notificationLogs, key = { it.id }) { log ->
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
                            text = "${log.customerPhone} • ${log.scenario.title(isArabic)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = EmeraldContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${log.costMad} MAD",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnEmeraldContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(text = log.renderedMessage, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isArabic) "تم الإرسال" else "Envoyé",
                            style = MaterialTheme.typography.labelSmall,
                            color = WhatsAppGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = CasablancaTime.formatDateTime(log.sentAtMillis, isArabic),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
