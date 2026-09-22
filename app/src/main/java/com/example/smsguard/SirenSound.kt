package com.example.smsguard

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Generates a short WAV siren tone on first use and caches it in the cache dir. */
object SirenSound {
    private const val SAMPLE_RATE = 22050
    private const val DURATION_SEC = 2
    private const val FREQUENCY = 1000.0

    fun file(context: Context): File {
        val target = File(context.cacheDir, "ding.wav")
        if (target.exists() && target.length() > 44L) return target
        generate(target)
        return target
    }

    private fun generate(target: File) {
        val totalSamples = (SAMPLE_RATE * DURATION_SEC).toInt()
        val dataSize = totalSamples * 2
        val sample = ByteArray(dataSize)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Simple ding: high pitch with exponential decay
            val decay = exp(-3.0 * t)
            val value = (sin(2.0 * PI * FREQUENCY * t) * decay * 0.9 * Short.MAX_VALUE).toInt()
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