package com.minigum.ui.screens.welcome

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Procedurally generated soothing ambient music for the welcome screen — no audio asset required.
 *
 * A slow 4-chord pad (warm major-7 voicings) with gentle tremolo and a soft low drone, sprinkled
 * with occasional bell tones, run through a short feedback delay for a lush, airy feel. Rendered
 * once to PCM, written to a WAV file in the cache dir, and looped via [MediaPlayer] (the most
 * reliable playback path across Android devices).
 *
 * Lifecycle-safe: [start]/[stop] are idempotent, playback happens off the main thread, and muting
 * silences (and later resumes) playback.
 */
class AmbientMusic(private val context: Context) {

    @Volatile
    var muted: Boolean = false
        private set

    private var player: MediaPlayer? = null
    private var wavFile: File? = null

    /** Begin looping (no-op if already playing or muted). Safe to call repeatedly. */
    fun start() {
        if (player != null || muted) return
        Thread {
            try {
                val file = wavFile ?: ensureWav()
                val mp = MediaPlayer.create(context.applicationContext, Uri.fromFile(file))
                if (mp == null) { Log.e(TAG, "MediaPlayer.create returned null"); return@Thread }
                mp.isLooping = true
                mp.setVolume(VOLUME, VOLUME)
                mp.start()
                player = mp
                Log.i(TAG, "playback started")
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }.also { it.isDaemon = true; it.name = "ambient-music" }.start()
    }

    /** Stop and release playback. */
    fun stop() {
        player?.let { p ->
            try { p.stop() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
        player = null
    }

    fun setMuted(m: Boolean) {
        muted = m
        if (m) stop() else start()
    }

    private fun ensureWav(): File {
        val pcm = render()
        val bytes = toWav(pcm)
        val file = File(context.cacheDir, "minigum_ambient.wav")
        file.writeBytes(bytes)
        wavFile = file
        return file
    }

    /** Build a 16-bit mono WAV (44-byte header + PCM data) for [pcm]. */
    private fun toWav(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArray(44 + dataSize)
        "RIFF".toByteArray().copyInto(out, 0)
        writeIntLE(out, 4, 36 + dataSize)
        "WAVE".toByteArray().copyInto(out, 8)
        "fmt ".toByteArray().copyInto(out, 12)
        writeIntLE(out, 16, 16) // subchunk size
        writeShortLE(out, 20, 1) // PCM
        writeShortLE(out, 22, 1) // mono
        writeIntLE(out, 24, SAMPLE_RATE)
        writeIntLE(out, 28, SAMPLE_RATE * 2) // byte rate
        writeShortLE(out, 32, 2) // block align
        writeShortLE(out, 34, 16) // bits per sample
        "data".toByteArray().copyInto(out, 36)
        writeIntLE(out, 40, dataSize)
        var i = 44
        for (s in pcm) {
            out[i++] = (s.toInt() and 0xFF).toByte()
            out[i++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun writeIntLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    /** Render ~20s of ambient pad into 16-bit PCM samples. */
    private fun render(): ShortArray {
        val total = (SAMPLE_RATE * DURATION_SECONDS).toInt()
        val dry = FloatArray(total)

        val chords = listOf(
            doubleArrayOf(130.81, 164.81, 196.00, 246.94),
            doubleArrayOf(146.83, 185.00, 220.00, 277.18),
            doubleArrayOf(110.00, 138.59, 164.81, 207.65),
            doubleArrayOf(98.00, 123.47, 146.83, 185.00),
        )
        val roots = doubleArrayOf(65.41, 73.42, 55.00, 49.00)
        val bellNotes = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.00)
        val bells = listOf(0.8 to 0, 5.6 to 2, 10.9 to 1, 14.7 to 4, 18.2 to 3)

        val chordCount = chords.size
        val chordDur = DURATION_SECONDS / chordCount
        var peak = 0.0001f

        for (i in 0 until total) {
            val t = i / SAMPLE_RATE.toDouble()
            val seg = ((t / chordDur).toInt() % chordCount).coerceIn(0, chordCount - 1)
            val local = t - seg * chordDur
            val env = sin(PI * local / chordDur).toFloat()
            val tremolo = (0.9 + 0.1 * sin(2 * PI * 0.07 * t)).toFloat()

            var s = 0.0
            for (f in chords[seg]) s += sin(2 * PI * f * t) * 0.25
            s += sin(2 * PI * roots[seg] * t) * 0.15
            var sample = (s * env * tremolo).toFloat()

            for ((t0, noteIdx) in bells) {
                if (t >= t0) {
                    val dt = (t - t0).toFloat()
                    sample += (sin(2 * PI * bellNotes[noteIdx] * t).toFloat() * exp(-dt * 1.4f) * 0.12f)
                }
            }
            dry[i] = sample
            val a = kotlin.math.abs(sample); if (a > peak) peak = a
        }

        val delay = (SAMPLE_RATE * 0.32).toInt()
        val wet = FloatArray(total)
        for (i in 0 until total) {
            val d = if (i >= delay) wet[i - delay] * 0.45f else 0f
            wet[i] = dry[i] + d
        }

        val out = ShortArray(total)
        for (i in 0 until total) {
            val x = tanh(wet[i] * 1.2).toFloat()
            out[i] = (x / peak.coerceAtLeast(0.1f) * 0.85f * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    companion object {
        private const val TAG = "AmbientMusic"
        private const val SAMPLE_RATE = 22050
        private const val DURATION_SECONDS = 20.0
        private const val VOLUME = 0.85f
    }
}
