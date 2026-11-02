package kaist.iclab.benchmark.wearable

import android.content.Context
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

data class ReplayPrediction(
    val predictedClass: Int,
    val maxLogit: Float,
    val maxProbability: Float,
    val latencyMs: Double,
)

/** Week 2 replacement baseline: complete (50,6) IMU model, not encoder + lab head. */
class M1SamosaRunner(context: Context) : AutoCloseable {
    companion object {
        const val MODEL_ASSET = "m1_samosa_float32.tflite"
        const val ROWS = 50
        const val CHANNELS = 6
        const val CLASSES = 19

        private val MEAN = floatArrayOf(
            0.46870455f, -5.353934f, 2.738805f, -0.017200477f, -0.006386256f, -0.028795335f
        )
        private val STD = floatArrayOf(
            5.8399177f, 4.692777f, 5.29747f, 1.4522898f, 0.7890654f, 0.85983855f
        )
    }

    private val model = context.assets.open(MODEL_ASSET).use { input ->
        val raw = input.readBytes()
        ByteBuffer.allocateDirect(raw.size).order(ByteOrder.nativeOrder()).apply {
            put(raw)
            rewind()
        }
    }
    private val interpreter = Interpreter(model, Interpreter.Options().apply {
        setNumThreads(2)
        setUseXNNPACK(true)
    })
    private val input = Array(1) { Array(ROWS) { FloatArray(CHANNELS) } }
    private val output = Array(1) { FloatArray(CLASSES) }

    fun run(raw: FloatArray): ReplayPrediction {
        require(raw.size == ROWS * CHANNELS) { "Expected ${ROWS * CHANNELS} values" }
        for (row in 0 until ROWS) {
            for (channel in 0 until CHANNELS) {
                val index = row * CHANNELS + channel
                input[0][row][channel] = (raw[index] - MEAN[channel]) / STD[channel]
            }
        }
        val start = SystemClock.elapsedRealtimeNanos()
        interpreter.run(input, output)
        val elapsed = SystemClock.elapsedRealtimeNanos() - start
        val logits = output[0]
        var predicted = 0
        for (index in 1 until CLASSES) if (logits[index] > logits[predicted]) predicted = index
        val max = logits[predicted]
        var denominator = 0.0
        for (logit in logits) denominator += exp((logit - max).toDouble())
        return ReplayPrediction(predicted, max, (1.0 / denominator).toFloat(), elapsed / 1_000_000.0)
    }

    override fun close() = interpreter.close()
}
