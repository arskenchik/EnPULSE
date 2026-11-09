package kaist.iclab.benchmark.wearable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.media.*
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/** Sensor workload required by battery conditions; it never stores raw sensor/audio data. */
class BatteryConditionWorkload(private val context: Context) : SensorEventListener {
    data class Spec(val condition: String, val description: String, val imuHz: Int? = null, val microphoneHz: Int? = null, val model: String? = null)
    companion object {
        val specs = mapOf(
            "B0" to Spec("B0", "Logger only"),
            "B1" to Spec("B1", "Accelerometer + gyroscope", imuHz = 50),
            "B2" to Spec("B2", "IMU + M1", imuHz = 50, model = "M1"),
            "B3" to Spec("B3", "IMU + M2", imuHz = 50, model = "M2"),
            "B4" to Spec("B4", "IMU + M3", imuHz = 10, model = "M3"),
            "B5" to Spec("B5", "Continuous microphone", microphoneHz = 16_000),
            "B6" to Spec("B6", "Continuous microphone + A1", microphoneHz = 16_000, model = "A1"))
    }
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var recorder: AudioRecord? = null
    private var audioThread: Thread? = null
    private val recording = AtomicBoolean(false)
    var imuEvents = 0L; private set
    var audioSamples = 0L; private set

    fun start(condition: String): Spec {
        val spec = requireNotNull(specs[condition])
        spec.imuHz?.let { hz ->
            listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE).forEach { type ->
                sensors.getDefaultSensor(type)?.let { sensors.registerListener(this, it, 1_000_000 / hz) }
            }
        }
        spec.microphoneHz?.let(::startAudio)
        return spec
    }
    private fun startAudio(rate: Int) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "RECORD_AUDIO permission required for B5/B6" }
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(rate)
        val r = AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        check(r.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
        recorder = r; recording.set(true); r.startRecording()
        audioThread = Thread({
            val buffer = ShortArray(size / 2)
            while (recording.get()) r.read(buffer, 0, buffer.size).takeIf { it > 0 }?.let { audioSamples += it }
        }, "battery-audio").also(Thread::start)
    }
    fun stop() {
        sensors.unregisterListener(this); recording.set(false)
        recorder?.runCatching { stop() }; audioThread?.join(2_000); recorder?.release()
        recorder = null; audioThread = null
    }
    override fun onSensorChanged(event: SensorEvent?) { imuEvents++ }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
