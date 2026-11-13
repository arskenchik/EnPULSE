package kaist.iclab.benchmark.wearable

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryConditionSpecTest {
    @Test fun modelConditionsMatchProtocolCadence() {
        val specs = BatteryConditionWorkload.specs
        assertEquals(1, specs.getValue("B2").hopSamples)
        assertEquals(50, specs.getValue("B2P").hopSamples)
        assertEquals(50, specs.getValue("B3").hopSamples)
        assertEquals(10, specs.getValue("B4").hopSamples)
        assertEquals(15_600, specs.getValue("B6").windowSamples)
        assertEquals(7_800, specs.getValue("B6").hopSamples)
    }
}
