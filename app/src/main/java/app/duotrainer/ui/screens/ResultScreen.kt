package app.duotrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.UiState
import app.duotrainer.core.Rating
import app.duotrainer.ui.components.AppCard
import app.duotrainer.ui.components.Dot
import app.duotrainer.ui.components.ProgressRing
import app.duotrainer.ui.components.StatTile
import app.duotrainer.ui.theme.StatusPalette

@Composable
fun ResultScreen(state: UiState, vm: AppViewModel, onDone: () -> Unit) {
    val s = state.session ?: return
    val status = StatusPalette.current
    val n = s.results.size.coerceAtLeast(1)
    val by = s.results.groupingBy { it.second }.eachCount()
    val ok = (by[Rating.GOOD] ?: 0) + (by[Rating.MASTERED] ?: 0)
    val minutes = ((s.lastAnswerAt - s.startedAt) / 60000.0)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(48.dp), tint = status.streak)
        Text("おつかれさまでした！", style = MaterialTheme.typography.headlineSmall)
        Text(s.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProgressRing(ok.toFloat() / n, size = 140.dp, stroke = 14.dp, color = status.mastered) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${ok * 100 / n}%", style = MaterialTheme.typography.headlineMedium)
                Text("正解率", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Rating.entries.forEach { r ->
                    val c = when (r) { Rating.WEAK -> status.weak; Rating.HARD -> status.hard; Rating.GOOD -> status.good; Rating.MASTERED -> status.mastered }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(c); Spacer(Modifier.width(6.dp))
                            Text(r.label, style = MaterialTheme.typography.labelLarge)
                        }
                        Text("${by[r] ?: 0}", style = MaterialTheme.typography.headlineSmall, color = c, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("学習時間", "%.1f".format(minutes), "分", Modifier.weight(1f), Icons.Rounded.Timer)
            StatTile("連続学習", "${vm.streak()}", "日", Modifier.weight(1f), Icons.Rounded.LocalFireDepartment, tint = status.streak)
        }
        Text(
            "苦手にした例文は明日また出ます。間隔を空けて思い出すほど、記憶は強くなります。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        if ((by[Rating.WEAK] ?: 0) > 0) {
            OutlinedButton(onClick = { vm.retryWeak() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("苦手だった ${by[Rating.WEAK]} 問をもう一度")
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("ホームへ") }
    }
}
