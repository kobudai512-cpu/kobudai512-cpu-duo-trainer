@file:OptIn(ExperimentalLayoutApi::class)

package app.duotrainer.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.duotrainer.AppViewModel
import app.duotrainer.UiState
import app.duotrainer.core.Cloze
import app.duotrainer.core.Confidence
import app.duotrainer.core.Deck
import app.duotrainer.core.Question
import app.duotrainer.core.Rating
import app.duotrainer.core.Sentence
import app.duotrainer.core.StudyMode
import app.duotrainer.core.TextUtil
import app.duotrainer.core.Word
import app.duotrainer.ui.components.AppCard
import app.duotrainer.ui.components.icon
import app.duotrainer.ui.theme.SentenceStyle
import app.duotrainer.ui.theme.StatusPalette

/** 1 問ぶんの回答の状態。 */
private class Answer(q: Question, deck: Deck, choices: List<Word>) {
    var text by mutableStateOf("")
    var revealed by mutableStateOf(false)
    var confidence by mutableStateOf<Confidence?>(null)
    var gaveUp by mutableStateOf(false)
    val cloze: Cloze? = q.sentence?.let { s -> Cloze.build(s, s.wordIds.mapNotNull(deck::word)) }
    val blanks: SnapshotStateList<String> = mutableStateListOf<String>().apply { repeat(cloze?.blanks?.size ?: 0) { add("") } }
    val pieces: List<String> = q.sentence?.let { TextUtil.puzzlePieces(it.en) } ?: emptyList()
    val shuffled: List<Int> = pieces.indices.shuffled().let { if (it == pieces.indices.toList() && pieces.size > 1) it.reversed() else it }
    val picked: SnapshotStateList<Int> = mutableStateListOf()
    val choices: List<Word> = choices
    var choice by mutableStateOf<Int?>(null)
}

@Composable
fun StudyScreen(state: UiState, vm: AppViewModel) {
    val session = state.session ?: return
    val q = session.current ?: return
    val deck = state.deck ?: return
    val settings = state.user.settings
    val planner = remember(session.index, session.questions) { vm.planner() }
    val a = remember(session.index, session.questions) {
        Answer(q, deck, if (q.mode == StudyMode.WORD && q.word != null) planner?.choicesFor(q.word) ?: emptyList() else emptyList())
    }
    var hintClosed by remember(session.questions) { mutableStateOf(false) }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) a.text = if (a.text.isBlank()) said else a.text.trimEnd() + " " + said
    }
    val launchMic = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "英語で話してください")
        try { mic.launch(intent) } catch (_: ActivityNotFoundException) { vm.toast("音声入力アプリが見つかりませんでした") }
    }

    // 聞く系のモードは最初に音声を流す。答え合わせ後も自動再生（設定）。
    LaunchedEffect(session.index, a.revealed) {
        val s = q.sentence
        when {
            !a.revealed && s != null && (q.mode == StudyMode.DICTATION || q.mode == StudyMode.SHADOWING) -> vm.playSentence(s)
            a.revealed && settings.autoPlay && s != null && q.mode != StudyMode.SHADOWING -> vm.playSentence(s)
            a.revealed && settings.autoPlay && q.word != null -> vm.speakText(q.word.en)
            else -> Unit
        }
    }

    val needsConfidence = q.mode != StudyMode.SHADOWING
    // 入力が済んだか（自信度ボタンを押すと、そのまま答え合わせになる）
    val canCheck = when (q.mode) {
        StudyMode.PUZZLE -> a.picked.size == a.pieces.size
        StudyMode.WORD -> a.choice != null
        else -> true
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // 上部バー
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.endSession() }) { Icon(Icons.Rounded.Close, "やめる") }
            LinearProgressIndicator(
                progress = { (session.index + if (a.revealed) 1 else 0).toFloat() / session.questions.size },
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text("${session.index + 1}/${session.questions.size}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(q.mode.label, q.mode.icon)
                Pill("Section ${q.section}" + (q.sentence?.let { " · No.${it.id}" } ?: ""))
                if (q.relearn) Pill("もう一度")
            }

            if (session.mixedSections && settings.interleaveHint && !hintClosed) {
                AppCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "いろいろなセクションの例文を混ぜて出しています。今は難しく感じて大丈夫。そのほうが記憶に残ります。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { hintClosed = true }) {
                            Icon(Icons.Rounded.Close, "閉じる", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }

            when (q.mode) {
                StudyMode.WORD -> WordQuestion(q.word!!, a, deck, vm)
                else -> SentenceQuestion(q, q.sentence!!, a, deck, vm, onMic = launchMic)
            }


            AnimatedVisibility(a.revealed) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Feedback(q, a, deck, vm)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 下部ボタン
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (!a.revealed && needsConfidence) {
                    // 自信度を選んだ瞬間に答えを表示する
                    ConfidenceButtons(
                        enabled = canCheck,
                        hint = when (q.mode) {
                            StudyMode.SELF -> "英訳してから選ぶと、答えが出ます"
                            StudyMode.PUZZLE -> if (canCheck) "選ぶと答え合わせ" else "単語をすべて並べてから選んでください"
                            StudyMode.WORD -> if (canCheck) "選ぶと答え合わせ" else "選択肢を選んでから"
                            else -> "答えを書いてから選ぶと、答え合わせ"
                        },
                    ) { c ->
                        a.confidence = c
                        if (c == Confidence.LOW && q.mode == StudyMode.SELF) a.gaveUp = true
                        a.revealed = true
                    }
                } else if (!a.revealed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { a.gaveUp = true; a.revealed = true }) { Text("わからない") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { a.revealed = true }, modifier = Modifier.height(52.dp)) {
                            Text("答えを見る", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                } else {
                    RatingButtons(q, a.confidence, suggested = suggestedRating(q, a), vm) { r -> vm.rate(q, r, a.confidence) }
                }
            }
        }
    }
}

