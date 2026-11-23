package app.duotrainer.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.duotrainer.core.Envelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** 端末内の DUO 音声フォルダ（「01 トラック 1.mp3」…「45 トラック 45.mp3」）を扱う。 */
class AudioLibrary(private val context: Context) {

    /** フォルダ内の音声ファイルを「セクション番号 → URI」にする。ファイル名の先頭の数字で判定。 */
    suspend fun scan(tree: Uri): Map<Int, Uri> = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return@withContext emptyMap()
        dir.listFiles()
            .filter { it.isFile && (it.type?.startsWith("audio/") == true || it.name?.lowercase()?.endsWith(".mp3") == true) }
            .mapNotNull { f ->
                val n = Regex("^\\s*(\\d+)").find(f.name.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                n?.let { it to f.uri }
            }
            .toMap()
    }

    private val cache = HashMap<Uri, Envelope>()

    /**
     * mp3 を PCM にデコードして、[frameMs] ごとの平均音量を求める。
     * 1 トラック数分なので数秒で終わる。結果はメモリにキャッシュする。
     */
    suspend fun envelope(uri: Uri, frameMs: Int = 10): Envelope = withContext(Dispatchers.Default) {
        cache[uri]?.let { return@withContext it }
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        val track = (0 until ex.trackCount).first {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        ex.selectTrack(track)
        val format = ex.getTrackFormat(track)
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val levels = ArrayList<Float>(60_000)
        var acc = 0.0
        var accN = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o >= 0 -> {
                        val out = codec.getOutputBuffer(o)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val perFrame = (sampleRate * channels * frameMs / 1000).coerceAtLeast(1)
                        while (shorts.hasRemaining()) {
                            acc += abs(shorts.get().toInt())
                            accN++
                            if (accN >= perFrame) {
                                levels += (acc / accN / 32768.0).toFloat()
                                acc = 0.0; accN = 0
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            ex.release()
        }
        Envelope(frameMs, levels.toFloatArray()).also { cache[uri] = it }
    }
}
