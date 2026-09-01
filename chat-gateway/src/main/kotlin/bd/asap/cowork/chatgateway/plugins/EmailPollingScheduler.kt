package bd.asap.cowork.chatgateway.plugins

import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.Task
import bd.asap.cowork.contextstore.EmailSettingsRepository
import bd.asap.cowork.orchestrator.Orchestrator
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.minutes

/**
 * The platform's first proactive (not user-triggered) agent work: on an
 * interval read from [EmailSettingsRepository], builds a synthetic
 * background-poll [Task] per enabled email account and hands it to the
 * existing [Orchestrator.handle] entry point — no orchestrator changes
 * needed, since that method already accepts a pre-built task with no live
 * chat message behind it. All the actual "what's new, is it important,
 * should this notify" logic lives inside `EmailAgent.checkForNewMail`; this
 * scheduler is deliberately just a trigger, on a loop that re-reads
 * settings every tick so a changed poll interval or account list takes
 * effect on the very next wait, not just after a restart.
 */
fun Application.configureEmailPolling() {
    val orchestrator by inject<Orchestrator>()
    val emailSettings by inject<EmailSettingsRepository>()

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    environment.monitor.subscribe(ApplicationStopping) { scope.cancel() }

    scope.launch {
        while (true) {
            val settings = emailSettings.get()
            val accountIds = settings.enabledAccountIds.ifEmpty { listOfNotNull(settings.defaultAccountId).toSet() }

            for (accountId in accountIds) {
                val task = Task(
                    capability = Capability.EMAIL,
                    input = "Check for new mail",
                    metadata = mapOf("trigger" to "background_poll", "accountId" to accountId),
                )
                orchestrator.handle(task).collect { /* logged by the agent's own Result event; nothing to forward to a live session here */ }
            }

            delay(settings.pollIntervalMinutes.minutes)
        }
    }
}
