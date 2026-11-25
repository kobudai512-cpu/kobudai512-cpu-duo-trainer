package app.duotrainer.core

import kotlin.random.Random

/** 1 問ぶんの出題。 */
data class Question(
    val key: String,
    val mode: StudyMode,
    val sentence: Sentence? = null,
    val word: Word? = null,
    /** 同じ回の中での「もう一度」（苦手にした直後の再確認）。復習予定は変えない。 */
    val relearn: Boolean = false,
) {
    val section: Int get() = sentence?.section ?: word?.section ?: 0
}

/** 出題範囲。 */
sealed interface Scope {
    data object Today : Scope
    data object Weak : Scope
    data class Section(val section: Int) : Scope
    /** 決まった例文の集まり（★ブックマークなど） */
    data class Custom(val title: String, val ids: List<Int>) : Scope
}

enum class Category(val label: String) { NEW("未学習"), WEAK("苦手"), HARD("難しい"), GOOD("正解"), MASTERED("簡単") }

fun Progress?.category(): Category = when (this?.last) {
    null -> Category.NEW
    Rating.WEAK -> Category.WEAK
    Rating.HARD -> Category.HARD
    Rating.GOOD -> Category.GOOD
    Rating.MASTERED -> Category.MASTERED
}

/** 出題リストを組み立てる。 */
class Planner(
    private val deck: Deck,
    private val progress: Map<String, Progress>,
    private val today: Long,
    private val random: Random = Random.Default,
) {
    fun dueSentences(): List<Sentence> = deck.sentences.filter { s ->
        progress[Keys.sentence(s.id)]?.let { it.due <= today } ?: false
    }.sortedBy { progress.getValue(Keys.sentence(it.id)).due }

    fun newSentences(): List<Sentence> = deck.sentences.filter { Keys.sentence(it.id) !in progress }

    fun weakSentences(): List<Sentence> =
        deck.sentences.filter { progress[Keys.sentence(it.id)]?.last == Rating.WEAK }

    /** 今日すでに新しく覚え始めた数。 */
    fun newStartedToday(): Int = progress.count { (k, p) -> k.startsWith("s:") && p.reps == 1 && p.lastDay == today }

    fun todayRemaining(newPerDay: Int): Pair<Int, Int> {
        val due = dueSentences().size
        val fresh = (newPerDay - newStartedToday()).coerceAtLeast(0).coerceAtMost(newSentences().size)
        return due to fresh
    }

    fun build(scope: Scope, mode: StudyMode, size: Int, newPerDay: Int): List<Question> {
        if (mode == StudyMode.WORD) return buildWords(scope, size)
        val list: List<Sentence> = when (scope) {
            Scope.Today -> {
                val (_, fresh) = todayRemaining(newPerDay)
                val due = interleave(dueSentences().take(size))
                val news = newSentences().take(fresh.coerceAtMost((size - due.size).coerceAtLeast(0)))
                mixIn(due, news)
            }
            Scope.Weak -> weakSentences().shuffled(random).take(size)
            is Scope.Section -> {
                val inSec = deck.sentencesIn(scope.section)
                val due = inSec.filter { progress[Keys.sentence(it.id)]?.let { p -> p.due <= today } ?: false }
                val news = inSec.filter { Keys.sentence(it.id) !in progress }
                val rest = inSec - due.toSet() - news.toSet()
                (news + due + rest).take(size)
            }
            is Scope.Custom -> scope.ids.mapNotNull(deck::sentence).shuffled(random).take(size)
        }
        return list.map { s -> Question(Keys.forSentence(mode, s.id), mode, sentence = s) }
    }

    private fun buildWords(scope: Scope, size: Int): List<Question> {
        val words = when (scope) {
            is Scope.Section -> deck.wordsIn(scope.section)
            Scope.Weak -> deck.words.filter { progress[Keys.word(it.id)]?.last == Rating.WEAK }.shuffled(random)
            is Scope.Custom -> deck.words.filter { it.sentenceId in scope.ids }.shuffled(random)
            Scope.Today -> {
                val studiedSections = deck.sentences.filter { Keys.sentence(it.id) in progress }.map { it.section }.toSet()
                    .ifEmpty { setOf(deck.sections.first()) }
                val due = deck.words.filter { progress[Keys.word(it.id)]?.let { p -> p.due <= today } ?: false }
                val fresh = deck.words.filter { it.section in studiedSections && Keys.word(it.id) !in progress }
                interleaveWords(due) + fresh
            }
        }
        return words.take(size).map { Question(Keys.word(it.id), StudyMode.WORD, word = it) }
    }

    /**
     * インターリービング：違うセクションの例文が続くように並べる。
     * セクションごとのグループから順番に 1 文ずつ取り出す。
     */
    fun interleave(items: List<Sentence>): List<Sentence> {
        val groups = items.groupBy { it.section }.values.map { it.shuffled(random).toMutableList() }.shuffled(random)
        val out = mutableListOf<Sentence>()
        while (groups.any { it.isNotEmpty() }) for (g in groups) if (g.isNotEmpty()) out += g.removeAt(0)
        return out
    }

    private fun interleaveWords(items: List<Word>): List<Word> {
        val groups = items.groupBy { it.section }.values.map { it.shuffled(random).toMutableList() }.shuffled(random)
        val out = mutableListOf<Word>()
        while (groups.any { it.isNotEmpty() }) for (g in groups) if (g.isNotEmpty()) out += g.removeAt(0)
        return out
    }

    /** 復習の合間に新しい例文を均等に差し込む。 */
    private fun mixIn(due: List<Sentence>, news: List<Sentence>): List<Sentence> {
        if (due.isEmpty()) return news
        if (news.isEmpty()) return due
        val gap = (due.size / news.size).coerceAtLeast(1)
        val out = mutableListOf<Sentence>()
        var ni = 0
        due.forEachIndexed { i, s ->
            out += s
            if ((i + 1) % gap == 0 && ni < news.size) out += news[ni++]
        }
        while (ni < news.size) out += news[ni++]
        return out
    }

    /** 単語テストの 4 択（同じセクションからまぎらわしい選択肢を選ぶ）。 */
    fun choicesFor(word: Word, count: Int = 4): List<Word> {
        val pool = (deck.wordsIn(word.section) + deck.wordsIn(word.section + 1) + deck.wordsIn(word.section - 1))
            .filter { it.id != word.id && it.en != word.en }
            .distinctBy { it.en }
        return (pool.shuffled(random).take(count - 1) + word).shuffled(random)
    }
}

/** 学習記録の集計。 */
object Stats {
    /**
     * これからの復習の予報：今日から [days] 日分、和文英訳の復習が何文ずつ出るか。
     * 今日の分には、期限を過ぎてたまっている文も含める。
     */
    fun forecast(progress: Map<String, Progress>, today: Long, days: Int = 7): List<Int> {
        val out = IntArray(days)
        for ((k, p) in progress) {
            if (!k.startsWith("s:")) continue
            val d = (p.due - today).coerceAtLeast(0)
            if (d < days) out[d.toInt()]++
        }
        return out.toList()
    }

    /** 今日（または昨日）までの連続学習日数。 */
    fun streak(days: Map<Long, DayLog>, today: Long): Int {
        var d = if ((days[today]?.answered ?: 0) > 0) today else today - 1
        var n = 0
        while ((days[d]?.answered ?: 0) > 0) { n++; d-- }
        return n
    }

    fun bestStreak(days: Map<Long, DayLog>): Int {
        val active = days.filter { it.value.answered > 0 }.keys.sorted()
        var best = 0; var cur = 0; var prev = Long.MIN_VALUE
        for (d in active) { cur = if (d == prev + 1) cur + 1 else 1; best = maxOf(best, cur); prev = d }
        return best
    }
}
