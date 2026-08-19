package app.pin.voicenotes.speech

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * স্পিচের ইন্টারফেস — UI কখনো সরাসরি Android-এর SpeechRecognizer ছোঁয় না।
 * ফলে টেস্টে নকল (fake) কন্ট্রোলার বসানো যায়, আর ভয়েসহীন ফোনেও অ্যাপ চলে।
 */

import kotlinx.coroutines.flow.StateFlow

enum class SpeechErrorKind { NO_MATCH, PERMISSION, UNAVAILABLE, NETWORK, BUSY, OTHER }

sealed interface SpeechState {
    data object Idle : SpeechState
    data class Listening(val partial: String) : SpeechState
    data object Processing : SpeechState
    data class Result(val text: String) : SpeechState
    data class Error(val kind: SpeechErrorKind) : SpeechState
}

/**
 * Abstraction over the device speech stack so UI and tests never touch
 * [android.speech.SpeechRecognizer] directly.
 */
interface SpeechController {
    val state: StateFlow<SpeechState>

    /** Smoothed microphone level in 0..1 while listening — drives the visualizer. */
    val level: StateFlow<Float>

    /** Whether any speech recognition service exists on this device. */
    fun isRecognitionAvailable(): Boolean

    /** Whether fully on-device recognition is supported (Android 12+ devices). */
    fun isOnDeviceAvailable(): Boolean

    fun startListening()
    fun stopListening()
    fun cancel()

    /** Return to [SpeechState.Idle] after a result or error was consumed. */
    fun reset()

    fun destroy()
}
