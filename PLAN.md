# PLAN.md

Technical architecture and roadmap for ASAP-Cowork: an AI agent platform that takes a mobile app from idea to production. This document expands on `CLAUDE.md` with a concrete multi-agent architecture, module breakdown, and phased roadmap.

## 1. Architectural Philosophy

Treat intelligence like microservices: one **orchestrator** (the brain) plus a set of **specialist agents**, each owning one narrow responsibility. The orchestrator never does the specialist work itself — it detects context, decomposes intent into tasks, routes tasks to the right agent(s), collects results, resolves conflicts, and keeps a persistent model of the project so follow-up requests ("add dark mode") are understood in context instead of treated as a fresh prompt.

Two architectural rules follow from this:

- **Agents are stateless workers.** All persistent understanding of the project (detected stack, file graph, conversation history, decisions made) lives in the orchestrator's `ProjectContext`, not inside an agent. Agents receive the context slice they need for a task and return a result — they don't remember anything between calls.
- **Start as a modular monolith, design for future service extraction.** Build this as one multi-module Kotlin/Ktor application with agents as separate modules communicating through a well-defined internal contract (not ad-hoc function calls). If a specific agent (e.g. the CI/CD or publishing agent) later needs independent scaling or isolation, it can be pulled out into its own deployable service without changing the contract other agents rely on.

## 2. The Orchestrator

The orchestrator is the only component that talks to the user and the only component that mutates `ProjectContext`. Its core responsibilities:

1. **Project detection (fingerprinting).** Inspect the workspace for signature files and infer stack(s) present, e.g.:
   - `build.gradle` / `build.gradle.kts` + `AndroidManifest.xml` → Android/Kotlin
   - `settings.gradle.kts` with `kotlin("multiplatform")` and `shared`/`commonMain` source sets → KMP
   - `pubspec.yaml` → Flutter
   - `*.xcodeproj` / `*.xcworkspace` / `Podfile` → iOS/Swift
   - `package.json` with `react-native` dependency → React Native
   - `pom.xml` or `build.gradle` with `spring-boot` → backend (Spring Boot); `package.json` with `express`/`fastify` → backend (Node)

   A project can match multiple signatures at once (e.g. KMP app + Spring Boot backend in one repo) — the orchestrator activates every agent whose signature matched, and nothing else.

2. **Context tracking.** Maintain a live `ProjectContext`: detected stack(s), module/file graph, active branch/feature being worked on, conversation history, decisions and their rationale, and the current task graph. This is what lets "add dark mode" resolve directly to the right files instead of asking the user to re-explain the project.

3. **Task decomposition & routing.** Turn a user request into a DAG of tasks, each tagged with the capability required (e.g. `android.ui`, `testing.unit`, `publish.playstore`). Route each task to the agent(s) that declare that capability.

4. **Result aggregation & conflict resolution.** Collect streamed results from agents, serialize writes when multiple tasks touch the same file/module, and surface a single coherent, streamed response to the chat UI.

5. **Lazy agent activation.** Only the agents relevant to the detected project type(s) are instantiated/invoked — a pure-Flutter project never wakes the Android or iOS native agents.

### Agent contract

Every agent implements the same interface so the orchestrator can treat them uniformly:

```kotlin
interface Agent {
    val id: String
    val capabilities: Set<Capability>          // e.g. Capability.ANDROID_BUILD, Capability.TEST_UNIT
    fun canHandle(task: Task): Boolean
    suspend fun execute(task: Task, context: ProjectContextView): Flow<AgentEvent>
}
```

- `ProjectContextView` is a read-only, capability-scoped slice of the full context (an iOS agent never sees Android internals unless relevant).
- `AgentEvent` is a stream of progress/log/result/file-change events, which lets the orchestrator forward live progress to the chat UI instead of waiting for a final blob.
- Agents declare capabilities rather than the orchestrator hardcoding "if Flutter, call FlutterAgent" — this keeps the roster extensible (see §5).

## 3. Agent Roadmap