/** 回答の出来から「おすすめ」の評価を決める（押すのは自分）。 */
private fun suggestedRating(q: Question, a: Answer): Rating {
    if (a.gaveUp) return Rating.WEAK
    if (q.mode == StudyMode.SELF) return when (a.confidence) {
        Confidence.HIGH -> Rating.MASTERED
        Confidence.LOW -> Rating.WEAK
        else -> Rating.GOOD
    }
    val ok = when (q.mode) {
        StudyMode.WORD -> a.choices.getOrNull(a.choice ?: -1)?.id == q.word?.id
        StudyMode.CLOZE -> a.cloze?.blanks?.all { TextUtil.sameWord(it.answer, a.blanks[it.index]) } == true
        StudyMode.PUZZLE -> a.picked.map { a.pieces[it] } == a.pieces
        StudyMode.SHADOWING -> a.text.isBlank() || TextUtil.diff(q.sentence!!.en, a.text).score >= 0.85f
        else -> TextUtil.diff(q.sentence!!.en, a.text).score >= 0.9f
    }
    return if (!ok) Rating.WEAK
    else if (a.confidence == Confidence.HIGH && q.mode.isTranslation) Rating.MASTERED else Rating.GOOD
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun JapaneseCard(ja: String, caption: String = "日本語訳") {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(ja, style = SentenceStyle)
        }
    }
}

