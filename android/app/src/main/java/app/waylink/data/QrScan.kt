package app.waylink.data

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Scans a QR code with Google's code scanner (Play services). It opens its own camera screen,
 * so Waylink needs no camera permission and never sees the camera feed.
 */
object QrScan {
    fun scan(ctx: Context, onCode: (String) -> Unit, onError: (String) -> Unit) {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        try {
            GmsBarcodeScanning.getClient(ctx, opts).startScan()
                .addOnSuccessListener { b -> b.rawValue?.let(onCode) ?: onError("That code is empty. Try again.") }
                .addOnFailureListener { e ->
                    val busy = (e as? MlKitException)?.errorCode == MlKitException.UNAVAILABLE
                    onError(if (busy) "The scanner is still downloading. Try again in a minute." else "Couldn’t open the scanner.")
                }
            // Cancelling (back button) is neither: nothing to say.
        } catch (e: Exception) { onError("Couldn’t open the scanner. Update Google Play services and try again.") }
    }
}
