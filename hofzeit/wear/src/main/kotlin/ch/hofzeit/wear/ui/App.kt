package ch.hofzeit.wear.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.input.RemoteInputIntentHelper
import ch.hofzeit.core.Format
import ch.hofzeit.core.current
import ch.hofzeit.core.isRunning
import ch.hofzeit.core.totals
import ch.hofzeit.core.workMs
import ch.hofzeit.wear.data.Repository

private object Routes {
    const val HOME = "home"
    const val RUNNING = "running"
    const val SWITCH = "switch"
    const val END = "end"
    const val DONE = "done/{id}"
    const val SUMMARY = "summary/{id}"
    const val TODAY = "today"
    const val HISTORY = "history"
    const val TASKS = "tasks"
    const val TASK = "task/{id}"
}

private const val INPUT_KEY = "name"

@Composable
fun HofzeitWearApp(repo: Repository, now: Long, ambient: Boolean, openSignal: Int) {
    val state by repo.state.collectAsStateWithLifecycle()
    val history by repo.history.collectAsStateWithLifecycle()
    val nav = rememberSwipeDismissableNavController()
    val context = LocalContext.current

    val today = Format.localDate(now)
    val todayFinished = remember(history, today) { history.filter { Format.localDate(it.start) == today } }
    val todaySessions = todayFinished + listOfNotNull(state.running)
    val todayWork = todaySessions.workMs(now)

    val addTask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(INPUT_KEY)?.toString()
        if (!text.isNullOrBlank()) repo.addTask(text)
    }

    // Beim Öffnen (auch über das Symbol auf dem Zifferblatt) direkt zur laufenden Arbeit.
    LaunchedEffect(openSignal) {
        val running = repo.state.value.running
        if (running != null && running.isRunning && nav.currentDestination?.route != Routes.RUNNING) {
            nav.navigate(Routes.RUNNING) { launchSingleTop = true }
        }
    }

    CompositionLocalProvider(LocalNow provides now) {
        HofzeitTheme {
            Box(Modifier.fillMaxSize()) {
                SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            tasks = state.tasks,
                            running = state.running,
                            todayWorkMs = todayWork,
                            todayByTask = todaySessions.totals(now).associate { it.taskId to it.ms },
                            onTask = { task ->
                                context.buzz()
                                repo.start(task)
                                nav.navigate(Routes.RUNNING) { launchSingleTop = true }
                            },
                            onOpenRunning = { nav.navigate(Routes.RUNNING) { launchSingleTop = true } },
                            onToday = { nav.navigate(Routes.TODAY) },
                            onHistory = { nav.navigate(Routes.HISTORY) },
                            onTasks = { nav.navigate(Routes.TASKS) },
                        )
                    }
                    composable(Routes.RUNNING) {
                        val running = state.running
                        if (running == null || !running.isRunning) {
                            LaunchedEffect(Unit) { nav.popBackStack(Routes.HOME, inclusive = false) }
                        } else {
                            RunningScreen(
                                session = running,
                                todayWorkMs = todayWork,
                                onPause = { context.buzz(); repo.pause() },
                                onResume = { context.buzz(); repo.resume() },
                                onSwitch = { nav.navigate(Routes.SWITCH) },
                                onEnd = { nav.navigate(Routes.END) },
                            )
                        }
                    }
                    composable(Routes.SWITCH) {
                        SwitchScreen(state.tasks, state.running?.current?.taskId) { task ->
                            context.buzz()
                            repo.start(task)
                            nav.popBackStack()
                        }
                    }
                    composable(Routes.END) {
                        ConfirmEndScreen(
                            workMs = state.running?.workMs(now) ?: 0,
                            onConfirm = {
                                context.buzz()
                                val done = repo.finish()
                                if (done != null) {
                                    nav.navigate("done/${done.id}") { popUpTo(Routes.HOME) }
                                } else {
                                    nav.popBackStack(Routes.HOME, inclusive = false)
                                }
                            },
                            onCancel = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.DONE) { entry ->
                        val id = entry.arguments?.getString("id")
                        val session = history.find { it.id == id }
                        if (session == null) {
                            LaunchedEffect(Unit) { nav.popBackStack(Routes.HOME, inclusive = false) }
                        } else {
                            SummaryScreen(
                                session = session,
                                justFinished = true,
                                onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
                                onDelete = {},
                            )
                        }
                    }
                    composable(Routes.SUMMARY) { entry ->
                        val id = entry.arguments?.getString("id")
                        val session = history.find { it.id == id }
                        if (session == null) {
                            LaunchedEffect(Unit) { nav.popBackStack() }
                        } else {
                            SummaryScreen(
                                session = session,
                                justFinished = false,
                                onDone = { nav.popBackStack() },
                                onDelete = {
                                    repo.deleteSession(session.id)
                                    nav.popBackStack()
                                },
                            )
                        }
                    }
                    composable(Routes.TODAY) { TodayScreen(todaySessions) }
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            history = history,
                            today = today,
                            onOpen = { nav.navigate("summary/${it.id}") },
                            onResend = { repo.resendAll() },
                        )
                    }
                    composable(Routes.TASKS) {
                        TasksScreen(
                            tasks = state.tasks,
                            onOpen = { nav.navigate("task/${it.id}") },
                            onAdd = {
                                val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                                val input = RemoteInput.Builder(INPUT_KEY).setLabel("Neue Tätigkeit").build()
                                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
                                addTask.launch(intent)
                            },
                            onRestore = { repo.restoreDefaults() },
                        )
                    }
                    composable(Routes.TASK) { entry ->
                        val id = entry.arguments?.getString("id")
                        val work = state.tasks.filter { !it.isPause }
                        val task = work.find { it.id == id }
                        if (task == null) {
                            LaunchedEffect(Unit) { nav.popBackStack() }
                        } else {
                            TaskDetailScreen(
                                task = task,
                                isFirst = work.firstOrNull()?.id == task.id,
                                onMoveTop = {
                                    repo.moveTaskToTop(task.id)
                                    nav.popBackStack()
                                },
                                onRemove = {
                                    repo.removeTask(task.id)
                                    nav.popBackStack()
                                },
                            )
                        }
                    }
                }
                if (ambient) AmbientScreen(state.running, now)
            }
        }
    }
}