@Composable
private fun SentenceQuestion(q: Question, s: Sentence, a: Answer, deck: Deck, vm: AppViewModel, onMic: () -> Unit) {
    when (q.mode) {
        StudyMode.FREE -> {
            JapaneseCard(s.ja)
            AnswerField(a, enabled = !a.revealed, onMic = onMic, placeholder = "英文を書く（マイクでも OK）")
        }
        StudyMode.SELF -> {
            JapaneseCard(s.ja)
            if (!a.revealed) Text(
                "頭の中で（できれば声に出して）英文を最後まで言ってから、答えを見ましょう。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StudyMode.CLOZE -> {
            JapaneseCard(s.ja)
            ClozeView(a)
        }
        StudyMode.PUZZLE -> {
            JapaneseCard(s.ja)
            PuzzleView(a)
        }
        StudyMode.DICTATION -> {
            ListenCard(s, vm)
            AnswerField(a, enabled = !a.revealed, onMic = null, placeholder = "聞こえた英文を書く")
            if (!a.revealed) {
                var showJa by remember(s.id) { mutableStateOf(false) }
                TextButton(onClick = { showJa = !showJa }) { Text(if (showJa) s.ja else "ヒント：日本語訳を見る") }
            }
        }
        StudyMode.SHADOWING -> {
            ListenCard(s, vm)
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(highlightKeywords(s, deck, MaterialTheme.colorScheme.primary), style = SentenceStyle)
                    Spacer(Modifier.height(8.dp))
                    Text(s.ja, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "音声のすぐ後を追いかけるように声に出しましょう。マイクで話すと、言えたかどうかを確認できます。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!a.revealed) FilledTonalButton(onClick = onMic, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Mic, null); Spacer(Modifier.width(8.dp)); Text("話して確認する")
            }
            if (a.text.isNotBlank() && !a.revealed) Text("認識結果：${a.text}", style = MaterialTheme.typography.bodyMedium)
        }
        StudyMode.WORD -> Unit
    }
}

@Composable
private fun ListenCard(s: Sentence, vm: AppViewModel) {
    val playing by vm.player.playing.collectAsState()
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            FilledIconButton(onClick = { vm.playSentence(s) }, modifier = Modifier.size(72.dp)) {
                Icon(if (playing) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.PlayArrow, "再生", Modifier.size(36.dp))
            }
            Spacer(Modifier.width(20.dp))
            FilledTonalButton(onClick = { vm.playSentence(s, speed = 0.75f) }) {
                Icon(Icons.Rounded.SlowMotionVideo, null); Spacer(Modifier.width(6.dp)); Text("ゆっくり")
            }
        }
    }
}

@Composable
private fun AnswerField(a: Answer, enabled: Boolean, onMic: (() -> Unit)?, placeholder: String) {
    OutlinedTextField(
        value = a.text,
        onValueChange = { a.text = it },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        textStyle = SentenceStyle,
        placeholder = { Text(placeholder) },
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        trailingIcon = onMic?.let { m -> { IconButton(onClick = m, enabled = enabled) { Icon(Icons.Rounded.Mic, "音声入力") } } },
    )
}

