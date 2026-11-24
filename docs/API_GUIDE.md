# Molly Backend Integration & API Specification

The Molly Android client interfaces with the following backend REST routes:

## 1. Authentication & Pairing
- **`GET /api/system/mobile-pairing-info`**
  - Returns: Local IP, server port, tunnel URLs, and APK download availability.
- **Header**: `Authorization: Bearer <token>`
- **Header**: `Bypass-Tunnel-Reminder: true` (Prevents localtunnel/proxy interstitial landing pages).

## 2. Session Management
- **`GET /api/v1/agents/sessions?limit=50&archived={bool}`**
  - Lists sessions belonging to the authenticated account.
- **`POST /api/v1/agents/sessions`**
  - Creates a thread with initial payload atomically.
  - Required Header: `Idempotency-Key: <UUID>`
- **`POST /api/v1/agents/sessions/{id}/archive`**
  - Toggles archive state for a thread.

## 3. Real-Time Conversation & Tools
- **`POST /api/v1/agents/sessions/{id}/events`**
  - Ingests user messages, input cancels, and tool approval decisions.
  - Event type for messages: `agent.session.input.message`
  - Event type for tool approval: `molly.session.input.resume`
- **`GET /api/v1/agents/sessions/{id}/items?limit=100&order=asc`**
  - Polls chat messages, tool execution calls, and thinking outputs.
