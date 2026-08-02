package bd.asap.cowork.toolintegrations

import kotlin.test.Test
import kotlin.test.assertEquals

class IosSimulatorTargetingTest {
    // A trimmed but structurally real fixture of `xcrun simctl list devices --json`'s
    // output shape, across two runtimes, with an unavailable device mixed in.
    private val fixture = """
        {
          "devices": {
            "com.apple.CoreSimulator.SimRuntime.iOS-16-0": [
              {
                "udid": "AAAA-1111",
                "name": "iPhone 14",
                "state": "Shutdown",
                "isAvailable": true
              }
            ],
            "com.apple.CoreSimulator.SimRuntime.iOS-17-0": [
              {
                "udid": "BBBB-2222",
                "name": "iPhone 15",
                "state": "Booted",
                "isAvailable": true
              },
              {
                "udid": "CCCC-3333",
                "name": "iPhone 15 Pro",
                "state": "Shutdown",
                "isAvailable": false
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses devices flattened across every runtime`() {
        val devices = IosSimulatorTargeting.parseDevices(fixture)
        assertEquals(3, devices.size)
        assertEquals(setOf("iPhone 14", "iPhone 15", "iPhone 15 Pro"), devices.map { it.name }.toSet())
    }

    @Test
    fun `keeps unavailable devices out of the booted set but still parses them`() {
        val devices = IosSimulatorTargeting.parseDevices(fixture)
        val proMax = devices.first { it.udid == "CCCC-3333" }
        assertEquals(false, proMax.isAvailable)
        assertEquals("Shutdown", proMax.state)
    }

    @Test
    fun `malformed json returns an empty list rather than throwing`() {
        assertEquals(emptyList(), IosSimulatorTargeting.parseDevices("not json"))
    }
}
