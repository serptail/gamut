package com.gamut.core.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

object ShazamSignature {
    const val SAMPLE_RATE_HZ = 16_000

    private const val FFT_SIZE = 2048
    private const val BINS = 1025
    private const val HOP = 128
    private const val RING = 256
    private const val DATA_URI_PREFIX = "data:audio/vnd.shazam.sig;base64,"

    class Result(val uri: String, val sampleMs: Int)

    fun fromPcm16(pcm: ByteArray): Result {
        val samples = ShortArray(pcm.size / 2)
        ByteBuffer.wrap(pcm, 0, samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        return fromSamples(samples)
    }

    fun fromSamples(samples: ShortArray): Result {
        val peaks = Generator().run(samples)
        val uri = DATA_URI_PREFIX + Base64.getEncoder().encodeToString(encode(samples.size, peaks))
        return Result(uri, (samples.size.toFloat() / SAMPLE_RATE_HZ * 1000f).toInt())
    }

    private class Peak(val pass: Int, val magnitude: Int, val bin: Int)

    private fun bandOf(hz: Int): Int = when (hz) {
        in 250..519 -> 0
        in 520..1449 -> 1
        in 1450..3499 -> 2
        in 3500..5500 -> 3
        else -> -1
    }

    private val hann = FloatArray(FFT_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * (i + 1) / (FFT_SIZE + 1))).toFloat()
    }

    private class Generator {
        private val ringSamples = ShortArray(FFT_SIZE)
        private var ringIndex = 0
        private val fftOut = Array(RING) { FloatArray(BINS) }
        private var fftIndex = 0
        private val spread = Array(RING) { FloatArray(BINS) }
        private var spreadIndex = 0
        private var spreadDone = 0
        private val fft = RealFft(FFT_SIZE)
        private val windowed = FloatArray(FFT_SIZE)
        private val bands = Array(4) { ArrayList<Peak>() }

        fun run(samples: ShortArray): Array<ArrayList<Peak>> {
            val chunks = samples.size / HOP
            val chunk = ShortArray(HOP)
            for (c in 0 until chunks) {
                System.arraycopy(samples, c * HOP, chunk, 0, HOP)
                doFft(chunk)
                doPeakSpreading()
                spreadDone++
                if (spreadDone >= 46) doPeakRecognition()
            }
            return bands
        }

        private fun doFft(chunk: ShortArray) {
            System.arraycopy(chunk, 0, ringSamples, ringIndex, HOP)
            ringIndex = (ringIndex + HOP) and (FFT_SIZE - 1)
            for (i in 0 until FFT_SIZE) {
                windowed[i] = ringSamples[(i + ringIndex) and (FFT_SIZE - 1)] * hann[i]
            }
            val out = fftOut[fftIndex]
            fft.powerSpectrum(windowed, out)
            for (i in 0 until BINS) out[i] = max(out[i] / (1 shl 17).toFloat(), 0.0000000001f)
            fftIndex = (fftIndex + 1) and (RING - 1)
        }

        private fun doPeakSpreading() {
            val real = fftOut[(fftIndex - 1) and (RING - 1)]
            val cur = spread[spreadIndex]
            System.arraycopy(real, 0, cur, 0, BINS)
            for (p in 0..1022) cur[p] = max(max(cur[p], cur[p + 1]), cur[p + 2])
            val copy = cur.copyOf()
            for (former in intArrayOf(1, 3, 6)) {
                val target = spread[(spreadIndex - former) and (RING - 1)]
                for (p in 0 until BINS) target[p] = max(target[p], copy[p])
            }
            spreadIndex = (spreadIndex + 1) and (RING - 1)
        }

        private fun doPeakRecognition() {
            val f46 = fftOut[(fftIndex - 46) and (RING - 1)]
            val s49 = spread[(spreadIndex - 49) and (RING - 1)]
            for (bin in 10..1014) {
                if (f46[bin] < 1f / 64f || f46[bin] < s49[bin - 1]) continue

                var maxNeighbor = 0f
                for (o in FREQ_NEIGHBORS) maxNeighbor = max(maxNeighbor, s49[bin + o])
                if (f46[bin] <= maxNeighbor) continue

                var maxOther = maxNeighbor
                for (o in TIME_NEIGHBORS) {
                    maxOther = max(maxOther, spread[(spreadIndex + o) and (RING - 1)][bin - 1])
                }
                if (f46[bin] <= maxOther) continue

                val pass = spreadDone - 46
                val mag = max(ln(f46[bin]), 1f / 64f) * 1477.3f + 6144f
                val magBefore = max(ln(f46[bin - 1]), 1f / 64f) * 1477.3f + 6144f
                val magAfter = max(ln(f46[bin + 1]), 1f / 64f) * 1477.3f + 6144f
                val var1 = mag * 2f - magBefore - magAfter
                val var2 = (magAfter - magBefore) * 32f / var1
                if (var1.isNaN() || var2.isNaN()) continue

                val corrected = ((bin * 64) + var2.toInt()) and 0xFFFF
                val hz = (corrected.toFloat() * (16000f / 2f / 1024f / 64f)).toInt()
                val band = bandOf(hz)
                if (band < 0) continue

                bands[band].add(Peak(pass, mag.toInt().coerceIn(0, 0xFFFF), corrected))
            }
        }
    }

    private val FREQ_NEIGHBORS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
    private val TIME_NEIGHBORS = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

    private fun encode(numberSamples: Int, bands: Array<ArrayList<Peak>>): ByteArray {
        val body = java.io.ByteArrayOutputStream()
        for (band in 0 until 4) {
            val peaks = bands[band]
            if (peaks.isEmpty()) continue
            val buf = ByteBuffer.allocate(peaks.size * 12 + 16).order(ByteOrder.LITTLE_ENDIAN)
            var pass = 0
            for (p in peaks) {
                if (p.pass - pass >= 255) {
                    buf.put(0xFF.toByte()).putInt(p.pass)
                    pass = p.pass
                }
                buf.put((p.pass - pass).toByte())
                buf.putShort(p.magnitude.toShort()).putShort(p.bin.toShort())
                pass = p.pass
            }
            val len = buf.position()
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(0x60030040 + band).putInt(len)
            body.write(header.array())
            body.write(buf.array(), 0, len)
            repeat((4 - len % 4) % 4) { body.write(0) }
        }
        val bodyBytes = body.toByteArray()

        val out = ByteBuffer.allocate(48 + 8 + bodyBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0xcafe2580.toInt())
        out.putInt(0)
        out.putInt(bodyBytes.size + 8)
        out.putInt(0x94119c00.toInt())
        out.putInt(0).putInt(0).putInt(0)
        out.putInt(3 shl 27)
        out.putInt(0).putInt(0)
        out.putInt(numberSamples + (SAMPLE_RATE_HZ * 0.24f).toInt())
        out.putInt((15 shl 19) + 0x40000)
        out.putInt(0x40000000)
        out.putInt(bodyBytes.size + 8)
        out.put(bodyBytes)
        val bytes = out.array()

        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value.toInt()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, crc)
        return bytes
    }

    private class RealFft(private val n: Int) {
        private val cosT = DoubleArray(n / 2) { cos(2.0 * PI * it / n) }
        private val sinT = DoubleArray(n / 2) { -sin(2.0 * PI * it / n) }
        private val rev = IntArray(n).also { r ->
            val bits = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
        }
        private val re = DoubleArray(n)
        private val im = DoubleArray(n)

        fun powerSpectrum(input: FloatArray, out: FloatArray) {
            for (i in 0 until n) {
                re[rev[i]] = input[i].toDouble()
                im[rev[i]] = 0.0
            }
            var size = 2
            while (size <= n) {
                val half = size / 2
                val step = n / size
                var start = 0
                while (start < n) {
                    var k = 0
                    for (j in start until start + half) {
                        val tr = re[j + half] * cosT[k] - im[j + half] * sinT[k]
                        val ti = re[j + half] * sinT[k] + im[j + half] * cosT[k]
                        re[j + half] = re[j] - tr
                        im[j + half] = im[j] - ti
                        re[j] += tr
                        im[j] += ti
                        k += step
                    }
                    start += size
                }
                size *= 2
            }
            for (k in 0..n / 2) out[k] = (re[k] * re[k] + im[k] * im[k]).toFloat()
        }
    }
}
