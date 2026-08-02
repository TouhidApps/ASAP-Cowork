package bd.asap.cowork.toolintegrations

/** Tracks the backend dev server this server started — same reasoning as [MetroSession]: a Spring Boot/Node/FastAPI/PHP dev server is long-running and doesn't fit ProcessRunner's run-to-completion model, so it needs a session to track and stop later instead. */
object BackendServerSession {
    data class Running(val process: Process, val projectDir: String, val stack: String, val port: Int)

    @Volatile private var running: Running? = null

    fun current(): Running? = running
    fun set(value: Running?) {
        running = value
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread { running?.process?.destroyForcibly() })
    }
}
