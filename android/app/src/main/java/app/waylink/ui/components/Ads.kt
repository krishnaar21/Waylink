package app.waylink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/**
 * Placeholder ads, so the placements can be judged on a phone before any ad SDK goes in. The advertisers are
 * made up. Each slot has its own Developer switch, and Backers and Patrons never see them. Never on ride,
 * navigation, SOS or call screens, and never full screen.
 */
object DummyAds {
    data class Ad(val icon: String, val title: String, val line: String, val cta: String)
    private val ADS = listOf(
        Ad("bike", "Ridgeline Helmets", "Light, ISI-certified lids for the daily ride", "Shop"),
        Ad("chain", "SlickLink Chain Lube", "Quieter chain, fewer adjustments", "Learn more"),
        Ad("wrench", "TorqueBay Doorstep Service", "A mechanic at your gate on Sunday", "Book"),
        Ad("fuel", "Milestone Fuel Card", "Cashback on every fill-up", "Apply"),
    )
    fun pick(slot: Int) = ADS[Math.floorMod(slot, ADS.size)]
    fun rides(s: AppState) = s.settings.adRides && !s.settings.adFree
    /** The live placement: near the top of Today, Rides, Fuel and Service, for everyone without the no-ads plan. */
    fun top(s: AppState) = !s.settings.adFree && !s.settings.hideAds
    /** Opens Support with Backer (the no-ads plan) picked. */
    const val REMOVE = "support:1"
    fun lists(s: AppState) = s.settings.adLists && !s.settings.adFree
}

@Composable
private fun AdTag() {
    val c = W.c
    Box(Modifier.clip(RoundedCornerShape(5.dp)).border(1.dp, c.ink4, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Txt("Ad", T.chip.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold), c.ink3)
    }
}

/**
 * Where ad content comes from. [SampleAds] (made-up advertisers) is the only source today; a real network's native
 * ad view drops in here later without touching the screens.
 */
interface AdSource {
    @Composable fun Card(slot: Int, modifier: Modifier, onRemove: (() -> Unit)?)
}

object SampleAds : AdSource {
    @Composable override fun Card(slot: Int, modifier: Modifier, onRemove: (() -> Unit)?) = SampleBanner(slot, modifier, onRemove)
}

/** The source the app uses. */
var adSource: AdSource = SampleAds

/** An ad card near the top of a screen, from [adSource]. */
@Composable
fun AdBanner(slot: Int, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) = adSource.Card(slot, modifier, onRemove)

/**
 * A native ad card that sits near the top of a screen, in the same card style as everything else. A quiet
 * "Remove ads" link under it opens Support with Backer picked; [onRemove] null hides the link.
 */
@Composable
private fun SampleBanner(slot: Int, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    val c = W.c
    val ad = DummyAds.pick(slot)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 14.dp, vertical = 12.dp).semantics { contentDescription = "Advertisement: ${ad.title}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.inkA(0.06f)), contentAlignment = Alignment.Center) { Ic(ad.icon, size = 22.dp, tint = c.ink2) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdTag()
                    Txt(ad.title, T.rowTitle, c.ink, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                Txt(ad.line, T.rowSub, c.ink3, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(c.inkA(0.07f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                Txt(ad.cta, T.chip.copy(fontWeight = FontWeight.SemiBold), c.ink2, maxLines = 1)
            }
        }
        if (onRemove != null) Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.End) {
            Txt("Remove ads", T.rowSub.copy(fontWeight = FontWeight.Medium), c.ink3,
                Modifier.clip(RoundedCornerShape(8.dp)).pressable(label = "Remove ads with Backer", onClick = onRemove).padding(horizontal = 6.dp, vertical = 4.dp))
        }
    }
}

/** A row that sits inside a list card, between entries (Fuel and Service history). [start] matches the list's padding. */
@Composable
fun AdRow(slot: Int, start: androidx.compose.ui.unit.Dp = 20.dp) {
    val c = W.c
    val ad = DummyAds.pick(slot)
    Row(
        Modifier.fillMaxWidth().background(c.inkA(0.025f)).padding(start = start, end = 16.dp, top = 14.dp, bottom = 14.dp)
            .semantics { contentDescription = "Advertisement: ${ad.title}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.inkA(0.06f)), contentAlignment = Alignment.Center) { Ic(ad.icon, size = 18.dp, tint = c.ink2) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(ad.title, T.row.copy(fontWeight = FontWeight.Medium), c.ink, maxLines = 1)
            Txt(ad.line, T.rowSub, c.ink3, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        AdTag()
    }
}
