package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DawnCoral
import com.example.ui.theme.HorizonAmber
import com.example.ui.theme.StarlightBlue

@Composable
fun TwilightHorizonCard(
    astronomicalTime: String?,
    nauticalTime: String?,
    civilTime: String?,
    sunriseTime: String?,
    activeStageLabel: String?,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Шкала рассвета",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Последовательность утренних сумерек до восхода солнца",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
            ) {
                val w = size.width
                val h = size.height
                val horizonY = h * 0.72f

                // Horizon gradient line
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            outlineColor.copy(alpha = 0.3f),
                            StarlightBlue.copy(alpha = 0.7f),
                            DawnCoral.copy(alpha = 0.8f),
                            HorizonAmber
                        )
                    ),
                    start = Offset(0f, horizonY),
                    end = Offset(w, horizonY),
                    strokeWidth = 3f
                )

                // Sun path arc rising from -18 deg to above horizon
                val arcPath = Path().apply {
                    moveTo(w * 0.08f, h * 0.92f)
                    cubicTo(
                        w * 0.36f, h * 0.76f,
                        w * 0.64f, h * 0.56f,
                        w * 0.92f, h * 0.22f
                    )
                }
                drawPath(
                    path = arcPath,
                    brush = Brush.horizontalGradient(
                        listOf(StarlightBlue, DawnCoral, HorizonAmber)
                    ),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )

                val points = listOf(
                    Offset(w * 0.14f, h * 0.87f) to StarlightBlue,
                    Offset(w * 0.40f, h * 0.75f) to Color(0xFF9F8BFF),
                    Offset(w * 0.67f, h * 0.57f) to DawnCoral,
                    Offset(w * 0.90f, h * 0.26f) to HorizonAmber
                )
                points.forEachIndexed { idx, (pt, color) ->
                    drawCircle(
                        color = color.copy(alpha = 0.28f),
                        radius = if (idx == 0) 18f else 13f,
                        center = pt
                    )
                    drawCircle(
                        color = color,
                        radius = if (idx == 0) 8f else 6f,
                        center = pt
                    )
                }
                // Highlight selected earliest node glow
                drawCircle(
                    color = primaryColor.copy(alpha = 0.22f),
                    radius = 22f,
                    center = points.first().first
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TwilightStageRow(
                    title = "Астрономические (-18°)",
                    subtitle = "Самые ранние сумерки",
                    time = astronomicalTime ?: "—",
                    accentColor = StarlightBlue,
                    isHighlighted = activeStageLabel?.contains("Астрономические", true) == true
                )
                TwilightStageRow(
                    title = "Навигационные (-12°)",
                    subtitle = "Силуэты и линия горизонта",
                    time = nauticalTime ?: "—",
                    accentColor = Color(0xFF9F8BFF),
                    isHighlighted = activeStageLabel?.contains("Навигационные", true) == true
                )
                TwilightStageRow(
                    title = "Гражданские (-6°)",
                    subtitle = "Светло до восхода",
                    time = civilTime ?: "—",
                    accentColor = DawnCoral,
                    isHighlighted = activeStageLabel?.contains("Гражданские", true) == true
                )
                TwilightStageRow(
                    title = "Восход солнца (0°)",
                    subtitle = "Появление солнечного диска",
                    time = sunriseTime ?: "—",
                    accentColor = HorizonAmber,
                    isHighlighted = false
                )
            }
        }
    }
}

@Composable
private fun TwilightStageRow(
    title: String,
    subtitle: String,
    time: String,
    accentColor: Color,
    isHighlighted: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor,
                    modifier = Modifier
                        .width(8.dp)
                        .height(26.dp)
                ) {}
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isHighlighted) "$subtitle · Выбрано для будильника" else subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHighlighted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Text(
                text = time,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                ),
                color = if (isHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
