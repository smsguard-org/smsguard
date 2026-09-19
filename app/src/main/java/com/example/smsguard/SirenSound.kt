package com.example.smsguard

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

object SirenSound {
    private const val SAMPLE_RATE = 22050
    private const val TONE_A = 660.0
    private const val TONE_B = 880.0
    private const val SWAP_EVERY = 0.6
    private const val DURATION_SEC = 12
    private const val FADE_SEC = 0.05

    fun file(context: Context): File {
        val target = File(context.cacheDir, "siren.wav")
        if (target.exists() && target.length() > 44L) return target
        generate(target)
        return target
    }

    private fun generate(target: File) {
        val totalSamples = (SAMPLE_RATE * DURATION_SEC).toInt()
        val fadeSamples = (SAMPLE_RATE * FADE_SEC).toInt()
        val dataSize = totalSamples * 2
        val sample = ByteArray(dataSize)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = if (((t / SWAP_EVERY).toInt()) % 2 == 0) TONE_A else TONE_B
            var value = (sin(2.0 * PI * freq * t) * 0.9 * Short.MAX_VALUE).toInt()
            val fade = fadeInFadeOut(i, totalSamples, fadeSamples)
            value = (value * fade).toInt()
            val le = (value and 0xFFFF)
            sample[i * 2] = (le).toByte()
            sample[i * 2 + 1] = (le shr 8).toByte()
        }

        BufferedOutputStream(FileOutputStream(target)).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(int32(36 + dataSize))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(int32(16))
            out.write(int16(1))
            out.write(int16(1))
            out.write(int32(SAMPLE_RATE))
            out.write(int32(SAMPLE_RATE * 2))
            out.write(int16(2))
            out.write(int16(16))
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(int32(dataSize))
            out.write(sample)
        }
    }

    private fun fadeInFadeOut(i: Int, total: Int, fade: Int): Double {
        if (fade <= 0) return 1.0
        val gain = if (i < fade) i.toDouble() / fade else 1.0
        val tail = if (i > total - fade) (total - i).toDouble() / fade else 1.0
        return gain.coerceIn(0.0, 1.0) * tail.coerceIn(0.0, 1.0)
    }

    private fun int16(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )
}