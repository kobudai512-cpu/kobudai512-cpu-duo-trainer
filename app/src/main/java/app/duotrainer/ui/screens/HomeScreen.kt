package app.duotrainer.ui.screens

import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.duotrainer.listen.ListenController
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.Route
import app.duotrainer.UiState
import app.duotrainer.core.Category
import app.duotrainer.core.Keys
import app.duotrainer.core.Scope
import app.duotrainer.core.StudyMode
import app.duotrainer.core.category
import app.duotrainer.ui.components.AppCard
import app.duotrainer.ui.components.CategoryBar
import app.duotrainer.ui.components.CategoryLegend
import app.duotrainer.ui.components.ModeTile
import app.duotrainer.ui.components.NumberBadge
import app.duotrainer.ui.components.ProgressRing
import app.duotrainer.ui.components.SectionTitle
import app.duotrainer.ui.theme.StatusPalette
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(state: UiState, vm: AppViewModel, open: (Route) -> Unit) {
    val deck = state.deck ?: return
    val user = state.user
    val planner = vm.planner() ?: return
    val (due, fresh) = planner.todayRemaining(user.settings.newPerDay)
    val todayLog = user.days[vm.today.toString()]
    val answeredToday = todayLog?.answered ?: 0
    val streak = vm.streak()
    val weakCount = planner.weakSentences().size
    val counts = deck.sentences.groupingBy { user.progress[Keys.sentence(it.id)].category() }.eachCount()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::setAudioFolder)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ヘッダー：あいさつと連続学習日数
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val d = LocalDate.now()
                Text("${d.monthValue}月${d.dayOfMonth}日", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(greeting(), style = MaterialTheme.typography.headlineSmall)
            }
            IconButton(onClick = { open(Route.Search) }) { Icon(Icons.Rounded.Search, "検索") }
            StreakChip(streak)
        }

        // 今日の復習（いちばん大きいカード）
        TodayCard(
            due = due, fresh = fresh, answered = answeredToday, goal = user.settings.dailyGoal,
            onStart = { mode ->
                if (due + fresh > 0) vm.startSession(Scope.Today, mode)
                else vm.startSession(Scope.Section(user.lastSection), mode)
            },
        )

        if (user.tracks.isEmpty()) {
            AppCard(Modifier.fillMaxWidth(), onClick = { pickFolder.launch(null) }, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("音声フォルダを設定", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("CD 音声を使うと、ディクテーションとシャドーイングが本物の音声になります",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        // 聞き流し中なら、すぐ戻れるように
        val listen by ListenController.state.collectAsState()
        if (listen.active) {
            AppCard(Modifier.fillMaxWidth(), onClick = { open(Route.Listen) }, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (listen.playing) "聞き流し中：${listen.title}" else "聞き流し（一時停止中）：${listen.title}",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(listen.current?.en.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { ListenController.toggle() }) {
                        Icon(if (listen.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // 苦手な例文をランダムに
        AppCard(Modifier.fillMaxWidth(), onClick = { vm.startSession(Scope.Weak, user.translationMode) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(StatusPalette.current.weak.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Shuffle, null, tint = StatusPalette.current.weak) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("苦手な例文をランダムに", style = MaterialTheme.typography.titleSmall)
                    Text("${user.translationMode.label}で。苦手 $weakCount 文", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
        }

        // 聞き流しと★ブックマーク
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("聞き流し", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        vm.startListening("Section ${user.lastSection}", deck.sentencesIn(user.lastSection)) { open(Route.Listen) }
                    }) { Text("Section ${user.lastSection}") }
                    TextButton(onClick = {
                        vm.startListening("苦手な例文", planner.weakSentences().shuffled()) { open(Route.Listen) }
                    }) { Text("苦手な例文 $weakCount") }
                }
            }
            AppCard(Modifier.weight(1f), onClick = { open(Route.Bookmarks) }) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, null, tint = StatusPalette.current.streak)
                        Spacer(Modifier.width(8.dp))
                        Text("ブックマーク", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${user.bookmarks.size}", style = MaterialTheme.typography.headlineMedium)
                    Text("文。★だけで学習・聞き流し", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 学習モード
        SectionTitle("学習モード")
        val modes = StudyMode.entries
        modes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { m ->
                    ModeTile(m, Modifier.weight(1f)) {
                        vm.startSession(if (m.isTranslation) Scope.Today else Scope.Section(user.lastSection), m)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "和文英訳は「今日の復習」から、そのほかは前回のセクション（Section ${user.lastSection}）から練習として出題します。例文の仕分け（苦手・正解・簡単）に入るのは和文英訳だけです。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 続きから
        SectionTitle("続きから")
        val sec = user.lastSection
        val inSec = deck.sentencesIn(sec)
        val mastered = inSec.count { user.progress[Keys.sentence(it.id)].category() == Category.MASTERED }
        AppCard(Modifier.fillMaxWidth(), onClick = { open(Route.Section(sec)) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberBadge(sec)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Section $sec", style = MaterialTheme.typography.titleSmall)
                    Text(inSec.firstOrNull()?.en.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text("$mastered/${inSec.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        // 全体の進み具合
        SectionTitle("DUO 3.0 全体")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${counts[Category.MASTERED] ?: 0}", style = MaterialTheme.typography.headlineMedium)
                    Text(" / ${deck.sentences.size} 文が「簡単」", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                CategoryBar(counts)
                CategoryLegend(counts)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when (h) {
        in 4..10 -> "おはようございます"
        in 11..17 -> "こんにちは"
        else -> "こんばんは"
    }
}

@Composable
private fun StreakChip(days: Int) {
    val c = StatusPalette.current.streak
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, null, tint = c, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text("$days", style = MaterialTheme.typography.titleMedium, color = c, fontWeight = FontWeight.Bold)
        Text(" 日連続", style = MaterialTheme.typography.labelMedium, color = c)
    }
}

@Composable
private fun TodayCard(due: Int, fresh: Int, answered: Int, goal: Int, onStart: (StudyMode) -> Unit) {
    val total = due + fresh
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3355E8), Color(0xFF5B6CFF), Color(0xFF7C5CFF))))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今日の復習", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("あと ", style = MaterialTheme.typography.titleMedium, color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp))
                        Text("$total", style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(" 文", style = MaterialTheme.typography.titleMedium, color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text("復習 $due ・ 新しい例文 $fresh", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
                ProgressRing(
                    progress = answered.toFloat() / goal.coerceAtLeast(1),
                    size = 88.dp, stroke = 9.dp, color = Color.White, track = Color.White.copy(alpha = 0.25f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$answered", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("/ $goal 問", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (total == 0) Text("今日の分は完了！ 続けるなら前回のセクションから", style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 8.dp))
            // 和文英訳の 2 つのやり方
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onStart(StudyMode.SELF) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3355E8)),
                ) {
                    Icon(Icons.Rounded.Psychology, null)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("入力なし", style = MaterialTheme.typography.titleSmall)
                        Text("頭の中で英訳", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = { onStart(StudyMode.FREE) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("入力あり", style = MaterialTheme.typography.titleSmall)
                        Text("書いて答え合わせ", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

