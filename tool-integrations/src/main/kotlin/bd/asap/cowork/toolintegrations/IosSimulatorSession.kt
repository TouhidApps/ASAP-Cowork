package bd.asap.cowork.toolintegrations

/**
 * Tracks the simulator this server most recently booted. Unlike
 * [EmulatorSession], `xcrun simctl boot` returns as soon as boot is
 * *requested* rather than handing back a process to own for the
 * simulator's lifetime — CoreSimulator manages that lifecycle itself — so
 * this only remembers which UDID to prefer, not a process handle to kill.
 */
object IosSimulatorSession {
    @Volatile private var udid: String? = null

    fun currentUdid(): String? = udid
    fun set(value: String?) {
        udid = value
    }
}
