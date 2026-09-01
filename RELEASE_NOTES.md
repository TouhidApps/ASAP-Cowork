# ASAP-Cowork v0.2.0 (unreleased)

Covers everything since the `0.1.0` tag (2026-08-03) — five commits already on `main`, plus the Email/Google Workspace work built and verified in this session, still uncommitted as of this build. Not yet tagged or pushed.

## Highlights

- **Gmail integration** — connect a Google account via OAuth (Admin > Tools > Email), then check, search, read, send, and reply to email from chat. A background poller checks for new mail and can notify on important messages.
- **New: Google Sheets / Docs / Calendar**, on the same connected Google account:
  - List, create, read, and edit spreadsheets — including writing **live formulas** (any cell value starting with `=` is evaluated by Sheets, not stored as text)
  - Read Google Docs (full text)
  - Read upcoming Google Calendar events
- **In-app + desktop notifications** — background agent events (like new mail) push a WebSocket `notification` event to the open chat session instead of requiring a manual check.
- **Workspace change viewer** — every chat turn's file edits are tracked in a shadow git history and viewable/diffable from the UI.
- **Usage & cost tracking** in the admin panel (per-provider token/cost accounting).
- **New General-Purpose agent** — a catch-all for file edits, package installs, or one-off shell tasks that don't fit a specialist agent.
- Notes agent improvements; Ollama admin service improvements.
- Java/Gradle toolchain-version alignment and updated multi-module/Clean Architecture guidance for scaffolded projects.

## Email / Google Workspace details

New tools on the Email agent: `list_spreadsheets`, `create_spreadsheet`, `read_spreadsheet`, `update_spreadsheet_values`, `append_spreadsheet_row`, `read_google_doc`, `list_calendar_events` — alongside the existing Gmail tools.

The single Google OAuth connection now requests `gmail.modify` + `spreadsheets` + `documents` + `calendar` + `drive.metadata.readonly`. **Existing connected accounts need to reconnect once** (Admin > Tools > Email) to pick up the new scopes — Sheets/Docs/Calendar/Drive calls will fail with an auth error until then.

## Fixes

- Sheets writes now actually apply formulas instead of just describing them in the reply — an incorrect "you cannot add formulas" instruction was removed; writes use Sheets' `USER_ENTERED` mode, which already supported this.
- Fixed a real bug in Drive's spreadsheet-listing request: an unencoded space in `orderBy=modifiedTime desc` was making Google reject the request with 400 — this had been misdiagnosed as needing to reconnect the account.
- The agent no longer asks you to type your email address when only one Google account is connected — every tool's account parameter is now optional and resolves automatically.
- Turn-to-turn routing is more reliable: which capability handled the previous turn is now recorded as a fact (new `chat_messages.capability` column) instead of inferred from the reply text, fixing repeated misrouting of short follow-ups (e.g. "add a footer: Thank you") to the wrong agent.
- The general-purpose fallback agent no longer claims another agent's integration "doesn't exist," or contradicts a prior turn's real results, just because it can't see how they were done.

## Database

New Flyway migrations, applied automatically on next startup: `V6` (email accounts), `V7` (OAuth tokens), `V8` (`chat_messages.capability`).

## Known limitations

- Sheets: no cell formatting, no adding/renaming tabs, no batch structural edits — value writes only (including formulas).
- Docs and Calendar are read-only for now.
- Gmail: no delete, trash, or archive, by design.

## Upgrade notes

1. Rebuild and restart chat-gateway to pick up the new DB migration and routing/behavior changes.
2. Reconnect your Google account from Admin > Tools > Email so the new Sheets/Docs/Calendar/Drive scopes take effect.
