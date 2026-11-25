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
    /** 対応付けの方式の版。上がったら読み込み時に作り直す。 */
    val version: Int = 0,
) {
    /** 保存済みの本文から、新しい方式で対応付けをやり直す（再インポート不要）。 */
    fun realigned(): Deck = Aligner.align(
        sentences.map { DeckParser.RawSentence(it.en, it.ja) },
        words.map { DeckParser.RawWord(it.en, it.ja, it.section) },
    )

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
    /** 言えたが、機能語や細かい所を間違えた／かなり詰まった */
    HARD("難しい"),
    GOOD("正解"),
    MASTERED("簡単"),
}

/** 答える前に選ぶ自信度。復習間隔の調整に使う。 */
@Serializable
enum class Confidence(val label: String) {
    LOW("わからない"),
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
    /** 旧バージョンとの互換のため残している（未使用）。 */
    val puzzleDone: Boolean = false,
    /** 前回の復習間隔（日）。0 = まだ間隔を空けた復習をしていない。 */
    val interval: Int = 0,
    /** 覚えやすさ（間隔を何倍ずつ伸ばすか）。苦手にすると下がる。 */
    val ease: Float = Srs.START_EASE,
)

/** 1 日分の学習ログ。 */
@Serializable
data class DayLog(
    val seconds: Long = 0,
    val answered: Int = 0,
    val correct: Int = 0,
)

enum class StudyMode(val label: String, val description: String) {
    FREE("和文英訳（入力あり）", "英文を書いて答え合わせ"),
    SELF("和文英訳（入力なし）", "頭の中で英訳して自分で判定"),
    CLOZE("穴埋め", "重要語だけを空欄に"),
    PUZZLE("語順パズル", "単語を並べ替えて英文に"),
    DICTATION("ディクテーション", "音声を聞いて書き取る"),
    SHADOWING("シャドーイング", "音声に合わせて声に出す"),
    WORD("単語テスト", "意味から英単語を選ぶ"),
    ;

    /** 和文英訳だけが例文の仕分け（苦手・正解・簡単）に入る本番。ほかのモードは練習。 */
    val isTranslation: Boolean get() = this == FREE || this == SELF
}

object Keys {
    fun sentence(id: Int) = "s:$id"
    fun word(id: Int) = "w:$id"
    /** 和文英訳以外の練習モードの記録（例文の「覚えた」には影響しない）。 */
    fun practice(mode: StudyMode, id: Int) = "p:${mode.name}:$id"

    fun forSentence(mode: StudyMode, id: Int) = if (mode.isTranslation) sentence(id) else practice(mode, id)
}
