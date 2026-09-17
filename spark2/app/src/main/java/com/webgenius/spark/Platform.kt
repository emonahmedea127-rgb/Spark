package com.webgenius.spark

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.webgenius.spark.core.Config
import com.webgenius.spark.core.Rules
import com.webgenius.spark.core.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SparkApplication : Application() {
    val secrets: SecretStore by lazy { AndroidSecretStore(this) }
    private val settings by lazy { getSharedPreferences("spark_config", MODE_PRIVATE) }
    fun config(): Config? = runCatching {
        if (settings.getBoolean("choose_project", false)) null else Config(
            settings.getString("url", BundledProject.URL).orEmpty(),
            settings.getString("publishable_key", BundledProject.PUBLISHABLE_KEY).orEmpty(),
        ).validate()
    }.getOrNull()
    fun saveConfig(config: Config) { settings.edit().putString("url", config.url).putString("publishable_key", config.publishableKey).putBoolean("choose_project", false).apply() }
    fun clearConfig() { settings.edit().clear().putBoolean("choose_project", true).apply() }
}

/** Refresh/access tokens and PKCE verifiers are encrypted with a device-held key.
 * No credentials, tokens, media or private settings are eligible for cloud backup. */
class AndroidSecretStore(context: Context) : SecretStore {
    private val preferences = context.getSharedPreferences("spark_secrets", Context.MODE_PRIVATE)
    private val key: SecretKey by lazy {
        val vault = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (vault.getKey("spark_session_v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("spark_session_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun read(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching {
            val pieces = encoded.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, this.key, GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse { preferences.edit().remove(key).commit(); null }
    }
    @Synchronized override fun write(key: String, value: String?) {
        if (value == null) { check(preferences.edit().remove(key).commit()); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, this@AndroidSecretStore.key) }
        val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(key, Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(body, Base64.NO_WRAP)).commit())
    }
}

/** Decode with bounds first, downsample, orient, then re-encode without EXIF/GPS. */
suspend fun preparePhoto(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { requireNotNull(it) { "Cannot open this photo." }; BitmapFactory.decodeStream(it, null, options) }
    require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 80_000_000) { "Choose a photo smaller than 80 megapixels." }
    var sample = 1
    while (options.outWidth / sample > 2048 || options.outHeight / sample > 2048) sample *= 2
    val decoded = resolver.openInputStream(uri).use {
        requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })) { "Unsupported photo." }
    }
    val orientation = resolver.openInputStream(uri).use { it?.let { stream -> ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    try {
        val output = ByteArrayOutputStream()
        check(oriented.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "Could not process the photo." }
        output.toByteArray().also(Rules::image)
    } finally { if (oriented !== decoded) oriented.recycle(); decoded.recycle() }
}
