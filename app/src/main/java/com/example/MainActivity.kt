package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.StorePlatform
import com.example.ui.components.MerchantAccountDialog
import com.example.ui.components.ParcelHubTopBar
import com.example.ui.components.WaybillLabelDialog
import com.example.ui.screens.CodAndIntegrationsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PlansAndBillingScreen
import com.example.ui.screens.ShipmentsScreen
import com.example.ui.screens.WhatsAppScreen
import com.example.ui.theme.ParcelHubTheme
import com.example.ui.theme.strings
import com.example.ui.viewmodel.ParcelHubViewModel

object ParcelHubRoutes {
    const val DASHBOARD = "dashboard"
    const val SHIPMENTS = "shipments"
    const val WHATSAPP = "whatsapp"
    const val COD_INTEGRATIONS = "cod_integrations"
    const val PLANS = "plans"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ParcelHubViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            ParcelHubTheme(language = state.language) {
                ParcelHubAppShell(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ParcelHubAppShell(viewModel: ParcelHubViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = strings()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ParcelHubRoutes.DASHBOARD

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.bannerMessage) {
        val msg = state.bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val destinations = listOf(
        NavDestinationItem(
            ParcelHubRoutes.DASHBOARD,
            s.navDashboard,
            Icons.Filled.Dashboard,
            Icons.Outlined.Dashboard
        ),
        NavDestinationItem(
            ParcelHubRoutes.SHIPMENTS,
            s.navShipments,
            Icons.Filled.LocalShipping,
            Icons.Outlined.LocalShipping
        ),
        NavDestinationItem(
            ParcelHubRoutes.WHATSAPP,
            s.navWhatsApp,
            Icons.Filled.Forum,
            Icons.Outlined.Forum
        ),
        NavDestinationItem(
            ParcelHubRoutes.COD_INTEGRATIONS,
            s.navCod,
            Icons.Filled.AccountBalanceWallet,
            Icons.Outlined.AccountBalanceWallet
        ),
        NavDestinationItem(
            ParcelHubRoutes.PLANS,
            s.navPlans,
            Icons.Filled.WorkspacePremium,
            Icons.Outlined.WorkspacePremium
        )
    )

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                ParcelHubTopBar(
                    session = state.session,
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onOpenAuth = { viewModel.openAuthDialog() }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        destinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        destinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_${item.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ParcelHubRoutes.DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ParcelHubRoutes.DASHBOARD) {
                        DashboardScreen(
                            state = state,
                            onImportYouCanTest = { viewModel.importSimulatedStoreOrder(StorePlatform.YOUCAN) },
                            onRefreshTracking = { viewModel.runFallbackPolling() },
                            onToggleBlacklist = { viewModel.toggleBlacklist(it) },
                            onInspectOrder = { query ->
                                viewModel.updateSearchQuery(query)
                                navigateTo(ParcelHubRoutes.SHIPMENTS)
                            },
                            onOpenShipments = {
                                viewModel.updateSearchQuery("")
                                navigateTo(ParcelHubRoutes.SHIPMENTS)
                            }
                        )
                    }
                    composable(ParcelHubRoutes.SHIPMENTS) {
                        ShipmentsScreen(
                            state = state,
                            onSearchChange = { viewModel.updateSearchQuery(it) },
                            onSelectStatus = { viewModel.selectStatusFilter(it) },
                            onSelectCarrier = { viewModel.selectCarrierFilter(it) },
                            onCreateOrder = { name, phone, city, addr, prod, cod, platform ->
                                viewModel.createManualOrder(name, phone, city, addr, prod, cod, platform)
                            },
                            onDispatchCarrier = { orderId, carrier ->
                                viewModel.dispatchToCarrier(orderId, carrier)
                            },
                            onCancelShipment = { viewModel.cancelShipment(it) },
                            onSyncToStore = { viewModel.syncOrderToStore(it) },
                            onOpenLabel = { viewModel.openLabelPreview(it) },
                            onSimulateWebhook = { id, raw, evtId, note, validSig, stale ->
                                viewModel.simulateWebhook(id, raw, evtId, note, validSig, stale)
                            }
                        )
                    }
                    composable(ParcelHubRoutes.WHATSAPP) {
                        WhatsAppScreen(
                            state = state,
                            onSendWhatsApp = { id, scenario, darija ->
                                viewModel.sendWhatsApp(id, scenario, darija)
                            },
                            onSimulateReply = { id, confirmed ->
                                viewModel.simulateCustomerReply(id, confirmed)
                            },
                            onToggleOptOut = { viewModel.toggleOptOut(it) }
                        )
                    }
                    composable(ParcelHubRoutes.COD_INTEGRATIONS) {
                        CodAndIntegrationsScreen(
                            state = state,
                            onReconcileCod = { viewModel.reconcileCod(it) },
                            onImportStoreOrder = { viewModel.importSimulatedStoreOrder(it) },
                            onToggleStoreActive = { viewModel.toggleStoreActive(it) },
                            onToggleCarrierActive = { viewModel.toggleCarrierActive(it) },
                            onTestCarrierAdapter = { viewModel.testCarrierAdapter(it) },
                            onRefreshTracking = { viewModel.runFallbackPolling() },
                            onRetryFailedSync = { viewModel.retryDeadLetter(it) }
                        )
                    }
                    composable(ParcelHubRoutes.PLANS) {
                        PlansAndBillingScreen(
                            state = state,
                            onSelectPlan = { viewModel.selectPlan(it) }
                        )
                    }
                }
            }
        }
    }

    if (state.isAuthDialogOpen) {
        MerchantAccountDialog(
            session = state.session,
            isBusy = state.isBusy,
            authErrorMessage = state.authErrorMessage,
            onClearError = { viewModel.clearAuthError() },
            onDismiss = { viewModel.dismissAuthDialog() },
            onSignIn = { email, pwd -> viewModel.signIn(email, pwd) },
            onSignUp = { storeName, email, pwd -> viewModel.signUp(storeName, email, pwd) },
            onSignOut = { viewModel.signOut() }
        )
    }

    state.activeLabelPreview?.let { label ->
        WaybillLabelDialog(
            label = label,
            onDismiss = { viewModel.dismissLabelPreview() }
        )
    }
}
