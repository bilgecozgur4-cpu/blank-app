# Security Model

KUTALP PRIME is a high-privilege personal assistant. Security is part of the architecture, not a later feature.

## Current protections

1. **Server-side OpenAI key** — the browser never receives `OPENAI_API_KEY`.
2. **Optional local access token** — set `KUTALP_ACCESS_TOKEN` if the server is exposed to the LAN.
3. **Approval gate** — memory writes, deletes, tasks and prediction mutations require user approval.
4. **Audit log** — every tool attempt is recorded locally, including rejected attempts.
5. **No silent memory** — user information is not automatically written into long-term memory.
6. **Local database** — SQLite data stays on the machine unless the user intentionally backs it up or syncs it.
7. **No secret files in git** — `.env` and database files remain ignored.

## Operating rules

- Prefer `KUTALP_HOST=127.0.0.1`.
- If using `0.0.0.0`, set a strong `KUTALP_ACCESS_TOKEN` and use a trusted private network. For phone microphone/PWA use from another machine, serve KUTALP behind trusted HTTPS; plain LAN HTTP is not a secure browser context.
- Never commit API keys, OAuth tokens, passwords or private databases to GitHub.
- External connectors must use least-privilege scopes.
- Sending email, deleting files, purchases, financial orders, device control and other consequential actions must remain approval-gated.
- A future local-shell tool must run in a sandbox and must not execute arbitrary commands without explicit approval.

## Threats still open

- Browser/PWA is not a secure always-listening wake-word runtime.
- LAN HTTP is not encrypted and, on a phone, is not a reliable secure context for microphone/PWA features. Remote or cross-device voice deployment should use trusted HTTPS.
- SQLite is not yet encrypted at rest.
- Device-level biometric unlock is not yet implemented.
- External OAuth connectors are not yet installed.

These are tracked for later versions rather than hidden behind false claims of security.
