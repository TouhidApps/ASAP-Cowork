# ASAP-Cowork

An AI agent platform for building mobile apps end to end — planning, code generation, testing, debugging, and shipping to app stores.

## Setup (one-time)

1. Install a **Java 21+ runtime** if you don't already have one (e.g. from https://adoptium.net).
2. Copy `.env.example` to `.env` in this same folder, and fill in at least one LLM provider API key (Anthropic, OpenAI, or Gemini). You can also point it at a local Ollama install instead — see the comments in `.env.example`.

## Running it

- **macOS**: double-click `start.command` (or run `./start.sh` in a terminal).
- **Windows**: double-click `start.bat`.
- **Linux**: run `./start.sh` in a terminal.

This starts two local processes — `build-runner` (owns all builds/emulators/device access) and `chat-gateway` (the chat server, which also serves the web UI) — and opens your browser to **http://localhost:8081** once it's ready.

To stop, close the terminal window (or press Ctrl+C on macOS/Linux). On Windows, also close the separate "ASAP-Cowork build-runner" window that opened alongside it.

## Where things go

- Generated projects are created under a `workspace/` folder next to these files.
- Chat history and settings are stored in a `data/` folder next to these files — safe to back up, safe to delete if you want a clean slate (you'll lose chat history).

## What you still need for full functionality

ASAP-Cowork drives real developer tools rather than faking them — it doesn't bundle these, so install what you need for the platforms you're targeting:

- **Android**: Android Studio / Android SDK + platform tools (`adb`), a Java 21+ JDK
- **iOS**: a Mac with Xcode installed (not just Command Line Tools) for real builds/simulators
- **Flutter**: the Flutter SDK
- **React Native / Node backends**: Node.js + npm
- **Python backends**: Python 3
- **PHP backends**: PHP + MySQL/PostgreSQL if you want the database-backed admin panel to actually run

You don't need all of these — only the ones for the platform(s) you're actually building. The agent will tell you plainly if something it needs isn't installed.
