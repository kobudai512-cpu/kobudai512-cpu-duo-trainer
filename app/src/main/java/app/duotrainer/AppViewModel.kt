package app.duotrainer

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.duotrainer.audio.AudioLibrary
import app.duotrainer.audio.Player
import app.duotrainer.core.Category
import app.duotrainer.core.Confidence
import app.duotrainer.core.DayLog
import app.duotrainer.core.Deck
import app.duotrainer.core.Keys
import app.duotrainer.core.Planner
import app.duotrainer.core.Question
import app.duotrainer.core.Rating
import app.duotrainer.core.Scope
import app.duotrainer.core.Segment
import app.duotrainer.core.Segmenter
import app.duotrainer.core.Sentence
import app.duotrainer.core.Srs
import app.duotrainer.core.Stats
import app.duotrainer.core.StudyMode
import app.duotrainer.core.category
import app.duotrainer.data.AudioSource
import app.duotrainer.data.SectionAudio
import app.duotrainer.data.Settings
import app.duotrainer.data.Store
import app.duotrainer.data.UserData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 学習セッション（1 回分の出題）。 */
data class Session(
    val title: String,
    val questions: List<Question>,
    val index: Int = 0,
    val results: List<Pair<Question, Rating>> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val lastAnswerAt: Long = System.currentTimeMillis(),
    val scope: Scope,
    val mode: StudyMode,
) {
    val current: Question? get() = questions.getOrNull(index)
    val finished: Boolean get() = index >= questions.size
    val mixedSections: Boolean get() = questions.map { it.section }.distinct().size > 1
}

