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

    @Test fun srsIntervals() {
        val today = 100L
        val a = Srs.review(null, Rating.WEAK, Confidence.MID, today)
        assertEquals(101, a.due)
        val b = Srs.review(a, Rating.GOOD, Confidence.MID, 101)
        assertEquals(101 + 3, b.due)
        val c = Srs.review(b, Rating.GOOD, Confidence.MID, 104)
        assertEquals(104 + 7, c.due)
        val m = Srs.review(null, Rating.MASTERED, Confidence.HIGH, today)
        assertTrue(m.due - today >= 7)
        val low = Srs.review(b, Rating.GOOD, Confidence.LOW, 104)
        assertTrue(low.due < c.due)
    }

    @Test fun interleaveAlternatesSections() {
        val deck = DeckParser.parse(sample)
        val p = Planner(deck, emptyMap(), 0, kotlin.random.Random(1))
        val out = p.interleave(deck.sentences)
        assertEquals(3, out.size)
        assertTrue(out.zipWithNext().any { (x, y) -> x.section != y.section })
    }

    @Test fun puzzleFirstThenFree() {
        val deck = DeckParser.parse(sample)
        val fresh = Planner(deck, emptyMap(), 0).build(Scope.Section(1), StudyMode.FREE, 10, 10, true)
        assertTrue(fresh.all { it.mode == StudyMode.PUZZLE })
        val done = mapOf(Keys.sentence(1) to Progress(puzzleDone = true, last = Rating.GOOD, due = 5))
        val again = Planner(deck, done, 0).build(Scope.Section(1), StudyMode.FREE, 10, 10, true)
        assertEquals(StudyMode.FREE, again.first { it.key == "s:1" }.mode)
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
        assertTrue(two[0].endMs in 1400L..1700L)
        assertTrue(two[1].startMs in 2000L..2200L)
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