@Composable
private fun ClozeView(a: Answer) {
    val cloze = a.cloze ?: return
    val status = StatusPalette.current
    AppCard(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cloze.parts.forEach { part ->
                when (part) {
                    is Cloze.Text -> part.text.split(" ").forEachIndexed { i, w ->
                        if (i > 0) Spacer(Modifier.width(6.dp))
                        if (w.isNotEmpty()) Text(w, style = SentenceStyle)
                    }
                    is Cloze.Blank -> {
                        val value = a.blanks[part.index]
                        if (a.revealed) {
                            val ok = TextUtil.sameWord(part.answer, value)
                            Text(
                                if (ok) part.answer else part.answer + (if (value.isNotBlank()) " ($value)" else ""),
                                style = SentenceStyle.copy(fontWeight = FontWeight.Bold),
                                color = if (ok) status.mastered else status.weak,
                            )
                        } else {
                            BasicTextField(
                                value = value,
                                onValueChange = { a.blanks[part.index] = it.trim() },
                                singleLine = true,
                                textStyle = SentenceStyle.copy(color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .width((part.answer.length * 12 + 28).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (!a.revealed) Text(
        "空欄のヒント：頭文字 " + cloze.blanks.joinToString("  ") { it.answer.first() + "…" },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PuzzleView(a: Answer) {
    val status = StatusPalette.current
    // 並べた単語の位置（ルート座標）。ドラッグを離した場所から入れる位置を決める。
    val bounds = remember(a) { mutableStateMapOf<Int, Rect>() }
    var dragFrom by remember(a) { mutableStateOf<Int?>(null) }
    var dragOffset by remember(a) { mutableStateOf(Offset.Zero) }

    /** 指の位置にいちばん近い単語（そこに入る）。 */
    fun targetIndex(): Int? {
        val from = dragFrom ?: return null
        val start = bounds[from] ?: return null
        val p = start.center + dragOffset
        val live = bounds.filterKeys { it < a.picked.size }
        return live.entries.firstOrNull { it.value.contains(p) }?.key
            ?: live.entries.minByOrNull { (it.value.center - p).getDistance() }?.key
    }
    val hover = if (dragFrom != null) targetIndex() else null

    fun drop() {
        val from = dragFrom
        val target = targetIndex()
        if (from != null && target != null && target != from && from < a.picked.size) {
            val item = a.picked.removeAt(from)
            a.picked.add(target.coerceIn(0, a.picked.size), item)
        }
        dragFrom = null
        dragOffset = Offset.Zero
    }

    AppCard(Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.padding(16.dp).heightIn(min = 64.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 位置ごとの枠に、その位置の単語を表示する（並べ替えたら表示もそのまま追従する）
            a.picked.forEachIndexed { pos, idx ->
                run {
                    val currentPos by rememberUpdatedState(pos)
                    val dragging = dragFrom == pos
                    val color = when {
                        !a.revealed && hover == pos && !dragging -> MaterialTheme.colorScheme.tertiary  // ここに入る
                        !a.revealed -> MaterialTheme.colorScheme.primary
                        a.pieces[pos] == a.pieces[idx] -> status.mastered
                        else -> status.weak
                    }
                    PieceChip(
                        a.pieces[idx], filled = true, color = color,
                        // ドラッグの検出は「動く層」より外側に置く（内側だと指と一緒に座標が動いて、移動量が 0 になる）
                        modifier = Modifier
                            .onGloballyPositioned { bounds[currentPos] = it.boundsInRoot() }
                            .zIndex(if (dragging) 1f else 0f)
                            .pointerInput(a.revealed) {
                                if (a.revealed) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { dragFrom = currentPos; dragOffset = Offset.Zero },
                                    onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                    onDragEnd = { drop() },
                                    onDragCancel = { dragFrom = null; dragOffset = Offset.Zero },
                                )
                            }
                            .graphicsLayer {
                                if (dragging) {
                                    translationX = dragOffset.x; translationY = dragOffset.y
                                    scaleX = 1.08f; scaleY = 1.08f; shadowElevation = 12f
                                }
                            },
                    ) { if (!a.revealed) a.picked.removeAt(currentPos) }
                }
            }
        }
    }
    if (!a.revealed) {
        Text(
            "下の単語をタップして並べる。並べた単語はドラッグで順番を入れ替え、タップで下に戻せます。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            a.shuffled.filter { it !in a.picked }.forEach { idx ->
                PieceChip(a.pieces[idx], filled = false, color = MaterialTheme.colorScheme.primary) { a.picked.add(idx) }
            }
        }
    }
}

@Composable
private fun PieceChip(text: String, filled: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .then(if (filled) Modifier.background(color.copy(alpha = 0.12f)) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest))
            .border(BorderStroke(1.5.dp, if (filled) color else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = if (filled) color else MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun WordQuestion(w: Word, a: Answer, deck: Deck, vm: AppViewModel) {
    val status = StatusPalette.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            w.partOfSpeech?.let { Pill(it) ; Spacer(Modifier.height(8.dp)) }
            Text(w.meaning, style = MaterialTheme.typography.headlineSmall)
        }
    }
    a.choices.forEachIndexed { i, c ->
        val correct = c.id == w.id
        val border = when {
            !a.revealed && a.choice == i -> MaterialTheme.colorScheme.primary
            a.revealed && correct -> status.mastered
            a.revealed && a.choice == i -> status.weak
            else -> MaterialTheme.colorScheme.outlineVariant
        }
        Box(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(BorderStroke(2.dp, border), MaterialTheme.shapes.medium)
                .clickable(enabled = !a.revealed) { a.choice = i }
                .padding(16.dp),
        ) {
            Column {
                Text(c.en, style = MaterialTheme.typography.titleMedium)
                if (a.revealed) Text(c.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (a.revealed) {
        w.sentenceId?.let(deck::sentence)?.let { s ->
            AppCard(Modifier.fillMaxWidth(), onClick = { vm.playSentence(s) }) {
                Column(Modifier.padding(16.dp)) {
                    Text("この単語が出てくる例文 No.${s.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(highlightWords(s.en, listOf(w), MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodyLarge)
                    Text(s.ja, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ConfidenceButtons(enabled: Boolean, hint: String, onSelect: (Confidence) -> Unit) {
    val status = StatusPalette.current
    Column {
        Text("自信は？  $hint", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Confidence.entries.forEach { c ->
                val color = when (c) {
                    Confidence.LOW -> status.weak
                    Confidence.MID -> status.good
                    Confidence.HIGH -> status.mastered
                }
                Button(
                    onClick = { onSelect(c) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.14f), contentColor = color),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(c.label, style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}

@Composable
private fun Feedback(q: Question, a: Answer, deck: Deck, vm: AppViewModel) {
    val s = q.sentence ?: return
    val status = StatusPalette.current
    val typed = when (q.mode) {
        StudyMode.FREE, StudyMode.DICTATION -> a.text
        StudyMode.SHADOWING -> a.text
        else -> null
    }
    if (!typed.isNullOrBlank()) {
        val d = remember(typed, s.en) { TextUtil.diff(s.en, typed) }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("あなたの答え", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    val pct = (d.score * 100).toInt()
                    Text("一致度 $pct%", style = MaterialTheme.typography.titleSmall,
                        color = if (pct >= 90) status.mastered else if (pct >= 60) status.good else status.weak)
                }
                Spacer(Modifier.height(6.dp))
                Text(diffText(d, status.weak, status.streak), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("赤の取り消し線＝余分な語、オレンジの下線＝抜けている語", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    AppCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("正解", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.playSentence(s) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "再生", tint = MaterialTheme.colorScheme.primary) }
            }
            Text(highlightKeywords(s, deck, MaterialTheme.colorScheme.primary), style = SentenceStyle)
            Spacer(Modifier.height(6.dp))
            Text(s.ja, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val words = s.wordIds.mapNotNull(deck::word)
    if (words.isNotEmpty()) {
        Text("この例文の重要語", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            words.forEach { w ->
                Column(
                    Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { vm.speakText(w.en) }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(w.en, style = MaterialTheme.typography.titleSmall)
                    Text(w.ja, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RatingButtons(q: Question, confidence: Confidence?, suggested: Rating, vm: AppViewModel, onRate: (Rating) -> Unit) {
    val status = StatusPalette.current
    // 「簡単」は和文英訳だけ。練習モードは 苦手 / 正解 の 2 つ。
    val ratings = if (q.mode.isTranslation) Rating.entries else listOf(Rating.WEAK, Rating.GOOD)
    Column {
    if (!q.mode.isTranslation) Text(
        "練習モードの結果は例文の仕分けに入りません（仕分けは和文英訳だけ）",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ratings.forEach { r ->
            val color = when (r) { Rating.WEAK -> status.weak; Rating.GOOD -> status.good; Rating.MASTERED -> status.mastered }
            val isSuggested = r == suggested
            Button(
                onClick = { onRate(r) },
                modifier = Modifier.weight(1f).height(64.dp),
                colors = if (isSuggested) ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
                else ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.14f), contentColor = color),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(r.label, style = MaterialTheme.typography.titleMedium)
                    Text(vm.preview(q, r, confidence), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    }
}

// ---------- テキスト装飾 ----------

private fun highlightKeywords(s: Sentence, deck: Deck, color: Color): AnnotatedString =
    highlightWords(s.en, s.wordIds.mapNotNull(deck::word), color)

private fun highlightWords(text: String, words: List<Word>, color: Color): AnnotatedString {
    val tokens = TextUtil.tokens(text)
    val marks = words.flatMap { TextUtil.findPhrase(it.en, tokens).orEmpty() }.toSet()
    return buildAnnotatedString {
        var pos = 0
        tokens.forEachIndexed { i, t ->
            if (i in marks) {
                append(text.substring(pos, t.start))
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(t.text) }
                pos = t.end
            }
        }
        append(text.substring(pos))
    }
}

private fun diffText(d: TextUtil.DiffResult, extra: Color, missing: Color): AnnotatedString = buildAnnotatedString {
    d.parts.forEachIndexed { i, p ->
        if (i > 0) append(" ")
        when (p.kind) {
            TextUtil.DiffKind.MATCH -> append(p.text)
            TextUtil.DiffKind.EXTRA -> withStyle(SpanStyle(color = extra, textDecoration = TextDecoration.LineThrough)) { append(p.text) }
            TextUtil.DiffKind.MISSING -> withStyle(
                SpanStyle(color = missing, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
            ) { append(p.text) }
        }
    }
}

