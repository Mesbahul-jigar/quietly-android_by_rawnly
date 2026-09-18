package com.quietly.app

import android.content.Context
import android.media.*
import android.media.audiofx.AudioEffect
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import com.quietly.app.dsp.BiquadFilter
import com.quietly.app.dsp.SpectralSubtractor
import com.quietly.app.dsp.Vad
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Phone microphone -> noise suppression -> selected headphone output. */
class AncEngine(
    private val ctx: Context,
    private val onError: (String) -> Unit,
    private val onMetrics: (Int, Double) -> Unit
) {
    private var thread: Thread? = null
    @Volatile private var running = false
    private var sampleRate = 48000
    private var frameSize = 1024
    private var strength = 1f
    private var hpHz = 80
    private var lpHz = 14000
    @Volatile private var lastLatencyMs = 0
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private val effects = mutableListOf<AudioEffect>()

    fun measuredLatencyMs() = lastLatencyMs

    @Synchronized
    fun start(sr: Int, fft: Int, strength: Float) {
        if (running) return
        check(thread?.isAlive != true) { "Audio is still stopping. Please retry." }
        require(sr in 8000..48000 && fft in 128..4096)
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        check(am.mode == AudioManager.MODE_NORMAL) { "End the active call before starting monitoring." }
        val output = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLE_HEADSET)
        } ?: error("Connect headphones before starting monitoring.")
        val input = am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
        } ?: error("Phone microphone is unavailable.")
        sampleRate = sr; frameSize = fft; this.strength = strength.coerceIn(0f, 1f)
        try {
            val inMin = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val outMin = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(inMin > 0 && outMin > 0) { "Unsupported audio sample rate." }
            val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(inMin, fft * 4))
            record = rec
            check(rec.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed." }
            check(rec.setPreferredDevice(input)) { "Could not select phone microphone." }
            if (strength > 0f) {
                if (NoiseSuppressor.isAvailable()) runCatching {
                    NoiseSuppressor.create(rec.audioSessionId)?.let { effects.add(it); it.enabled = true }
                }
                if (AcousticEchoCanceler.isAvailable()) runCatching {
                    AcousticEchoCanceler.create(rec.audioSessionId)?.let { effects.add(it); it.enabled = true }
                }
            }
            val out = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(max(outMin, fft * 4)).setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
            track = out
            check(out.state == AudioTrack.STATE_INITIALIZED) { "Output initialization failed." }
            check(out.setPreferredDevice(output)) { "Could not select headphones." }
            out.setVolume(0.35f)
            rec.startRecording()
            check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone could not start." }
            out.play()
            running = true
            thread = Thread({ loop(rec, out, input.id, output.id) }, "QuietlyAudio").also { it.start() }
        } catch (e: Exception) {
            running = false
            release()
            throw e
        }
    }

    @Synchronized
    fun applyMode(sr: Int, fft: Int, strength: Float) {
        val restart = running
        stop()
        if (restart) start(sr, fft, strength)
        else { sampleRate = sr; frameSize = fft; this.strength = strength }
    }

    @Synchronized
    fun setEq(hp: Int, mid: Int, lp: Int) {
        val restart = running
        stop()
        hpHz = hp; lpHz = lp // Original preset's mid frequency has no gain/Q definition.
        if (restart) start(sampleRate, frameSize, strength)
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { record?.stop() }
        runCatching { track?.pause() }
        thread?.join(1500)
        check(thread?.isAlive != true) { "Audio is still stopping. Please retry." }
        thread = null
    }

    private fun release() {
        effects.forEach { runCatching { it.release() } }; effects.clear()
        runCatching { record?.stop() }; runCatching { record?.release() }; record = null
        runCatching { track?.stop() }; runCatching { track?.release() }; track = null
    }

    private fun loop(rec: AudioRecord, out: AudioTrack, inputId: Int, outputId: Int) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val sr = sampleRate
            val size = frameSize
            val frame = ShortArray(size)
            val ss = SpectralSubtractor(size)
            val vad = Vad()
            val hp = BiquadFilter.highPass(sr.toDouble(), hpHz.toDouble())
            val lp = BiquadFilter.lowPass(sr.toDouble(), lpHz.toDouble().coerceAtMost(sr * 0.45))
            var counter = 0
            while (running) {
                val n = rec.read(frame, 0, size)
                if (!running) break
                check(n > 0) { "Microphone read failed ($n)." }
                // Stop on a route change instead of feeding the phone speaker.
                if (counter > 2) {
                    check(out.routedDevice?.id == outputId) { "Headphones disconnected or output route changed." }
                    check(rec.routedDevice?.id == inputId) { "Microphone route changed." }
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    check(rec.activeRecordingConfiguration?.isClientSilenced != true) { "Another app is using the microphone." }
                }
                var sum = 0.0
                for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                val rms = sqrt(sum / n)
                val db = if (rms > 0) 20 * log10(rms / 32768) else -90.0
                if (strength > 0f) {
                    ss.process(frame, n, !vad.isVoice(frame, n), strength)
                    for (i in 0 until n) {
                        val v = lp.process(hp.process(frame[i] / 32768.0))
                        frame[i] = (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                    }
                }
                var offset = 0
                while (running && offset < n) {
                    val written = out.write(frame, offset, n - offset)
                    if (!running) break
                    check(written > 0) { "Audio output failed ($written)." }
                    offset += written
                }
                if (++counter % 8 == 0) {
                    lastLatencyMs = (2000.0 * size / sr).toInt()
                    onMetrics(lastLatencyMs, db)
                }
            }
        } catch (e: Exception) {
            if (running) onError(e.message ?: "Audio stopped unexpectedly.")
        } finally {
            running = false
            release()
        }
    }
}
