package kaist.iclab.benchmark.wearable

import android.content.Context
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** YAMNet runner matching the existing tracker-library VAD tensor contract. */
class YamnetBatteryRunner(context: Context) : AutoCloseable {
    companion object { const val INPUT_SAMPLES = 15_600; const val OUTPUT_CLASSES = 521 }
    private val model = context.assets.open("yamnet_classification.tflite").use { stream ->
        val bytes = stream.readBytes()
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
    }
    private val interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2); setUseXNNPACK(true) })
    private val input = ByteBuffer.allocateDirect(INPUT_SAMPLES * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(OUTPUT_CLASSES * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    init {
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(INPUT_SAMPLES)))
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, OUTPUT_CLASSES)))
    }
    @Synchronized fun run(pcm: ShortArray): Double {
        require(pcm.size == INPUT_SAMPLES)
        input.rewind(); val floats = input.asFloatBuffer(); for (sample in pcm) floats.put(sample / 32_768f)
        output.rewind(); val start = SystemClock.elapsedRealtimeNanos(); interpreter.run(input, output)
        return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
    }
    override fun close() = interpreter.close()
}
