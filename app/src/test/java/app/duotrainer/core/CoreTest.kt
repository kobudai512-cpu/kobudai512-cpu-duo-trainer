package app.duotrainer.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DUO 本体のデータは含めず、同じ形式の自作サンプルでテストする。 */
class CoreTest {

    private val sample = """
        #separator:tab
        #html:true
        #tags column:4
        borrow	【動】～を借りる	[sound:a.mp3]	1 SECTION
        umbrella	【名】傘	[sound:b.mp3]	1 SECTION
        look forward to ～	【動】～を楽しみに待つ	[sound:c.mp3]	1 SECTION
        "<span style=""color: red;"">give up</span>"	【動】あきらめる	[sound:d.mp3]	2 SECTION
        dream	【名】夢	[sound:e.mp3]	2 SECTION
        May I borrow your umbrella?	傘を借りてもいいですか。	[sound:f.mp3]
        I'm looking forward to the trip.	旅行を楽しみにしている。	[sound:g.mp3]
        Never give up your dreams.	夢をあきらめるな。	[sound:h.mp3]
    """.trimIndent()

    @Test fun parsesWordsAndSentences() {
        val deck = DeckParser.parse(sample)
        assertEquals(3, deck.sentences.size)
        assertEquals(5, deck.words.size)
        assertEquals("give up", deck.words[3].en)
        assertEquals(listOf(1, 1, 2), deck.sentences.map { it.section })
        assertEquals(listOf(1, 2), deck.sentences[0].wordIds)
        assertEquals(1, deck.word(1)!!.sentenceId)
        assertEquals(3, deck.word(5)!!.sentenceId)
        assertEquals("動", deck.word(1)!!.partOfSpeech)
        assertEquals("～を借りる", deck.word(1)!!.meaning)
    }

    @Test fun headwordMatchingHandlesInflection() {
        val t = TextUtil.tokens("He leaned against the pillar and gazed at the statue.")
        assertNotNull(TextUtil.findHeadword("lean", t))
        assertNotNull(TextUtil.findHeadword("gaze (at～)", t))
        assertNull(TextUtil.findHeadword("umbrella", t))
        val t2 = TextUtil.tokens("Let go of your negative outlook on life.")
        assertNotNull(TextUtil.findHeadword("let go of ～/let ～go", t2))
    }

    @Test fun phraseIncludesSmallWords() {
        fun blanks(head: String, s: String) =
            TextUtil.findPhrase(head, TextUtil.tokens(s))?.map { TextUtil.tokens(s)[it].text }
        assertEquals(listOf("once", "and", "for", "all"), blanks("once and for all", "Let's settle this once and for all."))
        assertEquals(listOf("at", "any", "moment"), blanks("at any moment", "It could start raining at any moment."))
        assertEquals(listOf("looking", "forward", "to"), blanks("look forward to ～", "I'm looking forward to the trip."))
        assertEquals(listOf("take", "a", "chance"), blanks("take a chance", "You should take a chance."))
        assertEquals(listOf("As", "for"), blanks("as for", "As for me, I'm fine."))
        assertEquals(listOf("shook", "head"), blanks("shake one's head", "He shook his head."))
    }

