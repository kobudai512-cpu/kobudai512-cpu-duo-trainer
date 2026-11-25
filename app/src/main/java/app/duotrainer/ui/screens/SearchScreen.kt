@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.duotrainer.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.Route
import app.duotrainer.UiState
import app.duotrainer.core.Keys
import app.duotrainer.core.Scope
import app.duotrainer.core.Sentence
import app.duotrainer.core.StudyMode
import app.duotrainer.core.category
import app.duotrainer.ui.components.AppCard
import app.duotrainer.ui.components.Dot
import app.duotrainer.ui.components.color
import app.duotrainer.ui.components.icon
import app.duotrainer.ui.theme.StatusPalette

/** 例文 1 件（★・再生・タップで日本語）。検索・ブックマーク共通。 */
@Composable
fun SentenceRow(
    s: Sentence,
    state: UiState,
    vm: AppViewModel,
    open: ((Route) -> Unit)? = null,
    highlight: String = "",
) {
    var expanded by remember(s.id) { mutableStateOf(false) }
    val on = s.id in state.user.bookmarks
    val cat = state.user.progress[Keys.sentence(s.id)].category()
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable { expanded = !expanded }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Dot(cat.color())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(s.en, style = MaterialTheme.typography.bodyLarge)
                if (expanded || (highlight.isNotEmpty() && s.ja.contains(highlight))) {
                    Spacer(Modifier.height(4.dp))
                    Text(s.ja, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expanded && open != null) TextButton(onClick = { open(Route.Section(s.section)) }) { Text("Section ${s.section} を開く") }
            }
            Column {
                IconButton(onClick = { vm.toggleBookmark(s.id) }) {
                    Icon(if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder, "ブックマーク",
                        tint = if (on) StatusPalette.current.streak else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.playSentence(s) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "再生") }
            }
        }
    }
}

/** 例文・単語の検索（英語でも日本語でも）。 */
@Composable
fun SearchScreen(state: UiState, vm: AppViewModel, open: (Route) -> Unit, back: () -> Unit) {
    val deck = state.deck ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var starredOnly by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = query.trim()
    val sentences = remember(q, starredOnly, deck, state.user.bookmarks) {
        if (q.isEmpty() && !starredOnly) emptyList()
        else deck.sentences.filter { s ->
            (!starredOnly || s.id in state.user.bookmarks) &&
                (q.isEmpty() || s.en.contains(q, ignoreCase = true) || s.ja.contains(q))
        }.take(200)
    }
    val words = remember(q, deck) {
        if (q.isEmpty()) emptyList()
        else deck.words.filter { it.en.contains(q, ignoreCase = true) || it.ja.contains(q) }.take(100)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("検索") },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            placeholder = { Text("英語・日本語で例文と単語を探す") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "消す") } },
            singleLine = true, shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = starredOnly, onClick = { starredOnly = !starredOnly }, label = { Text("★だけ") },
                leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) })
        }
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (q.isEmpty() && !starredOnly) item {
                Text("例：look forward、楽しみ、afraid のように、英語でも日本語でも探せます。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp))
            }
            if (sentences.isNotEmpty()) item {
                Text("例文 ${sentences.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            }
            items(sentences, key = { "s${it.id}" }) { s -> SentenceRow(s, state, vm, open, highlight = q) }
            if (words.isNotEmpty()) item {
                Text("単語 ${words.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            }
            items(words, key = { "w${it.id}" }) { w ->
                val s = w.sentenceId?.let(deck::sentence)
                AppCard(Modifier.fillMaxWidth(), onClick = { vm.speakText(w.en) }) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w.en, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("Section ${w.section}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(w.ja, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s != null) Text("No.${s.id}  ${s.en}", style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (q.isNotEmpty() && sentences.isEmpty() && words.isEmpty()) item {
                Text("見つかりませんでした", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** ★ブックマークした例文の一覧と、その例文だけでの学習・聞き流し。 */
@Composable
fun BookmarksScreen(state: UiState, vm: AppViewModel, open: (Route) -> Unit, back: () -> Unit) {
    val deck = state.deck ?: return
    val list = state.user.bookmarks.mapNotNull(deck::sentence).sortedBy { it.id }
    val ids = list.map { it.id }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("★ブックマーク ${list.size}") },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } },
            actions = { IconButton(onClick = { open(Route.Search) }) { Icon(Icons.Rounded.Search, "検索") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (list.isEmpty()) {
                    Text("学習中・教材・検索・聞き流しの画面で ☆ を押すと、ここに集まります。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp))
                } else AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.startListening("★ブックマーク", list) { open(Route.Listen) } }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Headphones, null); Spacer(Modifier.width(8.dp)); Text("★の例文を聞き流す")
                        }
                        Text("★の例文だけで学習", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StudyMode.entries.forEach { m ->
                                AssistChip(
                                    onClick = { vm.startSession(Scope.Custom("★ブックマーク", ids), m) },
                                    label = { Text(m.label) },
                                    leadingIcon = { Icon(m.icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
                                )
                            }
                        }
                    }
                }
            }
            items(list, key = { it.id }) { s -> SentenceRow(s, state, vm, open) }
        }
    }
}
