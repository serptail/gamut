package com.gamut.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

class SoundParams(
    val bands: List<EqBand> = emptyList(),
    val autoHeadroom: Boolean = true,
    val limiter: Boolean = true,
    val dither: Boolean = true,
    val crossfeed: Float = 0f,
    val width: Float = 1f,
)

@UnstableApi
internal class SoundEngineProcessor : BaseAudioProcessor() {
    @Volatile var params: SoundParams = SoundParams()

    @Volatile var pitchRatio = 1f

    private var channels = 2
    private var rate = 44100
    private var floatInput = false
    private var outFrameBytes = 4

    private var applied: SoundParams? = null
    private var appliedPitch = 1f
    private var running = false
    private var neutral = false

    private val b0 = DoubleArray(MAX_BANDS)
    private val b1 = DoubleArray(MAX_BANDS)
    private val b2 = DoubleArray(MAX_BANDS)
    private val a1 = DoubleArray(MAX_BANDS)
    private val a2 = DoubleArray(MAX_BANDS)
    private val on = BooleanArray(MAX_BANDS)
    private val order = IntArray(MAX_BANDS)
    private var eqBands = 0
    private val z1l = DoubleArray(MAX_BANDS)
    private val z2l = DoubleArray(MAX_BANDS)
    private val z1r = DoubleArray(MAX_BANDS)
    private val z2r = DoubleArray(MAX_BANDS)

    private var pre = 1.0
    private var widthOn = false
    private var width = 1.0

    private var xfOn = false
    private var xfB = 0.0
    private var xfA = 1.0
    private var xfAlpha = 0.0
    private var xfDelay = 1
    private var xdL = DoubleArray(0)
    private var xdR = DoubleArray(0)
    private var xw = 0
    private var lpL = 0.0
    private var lpR = 0.0

    private var limiterOn = true
    private var ditherOn = false
    private var laSize = 0
    private var laL = DoubleArray(0)
    private var laR = DoubleArray(0)
    private var pk = DoubleArray(0)
    private var lw = 0
    private var winMax = 0.0
    private var gain = 1.0
    private var atk = 0.0
    private var rel = 0.0

