# steakos.messaging — Email + Telegram adapters (ADR-022 / PR-8)

CORE_PLATFORM outbound adapters for the Silver messaging bar. **Not** a family plugin.
WhatsApp / WeChat are Gold — out of scope here.

## Pieces

| Type | Role |
|------|------|
| `MessagingAdapter` | Channel port: `isEnabled` / `isConfigured` / `send` / `health` |
| `EmailSmtpSender` | SMTP adapter; no-op when `messaging.emailEnabled` is false |
| `TelegramBotSender` | Telegram Bot API adapter; no-op when `messaging.telegramEnabled` is false |
| `MessagingOutboundService` | Façade + domain-event triggers → outbound; publishes `messaging.outbound.sent` |
| `*Transport` / `Recording*` | Pluggable delivery; recording stubs for unit tests |

## CompanySetting keys (PR-3)

| Key | Default | Notes |
|-----|---------|-------|
| `messaging.emailEnabled` | `false` | Opt-in |
| `messaging.telegramEnabled` | `false` | Opt-in |
| `messaging.email.smtpHost` / `.smtpPort` / `.username` / `.password` / `.fromAddress` | empty | Password encrypted |
| `messaging.telegram.botToken` / `.chatId` / `.apiBase` | empty | Token encrypted; apiBase defaults to `https://api.telegram.org` |

## Behaviour

- **Disabled (default):** `send` returns `skipped` / `disabled` — no network.
- **Enabled but not configured:** `skipped` / `not_configured`.
- **Enabled + configured:** deliver via SMTP / Telegram HTTP; audit event on success.
- Triggers (Si-M): `finance.invoice.*`, `stakeholder.communication.logged`, `agent.proposal.*`.

## Usage

```groovy
messagingOutboundService.send(npub, 'email', 'finance.invoice.overdue',
        [to: 'ops@example.com', subject: 'Overdue', text: 'Invoice 42 is overdue'])
messagingOutboundService.notifyAll(npub, 'agent.proposal.submitted', [text: 'Approve?'])
```
