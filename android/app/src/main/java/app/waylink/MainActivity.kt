package app.waylink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.waylink.data.AppViewModel
import app.waylink.ui.shell.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Status and navigation bar icons follow the app's theme, not just the phone's.
            val s by vm.state.collectAsStateWithLifecycle()
            val theme = s.settings.theme
            LaunchedEffect(theme) {
                val style = when (theme) {
                    "sketch" -> SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    "sketchdark" -> SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else -> SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppRoot(vm)
        }
    }
}
