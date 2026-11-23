@file:OptIn(ExperimentalMaterial3Api::class)

package app.duotrainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.UiState
import app.duotrainer.data.SectionAudio
import app.duotrainer.ui.components.AppCard
import app.duotrainer.ui.theme.StatusPalette

/**
 * CD トラックの自動分割を確認・調整する画面。
 * 1 文ずつ聞いて、ずれていたら「先頭で飛ばす数」と「感度」を変えて分割し直す。
 */
@Composable
fun AudioTuneScreen(section: Int, state: UiState, vm: AppViewModel, back: () -> Unit) {
    val deck = state.deck ?: return
    val sentences = deck.sentencesIn(section)
    val conf = state.user.sectionAudio[section.toString()]
        ?.takeIf { it.version == SectionAudio.CURRENT_VERSION } ?: SectionAudio()
    var skip by remember(section) { mutableIntStateOf(conf.skip) }
    var sens by remember(section) { mutableFloatStateOf(conf.sensitivity) }
    var tail by remember(section) { mutableIntStateOf(conf.tailMs) }
    val segs = conf.segments
    val status = StatusPalette.current

    LaunchedEffect(section) { if (segs.isEmpty()) vm.tuneSection(section, skip, sens, tail) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Section $section の音声") },
            navigationIcon = { IconButton(onClick = { vm.player.stop(); back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ok = segs.size == sentences.size
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null,
                                tint = if (ok) status.mastered else status.weak)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (ok) "${segs.size} 文に分けました。聞いてずれがないか確認してください。"
                                else "${segs.size} 個に分かれました（例文は ${sentences.size} 文）。一致しない間は読み上げ（TTS）を使います。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text("先頭で飛ばす数：$skip（最初の「Section $section」の読み上げ）", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { if (skip > 0) skip-- }) { Text("−") }
                            OutlinedButton(onClick = { skip++ }) { Text("＋") }
                        }
                        Text("無音の感度：${"%.2f".format(sens)}", style = MaterialTheme.typography.labelLarge)
                        Slider(value = sens, onValueChange = { sens = it }, valueRange = 0f..1f)
                        Text("文の終わりを延ばす：${"%.1f".format(tail / 1000.0)} 秒（語尾が切れるときは長く）", style = MaterialTheme.typography.labelLarge)
                        Slider(value = tail.toFloat(), onValueChange = { tail = (it / 100).toInt() * 100 }, valueRange = 0f..1500f)
                        Button(onClick = { vm.tuneSection(section, skip, sens, tail) }, modifier = Modifier.fillMaxWidth()) {
                            Text("この設定で分割し直す")
                        }
                    }
                }
            }
            itemsIndexed(sentences) { i, s ->
                val seg = segs.getOrNull(i)
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { seg?.let { vm.playSegment(section, it) } }, enabled = seg != null) {
                            Icon(Icons.Rounded.PlayArrow, "再生")
                        }
                        Column(Modifier.weight(1f)) {
                            Text("No.${s.id}  " + (seg?.let { "%.1f〜%.1f 秒".format(it.startMs / 1000.0, it.endMs / 1000.0) } ?: "—"),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.en, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