data class UiState(
    val loading: Boolean = true,
    val deck: Deck? = null,
    val user: UserData = UserData(),
    val session: Session? = null,
    val message: String? = null,
    val busy: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val library = AudioLibrary(app)
    val player = Player(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val today: Long get() = LocalDate.now().toEpochDay()

    private val loadJob = viewModelScope.launch {
        val deck = store.loadDeck()
        val user = store.loadUser()
        _state.value = UiState(loading = false, deck = deck, user = user)
    }

    /** スクリーンショットテスト用：状態を直接差し替える（保存はしない）。 */
    @androidx.annotation.VisibleForTesting
    fun replaceStateForTest(s: UiState) {
        loadJob.cancel()
        _state.value = s
    }

    private fun updateUser(save: Boolean = true, f: (UserData) -> UserData) {
        _state.update { it.copy(user = f(it.user)) }
        if (save) viewModelScope.launch { store.saveUser(_state.value.user) }
    }

    fun toast(msg: String?) = _state.update { it.copy(message = msg) }

    // ---------- 読み込み ----------

    fun importDeck(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = "例文データを解析しています…") }
        runCatching { store.importDeck(uri) }
            .onSuccess { deck ->
                _state.update {
                    it.copy(deck = deck, busy = null,
                        message = "${deck.sentences.size} 文・${deck.words.size} 語を読み込みました")
                }
            }
            .onFailure { e -> _state.update { it.copy(busy = null, message = e.message ?: "読み込みに失敗しました") } }
    }

    fun setAudioFolder(tree: Uri) = viewModelScope.launch {
        val cr = getApplication<Application>().contentResolver
        runCatching { cr.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        _state.update { it.copy(busy = "音声フォルダを確認しています…") }
        val tracks = library.scan(tree)
        updateUser { u ->
            u.copy(audioTreeUri = tree.toString(),
                tracks = tracks.map { (k, v) -> k.toString() to v.toString() }.toMap(),
                sectionAudio = emptyMap())
        }
        _state.update {
            it.copy(busy = null, message = if (tracks.isEmpty()) "音声ファイルが見つかりませんでした"
            else "${tracks.size} トラックの音声を見つけました")
        }
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        runCatching { store.exportBackup(uri, _state.value.user) }
            .onSuccess { toast("学習記録を書き出しました") }
            .onFailure { toast("書き出しに失敗しました") }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching { store.readBackup(uri) }
            .onSuccess { b ->
                updateUser { u -> b.copy(audioTreeUri = u.audioTreeUri, tracks = u.tracks, sectionAudio = u.sectionAudio) }
                toast("学習記録を読み込みました")
            }
            .onFailure { toast("このファイルは読み込めませんでした") }
    }

    fun resetProgress() = updateUser { it.copy(progress = emptyMap(), days = emptyMap()) }

    fun updateSettings(f: (Settings) -> Settings) = updateUser { it.copy(settings = f(it.settings)) }

    // ---------- 集計 ----------

    fun planner(): Planner? = _state.value.deck?.let { Planner(it, _state.value.user.progress, today) }

    fun categoryOf(key: String): Category = _state.value.user.progress[key].category()

    fun streak(): Int = Stats.streak(_state.value.user.dayLogs(), today)

    // ---------- 学習セッション ----------

    fun startSession(scope: Scope, mode: StudyMode) {
        val p = planner() ?: return
        val s = _state.value.user.settings
        val qs = p.build(scope, mode, s.sessionSize, s.newPerDay, s.puzzleFirst)
        if (qs.isEmpty()) {
            toast(when (scope) {
                Scope.Weak -> "苦手な問題はまだありません"
                Scope.Today -> "今日の分は終わりました！"
                is Scope.Section -> "出題できる問題がありません"
            })
            return
        }
        val title = when (scope) {
            Scope.Today -> "今日の学習"
            Scope.Weak -> "苦手を練習"
            is Scope.Section -> "Section ${scope.section}"
        }
        if (scope is Scope.Section) updateUser { it.copy(lastSection = scope.section) }
        _state.update { it.copy(session = Session(title, qs, scope = scope, mode = mode)) }
    }

    fun endSession() {
        player.stop()
        _state.update { it.copy(session = null) }
    }

    /** 答え合わせ後の 3 段階評価。 */
    fun rate(q: Question, rating: Rating, confidence: Confidence?) {
        val session = _state.value.session ?: return
        val now = System.currentTimeMillis()
        val spent = ((now - session.lastAnswerAt) / 1000).coerceIn(1, 180)
        val day = today
        updateUser { u ->
            val old = u.progress[q.key]
            var next = Srs.review(old, rating, confidence, day)
            if (q.mode == StudyMode.PUZZLE) next = next.copy(puzzleDone = true)
            val log = u.days[day.toString()] ?: DayLog()
            u.copy(
                progress = u.progress + (q.key to next),
                days = u.days + (day.toString() to log.copy(
                    seconds = log.seconds + spent,
                    answered = log.answered + 1,
                    correct = log.correct + if (rating != Rating.WEAK) 1 else 0,
                )),
            )
        }
        player.stop()
        _state.update {
            it.copy(session = session.copy(
                index = session.index + 1,
                results = session.results + (q to rating),
                lastAnswerAt = now,
            ))
        }
    }

    /** 結果画面から「苦手だった問題だけもう一度」。 */
    fun retryWeak() {
        val s = _state.value.session ?: return
        val weak = s.results.filter { it.second == Rating.WEAK }.map { (q, _) ->
            if (q.mode == StudyMode.PUZZLE) q.copy(mode = StudyMode.FREE) else q
        }
        if (weak.isEmpty()) { endSession(); return }
        _state.update { it.copy(session = Session("苦手をもう一度", weak.shuffled(), scope = Scope.Weak, mode = s.mode)) }
    }

    fun preview(q: Question, rating: Rating, confidence: Confidence?): String =
        Srs.preview(_state.value.user.progress[q.key], rating, confidence)

    // ---------- 音声 ----------

    private var segJob: Job? = null

    fun hasTrack(section: Int): Boolean = _state.value.user.tracks.containsKey(section.toString())

    /** 例文を読み上げる。CD 音声があればその区間、なければ TTS。 */
    fun playSentence(s: Sentence, speed: Float? = null) {
        val st = _state.value.user.settings
        val sp = speed ?: st.speed
        val track = _state.value.user.tracks[s.section.toString()]
        if (st.audioSource == AudioSource.TTS || track == null) {
            player.speak(s.en, sp); return
        }
        segJob?.cancel()
        segJob = viewModelScope.launch {
            val seg = runCatching { segmentFor(s) }.getOrNull()
            if (seg == null) player.speak(s.en, sp)
            else player.playTrack(Uri.parse(track), seg, sp)
        }
    }

    fun speakText(text: String) = player.speak(text, _state.value.user.settings.speed)

    private suspend fun segmentFor(s: Sentence): Segment? {
        val deck = _state.value.deck ?: return null
        val inSec = deck.sentencesIn(s.section)
        val idx = inSec.indexOfFirst { it.id == s.id }
        val segs = segments(s.section) ?: return null
        // 分割数が合わないときは TTS にまかせる
        return if (segs.size == inSec.size) segs.getOrNull(idx) else null
    }

    /** セクション音声を 1 文ずつに分けた結果（キャッシュあり）。 */
    suspend fun segments(section: Int, force: Boolean = false): List<Segment>? {
        val user = _state.value.user
        val deck = _state.value.deck ?: return null
        val track = user.tracks[section.toString()] ?: return null
        val conf = user.sectionAudio[section.toString()] ?: SectionAudio()
        if (!force && conf.segments.isNotEmpty()) return conf.segments
        val env = library.envelope(Uri.parse(track))
        val count = deck.sentencesIn(section).size
        val segs = Segmenter.split(env, count, Segmenter.Options(skip = conf.skip, sensitivity = conf.sensitivity))
        updateUser { u -> u.copy(sectionAudio = u.sectionAudio + (section.toString() to conf.copy(segments = segs))) }
        return segs
    }

    fun tuneSection(section: Int, skip: Int, sensitivity: Float) = viewModelScope.launch {
        updateUser(save = false) { u ->
            u.copy(sectionAudio = u.sectionAudio + (section.toString() to SectionAudio(skip, sensitivity)))
        }
        _state.update { it.copy(busy = "音声を分割しています…") }
        runCatching { segments(section, force = true) }
        _state.update { it.copy(busy = null) }
    }

    fun playSegment(section: Int, seg: Segment) {
        val track = _state.value.user.tracks[section.toString()] ?: return
        player.playTrack(Uri.parse(track), seg, _state.value.user.settings.speed)
    }

    override fun onCleared() {
        player.release()
    }
}
