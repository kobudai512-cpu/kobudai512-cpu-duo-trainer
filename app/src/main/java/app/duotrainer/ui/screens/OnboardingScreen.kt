package app.duotrainer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.UiState
import app.duotrainer.ui.components.AppCard

/** はじめての起動：DUO のテキストと音声フォルダを選んでもらう。 */
@Composable
fun OnboardingScreen(state: UiState, vm: AppViewModel) {
    val pickText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importDeck)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::setAudioFolder)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3355E8), Color(0xFF6A5CFF))))
                .padding(24.dp),
        ) {
            Column {
                Text("DUO Trainer", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "DUO 3.0 の例文を「思い出す練習」と「間隔を空けた復習」で覚えるアプリです。",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        Text(
            "教材データはアプリに入っていません。手元のファイルを読み込んでください（端末の中だけで使われます）。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StepCard(
            step = 1, icon = Icons.Rounded.Description,
            title = "例文データ（.txt）を選ぶ",
            body = "Anki から書き出した DUO 3.0 のテキスト（タブ区切り）",
            done = state.deck != null, doneText = null,
        ) { Button(onClick = { pickText.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("ファイルを選ぶ") } }
        StepCard(
            step = 2, icon = Icons.Rounded.Folder,
            title = "音声フォルダを選ぶ（あとからでも OK）",
            body = "「01 トラック 1.mp3」〜「45 トラック 45.mp3」が入ったフォルダ。選ばない場合は端末の読み上げを使います。",
            done = state.user.tracks.isNotEmpty(),
            doneText = "${state.user.tracks.size} トラック",
        ) { FilledTonalButton(onClick = { pickFolder.launch(null) }) { Text("フォルダを選ぶ") } }
    }
}

@Composable
private fun StepCard(
    step: Int, icon: ImageVector, title: String, body: String, done: Boolean, doneText: String?,
    action: @Composable () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("STEP $step", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF22A06B))
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                action()
                if (done && doneText != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(doneText, style = MaterialTheme.typography.labelLarge, color = Color(0xFF22A06B))
                }
            }
        }
    }
}
