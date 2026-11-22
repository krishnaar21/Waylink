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
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            // Status and navigation bar icons follow the app's theme, not just the phone's.
            val s by vm.state.collectAsStateWithLifecycle()
            val theme = app.waylink.ui.theme.resolveTheme(s.settings.theme, s.settings.supporter, androidx.compose.foundation.isSystemInDarkTheme())
            LaunchedEffect(theme) {
                val style = when (theme) {
                    "sketch", "paper" -> SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    "sketchdark", "obsidian", "amoled" -> SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else -> SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // Ads: Google's consent form, then AdMob, only for riders without the no-ads plan.
            val adFree = s.settings.adFree
            LaunchedEffect(adFree) { if (!adFree) app.waylink.ui.components.startAds(this@MainActivity) else app.waylink.ui.components.AdMob.clear() }
            AppRoot(vm)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** The widget's buttons: open a sheet (Park here, SOS) once the app is up. */
    private fun openFrom(i: android.content.Intent?) {
        val key = i?.getStringExtra(app.waylink.data.BikeWidget.EXTRA_OPEN) ?: return
        if (key in setOf("parking:new", "sos")) vm.askFor(key)
    }
}
