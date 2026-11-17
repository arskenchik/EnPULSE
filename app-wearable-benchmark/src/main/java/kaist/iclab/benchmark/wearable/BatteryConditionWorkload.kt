package kaist.iclab.benchmark.wearable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/** Executes the real sensor and model workload for battery conditions B0-B6/B2P. */
class BatteryConditionWorkload(private val context: Context) : SensorEventListener {
    data class Spec(val condition: String, val description: String, val imuHz: Int? = null,
        val microphoneHz: Int? = null, val model: String? = null,
        val windowSamples: Int? = null, val hopSamples: Int? = null)
    data class Stats(val imuEvents: Long, val imuSamples: Long, val audioSamples: Long, val inferences: Long,
        val droppedWindows: Long, val modelLoadMs: Double, val meanLatencyMs: Double,
        val maxLatencyMs: Double, val p95LatencyMs: Double, val first5P95LatencyMs: Double,
        val last5P95LatencyMs: Double, val p95DriftMs: Double)

    companion object {
        val specs = mapOf(
            "B0" to Spec("B0", "Logger only"),
            "B1" to Spec("B1", "Accelerometer + gyroscope", imuHz = 50),
            "B2" to Spec("B2", "IMU + M1 (20 ms hop)", 50, model = "M1", windowSamples = 50, hopSamples = 1),
            "B2P" to Spec("B2P", "IMU + M1 (1 s hop)", 50, model = "M1", windowSamples = 50, hopSamples = 50),
            "B3" to Spec("B3", "IMU + M2", 50, model = "M2", windowSamples = 100, hopSamples = 50),
            "B4" to Spec("B4", "IMU + M3", 10, model = "M3", windowSamples = 20, hopSamples = 10),
            "B5" to Spec("B5", "Continuous microphone", microphoneHz = 16_000),
            "B6" to Spec("B6", "Continuous microphone + YAMNet", microphoneHz = 16_000, model = "A1", windowSamples = 15_600, hopSamples = 7_800))
    }

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensorThread = HandlerThread("battery-imu")
    private var recorder: AudioRecord? = null
    private var audioThread: Thread? = null
    private val recording = AtomicBoolean(false)
    private var spec: Spec? = null
    private var imuRunner: M1SamosaRunner? = null
    private var audioRunner: YamnetBatteryRunner? = null
    private val imuWindow = ArrayDeque<FloatArray>()
    private val latestGyro = FloatArray(3)
    private val latestAccel = FloatArray(3)
    private var imuSamples = 0L
    private var targetImuPeriodNs = 0L
    private var nextImuSampleNs = 0L
    private var samplesSinceInference = 0
    private var imuEvents = 0L
    private var audioSamples = 0L
    private var inferenceCount = 0L
    private var droppedWindows = 0L
    private var modelLoadMs = 0.0
    private var latencySumMs = 0.0
    private var maxLatencyMs = 0.0
    private val latencyReservoir = ArrayList<Double>(32_768)
    private val first5Latencies = ArrayList<Double>(1_024)
    private val last5Latencies = ArrayDeque<Pair<Long, Double>>()
    private val workloadStartElapsedMs = SystemClock.elapsedRealtime()

