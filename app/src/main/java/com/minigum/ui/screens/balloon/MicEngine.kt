package com.minigum.ui.screens.balloon

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * Low-latency microphone sampler for the Balloon game.
 *
 * Continuously records from the mic on a background thread and exposes the latest RMS amplitude
 * (0..1, normalized against full-scale 16-bit) via [amplitude]. The ViewModel polls this each
 * physics tick, applies the per-device calibration (silence floor / blow ceiling), and turns the
 * result into thrust. Volume-only by design — any sound lifts the balloon.
 *
 * Robust to the mic dying mid-session (e.g. AudioRecord returning ERROR_DEAD_OBJECT after audio
 * focus changes or a run restart): read errors trigger a recorder recreation, and [ensureActive]
 * lets the game recover if the feed ever goes stale.
 */
class MicEngine {

    @Volatile
    var amplitude: Float = 0f
        private set

    @Volatile
    private var lastUpdateNs: Long = 0L

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var bufSize: Int = 640

    @Volatile
    private var running = false
    private var consecutiveErrors = 0

    /** Begin capturing. No-op if already running; safe to call repeatedly. */
    fun start() {
        if (running) return
        val sampleRate = pickSampleRate() ?: return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return
        bufSize = maxOf(minBuf, 320)
        open(sampleRate)
    }

    private fun open(sampleRate: Int) {
        try {
            val r = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize * 2, // bytes
            )
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                r.release()
                return
            }
            r.startRecording()
            record = r
            running = true
            consecutiveErrors = 0
            lastUpdateNs = System.nanoTime()
            thread = Thread { sampleLoop() }.apply { isDaemon = true; start() }
        } catch (_: SecurityException) {
            // Permission revoked between check and use — treated as no mic.
        } catch (_: Exception) {
            record?.release()
            record = null
        }
    }

    private fun sampleLoop() {
        val buffer = ShortArray(bufSize)
        while (running) {
            val r = record ?: break
            val read = r.read(buffer, 0, bufSize)
            if (read > 0) {
                var sum = 0.0
                for (i in 0 until read) {
                    val v = buffer[i] / 32768f
                    sum += (v * v).toDouble()
                }
                val rms = sqrt(sum / read).toFloat()
                if (!rms.isNaN()) {
                    amplitude = rms
                    lastUpdateNs = System.nanoTime()
                    consecutiveErrors = 0
                }
            } else {
                // Negative read = error (often DEAD_OBJECT after audio focus loss). Recreate.
                if (++consecutiveErrors >= 8) {
                    consecutiveErrors = 0
                    reopen()
                    break
                }
            }
        }
    }

    private fun reopen() {
        running = false
        try { record?.stop() } catch (_: Exception) {}
        record?.release()
        record = null
        start() // re-opens a fresh recorder + spawns a new sampling thread
    }

    /** True if the recorder stopped or the amplitude feed has gone silent too long. */
    fun isStale(): Boolean =
        !running || (lastUpdateNs != 0L && System.nanoTime() - lastUpdateNs > 700_000_000L)

    /** Recover the mic if it isn't healthy. Called by the game on run start and each tick. */
    fun ensureActive() {
        if (isStale()) {
            stop()
            start()
        }
    }

    fun stop() {
        running = false
        try { record?.stop() } catch (_: Exception) {}
        record?.release()
        record = null
        thread?.interrupt()
        amplitude = 0f
        lastUpdateNs = 0L
    }

    private fun pickSampleRate(): Int? {
        for (sr in intArrayOf(44100, 22050, 16000, 11025, 8000)) {
            if (AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0) return sr
        }
        return null
    }
}
