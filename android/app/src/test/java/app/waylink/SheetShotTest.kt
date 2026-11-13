package app.waylink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.waylink.core.AppState
import app.waylink.core.Sample
import app.waylink.core.freshState
import app.waylink.data.AppViewModel
import app.waylink.data.Live
import app.waylink.ui.components.Sheet
import app.waylink.ui.components.grain
import app.waylink.ui.fuel.fuelSheet
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.logbook.logbookSheet
import app.waylink.ui.rides.rideSheet
import app.waylink.ui.shell.ExtraHost
import app.waylink.ui.shell.SettingsScreen
import app.waylink.ui.shell.extraSheet
import app.waylink.ui.shell.settingsSheet
import app.waylink.ui.theme.W
import app.waylink.ui.theme.WaylinkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/** Open sheets over Settings, at 390 × 844 like a phone, for side-by-side checks with web-v0's sheets. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
class SheetShotTest {
    private val sample: AppState = Sample.seed(freshState()).copy(onboarded = true)
    private val live = Live()

    private inline fun <reified T> noop(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> null } as T

    private fun shot(name: String, build: (AppViewModel, SheetNav) -> SheetSpec?) {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        val nav = noop<SheetNav>()
        val spec = build(vm, nav) ?: error("no sheet for $name")
        captureRoboImage("build/shots/sheet-$name.png") {
            WaylinkTheme("auto", sample.settings.accent) {
                Box(Modifier.fillMaxSize().background(W.c.bg).grain()) {
                    SettingsScreen(sample, live, noop(), 80.dp)
                    Sheet(true, {}, spec.title, spec.sub) { spec.content(this) }
                }
            }
        }
    }

    @Test fun quick() = shot("quick") { vm, nav -> extraSheet("quick", sample, live, vm, nav, noop<ExtraHost>()) }
    @Test fun odo() = shot("odo") { vm, nav -> logbookSheet("odo", sample, vm, nav) }
    @Test fun service() = shot("svc") { vm, nav -> logbookSheet("svc:" + sample.vehicle.service.first().id, sample, vm, nav) }
    @Test fun fuel() = shot("fuel") { vm, nav -> fuelSheet("fuel", sample, vm, nav) }
    @Test fun trip() = shot("trip") { vm, nav -> rideSheet("trip-detail:" + sample.vehicle.trips.first().id, sample, live, vm, nav) }
    @Test fun specs() = shot("specs") { vm, nav -> settingsSheet("specs", sample, live, vm, nav) }
    @Test fun support() = shot("support") { vm, nav -> settingsSheet("support", sample, live, vm, nav) }
    @Test fun backup() = shot("backup") { vm, nav -> settingsSheet("backup", sample, live, vm, nav) }
}