    @Test fun headwordNotations() {
        assertEquals(listOf("a good deal of", "a great deal of"), TextUtil.alternatives("a good[great] deal of"))
        assertEquals(listOf("get lost", "be lost"), TextUtil.alternatives("get [be] lost"))
        assertEquals(listOf("shrink", "shrank", "shrunk"), TextUtil.alternatives("shrink-shrank-shrunk"))
        assertEquals(listOf("be amazed to hear", "be amazed to see"), TextUtil.alternatives("be amazed to hear[see, etc]"))
        fun blanks(head: String, s: String) =
            TextUtil.findPhrase(head, TextUtil.tokens(s))?.map { TextUtil.tokens(s)[it].text }
        assertEquals(listOf("a", "great", "deal", "of"), blanks("a good[great] deal of", "He wasted a great deal of time."))
        assertEquals(listOf("five-year-old"), blanks("～-year-old", "My five-year-old son"))
        assertEquals(listOf("1960s"), blanks("1960s", "It was popular in the 1960s."))
        assertEquals(listOf("catch", "a", "glimpse", "of"), blanks("catch[get/have] a glimpse of", "hoping to catch a glimpse of Michael."))
        assertEquals(listOf("threefold"), blanks("～-fold", "our surplus has swelled threefold."))
        assertEquals(listOf("became", "aware", "of"), blanks("become aware of", "I became aware of someone"))
        assertEquals(listOf("make", "heads", "or", "tails", "of"), blanks("cannot make heads or tails of ～", "they couldn't make heads or tails of them."))
        assertEquals(listOf("damage", "to"), blanks("do[cause] ～ damage toA", "The drought did severe damage to the crops."))
        assertEquals(listOf("looking", "forward", "to"), blanks("look forward to ～ing", "I'm looking forward to seeing you."))
        assertEquals(listOf("We'd", "better"), blanks("had better do", "We'd better make a reservation."))
        assertEquals(listOf("headache"), blanks("ache", "It will cure you of your headache."))
        assertEquals(listOf("Flies"), blanks("fly", "Flies and mosquitoes interfered."))
        assertEquals(listOf("The", "more", "the", "more"), blanks("the 比較級 S+V、the 比較級 S+V", "The more stubborn you are, the more isolated you become."))
        assertEquals(listOf("as", "many", "as"), blanks("as ～ as…（～=much/many/recently/high/farなど）", "The motel can accommodate as many as 400 guests."))
    }

    @Test fun clozeBlanksKeywords() {
        val deck = DeckParser.parse(sample)
        val s = deck.sentence(1)!!
        val c = Cloze.build(s, s.wordIds.mapNotNull(deck::word))
        assertEquals(listOf("borrow", "umbrella"), c.blanks.map { it.answer })
        val rebuilt = c.parts.joinToString("") { if (it is Cloze.Text) it.text else (it as Cloze.Blank).answer }
        assertEquals(s.en, rebuilt)
    }

    @Test fun diffIgnoresCaseAndContractions() {
        val r = TextUtil.diff("I'm looking forward to the trip.", "i am looking forward to the trip")
        assertTrue(r.isPerfect)
        val r2 = TextUtil.diff("Never give up your dreams.", "Never give your dream")
        assertTrue(r2.score < 0.9f)
        assertTrue(r2.parts.any { it.kind == TextUtil.DiffKind.MISSING && it.text == "up" })
    }

    @Test fun srsGrowsLittleByLittle() {
        // 毎回「得意」: 1 日 → 3 日 → 約 1 週間 → 約 2〜3 週間 → 約 1〜2 か月
        var p: Progress? = null
        var day = 0L
        val gaps = mutableListOf<Long>()
        repeat(5) {
            val n = Srs.review(p, Rating.GOOD, Confidence.MID, day)
            gaps += n.due - day; day = n.due; p = n
        }
        assertEquals(listOf(1L, 3L), gaps.take(2))
        assertTrue(gaps[2] in 6L..9L)
        assertTrue(gaps.zipWithNext().all { (a, b) -> b > a })
        assertTrue(gaps[4] in 30L..80L)
    }

    @Test fun srsWeakResetsAndSlowsDown() {
        val good = Srs.review(Srs.review(null, Rating.GOOD, Confidence.MID, 0), Rating.GOOD, Confidence.MID, 1)
        val weak = Srs.review(good, Rating.WEAK, Confidence.MID, 4)
        assertEquals(5, weak.due)
        assertTrue(weak.ease < good.ease)
        // 苦手のあと思い出せたら 3 日後（いきなり長くはしない）
        assertEquals(8, Srs.review(weak, Rating.GOOD, Confidence.MID, 5).due)
        val low = Srs.review(good, Rating.GOOD, Confidence.LOW, 4)
        val mid = Srs.review(good, Rating.GOOD, Confidence.MID, 4)
        val mastered = Srs.review(good, Rating.MASTERED, Confidence.HIGH, 4)
        assertTrue(low.due < mid.due && mid.due < mastered.due)
    }

    @Test fun srsReadsOldRecords() {
        val old = Progress(stage = 2, due = 10, last = Rating.GOOD, reps = 2)
        assertTrue(Srs.review(old, Rating.GOOD, Confidence.MID, 10).due - 10 >= 14)
    }

