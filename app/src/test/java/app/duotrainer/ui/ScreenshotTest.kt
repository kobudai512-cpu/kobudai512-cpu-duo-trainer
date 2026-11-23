package app.duotrainer.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.duotrainer.App
import app.duotrainer.AppViewModel
import app.duotrainer.Session
import app.duotrainer.UiState
import app.duotrainer.core.DayLog
import app.duotrainer.core.DeckParser
import app.duotrainer.core.Keys
import app.duotrainer.core.Progress
import app.duotrainer.core.Question
import app.duotrainer.core.Rating
import app.duotrainer.core.Scope
import app.duotrainer.core.StudyMode
import app.duotrainer.data.UserData
import app.duotrainer.ui.screens.SectionDetailScreen
import app.duotrainer.ui.screens.SectionsScreen
import app.duotrainer.ui.screens.StatsScreen
import app.duotrainer.ui.screens.StudyScreen
import app.duotrainer.ui.theme.DuoTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 画面のスクリーンショット（docs/screenshots に保存）。
 * DUO 本体ではなく、自作のサンプル例文で描画する。
 *   ./gradlew recordRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val sample = buildString {
        appendLine("#separator:tab")
        appendLine("#tags column:4")
        val words = listOf(
            Triple("borrow", "【動】～を借りる", 1), Triple("umbrella", "【名】傘", 1),
            Triple("look forward to ～", "【動】～を楽しみに待つ", 1), Triple("trip", "【名】旅行", 1),
            Triple("give up", "【動】あきらめる", 2), Triple("dream", "【名】夢", 2),
            Triple("decide", "【動】～を決める", 2), Triple("abroad", "【副】海外で", 2),
            Triple("forecast", "【名】予報", 3), Triple("cancel", "【動】～を中止する", 3),
            Triple("heavy", "【形】(雨が)激しい", 3),
        )
        words.forEach { (e, j, s) -> appendLine("$e\t$j\t\t$s SECTION") }
        listOf(
            "May I borrow your umbrella? It looks like rain." to "傘を借りてもいい？雨が降りそうだ。",
            "I'm really looking forward to the school trip." to "修学旅行を本当に楽しみにしている。",
            "Never give up on your dreams, no matter what." to "何があっても夢をあきらめるな。",
            "She decided to study abroad next year." to "彼女は来年留学することに決めた。",
            "The forecast says heavy rain, so the game was cancelled." to "予報では大雨なので、試合は中止になった。",
        ).forEach { (e, j) -> appendLine("$e\t$j\t\t") }
    }

    private fun vm(session: Session? = null): AppViewModel {
        val deck = DeckParser.parse(sample)
        val today = LocalDate.now().toEpochDay()
        val progress = mapOf(
            Keys.sentence(1) to Progress(stage = 3, due = today + 7, last = Rating.MASTERED, reps = 4, lastDay = today - 1, puzzleDone = true),
            Keys.sentence(2) to Progress(stage = 1, due = today, last = Rating.GOOD, reps = 2, lastDay = today - 3, puzzleDone = true),
            Keys.sentence(3) to Progress(stage = 0, due = today, last = Rating.WEAK, reps = 1, lastDay = today - 1, puzzleDone = true),
        )
        val days = (0..9).associate { d ->
            (today - d).toString() to DayLog(seconds = (300L + d * 97 % 900), answered = 12 + d * 3 % 17, correct = 9 + d * 2 % 11)
        }
        val user = UserData(progress = progress, days = days, lastSection = 1)
        return AppViewModel(ApplicationProvider.getApplicationContext()).also {
            it.replaceStateForTest(UiState(loading = false, deck = deck, user = user, session = session))
        }
    }

    @Test fun home() {
        val vm = vm()
        compose.setContent { DuoTheme { App(vm) } }
        compose.onRoot().captureRoboImage("../docs/screenshots/home.png")
    }

    @Test fun sections() {
        val vm = vm()
        compose.setContent { DuoTheme { SectionsScreen(vm.state.value, vm) {} } }
        compose.onRoot().captureRoboImage("../docs/screenshots/sections.png")
    }

    @Test fun sectionDetail() {
        val vm = vm()
        compose.setContent { DuoTheme { SectionDetailScreen(1, vm.state.value, vm, {}, {}) } }
        compose.onRoot().captureRoboImage("../docs/screenshots/section.png")
    }

    @Test fun stats() {
        val vm = vm()
        compose.setContent { DuoTheme { StatsScreen(vm.state.value, vm) } }
        compose.onRoot().captureRoboImage("../docs/screenshots/stats.png")
    }

    private fun session(mode: StudyMode, id: Int): Session {
        val deck = DeckParser.parse(sample)
        val qs = listOf(Question(Keys.sentence(id), mode, sentence = deck.sentence(id)!!), Question(Keys.sentence(5), mode, sentence = deck.sentence(5)!!))
        return Session("今日の学習", qs, scope = Scope.Today, mode = mode)
    }

    @Test fun freeQuestion() {
        val vm = vm(session(StudyMode.FREE, 3))
        compose.setContent { DuoTheme { StudyScreen(vm.state.value, vm) } }
        compose.onNode(hasSetTextAction()).performTextInput("Never give up your dream no matter what")
        compose.onNodeWithText("たぶん").performClick()
        compose.onRoot().captureRoboImage("../docs/screenshots/free_question.png")
        compose.onNodeWithText("答え合わせ").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/free_answer.png")
    }

    @Test fun cloze() {
        val vm = vm(session(StudyMode.CLOZE, 1))
        compose.setContent { DuoTheme { StudyScreen(vm.state.value, vm) } }
        compose.onRoot().captureRoboImage("../docs/screenshots/cloze.png")
    }

    @Test fun puzzle() {
        val vm = vm(session(StudyMode.PUZZLE, 4))
        compose.setContent { DuoTheme { StudyScreen(vm.state.value, vm) } }
        compose.onNodeWithText("She").performClick()
        compose.onNodeWithText("decided").performClick()
        compose.onRoot().captureRoboImage("../docs/screenshots/puzzle.png")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())
}
