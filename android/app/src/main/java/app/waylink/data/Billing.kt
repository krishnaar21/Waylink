package app.waylink.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time support products sold through Google Play. Prices are final, GST included. ₹120 unlocks the Paper
 * themes, ₹220 (suggested) also turns ads off, and ₹520 adds early access, future premium features and priority support. The product ids here must match the in-app products created in Play Console.
 */
object Support {
    data class Tier(val id: String, val price: Int, val icon: String, val name: String, val pitch: String, val perks: List<String>, val adFree: Boolean, val early: Boolean)
    val TIERS = listOf(
        Tier("friend_120", 120, "palette", "Friend",
            "Keeps Waylink free, and the Paper themes are yours.",
            listOf("The Paper themes"), adFree = false, early = false),
        Tier("backer_220", 220, "love", "Backer",
            "Everything in Friend, and no ads.",
            listOf("Everything in Friend", "No ads"), adFree = true, early = false),
        Tier("patron_520", 520, "sparkles", "Patron",
            "Everything in Backer, plus new features first and priority help.",
            listOf("Everything in Backer", "Early access to new features", "Future premium features", "Priority support"), adFree = true, early = true),
    )
    /** Preselected on the Support sheet. */
    const val SUGGESTED = 1
    fun tier(id: String) = TIERS.firstOrNull { it.id == id }
}

/**
 * Google Play Billing for the support products. Purchases are one-time and not consumed, so they come back
 * on a reinstall or a new phone ([restore]). There is no server: the purchase is checked on the phone, which
 * is enough for a cosmetic unlock. [onSupporter] runs with what the owned products unlock.
 */
class Billing(context: Context, private val onSupporter: (adFree: Boolean, early: Boolean) -> Unit, private val onMessage: (String) -> Unit, private val onThanks: (tierId: String) -> Unit = {}) {
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Play's tax-inclusive price per product id, once Play has answered. */
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()
    private val _owned = MutableStateFlow<Set<String>>(emptySet())
    val owned: StateFlow<Set<String>> = _owned.asStateFlow()
    private val details = mutableMapOf<String, ProductDetails>()
    private var restoring = false

    private val client: BillingClient? = runCatching {
        BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> onPurchases(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }.getOrNull()

    fun start() {
        val c = client ?: return
        if (c.isReady) { load(); return }
        runCatching {
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) { if (result.responseCode == BillingClient.BillingResponseCode.OK) load() }
                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    private fun load() {
        val c = client ?: return
        val products = Support.TIERS.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(BillingClient.ProductType.INAPP).build()
        }
        c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            found.productDetailsList.forEach { d ->
                details[d.productId] = d
                d.oneTimePurchaseOfferDetails?.formattedPrice?.let { p -> _prices.value = _prices.value + (d.productId to p) }
            }
        }
        restore()
    }

    /** Asks Play for what this account already owns. [announce] says so in a toast either way. */
    fun restore(announce: Boolean = false) {
        val c = client
        if (c == null || !c.isReady) { if (announce) onMessage("Google Play isn’t reachable right now. Try again in a moment."); if (c != null) start(); return }
        restoring = announce
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            val ok = result.responseCode == BillingClient.BillingResponseCode.OK
            val unlocked = ok && handle(purchases)
            if (restoring) onMessage(if (!ok) "Couldn’t reach Google Play. Try again in a moment." else if (unlocked) "Support restored. Thank you!" else "No earlier support found on this Google account.")
            restoring = false
        }
    }

    /** Opens Play's payment sheet for [id]. */
    fun buy(activity: Activity?, id: String) {
        val c = client; val d = details[id]
        if (activity == null || c == null || !c.isReady || d == null) {
            onMessage("Google Play payments aren’t available on this phone right now."); start(); return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        val r = c.launchBillingFlow(activity, params)
        if (r.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) { onMessage("You’ve already given this one. Thank you!"); restore() }
    }

    private fun onPurchases(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (purchases != null) {
                val unlocked = handle(purchases)
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED })
                    purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.flatMap { it.products }
                        .mapNotNull { Support.tier(it) }.maxByOrNull { it.price }?.let { onThanks(it.id) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> onMessage("The payment didn’t go through. Nothing was charged.")
        }
    }

    /** Acknowledges new purchases (Play refunds unacknowledged ones after 3 days) and reports the unlock. */
    private fun handle(purchases: List<Purchase>): Boolean {
        val c = client ?: return false
        var unlocks = false
        var adFree = false
        var early = false
        val mine = mutableSetOf<String>()
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
            if (!p.isAcknowledged) {
                c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
            }
            p.products.forEach { id -> mine += id; Support.tier(id)?.let { t -> unlocks = true; if (t.adFree) adFree = true; if (t.early) early = true } }
        }
        _owned.value = _owned.value + mine
        if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) onMessage("Your payment is pending. Your unlocks turn on once it clears.")
        if (unlocks) onSupporter(adFree, early)
        return unlocks
    }

    fun end() { runCatching { client?.endConnection() } }
}
