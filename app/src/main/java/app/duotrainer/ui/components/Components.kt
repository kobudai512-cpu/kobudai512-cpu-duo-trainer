package app.duotrainer.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duotrainer.core.Category
import app.duotrainer.core.StudyMode
import app.duotrainer.ui.theme.StatusPalette

val StudyMode.icon: ImageVector
    get() = when (this) {
        StudyMode.FREE -> Icons.Rounded.Edit
        StudyMode.CLOZE -> Icons.Rounded.Spellcheck
        StudyMode.PUZZLE -> Icons.Rounded.Extension
        StudyMode.DICTATION -> Icons.Rounded.Hearing
        StudyMode.SHADOWING -> Icons.Rounded.RecordVoiceOver
        StudyMode.WORD -> Icons.Rounded.Quiz
    }

@Composable
fun Category.color(): Color {
    val c = StatusPalette.current
    return when (this) {
        Category.NEW -> c.fresh
        Category.WEAK -> c.weak
        Category.GOOD -> c.good
        Category.MASTERED -> c.mastered
    }
}

/** 白いカード（abceed 風の、影の薄い角丸カード）。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = color)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation) { content() }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 円形の進捗リング。中央に任意の内容。 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** 覚えた・得意・苦手・未学習の積み上げ横棒。 */
@Composable
fun CategoryBar(counts: Map<Category, Int>, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val total = counts.values.sum().coerceAtLeast(1)
    val order = listOf(Category.MASTERED, Category.GOOD, Category.WEAK, Category.NEW)
    val colors = order.map { it.color() }
    Canvas(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50))) {
        var x = 0f
        order.forEachIndexed { i, c ->
            val w = size.width * (counts[c] ?: 0) / total
            if (w > 0f) drawRect(colors[i], Offset(x, 0f), Size(w, size.height))
            x += w
        }
    }
}

@Composable
fun CategoryLegend(counts: Map<Category, Int>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(Category.MASTERED, Category.GOOD, Category.WEAK, Category.NEW).forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.color()))
                Spacer(Modifier.width(6.dp))
                Text("${c.label} ${counts[c] ?: 0}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** 数字を大きく見せるタイル。 */
@Composable
fun StatTile(label: String, value: String, unit: String = "", modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary) {
    AppCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Text(unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 縦棒グラフ（日別の学習時間など）。 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    highlightLast: Boolean = true,
    valueLabel: (Float) -> String = { it.toInt().toString() },
) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val dim = color.copy(alpha = 0.45f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (v > 0f) Text(valueLabel(v), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Canvas(Modifier.padding(horizontal = 3.dp).fillMaxWidth().height(110.dp)) {
                        val h = size.height * (v / max)
                        drawRoundRect(track, Offset.Zero, size, CornerRadius(8f, 8f))
                        if (h > 0f) drawRoundRect(
                            if (highlightLast && i == values.lastIndex) color else dim,
                            Offset(0f, size.height - h), Size(size.width, h), CornerRadius(8f, 8f),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** 番号入りの丸いバッジ（セクション番号など）。 */
@Composable
fun NumberBadge(number: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primaryContainer, onColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(number.toString(), style = MaterialTheme.typography.titleMedium, color = onColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ModeTile(mode: StudyMode, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(mode.icon, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(10.dp))
            Text(mode.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(mode.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit) = if (enabled) clickable(onClick = onClick) else this
