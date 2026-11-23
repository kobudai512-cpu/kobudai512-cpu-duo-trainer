package app.duotrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duotrainer.ui.screens.AudioTuneScreen
import app.duotrainer.ui.screens.HomeScreen
import app.duotrainer.ui.screens.OnboardingScreen
import app.duotrainer.ui.screens.ResultScreen
import app.duotrainer.ui.screens.SectionDetailScreen
import app.duotrainer.ui.screens.SectionsScreen
import app.duotrainer.ui.screens.SettingsScreen
import app.duotrainer.ui.screens.StatsScreen
import app.duotrainer.ui.screens.StudyScreen
import app.duotrainer.ui.theme.DuoTheme

/** 画面遷移（小さなアプリなので自前のスタックで十分）。 */
sealed interface Route {
    data object Home : Route
    data object Sections : Route
    data object Stats : Route
    data object Settings : Route
    data class Section(val section: Int) : Route
    data class AudioTune(val section: Int) : Route
}

private enum class Tab(val route: Route, val label: String, val icon: ImageVector) {
    HOME(Route.Home, "ホーム", Icons.Rounded.Home),
    BOOK(Route.Sections, "教材", Icons.AutoMirrored.Rounded.MenuBook),
    STATS(Route.Stats, "記録", Icons.Rounded.BarChart),
    SETTINGS(Route.Settings, "設定", Icons.Rounded.Settings),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DuoTheme { App(vm) } }
    }
}

@Composable
fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.toast(null) }
    }

    fun go(r: Route) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun switchTab(r: Route) { stack.clear(); stack.add(r) }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    state.busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            icon = { CircularProgressIndicator() },
            text = { Text(msg) },
        )
    }

    // 教材が未読み込みなら最初の設定画面
    if (state.deck == null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(pad)) { OnboardingScreen(state, vm) }
        }
        return
    }

    // 学習中は全画面
    val session = state.session
    if (session != null) {
        BackHandler { vm.endSession() }
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(pad)) {
                if (session.finished) ResultScreen(state, vm, onDone = { vm.endSession() })
                else StudyScreen(state, vm)
            }
        }
        return
    }

    val route = stack.last()
    BackHandler(enabled = stack.size > 1) { back() }
    val isTab = Tab.entries.any { it.route == route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { switchTab(t.route) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "route") { r ->
                when (r) {
                    Route.Home -> HomeScreen(state, vm, open = ::go)
                    Route.Sections -> SectionsScreen(state, vm, open = ::go)
                    Route.Stats -> StatsScreen(state, vm)
                    Route.Settings -> SettingsScreen(state, vm)
                    is Route.Section -> SectionDetailScreen(r.section, state, vm, open = ::go, back = ::back)
                    is Route.AudioTune -> AudioTuneScreen(r.section, state, vm, back = ::back)
                }
            }
        }
    }
}
