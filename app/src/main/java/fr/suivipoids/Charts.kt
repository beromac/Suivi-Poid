package fr.suivipoids

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Courbe de poids : points bruts (petits cercles) + tendance lissée sur 7 jours (trait). */
@Composable
fun WeightChart(
    raw: List<WeightPoint>,
    trend: List<WeightPoint>,
    targetKg: Double? = null,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.outline
    val targetColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        if (raw.size < 2) return@Canvas
        val values = raw.map { it.kg } + trend.map { it.kg } + listOfNotNull(targetKg)
        val minY = (values.min() - 0.5)
        val maxY = (values.max() + 0.5)
        val minX = raw.first().date.toEpochDay().toFloat()
        val maxX = raw.last().date.toEpochDay().toFloat().coerceAtLeast(minX + 1f)

        fun x(epochDay: Long) = ((epochDay - minX) / (maxX - minX)) * size.width
        fun y(kg: Double) = (size.height - ((kg - minY) / (maxY - minY)).toFloat() * size.height)

        targetKg?.let {
            val ty = y(it)
            drawLine(
                color = targetColor,
                start = Offset(0f, ty),
                end = Offset(size.width, ty),
                strokeWidth = 2f,
            )
        }

        raw.forEach {
            drawCircle(color = dotColor, radius = 4f, center = Offset(x(it.date.toEpochDay()), y(it.kg)))
        }

        val path = Path()
        trend.forEachIndexed { i, p ->
            val px = x(p.date.toEpochDay())
            val py = y(p.kg)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 6f))
    }
}

/** Petits histogrammes : minutes d'activité par semaine. */
@Composable
fun WeeklyBars(
    minutesPerWeek: List<Long>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        if (minutesPerWeek.isEmpty()) return@Canvas
        val maxV = (minutesPerWeek.max()).coerceAtLeast(1L).toFloat()
        val slot = size.width / minutesPerWeek.size
        val barWidth = slot * 0.6f
        minutesPerWeek.forEachIndexed { i, v ->
            val h = (v / maxV) * size.height
            drawRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
            )
        }
    }
}
