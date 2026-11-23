@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.duotrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import app.duotrainer.ui.components.Dot
import app.duotrainer.ui.components.NumberBadge
import app.duotrainer.ui.components.color
import app.duotrainer.ui.components.icon

/** 教材タブ：45 セクションの一覧。 */
@Composable
fun SectionsScreen(state: UiState, vm: AppViewModel, open: (Route) -> Unit) {
    val deck = state.deck ?: return
    val progress = state.user.progress
    val all = deck.sentences.groupingBy { progress[Keys.sentence(it.id)].category() }.eachCount()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            // 本の表紙のようなヘッダー
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(width = 72.dp, height = 96.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF3355E8), Color(0xFF7C5CFF)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DUO", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("3.0", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DUO 3.0", style = MaterialTheme.typography.titleLarge)
                    Text("例文 ${deck.sentences.size} ・ 見出し語 ${deck.words.size}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CategoryBar(all, height = 8.dp)
                    Text("覚えた ${all[Category.MASTERED] ?: 0} 文", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(deck.sections) { sec ->
            val ss = deck.sentencesIn(sec)
            val counts = ss.groupingBy { progress[Keys.sentence(it.id)].category() }.eachCount()
            AppCard(Modifier.fillMaxWidth(), onClick = { open(Route.Section(sec)) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberBadge(sec)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Section $sec", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("例文 ${ss.size} ・ 単語 ${deck.wordsIn(sec).size}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CategoryBar(counts, height = 6.dp)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** セクションの詳細：学習の開始・例文と単語の一覧。 */
@Composable
fun SectionDetailScreen(section: Int, state: UiState, vm: AppViewModel, open: (Route) -> Unit, back: () -> Unit) {
    val deck = state.deck ?: return
    val progress = state.user.progress
    val sentences = deck.sentencesIn(section)
    val words = deck.wordsIn(section)
    var tab by remember { mutableIntStateOf(0) }
    val counts = sentences.groupingBy { progress[Keys.sentence(it.id)].category() }.eachCount()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Section $section") },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } },
            actions = {
                if (vm.hasTrack(section)) IconButton(onClick = { open(Route.AudioTune(section)) }) {
                    Icon(Icons.Rounded.GraphicEq, "音声の分割を調整")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryBar(counts)
                        CategoryLegend(counts)
                        Text("このセクションで学習", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StudyMode.entries.forEach { m ->
                                AssistChip(
                                    onClick = { vm.startSession(Scope.Section(section), m) },
                                    label = { Text(m.label) },
                                    leadingIcon = { Icon(m.icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("例文 ${sentences.size}") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("単語 ${words.size}") })
                }
            }
            if (tab == 0) items(sentences, key = { "s${it.id}" }) { s ->
                var openJa by remember { mutableStateOf(false) }
                val cat = progress[Keys.sentence(s.id)].category()
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.clickable { openJa = !openJa }.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${s.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Dot(cat.color())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.en, style = MaterialTheme.typography.bodyLarge)
                            if (openJa) {
                                Spacer(Modifier.height(4.dp))
                                Text(s.ja, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { vm.playSentence(s) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "再生") }
                    }
                }
            } else items(words, key = { "w${it.id}" }) { w ->
                val cat = progress[Keys.word(w.id)].category()
                AppCard(Modifier.fillMaxWidth(), onClick = { vm.speakText(w.en) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(cat.color())
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w.en, style = MaterialTheme.typography.titleSmall)
                            Text(w.ja, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        w.sentenceId?.let { Text("No.$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
