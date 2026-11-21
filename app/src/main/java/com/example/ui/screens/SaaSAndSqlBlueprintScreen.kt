package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.supabase.SupabaseSchemaSql
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.strings
import com.example.ui.viewmodel.ParcelHubUiState
import kotlin.math.roundToInt

@Composable
fun PlansAndBillingScreen(
    state: ParcelHubUiState,
    onSelectPlan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val isArabic = LocalAppLanguage.current == AppLanguage.AR

    var monthlyParcelsSlider by remember { mutableFloatStateOf(1200f) }

    val parcels = monthlyParcelsSlider.roundToInt()
    val planCostMad = when {
        parcels <= 50 -> 0.0
        parcels <= 400 -> 149.0
        parcels <= 1500 -> 349.0
        else -> 799.0
    }
    // Estimated Retour reduction of 8% * 35 MAD avg shipping fee saved per avoided return
    val avoidedRetours = (parcels * 0.08).roundToInt()
    val estimatedSavingsMad = avoidedRetours * 35.0
    val netMonthlyGainMad = estimatedSavingsMad - planCostMad

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Subscription Plans in MAD
        item {
            Text(
                text = s.plansTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(SupabaseSchemaSql.subscriptionTiers, key = { it.id }) { tier ->
            val isCurrent = state.session.currentPlanId == tier.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (tier.isRecommended) {
                        EmeraldContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
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
                            text = if (isArabic) tier.nameAr else tier.nameFr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tier.priceMadMonthly} MAD/${if (isArabic) "شهر" else "mois"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    }
                    val features = if (isArabic) tier.featuresAr else tier.featuresFr
                    features.forEach { feat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(15.dp))
                            Text(feat, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (isCurrent) {
                        Surface(
                            color = EmeraldContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isArabic) "الباقة الحالية" else "Plan actuel",
                                color = OnEmeraldContainer,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectPlan(tier.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("select_plan_${tier.id.lowercase()}")
                        ) {
                            Text(if (isArabic) "اختيار الباقة" else "Choisir ce plan")
                        }
                    }
                }
            }
        }

        // 2. Merchant ROI & Retour Savings Calculator
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = EmeraldPrimary)
                        Text(
                            text = s.roiCalculatorTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${if (isArabic) "عدد الطرود الشهري:" else "Volume mensuel :"} $parcels",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = monthlyParcelsSlider,
                        onValueChange = { monthlyParcelsSlider = it },
                        valueRange = 50f..4000f,
                        modifier = Modifier.testTag("roi_parcels_slider")
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isArabic) "تكلفة الاشتراك المناسبة:" else "Abonnement adapté :", style = MaterialTheme.typography.bodySmall)
                        Text("${planCostMad.toInt()} MAD", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isArabic) "طرود Retour التي يتم تفاديها (~8%):" else "Retours évités (~8%) :", style = MaterialTheme.typography.bodySmall)
                        Text("$avoidedRetours ${if (isArabic) "طرد" else "colis"}", fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isArabic) "التوفير التقديري في مصاريف الشحن:" else "Économies livraison :", style = MaterialTheme.typography.bodySmall)
                        Text("${estimatedSavingsMad.toInt()} MAD", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    }
                    Text(
                        text = if (isArabic) {
                            "صافي الربح الإضافي المتوقع شهرياً: +${netMonthlyGainMad.roundToInt()} MAD"
                        } else {
                            "Gain net mensuel estimé : +${netMonthlyGainMad.roundToInt()} MAD"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldPrimary
                    )
                }
            }
        }

        // 3. Moroccan Payments
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(s.paymentMethodsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (isArabic) {
                            "• البطاقة البنكية المغربية (CMI)\n• التحويل البنكي (Attijariwafa / CIH)\n• الدفع نقداً عبر وكالات CashPlus و Wafacash\n• البطاقات البنكية الدولية"
                        } else {
                            "• Carte bancaire marocaine (CMI)\n• Virement bancaire (Attijariwafa / CIH)\n• Paiement espèces via CashPlus / Wafacash\n• Cartes bancaires internationales"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
