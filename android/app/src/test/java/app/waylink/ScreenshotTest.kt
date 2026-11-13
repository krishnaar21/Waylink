package app.waylink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.waylink.core.AppState
import app.waylink.core.Sample
import app.waylink.core.freshState
import app.waylink.data.Live
import app.waylink.ui.alerts.AlertsScreen
import app.waylink.ui.components.grain
import app.waylink.ui.fuel.FuelScreen
import app.waylink.ui.logbook.LogbookScreen
import app.waylink.ui.rides.RidesScreen
import app.waylink.ui.route.RouteScreen
import app.waylink.ui.shell.ProfileScreen
import app.waylink.ui.shell.SettingsScreen
import app.waylink.ui.shell.Tab
import app.waylink.ui.shell.TabBar
import app.waylink.ui.theme.W
import app.waylink.ui.theme.WaylinkTheme
import app.waylink.ui.today.TodayScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/**
 * Renders each screen with the sample data at the web app's 390 dp phone width (1 px per dp), so the
 * pictures can be laid next to web-v0 screenshots. Run: ./gradlew :app:recordRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h1900dp-mdpi")
class ScreenshotTest {
    private val sample: AppState = Sample.seed(freshState()).copy(onboarded = true)
    private val live = Live()

    /** Every action does nothing: these are pictures. */
    private inline fun <reified T> noop(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> null } as T

    private fun shot(name: String, theme: String = "auto", tab: Tab? = null, content: @Composable () -> Unit) =
        captureRoboImage("build/shots/$name.png") {
            WaylinkTheme(theme, sample.settings.accent) {
                Box(Modifier.fillMaxSize().background(W.c.bg).grain()) {
                    content()
                    if (tab != null) TabBar(tab, sample, live, Modifier.align(Alignment.BottomCenter)) {}
                }
            }
        }

    @Test fun today() = shot("today", tab = Tab.TODAY) { TodayScreen(sample, live, noop(), 80.dp) }
    @Test fun todayDark() = shot("today-dark", "sketchdark", Tab.TODAY) { TodayScreen(sample, live, noop(), 80.dp) }
    @Test fun logbook() = shot("logbook", tab = Tab.TODAY) { LogbookScreen(sample, noop(), 80.dp) }
    @Test fun fuel() = shot("fuel", tab = Tab.TODAY) { FuelScreen(sample, noop(), 80.dp) }
    @Test fun trips() = shot("trips", tab = Tab.RIDES) { RidesScreen(sample, live, noop(), 80.dp) }
    @Test fun route() = shot("route", tab = Tab.ROUTE) { RouteScreen(sample, live, noop(), {}, 80.dp) }
    @Test fun alerts() = shot("alerts", tab = Tab.ALERTS) { AlertsScreen(sample, live, noop(), 80.dp) }
    @Test fun settings() = shot("settings", tab = Tab.SETTINGS) { SettingsScreen(sample, live, noop(), 80.dp) }
    @Test fun settingsDark() = shot("settings-dark", "sketchdark", Tab.SETTINGS) { SettingsScreen(sample, live, noop(), 80.dp) }
    @Test fun onboarding() = shot("onboarding") { app.waylink.ui.onboarding.Onboarding(freshState(), noop()) }
    @Test fun profile() = shot("profile", tab = Tab.SETTINGS) { ProfileScreen(sample, {}, {}, {}, 80.dp) }
}
