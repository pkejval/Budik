package io.github.pkejval.budik.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.Alarm
import io.github.pkejval.budik.ui.RingingScreen
import io.github.pkejval.budik.ui.ringingColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        // The alarm can only be stopped by the chosen dismiss gesture.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        lifecycleScope.launch {
            // The service publishes the alarm right after loading it; close if nothing rings.
            withTimeoutOrNull(5_000) { AlarmService.ringing.first { it != null } } ?: return@launch finish()
            AlarmService.ringing.first { it == null }
            finish()
        }

        val appearance = app.settings.ringingAppearance
        setContent {
            var alarm by remember { mutableStateOf<Alarm?>(null) }
            LaunchedEffect(Unit) { AlarmService.ringing.collect { if (it != null) alarm = it } }
            val current = alarm
            if (current != null) {
                RingingScreen(
                    label = current.label,
                    dismissMethod = current.dismissMethod,
                    appearance = appearance,
                    onDismiss = { startService(AlarmService.dismissIntent(this)) },
                )
            } else {
                Box(Modifier.fillMaxSize().background(ringingColors(appearance.style).top))
            }
        }
    }
}