| # | Agent | Responsibility |
|---|-------|-----------------|
| 1 | Project & Requirements Agent | Turns a raw idea into user stories, scope, and acceptance criteria |
| 2 | Architecture Advisor Agent | Chooses layering/patterns (MVVM, Clean Architecture, modularization strategy) |
| 3 | Tech Stack Recommendation Agent | Recommends native vs. cross-platform, libraries, backend stack given requirements |
| 4 | Project Scaffolding Agent | Generates the initial project skeleton for the chosen stack(s) |
| 5 | Android Development Agent | Kotlin/Android feature implementation |
| 6 | iOS Development Agent | Swift/iOS feature implementation |
| 7 | KMP Development Agent | Shared Kotlin Multiplatform module implementation |
| 8 | Flutter Development Agent | Flutter/Dart feature implementation |
| 9 | Backend Development Agent | Spring Boot / Node backend implementation |
| 10 | Testing Agent | Unit, UI, and integration test authoring |
| 11 | Debugging Agent | Runs/attaches to emulator, reads logcat, diagnoses failures, proposes fixes |
| 12 | CI/CD Build Agent | Configures and runs build pipelines |
| 13 | Publishing Agent | Firebase App Distribution + Google Play Console uploads |

Supporting (non-code-generation) agents used across the whole lifecycle, per `CLAUDE.md`:

| Agent | Responsibility |
|-------|-----------------|
| Branding Agent | Logo, naming, visual identity |
| Legal Doc Agent | Terms & Conditions / privacy policy generation |
| Landing Page Agent | Marketing/landing page generation |
| Store Asset Agent | Converts raw screenshots into Play Console/App Store–ready images |
| Media Capture Agent | Captures emulator screenshots/video for store listings and bug reports |

Planned expansion (phase 2+, not in initial build):

| Agent | Responsibility |
|-------|-----------------|
| Documentation Agent | Generates/maintains README, API docs, architecture docs |
| Analytics Agent | Wires up analytics SDKs and event tracking |
| Security Review Agent | Static analysis, dependency/CVE scanning, secrets detection |
| Performance Optimization Agent | Profiling, startup time, memory/battery analysis |

## 4. Module Architecture

Single Gradle multi-module project. Each agent is its own module implementing the shared `agent-sdk` contract, so it can be developed, tested, and (later) extracted independently.

```
asap-cowork/
├── orchestrator-core/        # project fingerprinting, ProjectContext, task graph, routing, conflict resolution
├── agent-sdk/                 # Agent interface, Task/AgentEvent/Capability contracts, shared DTOs
├── agents/
│   ├── requirements-agent/
│   ├── architecture-advisor-agent/
│   ├── techstack-agent/
│   ├── scaffolding-agent/
│   ├── android-agent/
│   ├── ios-agent/
│   ├── kmp-agent/
│   ├── flutter-agent/
│   ├── react-native-agent/
│   ├── backend-agent/
│   ├── testing-agent/
│   ├── debugging-agent/
│   ├── cicd-agent/
│   ├── publishing-agent/
│   ├── branding-agent/
│   ├── legal-doc-agent/
│   ├── landing-page-agent/
│   └── store-asset-agent/
├── tool-integrations/         # thin clients around external tools/APIs (see §6)
├── context-store/             # persistence for ProjectContext (Exposed/Postgres or SQLite for local mode)
├── llm-gateway/                # multi-provider LLM access (Claude, OpenAI, Gemini, Ollama); prompt templates, tool-calling glue
├── build-runner/               # dedicated service: owns all build/emulator/device execution, isolated from orchestrator
├── chat-gateway/               # Ktor module: WebSocket/SSE endpoint, session management, event fan-out to UI
└── web-ui/                     # React chat application (separate build, own package.json)
```

`orchestrator-core` depends on `agent-sdk` and `context-store`; every agent module depends only on `agent-sdk`, never on `orchestrator-core` or on each other — this is what keeps them swappable and independently extractable later.

### LLM provider abstraction

`llm-gateway` defines a single `LlmProvider` interface (`streamComplete(request): Flow<Token>`, tool-calling support, model list) with one implementation per provider: **Anthropic/Claude, OpenAI, Gemini, and Ollama (local)**. Each agent is configured with a provider + model, and the chat UI exposes a per-agent switcher so the user can, e.g., run the Debugging Agent on Claude while pointing the Legal Doc Agent at a local Ollama model. Provider/model choice is part of `ProjectContext` (or a global default overridden per agent), not hardcoded per agent — swapping providers must never require touching agent code.

## 5. Tool Integrations Layer

Agents don't shell out to external tools directly; they call a thin client in `tool-integrations`, which keeps side effects auditable and mockable for tests:

