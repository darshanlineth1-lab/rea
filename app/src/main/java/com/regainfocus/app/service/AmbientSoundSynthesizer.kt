package com.regainfocus.app.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.regainfocus.app.domain.model.SoundscapeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 100% Legal, Zero-Copyright, Offline Real-Time DSP Soundscape Synthesizer.
 * Generates seamless 16-bit stereo PCM ambient study audio using Android's AudioTrack:
 * - Gentle Rain (Paul Kellet 3-pole pink noise + stochastic droplet impulses)
 * - Ocean Surf (0.11 Hz coastal swell LFO with stereo phase spread)
 * - Deep Brown Noise (Leaky-integrator Brownian focus noise)
 * - Soft White Noise (Warm low-pass filtered acoustic masking)
 * - 10 Hz Alpha Binaural Focus (200 Hz Left / 210 Hz Right sine + 100 Hz warm pad)
 */
class AmbientSoundSynthesizer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var synthJob: Job? = null

    @Volatile
    private var currentType: SoundscapeType = SoundscapeType.NONE

    @Volatile
    private var currentVolume: Float = 0.55f

    fun updatePlayback(type: SoundscapeType, volume: Float, shouldPlay: Boolean) {
        currentVolume = volume.coerceIn(0f, 1f)
        if (!shouldPlay || type == SoundscapeType.NONE || currentVolume <= 0.01f) {
            stop()
            return
        }
        if (synthJob?.isActive == true && currentType == type) {
            return
        }
        stop()
        currentType = type
        startEngine(type)
    }

    fun stop() {
        currentType = SoundscapeType.NONE
        synthJob?.cancel()
        synthJob = null
    }

    private fun startEngine(type: SoundscapeType) {
        synthJob = scope.launch {
            val sampleRate = 22050
            val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufBytes = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
                .coerceAtLeast(4096)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(encoding)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufBytes * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch

            val framesPerChunk = 1024
            val pcmBuffer = ShortArray(framesPerChunk * 2)
            val rng = Random(System.nanoTime())

            // DSP filter state
            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var brownL = 0.0
            var brownR = 0.0
            var lpL = 0.0
            var lpR = 0.0
            var dropletEnv = 0.0
            var dropletFreq = 900.0
            var dropletPhase = 0.0
            var sampleIndex = 0L

            try {
                track.play()
                while (isActive && currentType == type) {
                    val vol = (currentVolume * 0.42f).toDouble()

                    for (i in 0 until framesPerChunk) {
                        val t = sampleIndex.toDouble() / sampleRate.toDouble()
                        var left = 0.0
                        var right = 0.0

                        when (type) {
                            SoundscapeType.NONE -> Unit

                            SoundscapeType.WHITE_NOISE -> {
                                val wL = rng.nextDouble(-1.0, 1.0)
                                val wR = rng.nextDouble(-1.0, 1.0)
                                lpL += 0.28 * (wL - lpL)
                                lpR += 0.28 * (wR - lpR)
                                left = lpL * 0.65
                                right = lpR * 0.65
                            }

                            SoundscapeType.BROWN_NOISE -> {
                                val wL = rng.nextDouble(-1.0, 1.0)
                                val wR = rng.nextDouble(-1.0, 1.0)
                                brownL = ((brownL + 0.025 * wL) / 1.025).coerceIn(-1.0, 1.0)
                                brownR = ((brownR + 0.025 * wR) / 1.025).coerceIn(-1.0, 1.0)
                                left = brownL * 2.6
                                right = brownR * 2.6
                            }

                            SoundscapeType.RAIN -> {
                                val white = rng.nextDouble(-1.0, 1.0)
                                b0 = 0.99765 * b0 + white * 0.0990460
                                b1 = 0.96300 * b1 + white * 0.2965164
                                b2 = 0.57000 * b2 + white * 1.0526913
                                val pink = (b0 + b1 + b2 + white * 0.1848) * 0.16

                                if (dropletEnv < 0.001 && rng.nextInt(1400) == 0) {
                                    dropletEnv = rng.nextDouble(0.12, 0.32)
                                    dropletFreq = rng.nextDouble(650.0, 1650.0)
                                }
                                val drop = if (dropletEnv > 0.001) {
                                    dropletPhase += (2.0 * PI * dropletFreq) / sampleRate
                                    dropletEnv *= 0.992
                                    sin(dropletPhase) * dropletEnv
                                } else 0.0

                                left = pink + drop * 0.7
                                right = pink * 0.96 + drop * 0.55
                            }

                            SoundscapeType.OCEAN_WAVES -> {
                                val wL = rng.nextDouble(-1.0, 1.0)
                                val wR = rng.nextDouble(-1.0, 1.0)
                                brownL = ((brownL + 0.03 * wL) / 1.03)
                                brownR = ((brownR + 0.03 * wR) / 1.03)
                                val swellL = 0.28 + 0.72 * (0.5 + 0.5 * sin(2.0 * PI * 0.11 * t))
                                val swellR = 0.28 + 0.72 * (0.5 + 0.5 * sin(2.0 * PI * 0.11 * t + 0.45))
                                left = brownL * 2.8 * swellL
                                right = brownR * 2.8 * swellR
                            }

                            SoundscapeType.BINAURAL_ALPHA -> {
                                val carrierL = sin(2.0 * PI * 200.0 * t) * 0.50
                                val carrierR = sin(2.0 * PI * 210.0 * t) * 0.50
                                val warmPad = sin(2.0 * PI * 100.0 * t) * 0.22
                                left = carrierL + warmPad
                                right = carrierR + warmPad
                            }
                        }

                        val sL = (left * vol * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        val sR = (right * vol * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                        pcmBuffer[i * 2] = sL
                        pcmBuffer[i * 2 + 1] = sR
                        sampleIndex++
                    }

                    track.write(pcmBuffer, 0, pcmBuffer.size)
                }
            } catch (_: Exception) {
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }
}
