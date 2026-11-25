// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import helium314.keyboard.latin.utils.Log

/**
 * Invisible activity that opens the system speech recognizer (RecognizerIntent) and stores the
 * recognized text. LatinIME inserts the text once the original app has focus again.
 * This replaces switching to a separate voice IME, which some voice IMEs (e.g. Google voice typing)
 * close immediately when started from a third-party keyboard.
 */
class VoiceInputActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // recognizer already started before recreation
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        intent.getStringExtra(EXTRA_LANGUAGE_TAG)?.let {
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, it)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(recognizerIntent, REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no speech recognizer found", e)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                pendingText = text
                pendingPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
                pendingTime = SystemClock.uptimeMillis()
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "VoiceInputActivity"
        private const val REQUEST_CODE = 1
        private const val EXTRA_LANGUAGE_TAG = "language_tag"
        private const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val MAX_PENDING_AGE_MS = 15_000L

        private var pendingText: String? = null
        private var pendingPackage: String? = null
        private var pendingTime = 0L

        @JvmStatic
        fun isRecognizerAvailable(context: Context): Boolean =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null

        @JvmStatic
        fun start(context: Context, languageTag: String?, targetPackage: String?) {
            pendingText = null
            context.startActivity(Intent(context, VoiceInputActivity::class.java)
                .putExtra(EXTRA_LANGUAGE_TAG, languageTag)
                .putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }

        /** Returns recognized text if it is recent and meant for [packageName], and clears it. */
        @JvmStatic
        fun consumePendingText(packageName: String?): String? {
            val text = pendingText ?: return null
            if (SystemClock.uptimeMillis() - pendingTime > MAX_PENDING_AGE_MS) {
                pendingText = null
                return null
            }
            if (pendingPackage != null && pendingPackage != packageName) return null
            pendingText = null
            return text
        }
    }
}
