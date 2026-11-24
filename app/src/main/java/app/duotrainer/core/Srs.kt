package app.duotrainer.core

import kotlin.math.roundToInt

/**
 * 分散学習のスケジューラ（エビングハウスの忘却曲線を参考にした、少しずつ伸ばす方式）。
 *
 * 忘却曲線は「覚えた直後に急に忘れ、思い出すたびに忘れ方がゆるやかになる」ので、
 *   その場でもう一度 → 1 日後 → 3 日後 → 約 1 週間後 → 約 2〜3 週間後 → 約 1〜2 か月後 …
 * と、思い出せるたびに間隔を「覚えやすさ（ease）」倍ずつ伸ばす。
 *
 * - 苦手   … 翌日に戻す（その回の中でもう一度出題）。ease を下げ、次からは伸び方がゆるやかに
 * - 得意   … 前回の間隔 × ease
 * - 覚えた … 前回の間隔 × ease × 1.3、ease を上げる（和文英訳のときだけ選べる）
 * - 自信度 … 自信なしで正解 → 短め＆ease 少し下げる、自信あり → 少し長め
 */
object Srs {
    const val START_EASE = 2.5f
    private const val MIN_EASE = 1.3f
    private const val MAX_EASE = 3.0f
    private const val MAX_INTERVAL = 180

    /** 以前の版（段階式）の stage → 日数。古い記録を読み替えるのに使う。 */
    private val LEGACY = intArrayOf(0, 3, 7, 14, 30, 60, 120)

    private fun lastInterval(p: Progress): Int =
        if (p.interval > 0) p.interval else LEGACY[p.stage.coerceIn(0, LEGACY.size - 1)]

    data class Plan(val interval: Int, val ease: Float)

    fun plan(current: Progress?, rating: Rating, confidence: Confidence?): Plan {
        val p = current ?: Progress()
        val prev = lastInterval(p)
        var ease = p.ease
        if (rating == Rating.WEAK) {
            return Plan(1, (ease - 0.2f).coerceAtLeast(MIN_EASE))
        }
        val conf = when (confidence) {
            Confidence.LOW -> { ease -= 0.1f; 0.7 }
            Confidence.HIGH -> 1.15
            else -> 1.0
        }
        if (rating == Rating.MASTERED) ease += 0.15f
        ease = ease.coerceIn(MIN_EASE, MAX_EASE)
        val base = when {
            prev <= 0 -> 1.0                 // はじめて思い出せた → 1 日後
            prev == 1 -> 3.0                 // 1 日後に思い出せた → 3 日後
            else -> prev * ease.toDouble()   // その後は ease 倍ずつ
        }
        val bonus = if (rating == Rating.MASTERED) 1.3 else 1.0
        var days = (base * conf * bonus).roundToInt().coerceIn(1, MAX_INTERVAL)
        // 思い出せたのに前回より短くはしない（「わからない」のときを除く）
        if (confidence != Confidence.LOW) days = maxOf(days, minOf(prev + 1, MAX_INTERVAL))
        if (rating == Rating.MASTERED) days = maxOf(days, 4)
        return Plan(days, ease)
    }

    fun review(current: Progress?, rating: Rating, confidence: Confidence?, today: Long): Progress {
        val p = current ?: Progress()
        val plan = plan(p, rating, confidence)
        return p.copy(
            stage = if (rating == Rating.WEAK) 0 else p.stage + 1,
            due = today + plan.interval,
            interval = plan.interval,
            ease = plan.ease,
            last = rating,
            reps = p.reps + 1,
            lapses = p.lapses + if (rating == Rating.WEAK) 1 else 0,
            lastDay = today,
        )
    }

    /** ボタンの下に出す「次はいつ」ラベル。 */
    fun preview(current: Progress?, rating: Rating, confidence: Confidence?): String =
        formatDays(plan(current, rating, confidence).interval)

    fun formatDays(days: Int): String = when {
        days <= 1 -> "明日"
        days < 7 -> "${days}日後"
        days < 30 -> "${(days / 7.0).roundToInt()}週間後"
        else -> "${(days / 30.0).roundToInt()}か月後"
    }
}
