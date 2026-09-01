package bd.asap.cowork.agents.email

import bd.asap.cowork.agentsdk.Agent
import bd.asap.cowork.agentsdk.AgentEvent
import bd.asap.cowork.agentsdk.Capability
import bd.asap.cowork.agentsdk.ConversationTurn
import bd.asap.cowork.agentsdk.ProjectContextView
import bd.asap.cowork.agentsdk.Task
import bd.asap.cowork.agentsdk.ToolActivityStatus as AgentToolActivityStatus
import bd.asap.cowork.contextstore.EmailAccount
import bd.asap.cowork.contextstore.EmailAccountRepository
import bd.asap.cowork.contextstore.EmailNotificationMode
import bd.asap.cowork.contextstore.EmailSettingsRepository
import bd.asap.cowork.llmgateway.AgentStreamEvent
import bd.asap.cowork.llmgateway.ChatMessage
import bd.asap.cowork.llmgateway.ChatRole
import bd.asap.cowork.llmgateway.LlmProviderRegistry
import bd.asap.cowork.llmgateway.ToolActivityStatus
import bd.asap.cowork.llmgateway.ToolExecutor
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import bd.asap.cowork.toolintegrations.calendar.CalendarApiClient
import bd.asap.cowork.toolintegrations.calendar.CalendarEventSummary
import bd.asap.cowork.toolintegrations.docs.DocsApiClient
import bd.asap.cowork.toolintegrations.drive.DriveApiClient
import bd.asap.cowork.toolintegrations.drive.DriveFileSummary
import bd.asap.cowork.toolintegrations.email.GmailApiClient
import bd.asap.cowork.toolintegrations.email.GmailMessageSummary
import bd.asap.cowork.toolintegrations.email.GmailOAuthClient
import bd.asap.cowork.toolintegrations.email.GmailOAuthCredentialsRegistry
import bd.asap.cowork.toolintegrations.notify.Notification
import bd.asap.cowork.toolintegrations.notify.NotificationDispatcher
import bd.asap.cowork.toolintegrations.notify.NotificationSeverity
import bd.asap.cowork.toolintegrations.sheets.SheetsApiClient
import bd.asap.cowork.toolintegrations.sheets.SpreadsheetCreated
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Checks, reads, searches, and sends Gmail, and reads Sheets/Docs/Calendar,
 * on the user's behalf — via Gmail's, Sheets', Docs', Calendar's, and (for
 * listing spreadsheets, which neither Sheets nor Docs' own API can do)
 * Drive's REST APIs ([GmailApiClient], [SheetsApiClient], [DocsApiClient],
 * [CalendarApiClient], [DriveApiClient]) — no browser involved at all. The
 * user connects an account once via "Sign in with Google" (OAuth, initiated
 * from the admin panel's Email tools page —
 * [bd.asap.cowork.chatgateway.features.email.EmailService]), which is what
 * this agent actually reads from ([accounts]); multiple accounts can be
 * connected side by side. One connected account backs all five services —
 * see `GOOGLE_OAUTH_SCOPES`. Gmail read/write; Sheets read/create/edit
 * (list, create, overwrite a range, append rows — formulas work too, since
 * writes use Sheets' USER_ENTERED mode, but no cell formatting or
 * sheet/tab management yet) and its Drive-metadata listing; Docs/Calendar
 * are read-only for v1 (their API clients are stubs — see each one's doc
 * comment). Never deletes, trashes, or archives anything in Gmail: no such
 * tool is defined anywhere this agent's tool list is built, in [tools]
 * below or in `GmailApiClient` — an intentional structural omission, not
 * just a prompt instruction, mirroring how
 * [bd.asap.cowork.agents.notes.NotesAgent] withholds `add_note` under
 * `readOnly`. (The `gmail.modify` OAuth scope this app requests technically
 * *permits* moving mail to Trash — Google only forbids *permanent*
 * deletion under that scope — but nothing here ever calls that endpoint.)
 *
 * Two very different execution paths share this one class:
 * - A normal chat request (`task.metadata["trigger"] != "background_poll"`)
 *   runs the full agentic tool-calling loop, exactly like every other agent.
 * - The background poll (`task.metadata["trigger"] == "background_poll"`,
 *   built by chat-gateway's `EmailPollingScheduler`) skips the LLM tool loop
 *   entirely and runs [checkForNewMail], a deterministic routine: list the
 *   inbox, diff against the persisted cursor, judge importance when the
 *   mode calls for it, and dispatch notifications directly — see
 *   [checkForNewMail]'s doc comment.
 */
class EmailAgent(
    private val providers: LlmProviderRegistry,
    private val accounts: EmailAccountRepository,
    private val emailSettings: EmailSettingsRepository,
    private val notifications: NotificationDispatcher,
) : Agent {
    override val id: String = "email-agent"
    override val capabilities: Set<Capability> = setOf(Capability.EMAIL)
    override val description: String =
        "Checks, reads, searches, and summarizes Gmail, and sends or replies to email, via a connected Google account (OAuth). Also lists, creates, reads, and edits Google Sheets (cell ranges — overwrite a range or add rows), reads Google Docs (full text), and reads Google Calendar (upcoming events) from that same connected account. Use for 'check my email/inbox', 'any new/important mail', 'read/find/search that email about...', 'send/reply to ...', 'list/show my (google) sheets/spreadsheets', 'create/read/edit/update that spreadsheet/sheet', 'add a row/formula/total to the sheet', 'calculate ... in the sheet', 'read/open that doc', or 'what's on my calendar' requests. Never deletes, trashes, or archives anything; can't edit Docs or Calendar events, and can't reformat/add sheets/tabs within a spreadsheet."

    override fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent> = flow {
        if (task.metadata["trigger"] == "background_poll") {
            emit(AgentEvent.Result(checkForNewMail(task.metadata["accountId"])))
            return@flow
        }

        // Which account a bare messageId (read_email/mark_email_read/reply_to_email
        // don't repeat emailAddress) refers to — set whenever a tool call names an
        // account explicitly, so those follow-up calls resolve against the same
        // account a preceding list_inbox/search_inbox/list_gmail_accounts named.
        var activeEmailAddress: String? = defaultAccountEmail()

        val executor = ToolExecutor { name, input, _ -> handleToolCall(name, input) { activeEmailAddress = it ?: activeEmailAddress; activeEmailAddress } }
        val reply = StringBuilder()

        providers.current()
            .runAgenticLoop(systemPrompt(), task.input, tools, executor, { name, _ -> toolLabel(name) }, history = task.history.map { it.toChatMessage() })
            .collect { event ->
                when (event) {
                    is AgentStreamEvent.TextDelta -> {
                        reply.append(event.text)
                        emit(AgentEvent.TextDelta(event.text))
                    }
                    is AgentStreamEvent.ToolActivity ->
                        emit(AgentEvent.ToolActivity(event.tool, event.label, event.status.toAgentEventStatus()))
                }
            }

        emit(AgentEvent.Result(reply.toString().trim()))
    }

    private suspend fun handleToolCall(name: String, input: Map<String, Any?>, activeAccount: (String?) -> String?): ToolResult =
        when (name) {
            LIST_ACCOUNTS -> {
                val list = accounts.list()
                if (list.isEmpty()) {
                    ToolResult("No Gmail account is connected yet — connect one from the admin panel's Email tools page (Admin > Tools > Email).")
                } else {
                    if (list.size == 1) activeAccount(list.single().emailAddress)
                    ToolResult(list.joinToString("\n") { "- ${it.emailAddress}${if (it.isDefault) " (default)" else ""}" })
                }
            }
            LIST_INBOX -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    GmailApiClient.listInbox(token).fold({ formatMessages(it) }, { e -> ToolResult("Couldn't read the inbox: ${e.message}", isError = true) })
                }
            }
            SEARCH_INBOX -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val query = (input["query"] as? String).orEmpty()
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    GmailApiClient.listInbox(token, query).fold({ formatMessages(it) }, { e -> ToolResult("Couldn't search: ${e.message}", isError = true) })
                }
            }
            READ_EMAIL -> {
                val messageId = (input["messageId"] as? String).orEmpty()
                withToken(activeAccount(null)) { _, token ->
                    GmailApiClient.getMessage(token, messageId).fold(
                        { ToolResult("From: ${it.sender}\nSubject: ${it.subject}\n\n${it.body}") },
                        { e -> ToolResult("Couldn't open message \"$messageId\": ${e.message}", isError = true) },
                    )
                }
            }
            MARK_READ -> {
                val messageId = (input["messageId"] as? String).orEmpty()
                withToken(activeAccount(null)) { _, token ->
                    GmailApiClient.markRead(token, messageId).fold({ ToolResult("Marked as read.") }, { e -> ToolResult("Couldn't mark as read: ${e.message}", isError = true) })
                }
            }
            SEND_EMAIL -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val to = (input["to"] as? String).orEmpty()
                val subject = (input["subject"] as? String).orEmpty()
                val body = (input["body"] as? String).orEmpty()
                withToken(emailAddress) { _, token ->
                    GmailApiClient.sendNew(token, to, subject, body).fold({ ToolResult("Sent to $to.") }, { e -> ToolResult("Couldn't send: ${e.message}", isError = true) })
                }
            }
            REPLY_EMAIL -> {
                val messageId = (input["messageId"] as? String).orEmpty()
                val body = (input["body"] as? String).orEmpty()
                withToken(activeAccount(null)) { _, token ->
                    val original = GmailApiClient.getMessage(token, messageId)
                        .getOrElse { e -> return@withToken ToolResult("Couldn't find message \"$messageId\": ${e.message}", isError = true) }
                    val replyTo = extractEmailAddress(original.sender)
                        ?: return@withToken ToolResult("Couldn't figure out who to reply to from \"${original.sender}\".", isError = true)
                    GmailApiClient.sendReply(token, original, replyTo, body).fold({ ToolResult("Reply sent.") }, { e -> ToolResult("Couldn't send reply: ${e.message}", isError = true) })
                }
            }
            LIST_SPREADSHEETS -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val nameContains = (input["nameContains"] as? String)
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    DriveApiClient.listSpreadsheets(token, nameContains).fold(
                        { formatSpreadsheetList(it) },
                        { e -> ToolResult("Couldn't list spreadsheets: ${e.message}", isError = true) },
                    )
                }
            }
            CREATE_SPREADSHEET -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val title = (input["title"] as? String).orEmpty()
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    SheetsApiClient.createSpreadsheet(token, title).fold(
                        { formatCreatedSpreadsheet(it) },
                        { e -> ToolResult("Couldn't create the spreadsheet: ${e.message}", isError = true) },
                    )
                }
            }
            READ_SPREADSHEET -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val spreadsheetId = (input["spreadsheetId"] as? String).orEmpty()
                val range = (input["range"] as? String).orEmpty()
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    SheetsApiClient.getValues(token, spreadsheetId, range).fold(
                        { formatSpreadsheetValues(it) },
                        { e -> ToolResult("Couldn't read the spreadsheet: ${e.message}", isError = true) },
                    )
                }
            }
            UPDATE_SPREADSHEET -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val spreadsheetId = (input["spreadsheetId"] as? String).orEmpty()
                val range = (input["range"] as? String).orEmpty()
                val values = parseRows(input["values"])
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    SheetsApiClient.updateValues(token, spreadsheetId, range, values).fold(
                        { ToolResult("Updated $range.") },
                        { e -> ToolResult("Couldn't update the spreadsheet: ${e.message}", isError = true) },
                    )
                }
            }
            APPEND_SPREADSHEET_ROW -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val spreadsheetId = (input["spreadsheetId"] as? String).orEmpty()
                val range = (input["range"] as? String).orEmpty()
                val values = parseRows(input["values"])
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    SheetsApiClient.appendValues(token, spreadsheetId, range, values).fold(
                        { ToolResult("Appended ${values.size} row(s).") },
                        { e -> ToolResult("Couldn't append to the spreadsheet: ${e.message}", isError = true) },
                    )
                }
            }
            READ_DOC -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val documentId = (input["documentId"] as? String).orEmpty()
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    DocsApiClient.getDocument(token, documentId).fold(
                        { ToolResult(it.ifBlank { "(empty document)" }) },
                        { e -> ToolResult("Couldn't read the document: ${e.message}", isError = true) },
                    )
                }
            }
            LIST_CALENDAR_EVENTS -> {
                val emailAddress = (input["emailAddress"] as? String) ?: activeAccount(null)
                val calendarId = (input["calendarId"] as? String).takeUnless { it.isNullOrBlank() } ?: "primary"
                withToken(emailAddress) { account, token ->
                    activeAccount(account.emailAddress)
                    CalendarApiClient.listUpcomingEvents(token, calendarId).fold(
                        { formatEvents(it) },
                        { e -> ToolResult("Couldn't read the calendar: ${e.message}", isError = true) },
                    )
                }
            }
            else -> ToolResult("Unknown tool: $name", isError = true)
        }

    private suspend fun withToken(emailAddress: String?, block: suspend (EmailAccount, String) -> ToolResult): ToolResult {
        if (emailAddress.isNullOrBlank()) {
            return ToolResult("More than one Gmail account is connected — call list_gmail_accounts and ask which one to use.", isError = true)
        }
        val account = accounts.list().firstOrNull { it.emailAddress.equals(emailAddress, ignoreCase = true) }
            ?: return ToolResult("\"$emailAddress\" isn't a connected Gmail account — call list_gmail_accounts to see what's available.", isError = true)
        val token = validAccessToken(account)
            ?: return ToolResult("\"$emailAddress\"'s connection has expired — reconnect it from the admin panel's Email tools page.", isError = true)
        return block(account, token)
    }

    /** Refreshes the stored access token when it's expired (or about to), persisting the new one — returns null if there's no usable token at all (never connected, or the refresh itself failed, e.g. access was revoked). */
    private suspend fun validAccessToken(account: EmailAccount): String? {
        val tokens = accounts.getTokens(account.id) ?: return null
        if (tokens.expiresAt > System.currentTimeMillis() + TOKEN_REFRESH_BUFFER_MS) return tokens.accessToken

        val credentials = GmailOAuthCredentialsRegistry.current() ?: return null
        val refreshed = GmailOAuthClient.refreshAccessToken(credentials, tokens.refreshToken).getOrNull() ?: return null
        val expiresAt = System.currentTimeMillis() + refreshed.expiresInSeconds * 1000
        accounts.updateAccessToken(account.id, refreshed.accessToken, expiresAt)
        return refreshed.accessToken
    }

    private fun extractEmailAddress(fromHeader: String): String? =
        Regex("<([^>]+)>").find(fromHeader)?.groupValues?.get(1)
            ?: fromHeader.trim().takeIf { it.contains("@") }

    private fun formatMessages(messages: List<GmailMessageSummary>): ToolResult {
        if (messages.isEmpty()) return ToolResult("No messages found.")
        val text = messages.joinToString("\n") { m ->
            "${if (m.unread) "[unread] " else ""}id=${m.messageId} | from: ${m.sender} | subject: ${m.subject} | ${m.snippet}"
        }
        return ToolResult(text)
    }

    private fun formatSpreadsheetList(files: List<DriveFileSummary>): ToolResult {
        if (files.isEmpty()) return ToolResult("No spreadsheets found in this account's Drive.")
        val text = files.joinToString("\n") { f -> "spreadsheetId=${f.fileId} | ${f.name}${f.modifiedTime?.let { " | modified $it" }.orEmpty()}" }
        return ToolResult(text)
    }

    private fun formatCreatedSpreadsheet(created: SpreadsheetCreated): ToolResult =
        ToolResult("Created. spreadsheetId=${created.spreadsheetId} — ${created.url}")

    /** The tool-call JSON arg for a 2D `values` grid arrives as `List<Any?>` of `List<Any?>` — flatten each cell to its string form. */
    private fun parseRows(rawValues: Any?): List<List<String>> =
        (rawValues as? List<*>).orEmpty().map { row -> (row as? List<*>).orEmpty().map { it?.toString().orEmpty() } }

    private fun formatSpreadsheetValues(rows: List<List<String>>): ToolResult {
        if (rows.isEmpty()) return ToolResult("No values found in that range.")
        return ToolResult(rows.joinToString("\n") { row -> row.joinToString(" | ") })
    }

    private fun formatEvents(events: List<CalendarEventSummary>): ToolResult {
        if (events.isEmpty()) return ToolResult("No upcoming events found.")
        val text = events.joinToString("\n") { e ->
            "id=${e.eventId} | ${e.title} | ${e.start} - ${e.end}${e.location?.let { " | $it" }.orEmpty()}"
        }
        return ToolResult(text)
    }

    private suspend fun defaultAccountEmail(): String? =
        accounts.list().let { list -> list.firstOrNull { it.isDefault }?.emailAddress ?: list.singleOrNull()?.emailAddress }

    /**
     * Deterministic routine the background poller runs on every tick (no LLM
     * tool-calling): list the inbox, work out which messages weren't seen on
     * the previous tick, judge importance with a cheap LLM call when the
     * mode requires it, dispatch a notification per qualifying message, and
     * advance the cursor past every message seen this tick regardless of
     * whether it notified — so nothing is re-evaluated next time.
     */
    private suspend fun checkForNewMail(accountId: String?): String {
        val account = accountId?.let { accounts.find(it) } ?: return "No account configured to poll."
        val settings = emailSettings.get()

        val token = validAccessToken(account)
            ?: return "Skipped poll for ${account.emailAddress}: connection expired — reconnect from the admin panel's Email tools page."

        val messages = GmailApiClient.listInbox(token).getOrElse { return "Skipped poll for ${account.emailAddress}: ${it.message}" }

        // First-ever poll for this account: there's no prior cursor to diff
        // against, and treating the whole existing inbox as "new" would
        // flood the user with notifications for mail they've already seen.
        // Establish the baseline cursor instead and notify starting next tick.
        if (account.lastSeenMessageId == null) {
            messages.firstOrNull()?.let { accounts.updateCursor(account.id, it.messageId, System.currentTimeMillis()) }
            return "Established baseline for ${account.emailAddress} (${messages.size} messages currently in view)."
        }

        // messages is newest-first. Everything above where the previous
        // cursor now sits is new since the last tick. If the cursor message
        // scrolled past this page entirely (more than one page's worth
        // arrived in one interval), this falls back to treating every
        // fetched row as new rather than guessing — a shorter poll interval
        // avoids this in practice.
        val seenIndex = messages.indexOfFirst { it.messageId == account.lastSeenMessageId }
        val newMessages = if (seenIndex == -1) messages else messages.subList(0, seenIndex)

        if (newMessages.isEmpty()) return "No new mail for ${account.emailAddress}."

        val channelIds = buildSet {
            if (settings.osEnabled) add("desktop")
            if (settings.inAppEnabled) add("in_app")
        }

        var notifiedCount = 0
        for (message in newMessages.asReversed()) {
            val shouldNotify = settings.mode == EmailNotificationMode.ALL || isImportant(message)
            if (shouldNotify && channelIds.isNotEmpty()) {
                notifications.dispatch(
                    Notification(
                        title = "New email from ${message.sender}",
                        body = message.subject.ifBlank { message.snippet },
                        sourceAgentId = id,
                        severity = if (settings.mode == EmailNotificationMode.IMPORTANT_ONLY) NotificationSeverity.IMPORTANT else NotificationSeverity.INFO,
                    ),
                    channelIds,
                )
                notifiedCount++
            }
        }

        messages.firstOrNull()?.let { accounts.updateCursor(account.id, it.messageId, System.currentTimeMillis()) }
        return "${account.emailAddress}: ${newMessages.size} new message(s), $notifiedCount notified."
    }

    private suspend fun isImportant(message: GmailMessageSummary): Boolean {
        val reply = StringBuilder()
        providers.current()
            .streamComplete(
                IMPORTANCE_SYSTEM_PROMPT,
                listOf(ChatMessage(ChatRole.USER, "Sender: ${message.sender}\nSubject: ${message.subject}\nSnippet: ${message.snippet}")),
                fast = true,
            )
            .collect { reply.append(it) }
        return reply.toString().trim().uppercase().startsWith("YES")
    }

    private fun ToolActivityStatus.toAgentEventStatus(): AgentToolActivityStatus = when (this) {
        ToolActivityStatus.STARTED -> AgentToolActivityStatus.STARTED
        ToolActivityStatus.FINISHED -> AgentToolActivityStatus.FINISHED
        ToolActivityStatus.FAILED -> AgentToolActivityStatus.FAILED
    }

    private fun toolLabel(name: String): String = when (name) {
        LIST_ACCOUNTS -> "Checking connected Gmail accounts"
        LIST_INBOX -> "Reading inbox"
        SEARCH_INBOX -> "Searching mail"
        READ_EMAIL -> "Opening message"
        MARK_READ -> "Marking message read"
        SEND_EMAIL -> "Sending email"
        REPLY_EMAIL -> "Sending reply"
        LIST_SPREADSHEETS -> "Listing spreadsheets"
        CREATE_SPREADSHEET -> "Creating spreadsheet"
        READ_SPREADSHEET -> "Reading spreadsheet"
        UPDATE_SPREADSHEET -> "Updating spreadsheet"
        APPEND_SPREADSHEET_ROW -> "Adding row to spreadsheet"
        READ_DOC -> "Reading document"
        LIST_CALENDAR_EVENTS -> "Checking calendar"
        else -> name
    }

    private companion object {
        const val TOKEN_REFRESH_BUFFER_MS = 60_000L

        const val LIST_ACCOUNTS = "list_gmail_accounts"
        const val LIST_INBOX = "list_inbox"
        const val SEARCH_INBOX = "search_inbox"
        const val READ_EMAIL = "read_email"
        const val MARK_READ = "mark_email_read"
        const val SEND_EMAIL = "send_email"
        const val REPLY_EMAIL = "reply_to_email"
        const val LIST_SPREADSHEETS = "list_spreadsheets"
        const val CREATE_SPREADSHEET = "create_spreadsheet"
        const val READ_SPREADSHEET = "read_spreadsheet"
        const val UPDATE_SPREADSHEET = "update_spreadsheet_values"
        const val APPEND_SPREADSHEET_ROW = "append_spreadsheet_row"
        const val READ_DOC = "read_google_doc"
        const val LIST_CALENDAR_EVENTS = "list_calendar_events"

        /**
         * Deliberately absent from every tool's `required` list: this agent
         * already resolves a missing/omitted emailAddress to the single
         * connected account automatically ([defaultAccountEmail], and
         * [activeAccount] once one's been established this turn). Marking it
         * required here would (and, before this was fixed, did) push the
         * model to treat it as something it must always supply — and, with
         * no account address visible in its own text history to reuse, to
         * just ask the user for their email address in plain chat instead of
         * calling list_gmail_accounts, even when only one account exists.
         */
        const val OPTIONAL_ACCOUNT_PARAM_DESC =
            "Which connected Google account to use. Optional — leave it out when there's only one connected account or one is already established in this conversation; it resolves automatically."

        val tools = listOf(
            ToolSpec(
                name = LIST_ACCOUNTS,
                description = "Lists every Gmail account the user has connected (via OAuth) to this app. Call this whenever it's unclear which account a request refers to, or before the first email action in a conversation if no default account is configured.",
                parametersSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any?>()),
            ),
            ToolSpec(
                name = LIST_INBOX,
                description = "Lists the most recent messages in the given Gmail account's inbox (sender, subject, snippet, read/unread).",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf("emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC)),
                    "required" to emptyList<String>(),
                ),
            ),
            ToolSpec(
                name = SEARCH_INBOX,
                description = "Searches the given Gmail account's mail using Gmail's own search syntax (e.g. \"from:alice subject:invoice\", \"is:unread\").",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "query" to mapOf("type" to "string", "description" to "Gmail search query."),
                    ),
                    "required" to listOf("query"),
                ),
            ),
            ToolSpec(
                name = READ_EMAIL,
                description = "Opens one message (by the messageId from list_inbox/search_inbox) and returns its full body text.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf("messageId" to mapOf("type" to "string", "description" to "The messageId from a prior list_inbox/search_inbox result.")),
                    "required" to listOf("messageId"),
                ),
            ),
            ToolSpec(
                name = MARK_READ,
                description = "Marks one message as read without opening its full content — use when the request only wants unread counts cleared, not the content read back.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf("messageId" to mapOf("type" to "string", "description" to "The messageId from a prior list_inbox/search_inbox result.")),
                    "required" to listOf("messageId"),
                ),
            ),
            ToolSpec(
                name = SEND_EMAIL,
                description = "Composes and sends a brand-new email from the given Gmail account.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "to" to mapOf("type" to "string", "description" to "Recipient email address."),
                        "subject" to mapOf("type" to "string"),
                        "body" to mapOf("type" to "string"),
                    ),
                    "required" to listOf("to", "subject", "body"),
                ),
            ),
            ToolSpec(
                name = REPLY_EMAIL,
                description = "Replies to an existing message (by the messageId from list_inbox/search_inbox/read_email) and sends the reply.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "messageId" to mapOf("type" to "string", "description" to "The messageId to reply to."),
                        "body" to mapOf("type" to "string"),
                    ),
                    "required" to listOf("messageId", "body"),
                ),
            ),
            ToolSpec(
                name = LIST_SPREADSHEETS,
                description = "Lists the Google Sheets that exist in the connected account's Drive (name, spreadsheetId, last-modified time), most-recently-modified first. Use this when the user wants to see/pick a sheet rather than naming a specific spreadsheetId — e.g. 'show my sheets', 'which spreadsheets do I have', 'open my cost list'.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "nameContains" to mapOf("type" to "string", "description" to "Optional filter — only list spreadsheets whose title contains this text."),
                    ),
                    "required" to emptyList<String>(),
                ),
            ),
            ToolSpec(
                name = CREATE_SPREADSHEET,
                description = "Creates a brand-new, blank Google Sheet with the given title, owned by the connected Google account. Returns its spreadsheetId and edit URL.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "title" to mapOf("type" to "string", "description" to "The spreadsheet's title."),
                    ),
                    "required" to listOf("title"),
                ),
            ),
            ToolSpec(
                name = READ_SPREADSHEET,
                description = "Reads a range of cell values from a Google Sheet the connected account can access.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "spreadsheetId" to mapOf("type" to "string", "description" to "The spreadsheet's id, from its URL (.../spreadsheets/d/{spreadsheetId}/...)."),
                        "range" to mapOf("type" to "string", "description" to "A1 notation range, e.g. \"Sheet1!A1:D20\"."),
                    ),
                    "required" to listOf("spreadsheetId", "range"),
                ),
            ),
            ToolSpec(
                name = UPDATE_SPREADSHEET,
                description = "Overwrites the cells in a specific range of a Google Sheet the connected account can access. Cells not covered by the given values grid are left untouched. Call read_spreadsheet first if you need to know what's already there. A cell value starting with \"=\" (e.g. \"=SUM(D2:D11)\") is written as a live formula, not literal text — use this for totals/calculations instead of just describing the formula in your reply.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "spreadsheetId" to mapOf("type" to "string", "description" to "The spreadsheet's id, from its URL (.../spreadsheets/d/{spreadsheetId}/...)."),
                        "range" to mapOf("type" to "string", "description" to "A1 notation range to overwrite, e.g. \"Sheet1!A2:C2\"."),
                        "values" to mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                            "description" to "Row-major 2D grid of cell values to write, sized to match range.",
                        ),
                    ),
                    "required" to listOf("spreadsheetId", "range", "values"),
                ),
            ),
            ToolSpec(
                name = APPEND_SPREADSHEET_ROW,
                description = "Adds one or more new rows to a Google Sheet, right after the last row with data — use this instead of update_spreadsheet_values when you're adding to a list rather than overwriting a known cell/range. A cell value starting with \"=\" is written as a live formula, not literal text.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "spreadsheetId" to mapOf("type" to "string", "description" to "The spreadsheet's id, from its URL (.../spreadsheets/d/{spreadsheetId}/...)."),
                        "range" to mapOf("type" to "string", "description" to "Sheet or A1 range identifying which sheet/table to append to, e.g. \"Sheet1\" or \"Sheet1!A:C\"."),
                        "values" to mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                            "description" to "Row-major 2D grid of the new row(s) to add.",
                        ),
                    ),
                    "required" to listOf("spreadsheetId", "range", "values"),
                ),
            ),
            ToolSpec(
                name = READ_DOC,
                description = "Reads the full plain-text body of a Google Doc the connected account can access.",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "documentId" to mapOf("type" to "string", "description" to "The document's id, from its URL (.../document/d/{documentId}/...)."),
                    ),
                    "required" to listOf("documentId"),
                ),
            ),
            ToolSpec(
                name = LIST_CALENDAR_EVENTS,
                description = "Lists upcoming events on the connected Google account's calendar (title, start/end time, location).",
                parametersSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "emailAddress" to mapOf("type" to "string", "description" to OPTIONAL_ACCOUNT_PARAM_DESC),
                        "calendarId" to mapOf("type" to "string", "description" to "Which calendar to list — defaults to the account's primary calendar if omitted."),
                    ),
                    "required" to emptyList<String>(),
                ),
            ),
        )

        val IMPORTANCE_SYSTEM_PROMPT = buildString {
            appendLine("You judge whether an email is important enough to interrupt the user with a notification.")
            appendLine("Reply with ONLY YES or NO, nothing else.")
            appendLine("YES for anything urgent, time-sensitive, from a real person addressing the user directly, or clearly requiring action.")
            appendLine("NO for routine notifications, marketing, newsletters, automated receipts, or anything that can wait.")
        }

        fun systemPrompt() = buildString {
            appendLine("You are the Email agent inside ASAP-Cowork. You read and send Gmail, and list/create/read/edit Sheets and read Docs/Calendar, via one connected Google account (OAuth).")
            appendLine("Every tool's emailAddress parameter is optional — omit it and it resolves to the one connected account automatically. Only call list_gmail_accounts first if there might genuinely be more than one account and it's unclear which is meant; if that's the case, ask the user to pick from the list it returns. Never ask the user to type out their email address from memory — you can always call list_gmail_accounts to see it instead.")
            appendLine("list_inbox/search_inbox return each message's messageId — reuse that exact id for read_email, mark_email_read, and reply_to_email; don't invent one.")
            appendLine("You can read, search, summarize, and send/reply to email. You cannot delete, trash, or archive anything — there is no tool for that, so never claim to have deleted or removed an email.")
            appendLine("IMPORTANT — you have no memory of anything beyond what's visibly written in this conversation, turn to turn. Whenever a reply of yours mentions a specific spreadsheet, doc, or event by name, you MUST also state its id (spreadsheetId/documentId/eventId) in that same reply, even if the user didn't ask for it — that id is the only way a later message like 'edit my data' or 'add this to the sheet' can be resolved back to the right item. Never omit it to keep a reply tidy.")
            appendLine("If the user refers to a spreadsheet/doc by name and you don't already see its id written somewhere earlier in this conversation, call list_spreadsheets yourself to resolve it — don't ask the user to paste a link, id, or export a file; you already have direct API access, there's no 'no Sheets/Drive access in this session' situation, ever.")
            appendLine("For spreadsheets: use append_spreadsheet_row to add new row(s) to the end of a list, and update_spreadsheet_values to overwrite a specific known range — read_spreadsheet first if you're not sure what's already there or which row is next.")
            appendLine("Formulas DO work: any cell value you write via update_spreadsheet_values/append_spreadsheet_row that starts with \"=\" (e.g. \"=SUM(D2:D11)\") is evaluated as a real live formula, exactly like typing it into the sheet — writes use Sheets' USER_ENTERED mode for this reason. When the user asks for a formula-based total, don't just describe the formula in your reply — call update_spreadsheet_values (or append_spreadsheet_row) with that formula string as one of the cell values, so it's actually written into the sheet.")
            appendLine("You cannot edit a Google Doc or create/change calendar events, and you cannot apply cell formatting (bold, colors, number formats) or add/rename sheets/tabs within a spreadsheet — so never claim to have done any of that.")
            appendLine("Never claim to have created, added, edited, or listed something unless you actually called the matching tool THIS turn and it returned success — don't narrate an action you didn't perform, even if a similar one succeeded earlier in the conversation.")
        }
    }
}

private fun ConversationTurn.toChatMessage() =
    ChatMessage(if (role == "assistant") ChatRole.ASSISTANT else ChatRole.USER, content)
