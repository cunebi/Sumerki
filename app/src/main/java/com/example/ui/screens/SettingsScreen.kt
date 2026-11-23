package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.LocationMode
import com.example.data.model.TwilightPreference
import com.example.system.PermissionStatusHelper
import com.example.ui.MainUiState
import com.example.ui.components.PermissionIcons
import com.example.ui.components.PermissionItemCard
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onRefreshPermissions: () -> Unit,
    onSetDailyMidnightEnabled: (Boolean) -> Unit,
    onSetMaxRetries: (Int) -> Unit,
    onSetRetryDelaySeconds: (Int) -> Unit,
    onSetTwilightPreference: (TwilightPreference) -> Unit,
    onSetAlarmOffsetMinutes: (Int) -> Unit,
    onSetAlarmLabel: (String) -> Unit,
    onSetLocationMode: (LocationMode) -> Unit,
    onOpenLocationPicker: () -> Unit,
    onReopenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = uiState.settings
    val perms = uiState.permissions
    var labelDraft by remember(settings.alarmLabel) { mutableStateOf(settings.alarmLabel) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineSmall
            )

            // 1. Daily 00:00 Schedule
            SettingsSectionCard(title = "Ночная проверка в 00:00") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Автозапуск каждый день в 00:00",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "В полночь проверяет сумерки и ставит будильник в «Часах»",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.dailyMidnightEnabled,
                        onCheckedChange = onSetDailyMidnightEnabled,
                        modifier = Modifier.testTag("settings_midnight_switch")
                    )
                }
            }

            // 2. Retries & Delay
            SettingsSectionCard(title = "Запросы и повторы") {
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
                            text = "Если ответ не найден после ${settings.maxRetryAttempts} попыток, будильник не ставится",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${settings.maxRetryAttempts}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            if (settings.maxRetryAttempts > 1) {
                                onSetMaxRetries(settings.maxRetryAttempts - 1)
                            }
                        },
                        enabled = settings.maxRetryAttempts > 1,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_retry_minus")
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Меньше попыток")
                    }
                    Slider(
                        value = settings.maxRetryAttempts.toFloat(),
                        onValueChange = { onSetMaxRetries(it.roundToInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = {
                            if (settings.maxRetryAttempts < 30) {
                                onSetMaxRetries(settings.maxRetryAttempts + 1)
                            }
                        },
                        enabled = settings.maxRetryAttempts < 30,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_retry_plus")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Больше попыток")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Пауза между попытками",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${settings.retryDelaySeconds} сек",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.retryDelaySeconds.toFloat(),
                    onValueChange = { onSetRetryDelaySeconds(it.roundToInt()) },
                    valueRange = 1f..20f,
                    steps = 18
                )
            }

            // 3. Location Mode
            SettingsSectionCard(title = "Местоположение") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.locationName.ifBlank { "Не выбрано" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (settings.latitude != null && settings.longitude != null) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.4f°, %.4f° (%s)",
                                    settings.latitude,
                                    settings.longitude,
                                    if (settings.locationMode == LocationMode.AUTO) "Авто" else "Вручную"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSetLocationMode(LocationMode.AUTO) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Авто (GPS)")
                    }
                    Button(
                        onClick = onOpenLocationPicker,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("settings_change_location_btn")
                    ) {
                        Text("Выбрать место")
                    }
                }
            }

            // 4. Twilight & Alarm Settings
            SettingsSectionCard(title = "Будильник и тип сумерек") {
                TwilightPreference.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSetTwilightPreference(option) }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = settings.twilightPreference == option,
                            onClick = { onSetTwilightPreference(option) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = option.titleRu, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = option.descriptionRu,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Смещение будильника",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            settings.alarmOffsetMinutes > 0 -> "+${settings.alarmOffsetMinutes} мин"
                            settings.alarmOffsetMinutes < 0 -> "${settings.alarmOffsetMinutes} мин"
                            else -> "Точно в сумерки"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.alarmOffsetMinutes.toFloat(),
                    onValueChange = { onSetAlarmOffsetMinutes(it.roundToInt()) },
                    valueRange = -30f..30f
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = labelDraft,
                    onValueChange = {
                        labelDraft = it
                        onSetAlarmLabel(it)
                    },
                    label = { Text("Подпись будильника в «Часах»") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 5. Permissions Hub & Wizard Re-launch
            Text(
                text = "Системные разрешения (${perms.coreGrantedCount}/4)",
                style = MaterialTheme.typography.titleLarge
            )

            PermissionItemCard(
                icon = PermissionIcons.Location,
                title = "Геопозиция",
                description = "Для автоматического определения координат.",
                visualHint = "Выберите «При использовании приложения».",
                isGranted = perms.locationGranted,
                actionLabel = "Выдать геопозицию",
                testTag = "settings_perm_location",
                onActionClick = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            )

            PermissionItemCard(
                icon = PermissionIcons.Notifications,
                title = "Уведомления",
                description = "Отчёты о поставленном будильнике или об ошибке после N попыток.",
                visualHint = "Разрешите показ уведомлений для канала «Будильник на сумерки».",
                isGranted = perms.notificationsGranted,
                actionLabel = "Включить уведомления",
                testTag = "settings_perm_notifications",
                onActionClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(PermissionStatusHelper.createAppSettingsIntent(context))
                    }
                }
            )

            PermissionItemCard(
                icon = PermissionIcons.Battery,
                title = "Фоновый режим без ограничений",
                description = "Гарантирует срабатывание проверки в 00:00 в режиме сна.",
                visualHint = "Отключите оптимизацию батареи для приложения «Сумерки».",
                isGranted = perms.batteryOptimizationIgnored,
                actionLabel = "Разрешить фоновый режим",
                testTag = "settings_perm_battery",
                onActionClick = {
                    context.startActivity(PermissionStatusHelper.createBatteryOptimizationIntent(context))
                }
            )

            PermissionItemCard(
                icon = PermissionIcons.ClockAlarm,
                title = "Редактирование часов и точные будильники",
                description = "Точный запуск в 00:00 и передача времени в «Часы».",
                visualHint = "Разрешите «Будильники и напоминания» в настройках системы.",
                isGranted = perms.exactAlarmGranted && perms.clockAppAvailable,
                actionLabel = "Настроить доступ к часам",
                testTag = "settings_perm_clock",
                onActionClick = {
                    context.startActivity(PermissionStatusHelper.createExactAlarmSettingsIntent(context))
                }
            )

            OutlinedButton(
                onClick = onReopenOnboarding,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("settings_reopen_wizard")
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Открыть мастер настройки заново")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
