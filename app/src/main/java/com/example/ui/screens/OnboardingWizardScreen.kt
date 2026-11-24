package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CityOption
import com.example.data.model.LocationMode
import com.example.data.model.TwilightPreference
import com.example.data.remote.CuratedCities
import com.example.system.PermissionStatusHelper
import com.example.ui.MainUiState
import com.example.ui.components.LocationPickerSheet
import com.example.ui.components.PermissionIcons
import com.example.ui.components.PermissionItemCard
import com.example.ui.theme.AuroraGreen
import com.example.ui.theme.AuroraGreenContainer
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OnboardingWizardScreen(
    uiState: MainUiState,
    isSigningInGoogle: Boolean,
    googleAuthError: String?,
    onGoogleSignInClick: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onAutoDetectLocation: () -> Unit,
    onSearchCities: (String) -> Unit,
    onSelectCity: (CityOption) -> Unit,
    onSelectCustomCoordinates: (Double, Double, String) -> Unit,
    onSetMaxRetries: (Int) -> Unit,
    onSetTwilightPreference: (TwilightPreference) -> Unit,
    onCompleteOnboarding: (runInitialCheck: Boolean) -> Unit
) {
    val context = LocalContext.current
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var showLocationSheet by rememberSaveable { mutableStateOf(false) }
    var autoDetectTriggeredOnce by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = currentStep > 0) {
        currentStep -= 1
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onRefreshPermissions()
        val granted = results.values.any { it }
        if (granted && currentStep == 2) {
            onAutoDetectLocation()
        }
    }

    val batchRuntimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
    }

    LaunchedEffect(currentStep) {
        if (currentStep == 2 && !autoDetectTriggeredOnce && uiState.permissions.locationGranted && !uiState.settings.hasLocation) {
            autoDetectTriggeredOnce = true
            onAutoDetectLocation()
        }
    }

    val stepTitles = listOf("Знакомство", "Разрешения", "Геопозиция", "Попытки и старт")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Step Header & Progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (currentStep > 0) {
                        IconButton(
                            onClick = { currentStep -= 1 },
                            modifier = Modifier.testTag("wizard_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Шаг ${currentStep + 1} из ${stepTitles.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stepTitles[currentStep],
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (currentStep + 1) / stepTitles.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Main Scrollable Step Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "wizard_step"
                    ) { step ->
                        when (step) {
                            0 -> WizardWelcomeStep(
                                isSignedIn = uiState.isSignedIn,
                                userName = uiState.currentUserName,
                                isSigningIn = isSigningInGoogle,
                                authError = googleAuthError,
                                onGoogleSignInClick = onGoogleSignInClick
                            )
                            1 -> WizardPermissionsStep(
                                uiState = uiState,
                                onRequestAllBasic = {
                                    val perms = mutableListOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    batchRuntimeLauncher.launch(perms.toTypedArray())
                                },
                                onRequestLocation = {
                                    locationLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                },
                                onRequestNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        context.startActivity(PermissionStatusHelper.createAppSettingsIntent(context))
                                    }
                                },
                                onRequestBattery = {
                                    context.startActivity(PermissionStatusHelper.createBatteryOptimizationIntent(context))
                                },
                                onRequestClockAccess = {
                                    if (!uiState.permissions.exactAlarmGranted) {
                                        context.startActivity(PermissionStatusHelper.createExactAlarmSettingsIntent(context))
                                    } else {
                                        context.startActivity(PermissionStatusHelper.createAppSettingsIntent(context))
                                    }
                                }
                            )
                            2 -> WizardLocationStep(
                                uiState = uiState,
                                onAutoDetectClick = {
                                    if (uiState.permissions.locationGranted) {
                                        onAutoDetectLocation()
                                    } else {
                                        locationLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            )
                                        )
                                    }
                                },
                                onQuickCityClick = onSelectCity,
                                onOpenFullPicker = { showLocationSheet = true }
                            )
                            else -> WizardPreferencesStep(
                                maxRetries = uiState.settings.maxRetryAttempts,
                                twilightPreference = uiState.settings.twilightPreference,
                                locationName = uiState.settings.locationName,
                                onSetMaxRetries = onSetMaxRetries,
                                onSetTwilightPreference = onSetTwilightPreference
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Wizard Controls
                when (currentStep) {
                    0 -> {
                        Button(
                            onClick = { currentStep = 1 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("wizard_start_button")
                        ) {
                            Text("Начать настройку")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                        }
                    }
                    1 -> {
                        Button(
                            onClick = { currentStep = 2 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("wizard_next_to_location")
                        ) {
                            Text(
                                if (uiState.permissions.coreGrantedCount == 4) {
                                    "Все разрешения готовы — далее"
                                } else {
                                    "Продолжить к геопозиции"
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                        }
                    }
                    2 -> {
                        Button(
                            onClick = { currentStep = 3 },
                            enabled = uiState.settings.hasLocation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("wizard_next_to_settings")
                        ) {
                            Text(
                                if (uiState.settings.hasLocation) "Далее к настройке попыток"
                                else "Сначала определите или выберите город"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                        }
                    }
                    else -> {
                        Button(
                            onClick = { onCompleteOnboarding(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("wizard_finish_and_test")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Завершить и проверить сумерки сейчас")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onCompleteOnboarding(false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("wizard_finish_only")
                        ) {
                            Text("Завершить и ждать проверки в 00:00")
                        }
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
                    onAutoDetectLocation()
                } else {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            },
            onQueryChange = onSearchCities,
            onCitySelected = onSelectCity,
            onCustomCoordinatesSaved = onSelectCustomCoordinates
        )
    }
}

@Composable
private fun WizardWelcomeStep(
    isSignedIn: Boolean,
    userName: String,
    isSigningIn: Boolean,
    authError: String?,
    onGoogleSignInClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(MaterialTheme.shapes.large)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_onboarding_welcome),
                contentDescription = "Иллюстрация утренних сумерек",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    )
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = "Мастер первой настройки",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Text(
            text = "Будильник на самые ранние сумерки",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Программа автоматизирует ночную проверку рассвета и сама передаёт время в системное приложение «Часы».",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WizardHighlightRow(
            icon = Icons.Rounded.Schedule,
            title = "Ежедневный запуск в 00:00",
            subtitle = "Ровно в полночь приложение запрашивает точное время самых ранних сумерек для ваших координат."
        )
        WizardHighlightRow(
            icon = Icons.Rounded.Refresh,
            title = "Надёжные повторные попытки",
            subtitle = "Если ответ не получен сразу, выполняется до 10 попыток (число настраивается). Если ответ так и не найден — будильник не ставится."
        )
        WizardHighlightRow(
            icon = Icons.Rounded.Alarm,
            title = "Интеграция с приложением «Часы»",
            subtitle = "Когда время определено, будильник автоматически добавляется в системные «Часы» на вашем устройстве."
        )

        // Optional Cloud Sync card
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isSignedIn) AuroraGreenContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(
                1.dp,
                if (isSignedIn) AuroraGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSignedIn) Icons.Rounded.CloudDone else Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = if (isSignedIn) AuroraGreen else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSignedIn) {
                            "Облако подключено: ${userName.ifBlank { "Google" }}"
                        } else {
                            "Облачное сохранение (необязательно)"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isSignedIn) {
                        "Настройки и журнал проверок будут сохраняться в Firebase Cloud Firestore."
                    } else {
                        "Войдите в Google, чтобы сохранять настройки и историю в облаке. Без входа всё работает локально на устройстве."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!authError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = authError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (!isSignedIn) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onGoogleSignInClick,
                        enabled = !isSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("wizard_optional_google_signin")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Вход через Google…")
                        } else {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Войти через Google")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardHighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WizardPermissionsStep(
    uiState: MainUiState,
    onRequestAllBasic: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestClockAccess: () -> Unit
) {
    val perms = uiState.permissions
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Доступы для надёжной работы",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Выдайте разрешения ниже, чтобы программа могла определять местоположение, работать в фоне в 00:00, присылать отчёты и ставить будильник в «Часы».",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!perms.locationGranted || !perms.notificationsGranted) {
            OutlinedButton(
                onClick = onRequestAllBasic,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("wizard_grant_basic_permissions")
            ) {
                Text("Запросить геопозицию и уведомления сразу")
            }
        }

        PermissionItemCard(
            icon = PermissionIcons.Location,
            title = "1. Доступ к геопозиции",
            description = "Определяет ваш город и координаты автоматически при каждой проверке.",
            visualHint = "Подсказка: в системном окне выберите «При использовании приложения». Если откажетесь, город можно указать вручную на следующем шаге.",
            isGranted = perms.locationGranted,
            actionLabel = "Разрешить геопозицию",
            testTag = "perm_location_btn",
            onActionClick = onRequestLocation
        )

        PermissionItemCard(
            icon = PermissionIcons.Notifications,
            title = "2. Уведомления",
            description = "Сообщает, на какое время поставлен будильник, или предупреждает, если после 10 попыток данные не получены.",
            visualHint = "Подсказка: нажмите «Разрешить» во всплывающем запросе, чтобы не пропустить ночной отчёт в 00:00.",
            isGranted = perms.notificationsGranted,
            actionLabel = "Разрешить уведомления",
            testTag = "perm_notifications_btn",
            onActionClick = onRequestNotifications
        )

        PermissionItemCard(
            icon = PermissionIcons.Battery,
            title = "3. Работа в фоновом режиме",
            description = "Защищает ночную проверку в 00:00 от отключения системой энергосбережения.",
            visualHint = "Подсказка: откроется диалог экономии заряда — выберите «Не ограничивать» / «Разрешить» для фоновой работы без пауз.",
            isGranted = perms.batteryOptimizationIgnored,
            actionLabel = "Отключить ограничения фона",
            testTag = "perm_battery_btn",
            onActionClick = onRequestBattery
        )

        PermissionItemCard(
            icon = PermissionIcons.ClockAlarm,
            title = "4. Редактирование часов и точный запуск",
            description = "Позволяет просыпаться точно в 00:00 и передавать будильник в системное приложение «Часы».",
            visualHint = if (perms.clockAppAvailable) {
                "Подсказка: на открывшемся экране включите переключатель «Разрешить установку будильников и напоминаний». Приложение «Часы» обнаружено на устройстве."
            } else {
                "Внимание: стандартное приложение «Часы» не обнаружено. Проверьте, что на устройстве установлен будильник с поддержкой Android Clock."
            },
            isGranted = perms.exactAlarmGranted && perms.clockAppAvailable,
            actionLabel = "Разрешить управление часами",
            testTag = "perm_exact_alarm_btn",
            onActionClick = onRequestClockAccess
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardLocationStep(
    uiState: MainUiState,
    onAutoDetectClick: () -> Unit,
    onQuickCityClick: (CityOption) -> Unit,
    onOpenFullPicker: () -> Unit
) {
    val settings = uiState.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Где вы находитесь?",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Время сумерек зависит от широты и долготы. Мы можем определить их автоматически или использовать выбранный вами город.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Current selected location card
        if (settings.hasLocation) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = AuroraGreenContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, AuroraGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = AuroraGreen
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.locationName.ifBlank { "Координаты сохранены" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s · %.4f°, %.4f°",
                                if (settings.locationMode == LocationMode.AUTO) "Автоматически" else "Вручную",
                                settings.latitude,
                                settings.longitude
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Auto-detect card
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LocationSearching,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Автоматическое определение",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Если геопозиция включена, координаты определятся сами и будут обновляться при смене города.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAutoDetectClick,
                    enabled = !uiState.isDetectingLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("wizard_auto_detect_btn")
                ) {
                    if (uiState.isDetectingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Ищем координаты…")
                    } else {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Определить автоматически")
                    }
                }

                if (!uiState.locationDetectError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = uiState.locationDetectError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Manual fallback card
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Выбор вручную",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Если автоматическое определение не сработало или вы хотите задать город вручную:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CuratedCities.presets.take(6).forEach { city ->
                        FilterChip(
                            selected = settings.locationName.startsWith(city.name),
                            onClick = { onQuickCityClick(city) },
                            label = { Text(city.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenFullPicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("wizard_open_city_search")
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Найти любой город или ввести координаты")
                }
            }
        }
    }
}

@Composable
private fun WizardPreferencesStep(
    maxRetries: Int,
    twilightPreference: TwilightPreference,
    locationName: String,
    onSetMaxRetries: (Int) -> Unit,
    onSetTwilightPreference: (TwilightPreference) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Попытки и тип сумерек",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Локация: ${locationName.ifBlank { "выбрана" }}. Осталось настроить поведение запросов в 00:00.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Retry configuration card
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Количество попыток запроса",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Если ответ не получен после всех попыток — будильник не ставится",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "$maxRetries",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(
                        onClick = { if (maxRetries > 1) onSetMaxRetries(maxRetries - 1) },
                        enabled = maxRetries > 1,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("wizard_retry_minus")
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить попытки")
                    }

                    Slider(
                        value = maxRetries.toFloat(),
                        onValueChange = { onSetMaxRetries(it.roundToInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(
                        onClick = { if (maxRetries < 30) onSetMaxRetries(maxRetries + 1) },
                        enabled = maxRetries < 30,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("wizard_retry_plus")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Увеличить попытки")
                    }
                }
            }
        }

        // Twilight preference card
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.NightlightRound,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Какие сумерки искать",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                TwilightPreference.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSetTwilightPreference(option) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = twilightPreference == option,
                            onClick = { onSetTwilightPreference(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = option.titleRu,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (twilightPreference == option) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = option.descriptionRu,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