    fun start(condition: String): Spec {
        val selected = requireNotNull(specs[condition]) { "Unknown condition $condition" }
        spec = selected
        val loadStart = SystemClock.elapsedRealtimeNanos()
        selected.model?.let { model ->
            when (model) {
                "M1" -> imuRunner = M1SamosaRunner(context, SamosaModels.M1)
                "M2" -> imuRunner = M1SamosaRunner(context, SamosaModels.M2)
                "M3" -> imuRunner = M1SamosaRunner(context, SamosaModels.M3)
                "A1" -> audioRunner = YamnetBatteryRunner(context)
            }
            modelLoadMs = (SystemClock.elapsedRealtimeNanos() - loadStart) / 1_000_000.0
        }
        selected.imuHz?.let { hz ->
            targetImuPeriodNs = 1_000_000_000L / hz
            sensorThread.start()
            val handler = Handler(sensorThread.looper)
            listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE).forEach { type ->
                val sensor = sensors.getDefaultSensor(type, true) ?: sensors.getDefaultSensor(type)
                sensor?.let { sensors.registerListener(this, it, 1_000_000 / (hz * 2), handler) }
            }
        }
        selected.microphoneHz?.let { startAudio(it, selected.condition == "B6") }
        return selected
    }

    private fun startAudio(rate: Int, withModel: Boolean) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "RECORD_AUDIO permission required for B5/B6" }
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(rate)
        val r = AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        check(r.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
        recorder = r; recording.set(true); r.startRecording()
        audioThread = Thread({ if (withModel) runYamnetAudio(r) else runRawAudio(r, size) }, "battery-audio").also(Thread::start)
    }

    private fun runRawAudio(r: AudioRecord, bufferBytes: Int) {
        val buffer = ShortArray(bufferBytes / 2)
        while (recording.get()) {
            val count = r.read(buffer, 0, buffer.size)
            if (count > 0) synchronized(this) { audioSamples += count }
        }
    }

    private fun runYamnetAudio(r: AudioRecord) {
        val window = ShortArray(15_600)
        var filled = 0
        while (recording.get()) {
            val count = r.read(window, filled, window.size - filled)
            if (count <= 0) continue
            synchronized(this) { audioSamples += count }
            filled += count
            if (filled == window.size) {
                audioRunner?.run(window)?.let(::recordInference)
                System.arraycopy(window, 7_800, window, 0, 7_800)
                filled = 7_800
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        synchronized(this) { imuEvents++ }
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            synchronized(this) { System.arraycopy(event.values, 0, latestGyro, 0, 3) }
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            synchronized(this) { System.arraycopy(event.values, 0, latestAccel, 0, 3) }
            if (nextImuSampleNs == 0L) nextImuSampleNs = event.timestamp
            while (event.timestamp >= nextImuSampleNs) {
                captureImuSample()
                nextImuSampleNs += targetImuPeriodNs
            }
        }
    }

    private fun captureImuSample() {
        val selected = spec ?: return
        val sample = synchronized(this) {
            imuSamples++
            floatArrayOf(latestAccel[0], latestAccel[1], latestAccel[2], latestGyro[0], latestGyro[1], latestGyro[2])
        }
        if (imuRunner == null) return
        imuWindow.addLast(sample)
        while (imuWindow.size > requireNotNull(selected.windowSamples)) imuWindow.removeFirst()
        if (imuWindow.size < selected.windowSamples) return
        samplesSinceInference++
        if (samplesSinceInference < requireNotNull(selected.hopSamples)) return
        samplesSinceInference = 0
        val input = FloatArray(selected.windowSamples * 6)
        var index = 0
        for (sample in imuWindow) for (value in sample) input[index++] = value
        try { recordInference(requireNotNull(imuRunner).run(input).latencyMs) }
        catch (_: Throwable) { synchronized(this) { droppedWindows++ } }
    }

    @Synchronized private fun recordInference(latencyMs: Double) {
        inferenceCount++; latencySumMs += latencyMs
        if (latencyMs > maxLatencyMs) maxLatencyMs = latencyMs
        if (latencyReservoir.size < 32_768) latencyReservoir += latencyMs
        else latencyReservoir[(inferenceCount % latencyReservoir.size).toInt()] = latencyMs
        val elapsed = SystemClock.elapsedRealtime() - workloadStartElapsedMs
        if (elapsed <= 5 * 60_000L) first5Latencies += latencyMs
        last5Latencies.addLast(elapsed to latencyMs)
        while (last5Latencies.isNotEmpty() && elapsed - last5Latencies.first().first > 5 * 60_000L) {
            last5Latencies.removeFirst()
        }
    }

    @Synchronized fun stats(): Stats {
        fun p95(values: Collection<Double>): Double {
            val ordered = values.sorted()
            return if (ordered.isEmpty()) 0.0 else ordered[((ordered.size - 1) * 0.95).toInt()]
        }
        val overallP95 = p95(latencyReservoir)
        val firstP95 = p95(first5Latencies)
        val lastP95 = p95(last5Latencies.map { it.second })
        return Stats(imuEvents, imuSamples, audioSamples, inferenceCount, droppedWindows, modelLoadMs,
            if (inferenceCount == 0L) 0.0 else latencySumMs / inferenceCount, maxLatencyMs,
            overallP95, firstP95, lastP95, lastP95 - firstP95)
    }

    fun stop() {
        sensors.unregisterListener(this)
        if (sensorThread.isAlive) sensorThread.quitSafely()
        recording.set(false); recorder?.runCatching { stop() }; audioThread?.join(2_000); recorder?.release()
        imuRunner?.close(); audioRunner?.close()
        recorder = null; audioThread = null; imuRunner = null; audioRunner = null
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