    @Test fun interleaveAlternatesSections() {
        val deck = DeckParser.parse(sample)
        val p = Planner(deck, emptyMap(), 0, kotlin.random.Random(1))
        val out = p.interleave(deck.sentences)
        assertEquals(3, out.size)
        assertTrue(out.zipWithNext().any { (x, y) -> x.section != y.section })
    }

    @Test fun freeTranslationFromFirstTime() {
        val deck = DeckParser.parse(sample)
        val fresh = Planner(deck, emptyMap(), 0).build(Scope.Today, StudyMode.FREE, 10, 10)
        assertTrue(fresh.isNotEmpty())
        assertTrue(fresh.all { it.mode == StudyMode.FREE && it.key.startsWith("s:") })
        // 和文英訳以外は練習用の記録に分ける（「覚えた」に影響しない）
        val cloze = Planner(deck, emptyMap(), 0).build(Scope.Section(1), StudyMode.CLOZE, 10, 10)
        assertTrue(cloze.all { it.key.startsWith("p:CLOZE:") })
    }

    @Test fun streakCounts() {
        val d = mapOf(8L to DayLog(answered = 3), 9L to DayLog(answered = 1), 10L to DayLog(answered = 2))
        assertEquals(3, Stats.streak(d, 10))
        assertEquals(3, Stats.streak(d, 11))
        assertEquals(0, Stats.streak(d, 12))
    }

    @Test fun segmenterSplitsOnLongestGaps() {
        // 発話 3 かたまり。1 文目と 2 文目の間は短い間（会話文の中）、2 文目と 3 文目の間は長い間。
        val frames = FloatArray(400) { 0.001f }
        fun speak(a: Int, b: Int) { for (i in a until b) frames[i] = 0.5f }
        speak(10, 60); speak(80, 140); speak(220, 300)
        val env = Envelope(10, frames)
        val runs = Segmenter.speechRuns(env, Segmenter.Options(minGapMs = 150))
        assertEquals(3, runs.size)
        val two = Segmenter.split(env, 2, Segmenter.Options(minGapMs = 150))
        assertEquals(2, two.size)
        // 文末は 0.7 秒延ばすが、次の文の手前（間の無音の中）で止める
        assertTrue(two[0].endMs in 1900L..2100L)
        assertTrue(two[1].startMs in 1900L..2200L)
        assertTrue(two[0].endMs <= two[1].startMs)
    }

    @Test fun segmenterSkipsSectionTitle() {
        // 先頭に短い「Section 1」、そのあと 2 文
        val frames = FloatArray(500) { 0.001f }
        fun speak(a: Int, b: Int) { for (i in a until b) frames[i] = 0.5f }
        speak(5, 40); speak(120, 200); speak(300, 420)
        val env = Envelope(10, frames)
        val segs = Segmenter.split(env, 2, Segmenter.Options(skip = 1))
        assertEquals(2, segs.size)
        assertTrue(segs[0].startMs >= 1000L)
        assertTrue(segs[1].endMs >= 4200L + 600L)
    }

    /** 手元に本物のデータがあるときだけ動く確認用テスト（-Dduo.file=path）。 */
    @Test fun realDeckIfAvailable() {
        val path = System.getProperty("duo.file")?.takeIf { it.isNotBlank() } ?: return
        val deck = DeckParser.parse(File(path).readText())
        println("sentences=${deck.sentences.size} words=${deck.words.size} sections=${deck.sections.size}")
        val linked = deck.words.count { it.sentenceId != null }
        println("words linked to a sentence: $linked / ${deck.words.size}")
        println("sentences without keywords: ${deck.sentences.count { it.wordIds.isEmpty() }}")
        println("per section: " + deck.sections.map { deck.sentencesIn(it).size })
        deck.sentences.take(3).forEach { s ->
            val c = Cloze.build(s, s.wordIds.mapNotNull(deck::word))
            println(c.parts.joinToString("") { if (it is Cloze.Text) it.text else "[${(it as Cloze.Blank).answer}]" })
        }
        assertTrue(deck.sections.size == 45)
    }
}
