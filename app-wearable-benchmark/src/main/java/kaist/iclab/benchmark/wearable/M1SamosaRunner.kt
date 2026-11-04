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

data class SamosaModelSpec(
    val id: String,
    val asset: String,
    val rows: Int,
    val mean: FloatArray,
    val std: FloatArray,
)

object SamosaModels {
    const val CHANNELS = 6
    const val CLASSES = 19

    val M1 = SamosaModelSpec(
        "m1", "m1_samosa_float32.tflite", 50,
        floatArrayOf(
            0.46870455f, -5.353934f, 2.738805f, -0.017200477f, -0.006386256f, -0.028795335f
        ),
        floatArrayOf(
            5.8399177f, 4.692777f, 5.29747f, 1.4522898f, 0.7890654f, 0.85983855f
        ),
    )
    val M2 = SamosaModelSpec(
        "m2", "m2_samosa_float32.tflite", 100,
        M1.mean.copyOf(), M1.std.copyOf(),
    )
    val M3 = SamosaModelSpec(
        "m3", "m3_samosa_float32.tflite", 20,
        floatArrayOf(
            0.46650633f, -5.3541913f, 2.7367618f, -0.018836468f, -0.007033491f, -0.028414898f
        ),
        floatArrayOf(
            5.844831f, 4.701788f, 5.2644677f, 1.4518188f, 0.7907644f, 0.8600537f
        ),
    )

    fun require(id: String): SamosaModelSpec = when (id.lowercase()) {
        "m1" -> M1
        "m2" -> M2
        "m3" -> M3
        else -> error("model must be m1, m2, or m3")
    }
}

/** Complete IMU replacement baseline runner, not encoder + lab head. */
class M1SamosaRunner(context: Context, val spec: SamosaModelSpec = SamosaModels.M1) : AutoCloseable {

    private val model = context.assets.open(spec.asset).use { input ->
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
    private val input = Array(1) { Array(spec.rows) { FloatArray(SamosaModels.CHANNELS) } }
    private val output = Array(1) { FloatArray(SamosaModels.CLASSES) }

    fun run(raw: FloatArray): ReplayPrediction {
        require(raw.size == spec.rows * SamosaModels.CHANNELS) {
            "Expected ${spec.rows * SamosaModels.CHANNELS} values"
        }
        for (row in 0 until spec.rows) {
            for (channel in 0 until SamosaModels.CHANNELS) {
                val index = row * SamosaModels.CHANNELS + channel
                input[0][row][channel] = (raw[index] - spec.mean[channel]) / spec.std[channel]
            }
        }
        val start = SystemClock.elapsedRealtimeNanos()
        interpreter.run(input, output)
        val elapsed = SystemClock.elapsedRealtimeNanos() - start
        val logits = output[0]
        var predicted = 0
        for (index in 1 until SamosaModels.CLASSES) if (logits[index] > logits[predicted]) predicted = index
        val max = logits[predicted]
        var denominator = 0.0
        for (logit in logits) denominator += exp((logit - max).toDouble())
        return ReplayPrediction(predicted, max, (1.0 / denominator).toFloat(), elapsed / 1_000_000.0)
    }

    override fun close() = interpreter.close()
}
