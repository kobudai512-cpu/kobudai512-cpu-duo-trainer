package app.duotrainer.core

/** 英文の比較・見出し語マッチングのための小さな道具箱。 */
object TextUtil {

    private val WORD = Regex("[A-Za-z][A-Za-z'’-]*")

    private val STOP = setOf(
        "a", "an", "the", "of", "to", "in", "on", "at", "for", "with", "by", "from",
        "be", "is", "am", "are", "was", "were", "one", "one's", "oneself", "sb", "sth",
        "do", "does", "doing", "done", "and", "or", "as", "it", "that", "this", "not", "no",
        "a's", "b", "s", "v",
    )

    /** 英文中の単語トークン（位置付き）。 */
    data class Token(val text: String, val start: Int, val end: Int) {
        val lower: String = text.lowercase().replace('’', '\'')
    }

    fun tokens(s: String): List<Token> =
        WORD.findAll(s).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

    /**
     * 見出し語から照合用のキーを作る。
     * "let go of ～/let ～go" → [[let, go], [let, go]]、"assure A (that)" → [[assure]]
     */
    fun headwordKeys(headword: String): List<List<String>> {
        val stripped = headword
            .replace(Regex("\\(.*?\\)|（.*?）|\\[.*?]"), " ")
        return stripped.split('/', '／', '、', ',')
            .map { alt ->
                WORD.findAll(alt).map { it.value }
                    .filterNot { it == "A" || it == "B" }
                    .map { it.lowercase().replace('’', '\'') }
                    .filterNot { it in STOP }
                    .toList()
            }
            .filter { it.isNotEmpty() }
    }

    /** 活用形をゆるく吸収するための前方一致用の語幹。 */
    fun stem(word: String): String {
        val w = word.lowercase()
        return when {
            w.length <= 3 -> w
            w.length == 4 -> w.substring(0, 3)
            else -> w.substring(0, maxOf(4, w.length - 2))
        }
    }

    /** 見出し語が英文に含まれていれば、そのトークン位置を返す（含まれなければ null）。 */
    fun findHeadword(headword: String, sentence: List<Token>): List<Int>? =
        findKeys(headwordKeys(headword), sentence)

    /** headwordKeys() の結果を使い回す版。 */
    fun findKeys(keys: List<List<String>>, sentence: List<Token>): List<Int>? {
        for (alt in keys) {
            val hits = mutableListOf<Int>()
            var ok = true
            var from = 0
            for (key in alt) {
                val st = stem(key)
                val idx = (from until sentence.size).firstOrNull { sentence[it].lower.startsWith(st) }
                    ?: sentence.indices.firstOrNull { sentence[it].lower.startsWith(st) }
                if (idx == null) { ok = false; break }
                hits += idx
                from = idx + 1
            }
            if (ok && hits.isNotEmpty()) return hits.distinct()
        }
        return null
    }

    fun containsHeadword(headword: String, sentence: String) =
        findHeadword(headword, tokens(sentence)) != null

    private val CONTRACTIONS = listOf(
        "won't" to "will not", "can't" to "can not", "cannot" to "can not",
        "shan't" to "shall not", "n't" to " not", "'m" to " am", "'re" to " are",
        "'ll" to " will", "'ve" to " have", "'d" to " would",
    )

    /** 比較用に正規化した単語列。大文字小文字・句読点・短縮形の差は無視する。 */
    fun normalizeWords(s: String): List<String> {
        var t = s.lowercase()
            .replace('’', '\'').replace('‘', '\'')
            .replace('“', ' ').replace('”', ' ')
        for ((a, b) in CONTRACTIONS) t = t.replace(a, b)
        return t.replace(Regex("[^a-z0-9' ]"), " ")
            .split(' ')
            .map { it.trim('\'') }
            .filter { it.isNotBlank() }
    }

    enum class DiffKind { MATCH, MISSING, EXTRA }
    data class DiffPart(val text: String, val kind: DiffKind)
    data class DiffResult(val parts: List<DiffPart>, val score: Float) {
        val isPerfect: Boolean get() = score >= 0.999f
    }

    /** 正解文と回答を単語単位の LCS で比較する。score は 0..1。 */
    fun diff(expected: String, answer: String): DiffResult {
        val e = normalizeWords(expected)
        val a = normalizeWords(answer)
        if (e.isEmpty()) return DiffResult(emptyList(), if (a.isEmpty()) 1f else 0f)
        val dp = Array(e.size + 1) { IntArray(a.size + 1) }
        for (i in e.indices.reversed()) for (j in a.indices.reversed()) {
            dp[i][j] = if (e[i] == a[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val parts = mutableListOf<DiffPart>()
        var i = 0; var j = 0
        while (i < e.size || j < a.size) {
            when {
                i < e.size && j < a.size && e[i] == a[j] -> { parts += DiffPart(e[i], DiffKind.MATCH); i++; j++ }
                j < a.size && (i == e.size || dp[i][j + 1] >= dp[i + 1][j]) -> { parts += DiffPart(a[j], DiffKind.EXTRA); j++ }
                else -> { parts += DiffPart(e[i], DiffKind.MISSING); i++ }
            }
        }
        // 抜けも余計な単語も同じ重さで減点する（F1 と同じ形）
        val score = 2f * dp[0][0] / (e.size + a.size)
        return DiffResult(parts, score.coerceIn(0f, 1f))
    }

    /** 1 語の答え合わせ（穴埋め用）。 */
    fun sameWord(expected: String, answer: String): Boolean =
        normalizeWords(expected) == normalizeWords(answer)

    /** 語順パズル用のチップ。句読点は直前の単語にくっつけたまま分ける。 */
    fun puzzlePieces(sentence: String): List<String> =
        sentence.replace('“', '"').replace('”', '"')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
}
