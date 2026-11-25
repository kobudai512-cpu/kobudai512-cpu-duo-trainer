package app.duotrainer.data

import android.content.Context
import android.net.Uri
import app.duotrainer.core.DayLog
import app.duotrainer.core.Deck
import app.duotrainer.core.DeckParser
import app.duotrainer.core.Progress
import app.duotrainer.core.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class AudioSource(val label: String) {
    CD("CD 音声（自動で 1 文ずつ分割）"),
    TTS("端末の読み上げ（TTS）"),
}

@Serializable
data class Settings(
    val newPerDay: Int = 10,
    val sessionSize: Int = 20,
    val audioSource: AudioSource = AudioSource.CD,
    val speed: Float = 1.0f,
    val autoPlay: Boolean = true,
    val interleaveHint: Boolean = true,
    val dailyGoal: Int = 20,
)

/** セクション音声の分割設定と結果。 */
@Serializable
data class SectionAudio(
    /** 先頭で飛ばす発話の数（各トラックの最初の「Section N」の読み上げ）。 */
    val skip: Int = 1,
    val sensitivity: Float = 0.5f,
    /** 文の終わりを延ばす長さ（ms）。 */
    val tailMs: Int = 700,
    /** 分割方法を変えたら上げる。古い分割結果は作り直す。 */
    val version: Int = CURRENT_VERSION,
    val segments: List<Segment> = emptyList(),
) {
    companion object { const val CURRENT_VERSION = 2 }
}

/** 端末に保存するユーザーデータ（進捗・ログ・設定）。DUO 本文は含まない。 */
@Serializable
data class UserData(
    val progress: Map<String, Progress> = emptyMap(),
    /** key = epochDay の文字列 */
    val days: Map<String, DayLog> = emptyMap(),
    val settings: Settings = Settings(),
    val audioTreeUri: String? = null,
    /** key = セクション番号 → トラックのファイル URI */
    val tracks: Map<String, String> = emptyMap(),
    val sectionAudio: Map<String, SectionAudio> = emptyMap(),
    val lastSection: Int = 1,
    /** ★ブックマークした例文の番号 */
    val bookmarks: List<Int> = emptyList(),
    /** 最後に使った和文英訳のやり方（入力 / 判定）。 */
    val translationMode: app.duotrainer.core.StudyMode = app.duotrainer.core.StudyMode.SELF,
) {
    fun dayLogs(): Map<Long, DayLog> = days.mapKeys { it.key.toLong() }
}

class Store(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val deckFile get() = File(context.filesDir, "deck.json")
    private val userFile get() = File(context.filesDir, "user.json")

    suspend fun loadDeck(): Deck? = withContext(Dispatchers.IO) {
        val deck = runCatching { json.decodeFromString<Deck>(deckFile.readText()) }.getOrNull() ?: return@withContext null
        if (deck.version >= app.duotrainer.core.Aligner.VERSION) return@withContext deck
        // 対応付けの方式が新しくなったので作り直して保存（id はファイルの順なので学習記録はそのまま）
        val fresh = withContext(Dispatchers.Default) { deck.realigned() }
        runCatching { deckFile.writeText(json.encodeToString(Deck.serializer(), fresh)) }
        fresh
    }

    suspend fun loadUser(): UserData = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<UserData>(userFile.readText()) }.getOrElse { UserData() }
    }

    suspend fun saveUser(user: UserData) = withContext(Dispatchers.IO) {
        val tmp = File(context.filesDir, "user.json.tmp")
        tmp.writeText(json.encodeToString(UserData.serializer(), user))
        tmp.renameTo(userFile)
    }

    /** Anki のテキストを読み込んで、解析済みの教材として保存する。 */
    suspend fun importDeck(uri: Uri): Deck = withContext(Dispatchers.Default) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw DeckParser.ParseException("ファイルを開けませんでした。")
        val deck = DeckParser.parse(text.removePrefix("﻿"))
        withContext(Dispatchers.IO) { deckFile.writeText(json.encodeToString(Deck.serializer(), deck)) }
        deck
    }

    suspend fun exportBackup(uri: Uri, user: UserData) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(json.encodeToString(UserData.serializer(), user.copy(audioTreeUri = null, tracks = emptyMap())).toByteArray())
        }
    }

    suspend fun readBackup(uri: Uri): UserData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("ファイルを開けませんでした。")
        json.decodeFromString<UserData>(text)
    }
}
