package app.waylink.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import app.waylink.core.Computer
import app.waylink.core.Vault
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The computers linked to this phone (Waylink for desktop). Each link's key is locked with an
 * Android Keystore key before it touches the disk, so a copy of the app's files alone can't read the sync.
 */
class Computers(ctx: Context) {
    private val file = AtomicFile(File(ctx.filesDir, "waylink-computers.bin"))
    private val alias = "waylink-computers"

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }

    private val ser = ListSerializer(Computer.serializer())

    fun load(): List<Computer> = try {
        if (!file.baseFile.exists()) emptyList() else {
            val b = file.readFully()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, b, 0, 12))
            Vault.json.decodeFromString(ser, String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8))
        }
    } catch (e: Exception) { emptyList() } // a key lost with a restore from another phone: link again

    fun save(list: List<Computer>) {
        if (list.isEmpty()) { file.delete(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val bytes = c.iv + c.doFinal(Vault.json.encodeToString(ser, list).toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out) }
    }
}
