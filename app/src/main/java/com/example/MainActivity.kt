package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.auth.signOutFromGoogle
import com.example.ui.components.LocationPickerSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.OnboardingWizardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TwilightAlarmTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Главная", Icons.Rounded.Home, Icons.Outlined.Home, "nav_dashboard"),
    HISTORY("Журнал", Icons.Rounded.History, Icons.Outlined.History, "nav_history"),
    SETTINGS("Настройки", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwilightAlarmTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var isSigningInGoogle by remember { mutableStateOf(false) }
    var googleAuthError by remember { mutableStateOf<String?>(null) }

    // Silent sign-in attempt on startup if user previously authorized Google on this device;
    // does NOT block UI if unauthenticated.
    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val uid = currentUser?.uid
    val displayName = currentUser?.displayName ?: currentUser?.email ?: ""
    val scopeKey = uid ?: "local_guest"

    key(scopeKey) {
        val viewModel: MainViewModel = viewModel(
            key = scopeKey,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) as TwilightApp
                    MainViewModel(
                        container = app.container,
                        currentUserId = uid,
                        currentUserName = displayName
                    )
                }
            }
        )
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshPermissions()
        }

        TwilightRootContent(
            uiState = uiState,
            viewModel = viewModel,
            isSigningInGoogle = isSigningInGoogle,
            googleAuthError = googleAuthError,
            onGoogleSignIn = {
                googleAuthError = null
                isSigningInGoogle = true
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = { isSigningInGoogle = false },
                    onAuthError = { msg ->
                        isSigningInGoogle = false
                        googleAuthError = msg
                    },
                    scope = scope,
                    onAuthCancelled = { isSigningInGoogle = false }
                )
            },
            onSignOut = {
                viewModel.prepareSignOut()
                signOutFromGoogle(
                    credentialManager = credentialManager,
                    scope = scope
                )
            }
        )
    }
}

@Composable
private fun TwilightRootContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isSigningInGoogle: Boolean,
    googleAuthError: String?,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    if (uiState.isLoading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (!uiState.settings.onboardingCompleted) {
        OnboardingWizardScreen(
            uiState = uiState,
            isSigningInGoogle = isSigningInGoogle,
            googleAuthError = googleAuthError,
            onGoogleSignInClick = onGoogleSignIn,
            onRefreshPermissions = viewModel::refreshPermissions,
            onAutoDetectLocation = { viewModel.detectLocationAutomatically() },
            onSearchCities = viewModel::searchCities,
            onSelectCity = viewModel::selectManualCity,
            onSelectCustomCoordinates = viewModel::selectManualCoordinates,
            onSetMaxRetries = viewModel::updateMaxRetryAttempts,
            onSetTwilightPreference = viewModel::updateTwilightPreference,
            onCompleteOnboarding = viewModel::completeOnboarding
        )
        return
    }

    var activeTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
    var showLocationSheet by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = activeTab != MainTab.DASHBOARD) {
        activeTab = MainTab.DASHBOARD
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshPermissions()
        if (results.values.any { it }) {
            viewModel.detectLocationAutomatically()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
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
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (activeTab) {
                        MainTab.DASHBOARD -> DashboardScreen(
                            uiState = uiState,
                            onRunCheckNow = viewModel::runManualCheckNow,
                            onDismissLiveBanner = viewModel::dismissCheckBanner,
                            onOpenLocationPicker = { showLocationSheet = true },
                            onOpenSettingsTab = { activeTab = MainTab.SETTINGS }
                        )
                        MainTab.HISTORY -> HistoryScreen(
                            history = uiState.history,
                            onClearHistory = viewModel::clearHistory
                        )
                        MainTab.SETTINGS -> SettingsScreen(
                            uiState = uiState,
                            onRefreshPermissions = viewModel::refreshPermissions,
                            onSetDailyMidnightEnabled = viewModel::updateDailyMidnightEnabled,
                            onSetMaxRetries = viewModel::updateMaxRetryAttempts,
                            onSetRetryDelaySeconds = viewModel::updateRetryDelaySeconds,
                            onSetTwilightPreference = viewModel::updateTwilightPreference,
                            onSetAlarmOffsetMinutes = viewModel::updateAlarmOffsetMinutes,
                            onSetAlarmLabel = viewModel::updateAlarmLabel,
                            onSetReplaceOldAlarm = viewModel::updateReplaceOldAlarm,
                            onSetLocationMode = viewModel::setLocationMode,
                            onOpenLocationPicker = { showLocationSheet = true },
                            onReopenOnboarding = viewModel::reopenOnboarding,
                            isSigningInGoogle = isSigningInGoogle,
                            googleAuthError = googleAuthError,
                            onGoogleSignIn = onGoogleSignIn,
                            onSyncNow = viewModel::syncNowToCloud,
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }

    if (showLocationSheet) {
        LocationPickerSheet(
            currentLocationName = uiState.settings.locationName,
            searchQuery = uiState.citySearchQuery,
            searchResults = uiState.citySearchResults,
            isSearching = uiState.isSearchingCities,
            isDetectingGps = uiState.isDetectingLocation,
            detectError = uiState.locationDetectError,
            searchError = uiState.citySearchError,
            hasLocationPermission = uiState.permissions.locationGranted,
            onDismiss = { showLocationSheet = false },
            onRequestLocationPermissionAndDetect = {
                if (uiState.permissions.locationGranted) {
                    viewModel.detectLocationAutomatically()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            },
            onQueryChange = viewModel::searchCities,
            onCitySelected = viewModel::selectManualCity,
            onCustomCoordinatesSaved = viewModel::selectManualCoordinates
        )
    }
}
