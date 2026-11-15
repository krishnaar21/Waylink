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
 * One-time "support" products sold through Google Play. Each amount is the base price; GST is added on top
 * (Play shows the tax-inclusive price, e.g. ₹118 for ₹100). Every product of ₹100 or more unlocks the Paper themes.
 * The product ids here must match the in-app products created in Play Console.
 */
object Support {
    const val GST_PERCENT = 18
    data class Tier(val id: String, val base: Int, val icon: String, val line: String) {
        val withTax: Int get() = Math.round(base * (100 + GST_PERCENT) / 100.0).toInt()
        val unlocksPaper: Boolean get() = base >= app.waylink.ui.theme.SUPPORTER_PRICE
    }
    val TIERS = listOf(
        Tier("support_50", 50, "tea", "A cutting chai for the developer"),
        Tier("support_100", 100, "coffee", "A coffee, and the Paper themes are yours"),
        Tier("support_200", 200, "fuel", "Two litres of petrol for test rides"),
        Tier("support_500", 500, "wrench", "Towards a month of upkeep"),
    )
    fun tier(id: String) = TIERS.firstOrNull { it.id == id }
}

/**
 * Google Play Billing for the support products. Purchases are one-time and not consumed, so they come back
 * on a reinstall or a new phone ([restore]). There is no server: the purchase is checked on the phone, which
 * is enough for a theme unlock. [onSupporter] runs when Play reports an owned product that unlocks Paper.
 */
class Billing(context: Context, private val onSupporter: () -> Unit, private val onMessage: (String) -> Unit) {
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
            if (restoring) onMessage(if (!ok) "Couldn’t reach Google Play. Try again in a moment." else if (unlocked) "Support restored. Paper is unlocked." else "No earlier support found on this Google account.")
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
                    onMessage(if (unlocked) "Thank you for supporting Waylink. Paper is unlocked." else "Thank you for supporting Waylink.")
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
        val mine = mutableSetOf<String>()
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
            if (!p.isAcknowledged) {
                c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
            }
            p.products.forEach { id -> mine += id; if (Support.tier(id)?.unlocksPaper == true) unlocks = true }
        }
        _owned.value = _owned.value + mine
        if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) onMessage("Your payment is pending. Paper unlocks once it clears.")
        if (unlocks) onSupporter()
        return unlocks
    }

    fun end() { runCatching { client?.endConnection() } }
}
