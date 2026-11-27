package ch.hofzeit.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    private val onWatchData = DataClient.OnDataChangedListener {
        lifecycleScope.launch { repository.refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = repository

        setContent {
            val archive by repo.archive.collectAsStateWithLifecycle()
            val running by repo.running.collectAsStateWithLifecycle()
            val watch by repo.watch.collectAsStateWithLifecycle()
            val now by produceState(System.currentTimeMillis()) {
                while (true) {
                    delay(20_000)
                    value = System.currentTimeMillis()
                }
            }
            // Monat als Zahl (Jahr * 12 + Monat), damit er beim Drehen erhalten bleibt.
            var monthIndex by rememberSaveable {
                mutableIntStateOf(YearMonth.now().let { it.year * 12 + it.monthValue - 1 })
            }
            val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
            var busy by remember { mutableStateOf(false) }
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            PhoneTheme {
                PhoneScreen(
                    state = PhoneUiState(archive.sessions, running, watch, now, busy),
                    month = month,
                    onMonth = { monthIndex = it.year * 12 + it.monthValue - 1 },
                    onFetch = {
                        scope.launch {
                            busy = true
                            val watches = repo.requestFromWatch()
                            if (watches > 0) delay(2_500)
                            repo.refresh()
                            busy = false
                            snackbar.showSnackbar(
                                if (watches > 0) "Zeiten von der Uhr geholt"
                                else "Keine Uhr erreichbar. Ist Bluetooth an und die Uhr in der Nähe?"
                            )
                        }
                    },
                    onShare = { shareMonth(this@MainActivity, archive.sessions, month) },
                    onDelete = { repo.delete(it.id) },
                    snackbar = snackbar,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(onWatchData)
        lifecycleScope.launch {
            repository.refresh()
            // Erster Start: die Uhr um alles bitten, was sie schon hat.
            if (repository.archive.value.sessions.isEmpty()) {
                if (repository.requestFromWatch() > 0) {
                    delay(3_000)
                    repository.refresh()
                }
            }
        }
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(onWatchData)
        super.onPause()
    }
}
