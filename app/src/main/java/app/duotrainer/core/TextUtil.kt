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

    /** 不規則動詞の過去形・過去分詞（語幹の前方一致では拾えないもの）。 */
    private val IRREGULAR: Map<String, Set<String>> = mapOf(
        "shake" to "shook shaken", "take" to "took taken", "give" to "gave given", "break" to "broke broken",
        "come" to "came", "get" to "got gotten", "go" to "went gone", "run" to "ran", "bring" to "brought",
        "tell" to "told", "lie" to "lay lain lying", "lay" to "laid", "sit" to "sat", "seek" to "sought",
        "bear" to "bore born borne", "make" to "made", "think" to "thought", "catch" to "caught",
        "buy" to "bought", "fall" to "fell fallen", "keep" to "kept", "hold" to "held", "leave" to "left",
        "tear" to "tore torn", "throw" to "threw thrown", "see" to "saw seen", "find" to "found",
        "feel" to "felt", "stand" to "stood", "wear" to "wore worn", "blow" to "blew blown",
        "shrink" to "shrank shrunk", "have" to "had has", "say" to "said", "speak" to "spoke spoken",
        "write" to "wrote written", "choose" to "chose chosen", "drive" to "drove driven",
        "fight" to "fought", "teach" to "taught", "send" to "sent", "spend" to "spent", "lose" to "lost",
        "win" to "won", "begin" to "began begun", "drink" to "drank drunk", "swear" to "swore sworn",
        "creep" to "crept", "steal" to "stole stolen", "hide" to "hid hidden", "forget" to "forgot forgotten",
        "grow" to "grew grown", "know" to "knew known", "draw" to "drew drawn", "fly" to "flew flown",
        "ride" to "rode ridden", "rise" to "rose risen", "sell" to "sold", "strike" to "struck",
        "hang" to "hung", "dig" to "dug", "lend" to "lent", "mean" to "meant", "meet" to "met",
        "pay" to "paid", "sleep" to "slept", "sweep" to "swept", "weep" to "wept", "bite" to "bit bitten",
        "freeze" to "froze frozen", "wake" to "woke woken", "shine" to "shone", "shoot" to "shot",
        "sink" to "sank sunk", "sting" to "stung", "swim" to "swam swum", "be" to "is am are was were been",
        "feed" to "fed", "lead" to "led", "deal" to "dealt", "build" to "built", "bend" to "bent",
        "stick" to "stuck", "hear" to "heard", "understand" to "understood", "arise" to "arose arisen",
    ).mapValues { it.value.split(' ').toSet() }

    /** トークンが見出し語の 1 語（活用形を含む）に当たるか。 */
    fun matchesKey(token: Token, key: String): Boolean =
        token.lower.startsWith(stem(key)) || IRREGULAR[key]?.contains(token.lower) == true

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
                val idx = (from until sentence.size).firstOrNull { matchesKey(sentence[it], key) }
                    ?: sentence.indices.firstOrNull { matchesKey(sentence[it], key) }
                if (idx == null) { ok = false; break }
                hits += idx
                from = idx + 1
            }
            if (ok && hits.isNotEmpty()) return hits.distinct()
        }
        return null
    }

    /** 見出し語の中の「何が入ってもいい所」（～, A, one's など）。 */
    private val PLACEHOLDER = setOf("sb", "sth", "one's", "oneself", "do", "doing", "done", "s", "v")

    private fun isPlaceholder(original: String) =
        original == "A" || original == "B" || original.lowercase().replace('’', '\'') in PLACEHOLDER

    private fun sameOrInflected(token: Token, word: String): Boolean {
        val w = word.lowercase().replace('’', '\'')
        return token.lower == w || (w.length >= 4 && token.lower.startsWith(stem(w))) || IRREGULAR[w]?.contains(token.lower) == true
    }

    /**
     * 穴埋め・強調表示用：見出し語の熟語全体の位置を返す。
     * 内容語（once, all など）だけでなく、その間と前後にある and / for / at / of / to などの
     * 小さな語も、見出し語に含まれていれば一緒に含める。
     *   "once and for all" → once, and, for, all
     *   "at any moment"    → at, any, moment
     */
    fun findPhrase(headword: String, sentence: List<Token>): List<Int>? {
        val stripped = headword.replace(Regex("\\(.*?\\)|（.*?）|\\[.*?]"), " ")
        for (alt in stripped.split('/', '／', '、', ',')) {
            val words = WORD.findAll(alt).map { it.value }.toList()
            if (words.isEmpty()) continue
            val isContent = words.map { !isPlaceholder(it) && it.lowercase().replace('’', '\'') !in STOP }
            val content = words.filterIndexed { i, _ -> isContent[i] }.map { it.lowercase().replace('’', '\'') }
            val hits: List<Int> = if (content.isNotEmpty()) {
                findKeys(listOf(content), sentence) ?: continue
            } else {
                // 小さな語だけの熟語（"as for", "be in" など）は、そのまま並んでいる所を探す
                val seq = words.filterNot(::isPlaceholder)
                if (seq.isEmpty()) continue
                val at = (0..sentence.size - seq.size).firstOrNull { s ->
                    seq.indices.all { sameOrInflected(sentence[s + it], seq[it]) }
                } ?: continue
                seq.indices.map { at + it }
            }
            if (hits.isEmpty()) continue
            val out = hits.toSortedSet()
            val lo = out.first()
            val hi = out.last()
            val small = words.filterNot(::isPlaceholder).map { it.lowercase().replace('’', '\'') }.toSet()
            // 内容語の間にある、見出し語に含まれる小さな語
            for (i in lo..hi) if (sentence[i].lower in small) out += i
            // 前に付く語（at any moment の at など）
            val firstContent = isContent.indexOfFirst { it }.let { if (it < 0) 0 else it }
            var j = firstContent - 1
            var p = lo - 1
            while (j >= 0 && p >= 0 && !isPlaceholder(words[j]) && sameOrInflected(sentence[p], words[j])) {
                out += p; j--; p--
            }
            // 後ろに付く語（look forward to の to など）
            val lastContent = isContent.indexOfLast { it }.let { if (it < 0) words.lastIndex else it }
            j = lastContent + 1
            p = hi + 1
            while (j < words.size && p < sentence.size && !isPlaceholder(words[j]) && sameOrInflected(sentence[p], words[j])) {
                out += p; j++; p++
            }
            return out.toList()
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
