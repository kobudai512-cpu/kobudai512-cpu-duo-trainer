package app.duotrainer.core

/**
 * Anki からエクスポートした DUO 3.0 のテキスト（タブ区切り）を読み込む。
 *
 * 1 行 = 英語 \t 日本語 \t [sound:...] \t タグ
 * タグが「N SECTION」の行は見出し語、タグが空の行は例文として扱う。
 * 例文にはセクションの情報がないので、見出し語との一致からセクションを推定する。
 */
object DeckParser {

    data class RawWord(val en: String, val ja: String, val section: Int)
    data class RawSentence(val en: String, val ja: String)

    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Deck {
        val words = mutableListOf<RawWord>()
        val sentences = mutableListOf<RawSentence>()
        var tagColumn = 4
        text.lineSequence().forEach { line ->
            if (line.startsWith("#")) {
                Regex("#tags column:(\\d+)").find(line)?.let { tagColumn = it.groupValues[1].toInt() }
                return@forEach
            }
            if (line.isBlank()) return@forEach
            val cols = line.split('\t').map(::clean)
            if (cols.size < 2) return@forEach
            val en = cols[0]
            val ja = cols[1]
            if (en.isBlank()) return@forEach
            val tag = cols.getOrNull(tagColumn - 1).orEmpty()
            val sec = Regex("(\\d+)\\s*SECTION", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toInt()
            if (sec != null) words += RawWord(en, ja, sec) else sentences += RawSentence(en, ja)
        }
        if (sentences.isEmpty()) throw ParseException("例文が見つかりませんでした。DUO 3.0 のテキストを選んでください。")
        if (words.isEmpty()) throw ParseException("見出し語（N SECTION タグ付きの行）が見つかりませんでした。")
        return Aligner.align(sentences, words)
    }

    /** Anki の HTML 付きフィールドをプレーンテキストにする。 */
    fun clean(field: String): String {
        var s = field.trim()
        if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) {
            s = s.substring(1, s.length - 1).replace("\"\"", "\"")
        }
        s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\[sound:[^]]*]"), "")
        s = s.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replace(' ', ' ')
        return s.replace(Regex("\\s+"), " ").trim()
    }
}

/** 例文と見出し語を結び付ける。 */
object Aligner {
    const val VERSION = 5

    fun align(sentences: List<DeckParser.RawSentence>, words: List<DeckParser.RawWord>): Deck {
        val sentTokens = sentences.map { TextUtil.tokens(it.en) }
        val keys = words.map { TextUtil.headwordKeys(it.en) }
        val bySection = words.withIndex().groupBy({ it.value.section }, { it.index })
        val sections = bySection.keys.sorted()

        // 1) 例文ごとに「どのセクションの見出し語を一番多く含むか」でセクションを決める
        val secOf = IntArray(sentences.size)
        var prev = sections.first()
        for (k in sentences.indices) {
            var best = prev
            var bestScore = 0
            for (sec in sections) {
                val score = bySection.getValue(sec).count { TextUtil.findKeys(keys[it], sentTokens[k]) != null }
                if (score > bestScore || (score == bestScore && score > 0 && kotlin.math.abs(sec - prev) < kotlin.math.abs(best - prev))) {
                    best = sec; bestScore = score
                }
            }
            secOf[k] = best
            prev = best
        }
        // 2) 前後が同じセクションなのに 1 文だけ違うものはならす
        for (k in 1 until sentences.size - 1) {
            if (secOf[k - 1] == secOf[k + 1] && secOf[k] != secOf[k - 1]) secOf[k] = secOf[k - 1]
        }

        // 3) 見出し語 → 例文（同じセクションの中で、出てきた順に近いものを優先）
        val wordSentence = arrayOfNulls<Int>(words.size)
        for ((sec, ids) in bySection) {
            val cands = sentences.indices.filter { secOf[it] == sec }
            var cursor = 0
            for (wi in ids) {
                val ordered = cands.drop(cursor) + cands.take(cursor)
                // as for / be in のような小さな語だけの熟語は、並びそのものを探す
                val hit = ordered.firstOrNull {
                    if (keys[wi].isEmpty()) TextUtil.findPhrase(words[wi].en, sentTokens[it]) != null
                    else TextUtil.findKeys(keys[wi], sentTokens[it]) != null
                }
                if (hit != null) {
                    wordSentence[wi] = hit
                    cursor = cands.indexOf(hit)
                }
            }
        }
        // 3') 同じセクションで見つからない語は、となりのセクション → 全体 の順に探す
        //     （教材側のタグと例文の区切りが 1 文ずれていることがあるため）
        for (wi in words.indices) {
            if (wordSentence[wi] != null) continue
            val sec = words[wi].section
            val order = sentences.indices.sortedBy { kotlin.math.abs(secOf[it] - sec) }
            wordSentence[wi] = order.firstOrNull {
                kotlin.math.abs(secOf[it] - sec) <= 1 && TextUtil.findPhrase(words[wi].en, sentTokens[it]) != null
            }
        }

        val outSentences = sentences.mapIndexed { k, s ->
            Sentence(
                id = k + 1,
                en = s.en,
                ja = s.ja,
                section = secOf[k],
                wordIds = words.indices.filter { wordSentence[it] == k }.map { it + 1 },
            )
        }
        val outWords = words.mapIndexed { i, w ->
            Word(id = i + 1, en = w.en, ja = w.ja, section = w.section, sentenceId = wordSentence[i]?.plus(1))
        }
        return Deck(outSentences, outWords, VERSION)
    }
}
