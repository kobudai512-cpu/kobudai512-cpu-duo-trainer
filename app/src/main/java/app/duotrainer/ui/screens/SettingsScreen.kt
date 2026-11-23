package app.duotrainer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.UiState
import app.duotrainer.data.AudioSource
import app.duotrainer.ui.components.AppCard
import app.duotrainer.ui.components.SectionTitle
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel) {
    val s = state.user.settings
    val deck = state.deck
    var confirmReset by remember { mutableStateOf(false) }
    val pickText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importDeck) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setAudioFolder) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBackup) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("設定", style = MaterialTheme.typography.headlineSmall)

        SectionTitle("学習")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Stepper("1 日の新しい例文", s.newPerDay, 0..50, step = 5) { v -> vm.updateSettings { it.copy(newPerDay = v) } }
                HorizontalDivider()
                Stepper("1 回の問題数", s.sessionSize, 5..60, step = 5) { v -> vm.updateSettings { it.copy(sessionSize = v) } }
                HorizontalDivider()
                Stepper("1 日の目標（問）", s.dailyGoal, 5..200, step = 5) { v -> vm.updateSettings { it.copy(dailyGoal = v) } }
                HorizontalDivider()
                Toggle("インターリービングのひと言を表示", "「今は難しく感じて大丈夫」", s.interleaveHint) { v -> vm.updateSettings { it.copy(interleaveHint = v) } }
            }
        }

        SectionTitle("音声")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                AudioSource.entries.forEach { src ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(audioSource = src) } }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.audioSource == src, onClick = { vm.updateSettings { it.copy(audioSource = src) } })
                        Text(src.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("再生速度 ×${"%.2f".format(s.speed)}", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = s.speed,
                        onValueChange = { v -> vm.updateSettings { it.copy(speed = (v * 20).roundToInt() / 20f) } },
                        valueRange = 0.5f..1.5f,
                    )
                }
                HorizontalDivider()
                Toggle("答え合わせのあとに自動で再生", null, s.autoPlay) { v -> vm.updateSettings { it.copy(autoPlay = v) } }
                HorizontalDivider()
                LinkRow(Icons.Rounded.Folder, "音声フォルダ",
                    if (state.user.tracks.isEmpty()) "未設定" else "${state.user.tracks.size} トラック") { pickFolder.launch(null) }
            }
        }

        SectionTitle("データ")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                LinkRow(Icons.Rounded.Description, "例文データを読み込み直す",
                    deck?.let { "${it.sentences.size} 文・${it.words.size} 語" } ?: "未読み込み") {
                    pickText.launch(arrayOf("text/*", "application/octet-stream"))
                }
                HorizontalDivider()
                LinkRow(Icons.Rounded.Upload, "学習記録を書き出す", "機種変更のときに") {
                    exportLauncher.launch("duo-trainer-backup-${LocalDate.now()}.json")
                }
                HorizontalDivider()
                LinkRow(Icons.Rounded.Download, "学習記録を読み込む", null) { importLauncher.launch(arrayOf("application/json", "*/*")) }
                HorizontalDivider()
                LinkRow(Icons.Rounded.RestartAlt, "学習記録をリセット", null) { confirmReset = true }
            }
        }
        Text(
            "DUO 3.0 の例文・音声は著作権で保護されています。このアプリは自分用で、教材データは端末の中だけで使われます。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("学習記録をリセットしますか？") },
        text = { Text("仕分け・復習予定・学習時間がすべて消えます。元に戻せません。") },
        confirmButton = { TextButton(onClick = { vm.resetProgress(); confirmReset = false }) { Text("リセット") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("やめる") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceIn(range)) }) { Text("−") }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = { onChange((value + step).coerceIn(range)) }) { Text("＋") }
    }
}

@Composable
private fun Toggle(label: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