- **Build tools:** Gradle Tooling API wrapper (Android/KMP), `xcodebuild` wrapper (iOS), Flutter CLI wrapper, npm/yarn wrapper (React Native/backend)
- **Device control:** ADB client (install, logcat streaming, shell commands), iOS Simulator control (`simctl`), emulator lifecycle management
- **Media capture:** screenshot (`adb screencap` / `simctl io screenshot`) and screen recording wrappers
- **Store/distribution:** Firebase App Distribution API client, Google Play Developer API client (App Bundle upload, store listing/screenshot upload)
- **Version control:** Git client for committing/branching generated changes
- **LLM access:** routed through `llm-gateway`, not called directly by agents, so prompt/tool-call conventions stay consistent across all agents

This layer is the sandboxing boundary. All builds, emulator runs, and generated/untrusted code execution are delegated to the **build-runner service** — a separate, always-on process the orchestrator talks to over a local API/queue, never invoking Gradle/Xcode/ADB/emulator processes itself. This keeps a runaway or malicious build from ever touching the orchestrator's process, and gives a single place to later add container/VM-level isolation without changing how agents call these tools.

## 6. Chat & Streaming Architecture

- `chat-gateway` (Ktor) exposes a WebSocket (preferred over SSE so the UI can also send interrupt/follow-up messages mid-stream) endpoint per session.
- Each user message enters the orchestrator, which streams back a mix of event types as they occur: `AgentActivated`, `TaskProgress`, `FileChanged`, `LogOutput` (e.g. live logcat during debugging), `AgentResult`, `FinalResponse`.
- The React `web-ui` renders these incrementally: activated agents as chips/badges, file diffs as they land, logs in a live console pane, and the assistant's running text response streamed token-by-token — so a multi-minute build/debug task feels like watching an engineer work, not waiting on a spinner.
- Session state (which agents are active, current task graph) is kept server-side in `ProjectContext` so a UI reconnect resumes rather than restarts.
- The UI includes a per-agent model switcher (Claude / OpenAI / Gemini / Ollama) backed by `llm-gateway`'s provider list, so the user can reassign which provider/model powers any given agent at any time.

## 7. Technology Stack

| Layer | Technology |
|-------|------------|
| Orchestrator & agents | Kotlin, coroutines/Flow for streaming |
| API/chat server | Ktor (WebSocket + REST) |
| Frontend | React, WebSocket client for streaming chat, per-agent model switcher |
| LLM providers | Anthropic/Claude, OpenAI, Gemini, Ollama (local) — behind one `LlmProvider` interface, user-switchable per agent |
| Context/task persistence | Exposed ORM over SQLite (local-first v1) |
| Build/emulator execution | Dedicated `build-runner` service, isolated from the orchestrator process |
| Build tool | Gradle (multi-module) |
| Mobile targets supported | Kotlin/Android, Swift/iOS, Kotlin Multiplatform, Flutter, React Native |
| Backend targets supported | Spring Boot (Kotlin/Java), Node.js |
| Distribution | Firebase App Distribution API, Google Play Developer API |

## 8. Build Phases

1. **Foundation:** `agent-sdk` contract, `orchestrator-core` with project fingerprinting + `ProjectContext`, `chat-gateway` with streaming wired to a minimal React shell, and one working agent end-to-end (Scaffolding Agent) to validate the whole pipeline before building the rest.
2. **Planning agents:** Requirements, Architecture Advisor, Tech Stack Recommendation — establish the "from scratch" front half of the lifecycle.
3. **Platform development agents:** Android, iOS, KMP, Flutter (React Native alongside or immediately after) — the core code-generation capability.
4. **Backend agent** for projects that need a server component.
5. **Quality loop:** Testing Agent, Debugging Agent (emulator + logcat + screenshot/video capture) — this is what makes generated code trustworthy rather than just generated.
6. **Ship loop:** CI/CD Build Agent, Publishing Agent (Firebase App Distribution, Play Console), Store Asset Agent, Landing Page Agent, Legal Doc Agent, Branding Agent — completes "to production."
7. **Phase 2 expansion:** Documentation, Analytics, Security Review, Performance Optimization agents, added as new modules against the same `agent-sdk` contract — no orchestrator changes required if the contract is respected.

## 9. Prior Prototype Reference

