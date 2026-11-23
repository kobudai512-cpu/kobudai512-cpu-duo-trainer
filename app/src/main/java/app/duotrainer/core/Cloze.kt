package app.duotrainer.core

/** 穴埋め問題。parts を順に並べると元の英文になる。 */
data class Cloze(val parts: List<Part>) {
    sealed interface Part
    data class Text(val text: String) : Part
    data class Blank(val index: Int, val answer: String) : Part

    val blanks: List<Blank> get() = parts.filterIsInstance<Blank>()

    companion object {
        /**
         * DUO で重要とされる見出し語の部分だけを空欄にする。
         * 見出し語が見つからなかった例文は、一番長い単語を空欄にする。
         */
        fun build(sentence: Sentence, words: List<Word>): Cloze {
            val tokens = TextUtil.tokens(sentence.en)
            val blankIdx = sortedSetOf<Int>()
            for (w in words) TextUtil.findHeadword(w.en, tokens)?.let { blankIdx += it }
            if (blankIdx.isEmpty() && tokens.isNotEmpty()) {
                blankIdx += tokens.indices.maxBy { tokens[it].text.length }
            }
            val parts = mutableListOf<Part>()
            var pos = 0
            var n = 0
            for (i in blankIdx) {
                val t = tokens[i]
                if (t.start > pos) parts += Text(sentence.en.substring(pos, t.start))
                parts += Blank(n++, t.text)
                pos = t.end
            }
            if (pos < sentence.en.length) parts += Text(sentence.en.substring(pos))
            return Cloze(parts)
        }
    }
}
