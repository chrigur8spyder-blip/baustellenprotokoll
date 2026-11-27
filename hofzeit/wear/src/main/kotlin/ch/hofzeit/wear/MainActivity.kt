package ch.hofzeit.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import ch.hofzeit.wear.ui.HofzeitWearApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val ambient = MutableStateFlow(false)
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val openSignal = MutableStateFlow(0)

    // Handgelenk unten: App bleibt sichtbar (Always-On), wird nur noch jede Minute aufgefrischt.
    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient.value = true
                tick()
            }

            override fun onExitAmbient() {
                ambient.value = false
                tick()
            }

            override fun onUpdateAmbient() = tick()
        },
    )

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) repository.refreshOngoing()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    ambient.first { !it }
                    tick()
                    delay(1_000 - System.currentTimeMillis() % 1_000)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val isAmbient by ambient.collectAsState()
            val nowMs by now.collectAsState()
            val signal by openSignal.collectAsState()
            HofzeitWearApp(repository, nowMs, isAmbient, signal)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openSignal.value++
    }

    private fun tick() {
        now.value = System.currentTimeMillis()
    }
}