An earlier monolith prototype exists at `/Users/touhid/Desktop/AI-Cowork/ASAP-AI-Agent` (Ktor + React, package-by-feature, single `features/chat/` package). **Its architecture is not being reused** — everything (Android tools, Flutter tools, KMP tools, emulator/logcat/screenshot/video capture, all four LLM providers) lives flattened inside one feature package with one `ToolRegistry`, which is exactly the structure this plan replaces with per-agent modules. However, it already has working implementations of real capabilities this plan calls for, which are worth porting into the correct module rather than rebuilding from scratch:

| Prototype code (flat, in `features/chat/` or `features/admin/`) | Ports into (this plan's module) |
|---|---|
| `LlmAgentService` interface, `LlmProviderRegistry`, `OpenAiAgentService`/`ClaudeAgentService`/`GeminiAgentService`/`OllamaAgentService` (4 providers already working, including Ollama) | `llm-gateway` — confirms the `LlmProvider` interface shape; extend from "one active provider globally" to "provider selectable per agent" |
| `ToolSpec`, `ToolRegistry`, `ProcessRunner`, `TerminalTools` | `agent-sdk` (tool-spec contract) + `tool-integrations` (process execution primitive) |
| `AndroidBuildVersions`, `AndroidDeployTools`, `AndroidDeviceTools`, `AndroidProjectTools`, `GradleTools`, `GradleWrapperGenerator` | `android-agent` + `tool-integrations` (Gradle client) |
| `FlutterBuildTools`, `FlutterProjectTools` | `flutter-agent` |
| `KmpProjectTools` | `kmp-agent` |
| `EmulatorTools`, `LogcatRoutes` | `debugging-agent` + `build-runner` (owns the actual emulator/ADB process) |
| `ScreenshotTools`/`ScreenshotRoutes` (Playwright-based), `VideoRoutes` | Media Capture Agent + `tool-integrations` |
| `FirebaseCredentialsRegistry`/`Repository`, `FirebaseCliService` | Publishing Agent (Firebase App Distribution credential handling) |
| `ToolchainDetector`/`Installer`/`Environment`/`PathsRegistry` | `build-runner` (detects/bootstraps SDKs the build-runner needs before it can execute a build) |
| `WorkspaceService`/`Registry`/`SettingsRepository` | `orchestrator-core`'s `ProjectContext` (workspace root tracking) |
| `TaskModels`/`TaskRepository` (persisted tasks) | `context-store` (task graph persistence) |
| Exposed + SQLite + Flyway + HikariCP | Confirms `context-store`'s tech choice in §7 — reuse this exact stack |
| `ApiResponse<T>` envelope, `AppException` → `StatusPages` | `chat-gateway` (REST error/response conventions) |
| Admin panel (`/admin`: status, conversation view/reset, provider switch, `ADMIN_TOKEN` bearer auth) | Basis for an orchestrator ops view (active agents, per-agent provider, task graph) in `web-ui`, gated the same simple-shared-token way given single-developer v1 |
| `PhpServerTools` | Not adopted — backend targets are Spring Boot/Node per §7, no PHP in scope |

Net effect: Phase 1 (`agent-sdk` + `orchestrator-core` + one working agent) can move faster than a from-scratch build because working Android/Flutter/KMP/emulator/screenshot/provider-switching code already exists to port — it just needs to move from one flat package into the contract-based module boundaries this plan defines.

## 10. Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| LLM providers | Multi-provider: Anthropic/Claude, OpenAI, Gemini, Ollama (local), selectable and switchable per agent by the user | User wants provider choice/flexibility, not lock-in to one vendor; Ollama also supports fully offline/local use |
| Default model | `claude-opus-5` for the orchestrator and platform dev agents (Android, iOS, KMP, Flutter, Backend, Debugging) | Strongest current model for agentic, multi-file, long-horizon coding work — exactly what these agents do. Narrower/templated agents (Branding, Legal Doc, Landing Page, Store Assets) can be pointed at Sonnet 5 or a local Ollama model per-agent later, no code change needed given the `LlmProvider` abstraction in §4 |
| Deployment target (v1) | Local-first: SQLite for `context-store`, emulators controlled directly on the developer's machine | Matches single-developer use case, fastest to build, pairs naturally with local Ollama support |
| Build/emulator sandboxing | Dedicated `build-runner` service, isolated from the orchestrator process | Clean separation of concerns; keeps the sandboxing boundary in one place so container/VM isolation can be added later without touching agent code |
| Multi-user support (v1) | Single-developer only | Simpler auth/session model for v1; the agent-sdk contract doesn't need to change to add multi-user later |

No open decisions remain blocking Phase 1 scaffolding.
