@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.duotrainer.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.UiState
import app.duotrainer.listen.ListenController
import app.duotrainer.listen.ListenPattern
import app.duotrainer.ui.components.AppCard
import app.duotrainer.ui.theme.SentenceStyle
import app.duotrainer.ui.theme.StatusPalette

/** 聞き流し：英→日→英 などを続けて流す。画面を消しても続く。 */
@Composable
fun ListenScreen(state: UiState, vm: AppViewModel, back: () -> Unit) {
    val st by ListenController.state.collectAsState()
    var hideEn by remember { mutableStateOf(false) }
    var hideJa by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    // Android 13 以降は通知の許可がないと、画面を消したときの操作パネルが出ない
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS) }
    LaunchedEffect(st.index) { if (st.items.isNotEmpty()) list.animateScrollToItem(st.index) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (st.active) "聞き流し：${st.title}" else "聞き流し", maxLines = 1) },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (!st.active) {
            Text(
                "聞き流しは止まっています。ホーム・教材・ブックマークの「聞き流し」から始めてください。",
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge,
            )
            return
        }
        val item = st.current
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("No.${item?.sentenceId}  ·  Section ${item?.section}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (st.phase.isNotEmpty()) Text(st.phase, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        if (item != null) {
                            val on = state.user.bookmarks.contains(item.sentenceId)
                            IconButton(onClick = { vm.toggleBookmark(item.sentenceId) }) {
                                Icon(if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder, "ブックマーク",
                                    tint = if (on) StatusPalette.current.streak else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(if (hideEn) "（英文は非表示）" else item?.en.orEmpty(), style = SentenceStyle,
                        modifier = Modifier.clickable { hideEn = !hideEn })
                    Spacer(Modifier.height(8.dp))
                    Text(if (hideJa) "（日本語は非表示）" else item?.ja.orEmpty(), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { hideJa = !hideJa })
                }
            }
            LinearProgressIndicator(
                progress = { (st.index + 1f) / st.items.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            // 操作ボタン
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { ListenController.stop(); back() }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Stop, "停止") }
                FilledTonalIconButton(onClick = { ListenController.prev() }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipPrevious, "前へ") }
                FilledIconButton(onClick = { ListenController.toggle() }, modifier = Modifier.size(72.dp)) {
                    Icon(if (st.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (st.playing) "一時停止" else "再生", Modifier.size(36.dp))
                }
                FilledTonalIconButton(onClick = { ListenController.next() }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipNext, "次へ") }
                Text("${st.index + 1}/${st.items.size}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
            // 流し方
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ListenPattern.entries.forEach { p ->
                    FilterChip(selected = st.pattern == p, onClick = { ListenController.setPattern(p) }, label = { Text(p.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("くり返し", style = MaterialTheme.typography.labelLarge)
                listOf(1, 2, 3).forEach { n ->
                    FilterChip(selected = st.repeat == n, onClick = { ListenController.setRepeat(n) }, label = { Text("${n}回") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最後まで行ったら最初から", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = st.loop, onCheckedChange = { ListenController.setLoop(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("間 ${"%.1f".format(st.gapSec)}秒", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(76.dp))
                Slider(value = st.gapSec, onValueChange = { ListenController.setGap((it * 2).toInt() / 2f) }, valueRange = 0.5f..5f)
            }
            Text("英文・日本語はタップで隠せます。画面を消しても再生は続き、通知から操作できます。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(st.items) { i, it ->
                val now = i == st.index
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(if (now) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { ListenController.jumpTo(i) }.padding(12.dp),
                ) {
                    Text("${it.sentenceId}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(it.en, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }
    }
}
