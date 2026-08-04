# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Vision

ASAP-Cowork is an AI agent platform for mobile app developers, covering the full lifecycle from scratch to production. The agent should support all major mobile SDKs and frameworks (Kotlin, Swift, KMP, Flutter, React Native, etc.).

End-to-end capabilities the agent is meant to provide:
- Planning and product design
- Logo and branding generation
- Project scaffolding/build across native and cross-platform stacks (Kotlin, Swift, KMP, Flutter, React Native, etc.)
- Terms & Conditions / legal doc generation
- Landing page generation
- Converting screenshots into Play Console–ready store images
- Writing test cases
- Running and debugging via emulator, including reading logcat output
- Capturing screenshots and video from the emulator
- Uploading builds to Firebase App Distribution
- Uploading builds to Google Play Console

Control surface: a chat UI drives the agent, with streaming chat responses enabled.

## Technology Stack

- Backend: Kotlin, Ktor
- Frontend: React
- Chat: streaming enabled

## Architecture Guidelines

Every project scaffolded by or within this codebase — this repo's own modules (e.g. `chat-gateway`, `build-runner`) and any mobile app project the scaffolding agent generates for end users (Kotlin, Swift, KMP, Flutter, React Native, etc.) — must follow:

- **Multi-module architecture**: split by layer/feature into separate modules (Gradle modules, Swift packages, Flutter/Dart packages, etc.) rather than one monolithic module.
- **Clean Architecture**: enforce the dependency rule — domain layer has no outward dependencies, data/infrastructure and presentation/UI layers depend inward on domain, never the reverse.

## Status

This repository does not yet contain code. Update this file with real build/lint/test commands and a high-level architecture overview once implementation begins.
