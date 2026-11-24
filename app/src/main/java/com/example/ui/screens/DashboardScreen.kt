package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CheckLiveState
import com.example.data.model.LocationMode
import com.example.system.ClockAlarmHelper
import com.example.ui.MainUiState
import com.example.ui.components.TwilightHorizonCard
import com.example.ui.theme.AuroraGreen
import com.example.ui.theme.AuroraGreenContainer
import com.example.ui.theme.HorizonAmber
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onRunCheckNow: () -> Unit,
    onDismissLiveBanner: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = uiState.settings
    val latest = uiState.latestCheck
    val live = uiState.checkLiveState

    val heroTime = when (live) {
        is CheckLiveState.Succeeded -> live.times.selectedTimeLocal
        else -> latest?.twilightTime ?: "--:--"
    }
    val heroStage = when (live) {
        is CheckLiveState.Succeeded -> live.times.selectedStageLabel
        else -> latest?.twilightStage ?: settings.twilightPreference.shortTitleRu
    }

    val astroTime = (live as? CheckLiveState.Succeeded)?.times?.astronomicalBeginLocal ?: latest?.astronomicalTime
    val nautTime = (live as? CheckLiveState.Succeeded)?.times?.nauticalBeginLocal ?: latest?.nauticalTime
    val civilTime = (live as? CheckLiveState.Succeeded)?.times?.civilBeginLocal ?: latest?.civilTime
    val sunriseTime = (live as? CheckLiveState.Succeeded)?.times?.sunriseLocal ?: latest?.sunriseTime

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
            // Permission Warning Pill if any critical permission missing
            if (uiState.permissions.coreGrantedCount < 4) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSettingsTab() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Выданы ${uiState.permissions.coreGrantedCount} из 4 разрешений. Нажмите, чтобы проверить доступ для 00:00.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Hero Twilight Card
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_twilight_hero),
                        contentDescription = "Фон рассвета",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xD90B1124),
                                        Color(0xB80E1630),
                                        Color(0xEC0B1124)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = HorizonAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (settings.dailyMidnightEnabled) {
                                            "00:00 · ${uiState.countdownToMidnightText}"
                                        } else {
                                            "Автопроверка в 00:00 выкл."
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onOpenLocationPicker() }
                                    .testTag("dashboard_location_pill")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Place,
                                        contentDescription = "Изменить местоположение",
                                        tint = HorizonAmber,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = settings.locationName.ifBlank { "Выбрать город" }
                                            .substringBefore(" · "),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        Text(
                            text = heroStage.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelLarge,
                            color = HorizonAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = heroTime,
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val statusText = when {
                            latest == null -> "Нажмите кнопку ниже, чтобы узнать время сумерек и поставить будильник"
                            latest.success && latest.alarmSet ->
                                "Будильник поставлен в «Часы» · Попытка ${latest.attemptsUsed}/${latest.maxAttempts}"
                            latest.success && !latest.alarmSet ->
                                "Время найдено (${latest.dateLabel}) · Откройте «Часы» для подтверждения"
                            else ->
                                "Последняя проверка (${latest.attemptsUsed}/${latest.maxAttempts}): будильник не поставлен"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                        if (!settings.locationTimezoneId.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Местное время выбранного города: ${settings.locationTimezoneId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onRunCheckNow,
                            enabled = live !is CheckLiveState.Running,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HorizonAmber,
                                contentColor = Color(0xFF0B1124)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("dashboard_check_now_button")
                        ) {
                            if (live is CheckLiveState.Running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF0B1124)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Попытка ${live.attempt} из ${live.maxAttempts}…")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Проверить сейчас и поставить будильник")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { ClockAlarmHelper.openClockApp(context) },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("dashboard_open_clock_button")
                        ) {
                            Icon(Icons.Rounded.Alarm, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Открыть системные «Часы»")
                        }
                    }
                }
            }

            // Live Check Feedback Banner
            AnimatedVisibility(visible = live !is CheckLiveState.Idle) {
                when (live) {
                    is CheckLiveState.Running -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = live.statusMessage,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { live.attempt / live.maxAttempts.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                    is CheckLiveState.Succeeded -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = AuroraGreenContainer,
                            border = BorderStroke(1.dp, AuroraGreen.copy(alpha = 0.45f))
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
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Успешно с попытки ${live.attemptsUsed}/${live.maxAttempts}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AuroraGreen
                                    )
                                    Text(
                                        text = live.message,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                TextButton(onClick = onDismissLiveBanner) {
                                    Text("ОК")
                                }
                            }
                        }
                    }
                    is CheckLiveState.Failed -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Будильник не поставлен (${live.attemptsUsed}/${live.maxAttempts})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = live.reason,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                TextButton(onClick = onDismissLiveBanner) {
                                    Text("Скрыть")
                                }
                            }
                        }
                    }
                    CheckLiveState.Idle -> Unit
                }
            }

            // Quick Configuration Summary Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SummaryMiniTile(
                    icon = Icons.Rounded.Refresh,
                    label = "Лимит попыток",
                    value = "до ${settings.maxRetryAttempts}",
                    onClick = onOpenSettingsTab,
                    modifier = Modifier.weight(1f)
                )
                SummaryMiniTile(
                    icon = Icons.Rounded.Place,
                    label = "Геопозиция",
                    value = if (settings.locationMode == LocationMode.AUTO) "Авто" else "Вручную",
                    onClick = onOpenLocationPicker,
                    modifier = Modifier.weight(1f)
                )
            }

            // Custom Canvas Twilight Horizon Card
            TwilightHorizonCard(
                astronomicalTime = astroTime,
                nauticalTime = nautTime,
                civilTime = civilTime,
                sunriseTime = sunriseTime,
                activeStageLabel = heroStage
            )
        }
    }
}

@Composable
private fun SummaryMiniTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
