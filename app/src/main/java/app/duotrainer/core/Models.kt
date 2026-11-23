package app.duotrainer.core

import kotlinx.serialization.Serializable

/** DUO の例文 1 文。id は 1 始まりの通し番号（読み込んだファイルの順）。 */
@Serializable
data class Sentence(
    val id: Int,
    val en: String,
    val ja: String,
    val section: Int,
    /** この例文に含まれる見出し語（Word.id）。穴埋めの空欄に使う。 */
    val wordIds: List<Int> = emptyList(),
)

/** DUO の見出し語・熟語 1 件。 */
@Serializable
data class Word(
    val id: Int,
    val en: String,
    val ja: String,
    val section: Int,
    val sentenceId: Int? = null,
) {
    /** 「【動】～を尊重する」の「動」。 */
    val partOfSpeech: String?
        get() = Regex("^【(.+?)】").find(ja)?.groupValues?.get(1)

    /** 品詞記号を取り除いた意味。 */
    val meaning: String
        get() = ja.replace(Regex("^【.+?】"), "").trim()
}

@Serializable
data class Deck(
    val sentences: List<Sentence>,
    val words: List<Word>,
) {
    val sections: List<Int> get() = sentences.map { it.section }.distinct().sorted()
    fun sentencesIn(section: Int) = sentences.filter { it.section == section }
    fun wordsIn(section: Int) = words.filter { it.section == section }
    fun sentence(id: Int) = sentences.firstOrNull { it.id == id }
    fun word(id: Int) = words.firstOrNull { it.id == id }
}

/** 答え合わせ後の 3 段階評価。 */
@Serializable
enum class Rating(val label: String) {
    WEAK("苦手"),
    GOOD("得意"),
    MASTERED("覚えた"),
}

/** 答える前に選ぶ自信度。復習間隔の調整に使う。 */
@Serializable
enum class Confidence(val label: String) {
    LOW("自信なし"),
    MID("たぶん"),
    HIGH("自信あり"),
}

/** 1 項目（例文 or 単語）の学習状態。日付は epochDay。 */
@Serializable
data class Progress(
    val stage: Int = 0,
    val due: Long = 0,
    val last: Rating? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastDay: Long = -1,
    /** 語順パズルを一度やったか（2 回目からは自由英訳に切り替える）。 */
    val puzzleDone: Boolean = false,
)

/** 1 日分の学習ログ。 */
@Serializable
data class DayLog(
    val seconds: Long = 0,
    val answered: Int = 0,
    val correct: Int = 0,
)

enum class StudyMode(val label: String, val description: String) {
    FREE("自由英訳", "日本語を見て英文を書く"),
    CLOZE("穴埋め", "重要語だけを空欄に"),
    PUZZLE("語順パズル", "単語を並べ替えて英文に"),
    DICTATION("ディクテーション", "音声を聞いて書き取る"),
    SHADOWING("シャドーイング", "音声に合わせて声に出す"),
    WORD("単語テスト", "意味から見出し語を選ぶ"),
}

object Keys {
    fun sentence(id: Int) = "s:$id"
    fun word(id: Int) = "w:$id"
}
