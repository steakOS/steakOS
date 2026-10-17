# steakos.events — ADR-025 in-process skeleton (OI-A02)

Logical event bus for cross-family integration. **No Kafka / outbox yet** — same event
names will carry forward when persistence lands.

## Pieces

| Type | Role |
|------|------|
| `DomainEventNames` | Stable name constants + intended consumers |
| `DomainEventRegistry` | Validate / catalog names |
| `DomainEvent` | Envelope (`eventId`, `name`, `version`, `npubOwner`, `source`, `payload`) |
| `DomainEventSubscriber` | Handler interface |
| `DomainEventBusService` | In-process publish / subscribe façade |

## Silver contract names (Sales / Finance Advanced + messaging later)

| Publisher | Event | Intended consumers |
|-----------|-------|--------------------|
| Sales | `sales.order.confirmed` | Finance, Delivery, SCM |
| SalesAdvanced | `sales.opportunity.won` | Sales / audit |
| SalesAdvanced | `sales.pricelist.updated` | Sales / audit |
| Finance | `finance.invoice.issued` / `.settled` / `.overdue` | Sales, Collections, Messaging |
| FinanceAdvanced | `finance.match.completed` | AP / payables |
| SCM | `scm.receipt.posted` | Finance match |
| GRRCBasics | `grrc.risk.opened` | Capability, agents |
| StakeholderBasics | `stakeholder.communication.logged` | Messaging |
| Agents | `agent.proposal.submitted` / `.approved` | Messaging, audit |
| Messaging | `messaging.outbound.sent` | Audit |

Plugins must not inject each other’s services (ADR-025); publish/subscribe these names instead.

## Usage (stub)

```groovy
domainEventBusService.subscribe(DomainEventNames.SALES_ORDER_CONFIRMED) { DomainEvent e ->
    // handle
}
domainEventBusService.publish(
        DomainEventNames.SALES_ORDER_CONFIRMED, 'sales', npubOwner, [orderId: id])
```
