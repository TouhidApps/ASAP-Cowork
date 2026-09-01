package bd.asap.cowork.chatgateway

import bd.asap.cowork.chatgateway.plugins.configureDI
import bd.asap.cowork.chatgateway.plugins.configureDatabase
import bd.asap.cowork.chatgateway.plugins.configureEmailPolling
import bd.asap.cowork.chatgateway.plugins.configureHTTP
import bd.asap.cowork.chatgateway.plugins.configureRouting
import bd.asap.cowork.chatgateway.plugins.configureSerialization
import bd.asap.cowork.chatgateway.plugins.configureSockets
import bd.asap.cowork.chatgateway.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val contextDatabase = configureDatabase()
    configureDI(contextDatabase)
    configureSerialization()
    configureStatusPages()
    configureHTTP()
    configureSockets()
    configureRouting()
    configureEmailPolling()
}
