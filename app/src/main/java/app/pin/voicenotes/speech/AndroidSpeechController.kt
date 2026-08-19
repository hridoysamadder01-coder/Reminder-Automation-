package app.pin.voicenotes.speech

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Real implementation over the Android speech stack.
 *
 * Prefers the explicit on-device recognizer when the device supports it and
 * there is no network (Android 12+); otherwise the platform's default
 * recognizer service is used, which on modern devices already runs on-device
 * models when available. No audio ever goes to app-controlled servers — only
 * to whatever recognition service the device itself provides.
 */
class AndroidSpeechController(private val context: Context) : SpeechController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recognizerIsOnDevice = false

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state

    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level

    override fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    override fun isOnDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun startListening() {
        mainHandler.post {
            if (!isRecognitionAvailable()) {
                _state.value = SpeechState.Error(SpeechErrorKind.UNAVAILABLE)
                return@post
            }
            ensureRecognizer()
            _state.value = SpeechState.Listening("")
            _level.value = 0f
            recognizer?.startListening(recognitionIntent())
        }
    }

    override fun stopListening() {
        mainHandler.post {
            if (_state.value is SpeechState.Listening) {
                _state.value = SpeechState.Processing
                recognizer?.stopListening()
            }
        }
    }

    override fun cancel() {
        mainHandler.post {
            recognizer?.cancel()
            _state.value = SpeechState.Idle
            _level.value = 0f
        }
    }

    override fun reset() {
        _state.value = SpeechState.Idle
        _level.value = 0f
    }

    override fun destroy() {
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    // ------------------------------------------------------------------

    private fun ensureRecognizer() {
        val wantOnDevice = isOnDeviceAvailable() && !hasNetwork()
        if (recognizer != null && wantOnDevice == recognizerIsOnDevice) return

        recognizer?.destroy()
        recognizerIsOnDevice = wantOnDevice
        recognizer = if (wantOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.also { it.setRecognitionListener(listener) }
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Bangla first; recognizers that don't support it fall back to
            // the device default, and mixed Bangla-English is common anyway.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SpeechState.Listening("")
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // Typical range is about -2..10 dB; smooth into 0..1.
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _level.value = _level.value * 0.6f + normalized * 0.4f
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.value = SpeechState.Processing
        }

        override fun onError(error: Int) {
            // A cancel() can surface as CLIENT error after we already reset.
            if (_state.value == SpeechState.Idle && error == SpeechRecognizer.ERROR_CLIENT) return
            val kind = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorKind.NO_MATCH
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorKind.PERMISSION
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechErrorKind.NETWORK
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechErrorKind.BUSY
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechErrorKind.UNAVAILABLE
                else -> SpeechErrorKind.OTHER
            }
            _state.value = SpeechState.Error(kind)
            _level.value = 0f
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _level.value = 0f
            _state.value = if (text.isBlank()) {
                SpeechState.Error(SpeechErrorKind.NO_MATCH)
            } else {
                SpeechState.Result(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (partial.isNotBlank() && _state.value is SpeechState.Listening) {
                _state.value = SpeechState.Listening(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
