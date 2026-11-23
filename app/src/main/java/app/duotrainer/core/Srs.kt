package app.duotrainer.core

import kotlin.math.roundToInt

/**
 * 分散学習のスケジューラ。
 *
 * - 苦手 → 翌日にもう一度（1 日後 → 3 日後 → 1 週間後…と伸ばし直す）
 * - 得意 → 1 段階進める
 * - 覚えた → 2 段階進める（最短でも 1 週間後）
 * - 自信度で間隔を補正する（自信なしで正解 → 短め、自信ありで正解 → 長め）
 */
object Srs {
    /** stage → 次の復習までの日数。 */
    val INTERVALS = intArrayOf(1, 3, 7, 14, 30, 60, 120)
    private val MAX_STAGE = INTERVALS.size - 1

    fun intervalDays(stage: Int, rating: Rating, confidence: Confidence?): Int {
        if (rating == Rating.WEAK) return 1
        val base = INTERVALS[stage.coerceIn(0, MAX_STAGE)]
        val factor = when (confidence) {
            Confidence.LOW -> 0.6
            Confidence.HIGH -> 1.25
            else -> 1.0
        }
        val days = (base * factor).roundToInt().coerceAtLeast(1)
        return if (rating == Rating.MASTERED) days.coerceAtLeast(7) else days
    }

    fun nextStage(current: Progress, rating: Rating): Int = when (rating) {
        Rating.WEAK -> 0
        Rating.GOOD -> (current.stage + 1).coerceAtMost(MAX_STAGE)
        Rating.MASTERED -> (current.stage + 2).coerceAtMost(MAX_STAGE)
    }

    fun review(current: Progress?, rating: Rating, confidence: Confidence?, today: Long): Progress {
        val p = current ?: Progress()
        val stage = nextStage(p, rating)
        val interval = intervalDays(stage, rating, confidence)
        return p.copy(
            stage = stage,
            due = today + interval,
            last = rating,
            reps = p.reps + 1,
            lapses = p.lapses + if (rating == Rating.WEAK) 1 else 0,
            lastDay = today,
        )
    }

    /** ボタンの下に出す「次はいつ」ラベル。 */
    fun preview(current: Progress?, rating: Rating, confidence: Confidence?): String {
        val p = current ?: Progress()
        return formatDays(intervalDays(nextStage(p, rating), rating, confidence))
    }

    fun formatDays(days: Int): String = when {
        days <= 1 -> "明日"
        days < 7 -> "${days}日後"
        days < 30 -> "${(days / 7.0).roundToInt()}週間後"
        else -> "${(days / 30.0).roundToInt()}か月後"
    }
}
