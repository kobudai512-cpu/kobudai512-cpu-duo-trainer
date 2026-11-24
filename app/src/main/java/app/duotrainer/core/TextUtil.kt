package app.duotrainer.core

/** 英文の比較・見出し語マッチングのための小さな道具箱。 */
object TextUtil {

    private val WORD = Regex("[A-Za-z0-9][A-Za-z0-9'’-]*")

    private val STOP = setOf(
        "a", "an", "the", "of", "to", "in", "on", "at", "for", "with", "by", "from",
        "be", "is", "am", "are", "was", "were", "one", "one's", "oneself", "sb", "sth",
        "do", "does", "doing", "done", "and", "or", "as", "it", "that", "this", "not", "no",
        "a's", "b", "s", "v", "can", "cannot", "can't", "could", "couldn't",
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
    /** "～-fold" のような接尾辞の見出し語は "-fold" として扱う（threefold / five-year-old に当てる）。 */
    private fun wordsOf(alt: String): List<String> {
        Regex("^[～~]\\s*-([A-Za-z-]+)$").matchEntire(alt.trim())?.let { return listOf("-" + it.groupValues[1]) }
        return WORD.findAll(alt).map { it.value }.toList()
    }

    fun headwordKeys(headword: String): List<List<String>> {
        return alternatives(headword)
            .map { alt ->
                wordsOf(alt)
                    .filterNot { it == "A" || it == "B" }
                    .map { it.lowercase().replace('’', '\'') }
                    .filterNot { it in STOP }
            }
            .filter { it.isNotEmpty() }
    }

    /**
     * 見出し語の書き方の揺れを、照合用の候補に展開する。
     *   "a good[great] deal of"   → a good deal of / a great deal of
     *   "get [be] lost"           → get lost / be lost
     *   "not ～ anymore[any more]" → not ～ anymore / not ～ any more
     *   "be amazed to hear[see, etc]" → … hear / … see
     *   "shrink-shrank-shrunk"    → shrink / shrank / shrunk
     *   "assure A (that)"         → assure A（丸かっこは省略可能な部分として外す）
     */
    fun alternatives(headword: String): List<String> {
        val base = headword
            .replace(Regex("\\(.*?\\)|（.*?）"), " ")
            .replace(Regex("[～~]\\s*ing\\b"), " ～ ")      // look forward to ～ing の ing は語ではない
            .replace(Regex("([a-z])([AB])\\b"), "$1 $2")  // toA → to A
        val out = mutableListOf<String>()
        fun expand(s: String, depth: Int) {
            val m = Regex("(\\S*?)\\s*\\[([^\\]]*)]").find(s)
            if (m == null || depth > 4) { out += s; return }
            val before = s.substring(0, m.range.first)
            val after = s.substring(m.range.last + 1)
            val prefix = m.groupValues[1]
            val options = m.groupValues[2].split(',', '、', '/', '／').map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("etc") && !it.startsWith("the-") && it != "the" }
            expand(before + prefix + " " + after, depth + 1)
            for (o in options) expand(before + o + " " + after, depth + 1)
        }
        // かっこの外の / だけで分ける（catch[get/have] a glimpse of の / は分けない）
        val alts = mutableListOf<String>()
        var depth = 0
        val cur = StringBuilder()
        for (ch in base) {
            when {
                ch == '[' -> { depth++; cur.append(ch) }
                ch == ']' -> { depth--; cur.append(ch) }
                (ch == '/' || ch == '／') && depth == 0 -> { alts += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        alts += cur.toString()
        for (alt in alts) {
            val t = alt.trim()
            if (Regex("^[A-Za-z]+(-[A-Za-z]+){2,}$").matches(t)) out += t.split('-')   // 活用の並び
            else expand(t, 0)
        }
        return out.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.distinct()
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
        "stick" to "stuck", "hear" to "heard", "understand" to "understood", "arise" to "arose arisen", "become" to "became",
        "would" to "i'd you'd he'd she'd we'd they'd", "had" to "i'd you'd he'd she'd we'd they'd", "will" to "i'll you'll he'll she'll we'll they'll it'll",
    ).mapValues { it.value.split(' ').toSet() }

    /** トークンが見出し語の 1 語（活用形を含む）に当たるか。 */
    fun matchesKey(token: Token, key: String): Boolean =
        (key.startsWith("-") && token.lower.length > key.length && token.lower.endsWith(key.drop(1))) ||
        (!key.startsWith("-") && token.lower.startsWith(stem(key))) ||
        // fly → flies, carry → carried
        (key.length >= 3 && key.endsWith("y") && token.lower.startsWith(key.dropLast(1) + "i")) || IRREGULAR[key]?.contains(token.lower) == true ||
            // "～-year-old" "～-fold" のような後ろに付く語（five-year-old, three-fold）
            (key.contains('-') && token.lower.contains(key)) || token.lower.endsWith("-$key")

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
        return token.lower == w || (w.length >= 4 && matchesKey(token, w)) || IRREGULAR[w]?.contains(token.lower) == true
    }

    /**
     * 穴埋め・強調表示用：見出し語の熟語全体の位置を返す。
     * 内容語（once, all など）だけでなく、その間と前後にある and / for / at / of / to などの
     * 小さな語も、見出し語に含まれていれば一緒に含める。
     *   "once and for all" → once, and, for, all
     *   "at any moment"    → at, any, moment
     */
    fun findPhrase(headword: String, sentence: List<Token>): List<Int>? {
        findPattern(headword, sentence)?.let { return it }
        findPhraseExact(headword, sentence)?.let { return it }
        // 最後の手段：1 語の見出し語が合成語の後ろに付いている（ache → headache）
        val single = wordsOf(alternatives(headword).firstOrNull() ?: return null)
        if (single.size == 1 && single[0].length >= 4 && !single[0].startsWith("-")) {
            val w = single[0].lowercase()
            sentence.indices.firstOrNull { sentence[it].lower.length >= w.length + 3 && sentence[it].lower.endsWith(w) }
                ?.let { return listOf(it) }
        }
        return null
    }

    /** 語ではなく「型」を表す見出し語（the 比較級 …, the 比較級 … / as ～ as）。 */
    private fun findPattern(headword: String, s: List<Token>): List<Int>? {
        fun comparative(t: Token) = t.lower == "more" || t.lower == "less" || (t.lower.length > 4 && t.lower.endsWith("er"))
        if (headword.contains("比較級")) {
            val firsts = s.indices.filter { it + 1 < s.size && s[it].lower == "the" && comparative(s[it + 1]) }
            if (firsts.size >= 2) return firsts.take(2).flatMap { listOf(it, it + 1) }
            return null
        }
        if (Regex("^\\s*as\\s*[～~]\\s*as").containsMatchIn(headword)) {
            val i = (0 until s.size - 2).firstOrNull { s[it].lower == "as" && s[it + 2].lower == "as" } ?: return null
            return listOf(i, i + 1, i + 2)
        }
        return null
    }

    private fun findPhraseExact(headword: String, sentence: List<Token>): List<Int>? {
        for (alt in alternatives(headword)) {
            val words = wordsOf(alt)
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