    private var rng = 0x2545F4914F6CDD1DL

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        if ((encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount !in 1..2) {
            return AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        floatInput = encoding == C.ENCODING_PCM_FLOAT
        outFrameBytes = 2 * channels
        allocate()
        applied = null
        return AudioFormat(rate, channels, C.ENCODING_PCM_16BIT)
    }

    override fun onFlush() {
        clearState()
        running = false
    }
    override fun onReset() {
        clearState()
        running = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inFrameBytes = (if (floatInput) 4 else 2) * channels
        val frames = inputBuffer.remaining() / inFrameBytes
        if (frames == 0) return

        val p = params
        val pitch = pitchRatio
        if (p !== applied || pitch != appliedPitch) rebuild(p, pitch)

        if (neutral) {
            val out = replaceOutputBuffer(inputBuffer.remaining())
            if (running) {
                clearState()
                running = false
            }
            out.put(inputBuffer)
            out.flip()
            return
        }

        val out = replaceOutputBuffer(frames * outFrameBytes)
        running = true
        if (channels == 2) {
            repeat(frames) {
                val l: Double
                val r: Double
                if (floatInput) {
                    l = inputBuffer.getFloat().toDouble()
                    r = inputBuffer.getFloat().toDouble()
                } else {
                    l = inputBuffer.getShort() / 32768.0
                    r = inputBuffer.getShort() / 32768.0
                }
                frame(l, r, out)
            }
        } else {
            repeat(frames) {
                val l = if (floatInput) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
                frame(l, 0.0, out)
            }
        }
        flushDenormals()
        out.flip()
    }

    override fun onQueueEndOfStream() {
        if (!running) return
        val out = replaceOutputBuffer(laSize * outFrameBytes)
        repeat(laSize) { frame(0.0, 0.0, out) }
        out.flip()
        running = false
    }

    private fun frame(inL: Double, inR: Double, out: ByteBuffer) {
        val stereo = channels == 2
        var l = inL * pre
        var r = inR * pre

        for (j in 0 until eqBands) {
            val i = order[j]
            val yl = b0[i] * l + z1l[i]
            z1l[i] = b1[i] * l - a1[i] * yl + z2l[i]
            z2l[i] = b2[i] * l - a2[i] * yl
            l = yl
            if (stereo) {
                val yr = b0[i] * r + z1r[i]
                z1r[i] = b1[i] * r - a1[i] * yr + z2r[i]
                z2r[i] = b2[i] * r - a2[i] * yr
                r = yr
            }
        }

        if (widthOn) {
            val m = (l + r) * 0.5
            val s = (l - r) * 0.5 * width
            l = m + s
            r = m - s
        }

        if (xfOn) {
            lpL += xfAlpha * (l - lpL)
            lpR += xfAlpha * (r - lpR)
            val hpL = l - lpL
            val hpR = r - lpR
            xdL[xw] = lpL
            xdR[xw] = lpR
            xw = if (xw + 1 == xdL.size) 0 else xw + 1
            val dl = xdL[xw]
            val dr = xdR[xw]
            l = xfA * lpL + hpL + xfB * dr
            r = xfA * lpR + hpR + xfB * dl
        }

        val oL = laL[lw]
        val oR = laR[lw]
        val old = pk[lw]
        laL[lw] = l
        laR[lw] = r
        val peak = if (stereo) max(abs(l), abs(r)) else abs(l)
        pk[lw] = peak
        lw = if (lw + 1 == laSize) 0 else lw + 1
        if (peak >= winMax) {
            winMax = peak
        } else if (old >= winMax) {
            var m = 0.0
            for (k in 0 until laSize) if (pk[k] > m) m = pk[k]
            winMax = m
        }
        val target = if (limiterOn && winMax > LIMIT) LIMIT / winMax else 1.0
        gain = if (target < gain) target + (gain - target) * atk else target + (gain - target) * rel

        val outL = softClip(oL * gain)
        out.putShort(quantize(outL, if (ditherOn) rand() - rand() else 0.0))
        if (stereo) {
            val outR = softClip(oR * gain)
            out.putShort(quantize(outR, if (ditherOn) rand() - rand() else 0.0))
        }
    }

    private fun softClip(x: Double): Double {
        val a = abs(x)
        if (a <= KNEE) return x
        val y = KNEE + (1.0 - KNEE) * tanh((a - KNEE) / (1.0 - KNEE))
        return if (x < 0.0) -y else y
    }

    private fun quantize(x: Double, dither: Double): Short {
        val v = floor(x * 32768.0 + dither + 0.5)
        return (
            if (v > 32767.0) {
                32767.0
            } else if (v < -32768.0) {
                -32768.0
            } else {
                v
            }
            ).toInt().toShort()
    }

    private fun rand(): Double {
        rng = rng xor (rng shl 13)
        rng = rng xor (rng ushr 7)
        rng = rng xor (rng shl 17)
        return (rng ushr 11) * (1.0 / 9007199254740992.0)
    }

    private fun rebuild(p: SoundParams, pitch: Float) {
        val scale = 1.0 / pitch.toDouble().coerceIn(0.25, 4.0)
        val nyq = rate / 2.0

        eqBands = 0
        for (i in 0 until MAX_BANDS) {
            val band = p.bands.getOrNull(i)
            val f = band?.freqHz?.toDouble()?.times(scale) ?: 0.0
            if (band != null && abs(band.gainDb) >= 0.01f && f < nyq * 0.95) {
                design(i, band, f)
                if (!on[i]) {
                    z1l[i] = 0.0
                    z2l[i] = 0.0
                    z1r[i] = 0.0
                    z2r[i] = 0.0
                }
                on[i] = true
                order[eqBands++] = i
            } else {
                on[i] = false
            }
        }

        width = p.width.toDouble().coerceIn(0.0, 2.0)
        widthOn = channels == 2 && abs(width - 1.0) > 0.005

        var headroomDb = 0.0
        if (p.autoHeadroom) {
            if (eqBands > 0) headroomDb -= max(0.0, peakResponseDb())
        }
        pre = 10.0.pow(headroomDb / 20.0)

        xfB = p.crossfeed.toDouble().coerceIn(0.0, 0.45)
        xfA = 1.0 - xfB
        xfOn = channels == 2 && xfB > 0.001
        xfAlpha = 1.0 - exp(-2.0 * PI * XF_FC * scale / rate)

        limiterOn = p.limiter
        val modifies = eqBands > 0 || widthOn || xfOn || pre != 1.0
        ditherOn = p.dither && modifies
        neutral = !modifies && !limiterOn && !floatInput

        applied = p
        appliedPitch = pitch
    }

    private fun design(i: Int, band: EqBand, f0: Double) {
        val fs = rate.toDouble()
        val f = f0.coerceIn(10.0, fs * 0.48)
        val q = band.q.toDouble().coerceIn(0.1, 20.0)
        val a = 10.0.pow(band.gainDb.toDouble().coerceIn(-24.0, 24.0) / 40.0)
        val w0 = 2.0 * PI * f / fs
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val c: DoubleArray = when (band.type) {
            BandType.PEAK -> doubleArrayOf(
                1.0 + alpha * a,
                -2.0 * cw,
                1.0 - alpha * a,
                1.0 + alpha / a,
                -2.0 * cw,
                1.0 - alpha / a,
            )

            BandType.LOW_SHELF -> {
                val s = 2.0 * sqrt(a) * alpha
                doubleArrayOf(
                    a * ((a + 1.0) - (a - 1.0) * cw + s),
                    2.0 * a * ((a - 1.0) - (a + 1.0) * cw),
                    a * ((a + 1.0) - (a - 1.0) * cw - s),
                    (a + 1.0) + (a - 1.0) * cw + s,
                    -2.0 * ((a - 1.0) + (a + 1.0) * cw),
                    (a + 1.0) + (a - 1.0) * cw - s,
                )
            }

            BandType.HIGH_SHELF -> {
                val s = 2.0 * sqrt(a) * alpha
                doubleArrayOf(
                    a * ((a + 1.0) + (a - 1.0) * cw + s),
                    -2.0 * a * ((a - 1.0) + (a + 1.0) * cw),
                    a * ((a + 1.0) + (a - 1.0) * cw - s),
                    (a + 1.0) - (a - 1.0) * cw + s,
                    2.0 * ((a - 1.0) - (a + 1.0) * cw),
                    (a + 1.0) - (a - 1.0) * cw - s,
                )
            }
        }
        b0[i] = c[0] / c[3]
        b1[i] = c[1] / c[3]
        b2[i] = c[2] / c[3]
        a1[i] = c[4] / c[3]
        a2[i] = c[5] / c[3]
    }

    private fun peakResponseDb(): Double {
        var peak = -200.0
        val fMin = 10.0
        val fMax = rate * 0.49
        val n = 384
        for (k in 0 until n) {
            val f = fMin * (fMax / fMin).pow(k.toDouble() / (n - 1))
            val w = 2.0 * PI * f / rate
            val c1 = cos(w)
            val c2 = cos(2.0 * w)
            var db = 0.0
            for (j in 0 until eqBands) {
                val i = order[j]
                val num = b0[i] * b0[i] + b1[i] * b1[i] + b2[i] * b2[i] +
                    2.0 * (b0[i] * b1[i] + b1[i] * b2[i]) * c1 + 2.0 * b0[i] * b2[i] * c2
                val den = 1.0 + a1[i] * a1[i] + a2[i] * a2[i] +
                    2.0 * (a1[i] + a1[i] * a2[i]) * c1 + 2.0 * a2[i] * c2
                db += 10.0 * log10(max(num / den, 1e-30))
            }
            if (db > peak) peak = db
        }
        return peak
    }

    private fun allocate() {
        laSize = max(8, (rate * LOOKAHEAD_SEC).roundToInt())
        laL = DoubleArray(laSize)
        laR = DoubleArray(laSize)
        pk = DoubleArray(laSize)
        atk = exp(-6.9 / laSize)
        rel = exp(-1.0 / (rate * RELEASE_SEC))
        xfDelay = max(1, (rate * XF_DELAY_SEC).roundToInt())
        xdL = DoubleArray(xfDelay + 1)
        xdR = DoubleArray(xfDelay + 1)
        clearState()
    }

    private fun clearState() {
        z1l.fill(0.0)
        z2l.fill(0.0)
        z1r.fill(0.0)
        z2r.fill(0.0)
        laL.fill(0.0)
        laR.fill(0.0)
        pk.fill(0.0)
        xdL.fill(0.0)
        xdR.fill(0.0)
        lw = 0
        xw = 0
        winMax = 0.0
        gain = 1.0
        lpL = 0.0
        lpR = 0.0
    }

    private fun flushDenormals() {
        for (i in 0 until MAX_BANDS) {
            if (abs(z1l[i]) < TINY) z1l[i] = 0.0
            if (abs(z2l[i]) < TINY) z2l[i] = 0.0
            if (abs(z1r[i]) < TINY) z1r[i] = 0.0
            if (abs(z2r[i]) < TINY) z2r[i] = 0.0
        }
        if (abs(lpL) < TINY) lpL = 0.0
        if (abs(lpR) < TINY) lpR = 0.0
    }

    private companion object {
        const val MAX_BANDS = 10
        const val LOOKAHEAD_SEC = 0.0015
        const val RELEASE_SEC = 0.150
        const val LIMIT = 0.966
        const val KNEE = 0.985
        const val XF_FC = 500.0
        const val XF_DELAY_SEC = 0.00015
        const val TINY = 1e-25
    }
}
