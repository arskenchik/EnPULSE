package kaist.iclab.benchmark.wearable

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.File
import java.util.Locale

/** ADB-triggerable, labeled replay runner for deterministic on-watch evaluation. */
class ReplayService : Service() {
    companion object {
        const val ACTION_REPLAY = "kaist.iclab.benchmark.wearable.REPLAY"
        const val EXTRA_MODE = "mode"
        const val EXTRA_INPUT = "input"
        const val MODE_ACCURACY = "accuracy"
        const val MODE_LATENCY = "latency"
        private const val CHANNEL = "replay_evaluation"
        private const val NOTIFICATION = 9010
        private const val TAG = "M1ReplayService"
    }

    @Volatile private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Replay evaluation", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION, notification("Starting replay"))
        if (worker?.isAlive == true) return START_NOT_STICKY
        val mode = intent?.getStringExtra(EXTRA_MODE)?.lowercase(Locale.US) ?: MODE_ACCURACY
        require(mode == MODE_ACCURACY || mode == MODE_LATENCY) { "mode must be accuracy or latency" }
        val defaultInput = File(getExternalFilesDir("Replay"), "samosa_m1_test.bin")
        val input = File(intent?.getStringExtra(EXTRA_INPUT) ?: defaultInput.absolutePath)
        worker = Thread({ runReplay(input, mode) }, "m1-replay").apply { start() }
        return START_NOT_STICKY
    }

    private fun runReplay(input: File, mode: String) {
        try {
            require(input.isFile) { "Replay input does not exist: $input" }
            val outputDir = File(getExternalFilesDir("ReplayResults"),
                "m1_${mode}_${System.currentTimeMillis()}").apply { mkdirs() }
            val csv = File(outputDir, "predictions.csv")
            var measured = 0
            var correct = 0
            var latencySum = 0.0

            M1SamosaRunner(applicationContext).use { runner ->
                ReplayBinaryReader(input).use { reader ->
                    require(reader.header.rows == M1SamosaRunner.ROWS &&
                        reader.header.channels == M1SamosaRunner.CHANNELS) {
                        "M1 expects (50,6), file is (${reader.header.rows},${reader.header.channels})"
                    }
                    csv.bufferedWriter().use { writer ->
                        writer.appendLine("window_id,true_label,predicted_class,max_logit,max_probability,latency_ms,run,phase")
                        if (mode == MODE_ACCURACY) {
                            var window = reader.next()
                            while (window != null) {
                                val prediction = runner.run(window.values)
                                writeRow(writer, window, prediction, 1, "measured")
                                measured++
                                if (window.label == prediction.predictedClass) correct++
                                latencySum += prediction.latencyMs
                                if (measured % 500 == 0) update("Accuracy: $measured/${reader.header.count}")
                                window = reader.next()
                            }
                        } else {
                            val sample = ArrayList<ReplayWindow>(120)
                            repeat(120) { sample += requireNotNull(reader.next()) { "Latency mode needs 120 windows" } }
                            for (run in 1..3) {
                                sample.take(20).forEach { runner.run(it.values) }
                                sample.drop(20).take(100).forEach { window ->
                                    val prediction = runner.run(window.values)
                                    writeRow(writer, window, prediction, run, "measured")
                                    measured++
                                    if (window.label == prediction.predictedClass) correct++
                                    latencySum += prediction.latencyMs
                                }
                                update("Latency run $run/3")
                            }
                        }
                    }
                }
            }
            File(outputDir, "summary.txt").writeText(buildString {
                appendLine("mode=$mode")
                appendLine("input=${input.absolutePath}")
                appendLine("model=${M1SamosaRunner.MODEL_ASSET}")
                appendLine("threads=2")
                appendLine("xnnpack=true")
                appendLine("measured_windows=$measured")
                appendLine("correct=$correct")
                appendLine("accuracy=${if (measured > 0) correct.toDouble() / measured else 0.0}")
                appendLine("mean_latency_ms=${if (measured > 0) latencySum / measured else 0.0}")
            })
            Log.i(TAG, "Replay complete: ${outputDir.absolutePath}")
            update("Replay complete: $measured windows")
        } catch (error: Throwable) {
            Log.e(TAG, "Replay failed", error)
            update("Replay failed: ${error.message}")
        } finally {
            stopSelf()
        }
    }

    private fun writeRow(
        writer: java.io.BufferedWriter,
        window: ReplayWindow,
        prediction: ReplayPrediction,
        run: Int,
        phase: String,
    ) {
        writer.append(window.windowId.toString()).append(',')
            .append(window.label.toString()).append(',')
            .append(prediction.predictedClass.toString()).append(',')
            .append(prediction.maxLogit.toString()).append(',')
            .append(prediction.maxProbability.toString()).append(',')
            .append(String.format(Locale.US, "%.6f", prediction.latencyMs)).append(',')
            .append(run.toString()).append(',').append(phase).appendLine()
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(text))
    }

    private fun notification(text: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setContentTitle("M1 SaMOSA replay")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}
