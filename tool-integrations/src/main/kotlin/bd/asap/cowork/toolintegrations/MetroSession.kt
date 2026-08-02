package bd.asap.cowork.toolintegrations

/** Tracks the Metro bundler this server started — mirrors [EmulatorSession]'s reasoning: a debug React Native build needs it running to fetch JS, and only one meaningfully runs per project at a time. */
object MetroSession {
    data class Running(val process: Process, val projectDir: String, val port: Int)

    @Volatile private var running: Running? = null

    fun current(): Running? = running
    fun set(value: Running?) {
        running = value
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread { running?.process?.destroyForcibly() })
    }
}
