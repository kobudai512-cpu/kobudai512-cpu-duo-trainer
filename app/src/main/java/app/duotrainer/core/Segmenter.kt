package app.duotrainer.core

import kotlinx.serialization.Serializable

/** 1 文ぶんの音声区間（ミリ秒）。 */
@Serializable
data class Segment(val startMs: Long, val endMs: Long)

/** セクション音声の解析結果（音量の包絡線）。 */
class Envelope(val frameMs: Int, val levels: FloatArray) {
    val durationMs: Long get() = levels.size.toLong() * frameMs
}

/**
 * CD のトラック（1 セクション = 1 トラック）を、無音区間で 1 文ずつに切り分ける。
 *
 * 1. 音量が小さいフレームが minGapMs 以上続く所を「無音」とみなす
 * 2. 無音で区切られた発話のかたまりを作る
 * 3. 先頭の skip 個（タイトルの読み上げなど）を飛ばし、
 *    残りを「長い無音の上位 (count-1) 個」で count 文に分ける
 *    （会話文の中の短い間で切れてしまわないように）
 */
object Segmenter {

    data class Options(
        val skip: Int = 0,
        /** 0..1。大きいほど小さな音も無音扱いになる。 */
        val sensitivity: Float = 0.5f,
        val minGapMs: Int = 350,
    )

    fun speechRuns(env: Envelope, opt: Options): List<Segment> {
        val lv = env.levels
        if (lv.isEmpty()) return emptyList()
        val sorted = lv.sorted()
        val floor = sorted[(sorted.size * 0.05).toInt().coerceIn(0, sorted.size - 1)]
        val loud = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)]
        val ratio = 0.02f + 0.18f * opt.sensitivity.coerceIn(0f, 1f)
        val thr = floor + (loud - floor) * ratio
        val minGap = (opt.minGapMs / env.frameMs).coerceAtLeast(1)
        val minSpeech = (150 / env.frameMs).coerceAtLeast(1)

        val runs = mutableListOf<IntRange>()
        var i = 0
        var start = -1
        var silent = 0
        while (i < lv.size) {
            if (lv[i] >= thr) {
                if (start < 0) start = i
                silent = 0
            } else if (start >= 0) {
                silent++
                if (silent >= minGap) {
                    val end = i - silent + 1
                    if (end - start >= minSpeech) runs += start until end
                    start = -1; silent = 0
                }
            }
            i++
        }
        if (start >= 0 && lv.size - silent - start >= minSpeech) runs += start until (lv.size - silent)
        return runs.map { Segment(it.first.toLong() * env.frameMs, (it.last + 1).toLong() * env.frameMs) }
    }

    fun split(env: Envelope, count: Int, opt: Options = Options()): List<Segment> {
        val runs = speechRuns(env, opt).drop(opt.skip)
        if (runs.isEmpty() || count <= 0) return emptyList()
        if (runs.size <= count) return runs.map { pad(it, env) }
        // 発話のかたまり同士の間（無音）の長さが大きい順に count-1 個を区切りにする
        val gaps = (0 until runs.size - 1).map { it to (runs[it + 1].startMs - runs[it].endMs) }
        val cuts = gaps.sortedByDescending { it.second }.take(count - 1).map { it.first }.sorted()
        val out = mutableListOf<Segment>()
        var from = 0
        for (c in cuts + (runs.size - 1)) {
            out += pad(Segment(runs[from].startMs, runs[c].endMs), env)
            from = c + 1
        }
        return out
    }

    private fun pad(s: Segment, env: Envelope) =
        Segment((s.startMs - 120).coerceAtLeast(0), (s.endMs + 200).coerceAtMost(env.durationMs))
}
