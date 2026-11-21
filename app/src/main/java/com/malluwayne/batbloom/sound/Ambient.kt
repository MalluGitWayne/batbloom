package com.malluwayne.batbloom.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Soundscapes made on the phone from noise: no audio files, nothing downloaded. */
enum class Sound(val label: String) {
    Off("Off"),
    Rain("Rain"),
    Brown("Brown noise"),
    Ocean("Ocean"),
    Night("Night crickets"),
}

/**
 * Generates one soundscape, sample by sample, as floats in -1..1. Pure (no Android), so it is unit tested.
 * Rain: pink noise with drops. Brown: deep, soft rumble. Ocean: brown noise swelling like waves.
 * Night: a quiet breeze with two crickets chirping at their own pace.
 */
class SoundGenerator(private val sound: Sound, val sampleRate: Int = 44_100, seed: Int = 1) {
    private val rnd = Random(seed)
    private var t = 0L
    // pink noise (Paul Kellet's filter)
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
    private var b3 = 0.0; private var b4 = 0.0; private var b5 = 0.0; private var b6 = 0.0
    // brown noise
    private var brown = 0.0
    // rain drops
    private var dropLeft = 0
    private var dropAmp = 0.0
    private var dropLp = 0.0

    private fun white() = rnd.nextDouble() * 2 - 1

    private fun pink(): Double {
        val w = white()
        b0 = 0.99886 * b0 + w * 0.0555179
        b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522
        b5 = -0.7616 * b5 - w * 0.0168980
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
        return out * 0.11
    }

    private fun brownNoise(): Double {
        brown = (brown + 0.02 * white()) / 1.02
        return brown * 3.5
    }

    private fun cricket(timeS: Double, period: Double, offset: Double, freq: Double): Double {
        // A chirp: three 18 ms pulses with 14 ms gaps, once per period.
        val p = ((timeS + offset) % period)
        val pulse = (p / 0.032).toInt()
        val within = p % 0.032
        if (pulse > 2 || within > 0.018) return 0.0
        val env = sin(PI * within / 0.018)
        return sin(2 * PI * freq * timeS) * env
    }

    fun next(): Float {
        val timeS = t.toDouble() / sampleRate
        t++
        val v = when (sound) {
            Sound.Off -> 0.0
            Sound.Brown -> brownNoise() * 0.8
            Sound.Rain -> {
                // Drops: short bursts of bright noise that decay quickly.
                if (dropLeft <= 0 && rnd.nextDouble() < 0.0009) {
                    dropLeft = 260 + rnd.nextInt(500)
                    dropAmp = 0.15 + rnd.nextDouble() * 0.45
                }
                var drop = 0.0
                if (dropLeft > 0) {
                    dropLeft--
                    dropLp += (white() - dropLp) * 0.55
                    drop = dropLp * dropAmp * exp(-(760 - dropLeft).coerceAtLeast(0) / 140.0)
                }
                pink() * 0.75 + drop
            }
            Sound.Ocean -> {
                // Waves roll in about every 9 s; the swell rises faster than it falls.
                val phase = (timeS % 9.0) / 9.0
                val swell = if (phase < 0.4) sin(PI / 2 * phase / 0.4) else 0.5 + 0.5 * kotlin.math.cos(PI * (phase - 0.4) / 0.6)
                brownNoise() * (0.25 + 0.6 * swell) + pink() * 0.25 * swell
            }
            Sound.Night -> brownNoise() * 0.22 +
                cricket(timeS, 0.86, 0.0, 4300.0) * 0.10 +
                cricket(timeS, 1.13, 0.37, 3900.0) * 0.07
        }
        return v.coerceIn(-1.0, 1.0).toFloat()
    }

    fun fill(buf: FloatArray) { for (i in buf.indices) buf[i] = next() }
}

/** Plays one soundscape in the background, with a gentle fade in and out. */
object AmbientPlayer {
    /** Each playback has its own number; an older one fades out by itself when a newer one starts. */
    @Volatile private var generation = 0
    @Volatile var volume = 0.6f
    @Volatile var current: Sound = Sound.Off
        private set

    @Synchronized
    fun play(sound: Sound, vol: Float) {
        volume = vol
        if (sound == current) return
        val my = ++generation
        current = sound
        if (sound == Sound.Off) return
        Thread({ loop(sound, my) }, "batbloom-ambient").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        generation++
        current = Sound.Off
    }

    private fun loop(sound: Sound, my: Int) {
        val rate = 44_100
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setBufferSizeInBytes(maxOf(minBuf, 8192) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        val gen = SoundGenerator(sound, rate, seed = (System.nanoTime() % 100_000).toInt())
        val f = FloatArray(2048)
        val s = ShortArray(f.size)
        var gain = 0f
        val step = 1f / (rate * 1.5f) // 1.5 s fade
        try {
            track.play()
            while (true) {
                gen.fill(f)
                val live = generation == my
                val target = if (live) volume else 0f
                for (i in f.indices) {
                    gain += (if (gain < target) step else if (gain > target) -step else 0f)
                    s[i] = (f[i] * gain * 32_000).toInt().coerceIn(-32_768, 32_767).toShort()
                }
                track.write(s, 0, s.size)
                if (!live && gain <= 0.001f) break
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
