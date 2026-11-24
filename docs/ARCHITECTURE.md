# Molly Android Client Architecture

This document describes the software design and communication architecture of the Molly Android Companion application.

## 1. High-Level Overview

```text
[ Molly Web UI ] <--- (WebSocket / API) ---> [ Molly Backend (Port 5050) ]
                                                        ^
                                                        | REST API / Polling
                                                        v
                                             [ Molly Android Client ]
```

The Android application operates as a distributed frontend node. It interacts with the backend through REST endpoints, maintaining state consistency through scheduled polling and atomic event execution.

## 2. Key Modules & Responsibilities

- **`SplashActivity`**: Manages cold launch sequencing and checks persistent pairing configuration.
- **`IntroActivity`**: Displays onboarding slides explaining Agent synchronization, tool autonomy, and model selection.
- **`MainActivity`**: Provides camera-based QR code decoding via ZXing and manual JSON string parsing.
- **`ChatActivity`**: Hosts the message thread view, interaction drawer, tool execution cards, and real-time status indicators.
- **`MollyApiClient`**: Orchestrates HTTP communication, header injection (`Bypass-Tunnel-Reminder`, `Idempotency-Key`), session creation, and auto-healing error recovery.
- **`SessionHistoryManager`**: Handles local SQLite/SharedPreferences session persistence, archiving states, and metadata caching.

## 3. Auto-Healing Session Lifecycle

To prevent HTTP 404 (`Thread not found`) discrepancies when mobile sessions become desynchronized from the server:
1. `MollyApiClient.sendPrompt()` checks for a valid session ID.
2. If absent or rejected with HTTP 404, it invokes `createSessionAndSendPrompt()`.
3. The server creates a persistent thread and returns an assigned ID.
4. The client binds the returned ID for all subsequent events.
