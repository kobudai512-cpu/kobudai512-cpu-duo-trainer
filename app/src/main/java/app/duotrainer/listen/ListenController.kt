package app.duotrainer.listen

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.duotrainer.core.Segment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** 聞き流しの 1 文。CD 音声の区間が分かればそれを、なければ TTS で読む。 */
data class ListenItem(
    val sentenceId: Int,
    val section: Int,
    val en: String,
    val ja: String,
    val trackUri: String? = null,
    val segment: Segment? = null,
)

/** 1 文をどう流すか。 */
enum class ListenPattern(val label: String) {
    EN_JA_EN("英→日→英"),
    JA_EN("日→英（考えてから）"),
    EN("英語だけ"),
}

data class ListenState(
    val title: String = "",
    val items: List<ListenItem> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = false,
    /** 今読んでいるもの（"英語" "日本語" "間"） */
    val phase: String = "",
    val pattern: ListenPattern = ListenPattern.EN_JA_EN,
    val repeat: Int = 1,
    /** 文と文の間（秒） */
    val gapSec: Float = 1.5f,
    val loop: Boolean = true,
    val speed: Float = 1.0f,
) {
    val active: Boolean get() = items.isNotEmpty()
    val current: ListenItem? get() = items.getOrNull(index)
}

/**
 * 聞き流しの再生を管理する（アプリ全体で 1 つ）。
 * 画面を消しても止まらないよう、再生中は [ListenService]（通知付きの前面サービス）を動かす。
 */
object ListenController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var audio: ListenAudio? = null
    private var appContext: Context? = null

    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state

    fun start(context: Context, title: String, items: List<ListenItem>, speed: Float) {
        if (items.isEmpty()) return
        val ctx = context.applicationContext
        appContext = ctx
        if (audio == null) audio = ListenAudio(ctx)
        _state.update { it.copy(title = title, items = items, index = 0, speed = speed) }
        val intent = Intent(ctx, ListenService::class.java)
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
        play()
    }

    fun play() {
        if (!_state.value.active) return
        job?.cancel()
        _state.update { it.copy(playing = true) }
        job = scope.launch { runFrom(_state.value.index) }
    }

    fun pause() {
        job?.cancel()
        audio?.stop()
        _state.update { it.copy(playing = false, phase = "") }
    }

    fun toggle() = if (_state.value.playing) pause() else play()

    fun jumpTo(index: Int) {
        val n = _state.value.items.size
        if (n == 0) return
        _state.update { it.copy(index = ((index % n) + n) % n) }
        if (_state.value.playing) play()
    }

    fun next() = jumpTo(_state.value.index + 1)
    fun prev() = jumpTo(_state.value.index - 1)

    fun stop() {
        job?.cancel()
        audio?.stop()
        _state.value = ListenState(
            pattern = _state.value.pattern, repeat = _state.value.repeat,
            gapSec = _state.value.gapSec, loop = _state.value.loop,
        )
        appContext?.let { it.stopService(Intent(it, ListenService::class.java)) }
    }

    fun setPattern(p: ListenPattern) = _state.update { it.copy(pattern = p) }
    fun setRepeat(n: Int) = _state.update { it.copy(repeat = n.coerceIn(1, 5)) }
    fun setGap(sec: Float) = _state.update { it.copy(gapSec = sec) }
    fun setLoop(on: Boolean) = _state.update { it.copy(loop = on) }

    private suspend fun runFrom(start: Int) {
        val a = audio ?: return
        var i = start
        while (coroutineContext.isActive) {
            val st = _state.value
            if (i >= st.items.size) {
                if (!st.loop) break
                i = 0
            }
            _state.update { it.copy(index = i) }
            val item = st.items[i]
            repeat(st.repeat) {
                val s = _state.value
                val gap = (s.gapSec * 1000).toLong()
                when (s.pattern) {
                    ListenPattern.EN_JA_EN -> {
                        english(a, item, s.speed); pause(gap)
                        japanese(a, item, s.speed); pause(gap)
                        english(a, item, s.speed); pause(gap)
                    }
                    ListenPattern.JA_EN -> {
                        japanese(a, item, s.speed)
                        // 英文を思い出す時間（文の長さに合わせて少し長め）
                        pause(gap + item.en.length * 40L)
                        english(a, item, s.speed); pause(gap)
                    }
                    ListenPattern.EN -> { english(a, item, s.speed); pause(gap) }
                }
            }
            i++
        }
        _state.update { it.copy(playing = false, phase = "") }
    }

    private suspend fun english(a: ListenAudio, item: ListenItem, speed: Float) {
        _state.update { it.copy(phase = "英語") }
        val uri = item.trackUri
        val seg = item.segment
        if (uri != null && seg != null) a.playSegment(Uri.parse(uri), seg, speed)
        else a.speak(item.en, Locale.US, speed)
    }

    private suspend fun japanese(a: ListenAudio, item: ListenItem, speed: Float) {
        _state.update { it.copy(phase = "日本語") }
        a.speak(item.ja, Locale.JAPAN, speed)
    }

    private suspend fun pause(ms: Long) {
        _state.update { it.copy(phase = "間") }
        delay(ms)
    }
}

/** 聞き流し用の音声（終わるまで待てる suspend 版）。 */
private class ListenAudio(private val context: Context) {
    private var media: MediaPlayer? = null
    private var mediaUri: Uri? = null
    private val ready = CompletableDeferred<Unit>()
    private val done = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var counter = 0
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context) { ready.complete(Unit) }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { done.remove(id)?.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) { done.remove(id)?.complete(Unit) }
            override fun onError(id: String, errorCode: Int) { done.remove(id)?.complete(Unit) }
        })
    }

    suspend fun playSegment(uri: Uri, seg: Segment, speed: Float) {
        val mp = if (mediaUri == uri && media != null) media!! else {
            media?.release()
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(context, uri)
                prepare()
            }.also { media = it; mediaUri = uri }
        }
        mp.seekTo(seg.startMs.toInt())
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed) }
        mp.start()
        try {
            while (coroutineContext.isActive && mp.isPlaying && mp.currentPosition < seg.endMs) delay(30)
        } finally {
            if (mp.isPlaying) mp.pause()
        }
    }

    suspend fun speak(text: String, locale: Locale, speed: Float) {
        withTimeoutOrNull(5000) { ready.await() } ?: return
        val id = "u${counter++}"
        val wait = CompletableDeferred<Unit>()
        done[id] = wait
        tts.language = locale
        tts.setSpeechRate(speed)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        try {
            withTimeoutOrNull(5000L + text.length * 250L) { wait.await() }
        } finally {
            done.remove(id)
            if (!coroutineContext.isActive) tts.stop()
        }
    }

    fun stop() {
        media?.let { if (it.isPlaying) it.pause() }
        tts.stop()
    }
}
