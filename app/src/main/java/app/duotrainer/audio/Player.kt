package app.duotrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import app.duotrainer.core.Segment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** CD 音声の区間再生と、端末の読み上げ（TTS）をまとめたプレイヤー。 */
class Player(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var media: MediaPlayer? = null
    private var mediaUri: Uri? = null
    private var stopAt: Long = Long.MAX_VALUE

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private var ttsReady = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts.language = Locale.US
        }
    }

    private val watcher = object : Runnable {
        override fun run() {
            val mp = media ?: return
            if (mp.isPlaying && mp.currentPosition >= stopAt) {
                mp.pause()
                _playing.value = false
                return
            }
            if (mp.isPlaying) handler.postDelayed(this, 30)
        }
    }

    /** トラックの [segment] の部分だけを再生する（segment が null ならトラック全体）。 */
    fun playTrack(uri: Uri, segment: Segment?, speed: Float) {
        stop()
        val mp = if (mediaUri == uri && media != null) media!! else {
            media?.release()
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(appContext, uri)
                prepare()
            }.also { media = it; mediaUri = uri }
        }
        mp.setOnCompletionListener { _playing.value = false }
        stopAt = segment?.endMs ?: Long.MAX_VALUE
        mp.seekTo((segment?.startMs ?: 0L).toInt())
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed) }
        mp.start()
        _playing.value = true
        handler.post(watcher)
    }

    fun speak(text: String, speed: Float) {
        stop()
        if (!ttsReady) return
        tts.setSpeechRate(speed)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "duo")
    }

    val ttsAvailable: Boolean get() = ttsReady

    fun stop() {
        handler.removeCallbacks(watcher)
        media?.let { if (it.isPlaying) it.pause() }
        if (ttsReady) tts.stop()
        _playing.value = false
    }

    fun release() {
        stop()
        media?.release()
        media = null
        mediaUri = null
        tts.shutdown()
    }
}
