@file:OptIn(ExperimentalLayoutApi::class)

package app.duotrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.UiState
import app.duotrainer.core.Category
import app.duotrainer.core.Keys
import app.duotrainer.core.Stats
import app.duotrainer.core.category
import app.duotrainer.ui.components.AppCard
import app.duotrainer.ui.components.BarChart
import app.duotrainer.ui.components.CategoryBar
import app.duotrainer.ui.components.CategoryLegend
import app.duotrainer.ui.components.SectionTitle
import app.duotrainer.ui.components.StatTile
import app.duotrainer.ui.theme.StatusPalette
import java.time.LocalDate

/** 記録タブ：連続学習日数・学習時間・正解率のグラフ。 */
@Composable
fun StatsScreen(state: UiState, vm: AppViewModel) {
    val deck = state.deck ?: return
    val logs = state.user.dayLogs()
    val today = vm.today
    val status = StatusPalette.current
    var range by remember { mutableIntStateOf(7) }
    val days = (range - 1 downTo 0).map { today - it }
    val totalSec = logs.values.sumOf { it.seconds }
    val totalAns = logs.values.sumOf { it.answered }
    val totalOk = logs.values.sumOf { it.correct }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("学習記録", style = MaterialTheme.typography.headlineSmall)

        // 連続学習日数（大きく）
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = status.streak, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${Stats.streak(logs, today)}", style = MaterialTheme.typography.displaySmall, color = status.streak)
                    Text(" 日連続", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("最長", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Stats.bestStreak(logs)} 日", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 直近 7 日のスタンプ
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    (6 downTo 0).map { today - it }.forEach { d ->
                        val active = (logs[d]?.answered ?: 0) > 0
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(50))
                                    .background(if (active) status.streak else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (active) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Text(weekday(d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("総学習時間", "%.1f".format(totalSec / 3600.0), "時間", Modifier.weight(1f), Icons.Rounded.Timer)
            StatTile("回答数", "$totalAns", "問", Modifier.weight(1f), Icons.Rounded.EmojiEvents, tint = status.good)
            StatTile("正解率", if (totalAns > 0) "${totalOk * 100 / totalAns}" else "—", "%", Modifier.weight(1f), Icons.Rounded.CheckCircle, tint = status.mastered)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 30).forEach { r ->
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text("${r}日") })
            }
        }

        SectionTitle("学習時間（分）")
        AppCard(Modifier.fillMaxWidth()) {
            BarChart(
                values = days.map { (logs[it]?.seconds ?: 0L) / 60f },
                labels = days.map { label(it, range) },
                modifier = Modifier.padding(16.dp),
                valueLabel = { if (range > 14) "" else "%.0f".format(it) },
            )
        }

        SectionTitle("正解率（%）")
        AppCard(Modifier.fillMaxWidth()) {
            BarChart(
                values = days.map { d -> logs[d]?.let { if (it.answered > 0) it.correct * 100f / it.answered else 0f } ?: 0f },
                labels = days.map { label(it, range) },
                modifier = Modifier.padding(16.dp),
                color = status.mastered,
                valueLabel = { if (range > 14) "" else "%.0f".format(it) },
            )
        }

        SectionTitle("例文の仕分け")
        val counts = deck.sentences.groupingBy { state.user.progress[Keys.sentence(it.id)].category() }.eachCount()
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryBar(counts, height = 14.dp)
                CategoryLegend(counts)
            }
        }

        SectionTitle("セクション別の定着度")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    deck.sections.forEach { sec ->
                        val ss = deck.sentencesIn(sec)
                        val m = ss.count { state.user.progress[Keys.sentence(it.id)].category() == Category.MASTERED }
                        val g = ss.count { state.user.progress[Keys.sentence(it.id)].category() == Category.GOOD }
                        val h = ss.count { state.user.progress[Keys.sentence(it.id)].category() == Category.HARD }
                        val ratio = (m + g * 0.5f + h * 0.25f) / ss.size.coerceAtLeast(1)
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (ratio <= 0f) MaterialTheme.colorScheme.surfaceVariant
                                    else status.mastered.copy(alpha = 0.2f + 0.8f * ratio)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$sec", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = if (ratio > 0.5f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("色が濃いほど「簡単」「正解」が多いセクションです。", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun weekday(epochDay: Long): String =
    "月火水木金土日"[LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1].toString()

private fun label(epochDay: Long, range: Int): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return when {
        range <= 7 -> weekday(epochDay)
        range <= 14 -> "${d.dayOfMonth}"
        else -> if (d.dayOfMonth % 5 == 0 || d.dayOfMonth == 1) "${d.dayOfMonth}" else ""
    }
}
