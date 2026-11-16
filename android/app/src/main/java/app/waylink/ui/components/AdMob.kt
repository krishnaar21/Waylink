package app.waylink.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import app.waylink.BuildConfig
import app.waylink.ui.theme.W
import app.waylink.ui.theme.WaylinkColors
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google AdMob native ads for the top slots. The ad unit and app id come from the build (Google's test ids until
 * publish, see app/build.gradle.kts). Google's consent form (UMP) runs first; ads are only requested once it says
 * they may be. Backers and Patrons never get here: the slots are not drawn and [consentAndStart] is not called.
 */
object AdMob {
    private val started = AtomicBoolean(false)
    private var appContext: Context? = null
    private val _ads = MutableStateFlow<Map<Int, NativeAd>>(emptyMap())
    val ads: StateFlow<Map<Int, NativeAd>> = _ads.asStateFlow()
    private val loading = mutableSetOf<Int>()

    /** Shows Google's consent form when the rider's region needs one, then starts the SDK if ads are allowed. */
    fun consentAndStart(activity: Activity) {
        if (Build.FINGERPRINT == "robolectric" || started.get()) return
        runCatching {
            val info = UserMessagingPlatform.getConsentInformation(activity)
            info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { if (info.canRequestAds()) start(activity) }
            }, { if (info.canRequestAds()) start(activity) })
            // A rider who answered before can see ads straight away, without waiting for the update.
            if (info.canRequestAds()) start(activity)
        }
    }

    private fun start(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        appContext = ctx.applicationContext
        runCatching { MobileAds.initialize(ctx.applicationContext) {} }
    }

    /** Asks for one native ad for [slot], once; the card appears when it arrives and stays hidden if none does. */
    fun load(slot: Int) {
        val ctx = appContext ?: return
        if (slot in loading || _ads.value.containsKey(slot)) return
        loading += slot
        runCatching {
            AdLoader.Builder(ctx, BuildConfig.ADMOB_NATIVE)
                .forNativeAd { ad -> loading -= slot; _ads.value[slot]?.destroy(); _ads.value = _ads.value + (slot to ad) }
                .withAdListener(object : AdListener() { override fun onAdFailedToLoad(e: LoadAdError) { loading -= slot } })
                .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
                .build()
                .loadAd(AdRequest.Builder().build())
        }.onFailure { loading -= slot }
    }

    /** Frees every ad, for when a rider buys the no-ads plan. */
    fun clear() { _ads.value.values.forEach { it.destroy() }; _ads.value = emptyMap() }
}

/** The live ad source: an AdMob native ad laid out like Waylink's own cards, with the "Remove ads" link under it. */
object AdMobSource : AdSource {
    @Composable
    override fun Card(slot: Int, modifier: Modifier, onRemove: (() -> Unit)?) {
        LaunchedEffect(slot) { AdMob.load(slot) }
        val ads by AdMob.ads.collectAsState()
        val ad = ads[slot]
        val c = W.c
        if (ad != null) Column(modifier.fillMaxWidth()) {
            AndroidView(
                factory = { ctx -> NativeCard.build(ctx) },
                update = { v -> NativeCard.bind(v, ad, c) },
                modifier = Modifier.fillMaxWidth(),
            )
            RemoveAdsLink(onRemove)
        }
    }
}

/** Builds the native ad view from plain Android views, since AdMob registers its assets on views. */
private object NativeCard {
    private class Parts(val icon: ImageView, val tag: TextView, val head: TextView, val body: TextView, val cta: TextView)

    fun build(ctx: Context): NativeAdView {
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()
        val view = NativeAdView(ctx)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        }
        val icon = ImageView(ctx).apply { layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)); clipToOutline = true; scaleType = ImageView.ScaleType.CENTER_CROP }
        val words = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f); marginEnd = dp(10f) }
        }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tag = TextView(ctx).apply { text = "Ad"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setPadding(dp(5f), dp(1f), dp(5f), dp(1f)) }
        val head = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6f) }
        }
        val body = TextView(ctx).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val cta = TextView(ctx).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(dp(12f), dp(7f), dp(12f), dp(7f)); maxLines = 1 }
        top.addView(tag); top.addView(head)
        words.addView(top); words.addView(body)
        row.addView(icon); row.addView(words); row.addView(cta)
        view.addView(row)
        view.iconView = icon; view.headlineView = head; view.bodyView = body; view.callToActionView = cta
        view.tag = Parts(icon, tag, head, body, cta)
        return view
    }

    fun bind(view: NativeAdView, ad: NativeAd, c: WaylinkColors) {
        val p = view.tag as? Parts ?: return
        val d = view.resources.displayMetrics.density
        fun round(fill: Color, r: Float, stroke: Color? = null) = GradientDrawable().apply {
            cornerRadius = r * d; setColor(fill.toArgb()); if (stroke != null) setStroke((1 * d).toInt(), stroke.toArgb())
        }
        view.background = round(c.card, 20f, c.line)
        p.tag.background = round(Color.Transparent, 5f, c.ink4); p.tag.setTextColor(c.ink3.toArgb())
        p.head.text = ad.headline; p.head.setTextColor(c.ink.toArgb())
        p.body.text = ad.body ?: ""; p.body.setTextColor(c.ink3.toArgb())
        p.cta.text = ad.callToAction ?: "Open"; p.cta.setTextColor(c.ink2.toArgb()); p.cta.background = round(c.inkA(0.07f), 99f)
        p.icon.background = round(c.inkA(0.06f), 12f)
        val img = ad.icon?.drawable
        p.icon.setImageDrawable(img); p.icon.visibility = if (img != null) android.view.View.VISIBLE else android.view.View.GONE
        view.setNativeAd(ad)
    }
}

/** Activity helper so MainActivity stays short. */
fun startAds(activity: Activity) { adSource = AdMobSource; AdMob.consentAndStart(activity) }
