// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package steakos.configuration

import grails.gorm.transactions.Transactional
import steakos.generic.ObjectContent

/**
 * Context-sensitive help (Wave B polish).
 * Resolves desks, finance sections, and especially /domains/{pkg}/{Resource}[/{id}|/new].
 */
@Transactional
class HelpService {

    static final String HOST_PACKAGE = 'system'
    static final String HOST_RESOURCE = 'HelpTopic'
    static final String HELP_TYPE = 'Help'

    /** Family package → human label + desk path */
    private static final Map FAMILY_META = [
            core        : [label: 'My work', desk: null, blurb: 'Personal tasks and activities'],
            sales       : [label: 'Sales & Marketing', desk: '/sales', blurb: 'Customers, quotes, and orders'],
            procurement : [label: 'Buyers', desk: '/scm/buyers', blurb: 'Vendors, products, services, POs'],
            production  : [label: 'Shop floor', desk: '/scm/shopfloor', blurb: 'Production, quality, inventory'],
            supplychain : [label: 'Shop floor', desk: '/scm/shopfloor', blurb: 'Stock moves and inventory'],
            delivery    : [label: 'Service delivery', desk: '/delivery', blurb: 'Services, resources, fulfillment orders'],
            service     : [label: 'Customer service', desk: '/aftersales', blurb: 'Inquiries, warranties, claims, RMAs'],
            human       : [label: 'Human capital', desk: '/hr', blurb: 'People, time, payroll, goals'],
            digital     : [label: 'Platform & IT', desk: '/digital', blurb: 'Systems, CIs, changes, tickets'],
            finance     : [label: 'Financial resources', desk: '/finance/treasury', blurb: 'Ledger, invoices, treasury, AP'],
            capability  : [label: 'Business capabilities', desk: '/capability/projects', blurb: 'Projects, processes, quality, change'],
            agents      : [label: 'Agentic workforce', desk: '/agents/agents', blurb: 'Agents, hives, HITL, memory'],
            strategy    : [label: 'Strategy', desk: '/strategy', blurb: 'Competitors and strategy topics'],
            development : [label: 'Development', desk: '/development', blurb: 'NPD / revision projects'],
            asset       : [label: 'Assets', desk: '/assets', blurb: 'Physical plant, vehicles, and tools'],
            generic     : [label: 'Content catalog', desk: '/help', blurb: 'System content types'],
            configuration: [label: 'Configuration', desk: '/settings', blurb: 'Company and user settings'],
            helper      : [label: 'Reference data', desk: '/system/catalogs', blurb: 'Countries, currencies, statuses'],
    ].asImmutable()

    /** Reusable operator chrome guide — append to desk help bodies */
    private static final String HOW_DESKS = '''## How desks look

Most desks show a **full-width list** — there is no empty white detail column waiting for a click.

- **Tabs** and **New** share one toolbar row
- Table **Action** column: **View (Eye) · Edit (Pencil) · Delete (Trash)** — missing actions leave blank space so icons stay aligned
- **View** / **Edit** open in a dialog; Edit stays unavailable when the record cannot be changed (for example an issued purchase order or accepted quote)
- Process actions for documents live at the bottom of the **View** dialog on list-style desks
- Some desks show a **detail panel only after you select a row** (Shop Floor, Digital, Finance invoices/transactions, CRM, Pricing, HR, Capability, Agents) — never an empty panel on load

Examples: Task list · Buyers desk.'''

    private static final String HOW_DESKS_DE = '''## So sehen Desks aus

Die meisten Desks zeigen eine **Liste über die volle Breite** — ohne leere weiße Detailspalte.

- **Tabs** und **Neu** teilen eine Werkzeugleiste
- Tabellen-Spalte **Aktion**: **Ansehen (Auge) · Bearbeiten (Stift) · Löschen (Papierkorb)** — fehlende Aktionen lassen leere Platzhalter
- **Ansehen** / **Bearbeiten** öffnen einen Dialog; Bearbeiten bleibt gesperrt, wenn der Datensatz nicht änderbar ist (z. B. ausgestellte Bestellung / angenommenes Angebot)
- Prozessaktionen für Belege stehen unten im **Ansichts-Dialog**
- Manche Desks zeigen das **Detail erst nach Zeilenauswahl** (Shopfloor, Digital, Finance Rechnungen/Buchungen, CRM, Pricing, HR, Capability, Agents) — nie eine leere Spalte beim Laden

Beispiele: Aufgabenliste · Einkaufs-Desk.'''

    private static final String HOW_DESKS_ES = '''## Aspecto de los escritorios

La mayoría de escritorios muestran una **lista a ancho completo** — sin columna de detalle blanca vacía.

- **Pestañas** y **Nuevo** comparten una barra de herramientas
- Columna **Acción**: **Ver (Ojo) · Editar (Lápiz) · Eliminar (Papelera)** — las acciones ausentes dejan huecos para alinear iconos
- **Ver** / **Editar** abren un diálogo; Editar no está disponible si el registro no se puede cambiar (p. ej. OC emitida / oferta aceptada)
- Las acciones de proceso de documentos viven al pie del diálogo de **Ver**
- Algunos escritorios muestran el **detalle solo al seleccionar una fila** (Taller, Digital, facturas/asientos Finance, CRM, Pricing, RR. HH., Capability, Agents) — nunca un panel vacío al cargar

Ejemplos: lista de tareas · escritorio de compradores.'''

    /** Optional rich copy for high-traffic master data (key = package.Resource) */
    private static final Map DOMAIN_BLURBS = [
            'core.Task'              : '''Tasks are the **single work-item type** (personal, process-linked, or agent-assigned).

**Fields**
- name, description
- **status** — OPEN · IN_PROGRESS · DONE · CANCELLED
- **urgency** / **importance** — 1 low · 2 medium · 3 high (Eisenhower 3×3)
- **deadlineAt**, startAt, endAt
- **assigneeHumanNpub** and/or **assigneeAgentId**
- Content rail for links, files, notes

**Tips**
- Prefer status transitions over deleting work history
- Use **My work** (`/work`) for the Eisenhower **Action - NOW** board (and **Agentic Tasks** toggle)
- Capability ProjectItem can link via optional taskId
- Domain list **Action** column: **View (Eye) · Edit (Pencil) · Delete (Trash)** — View/Edit open **modals** ''',
            'capability.Process'     : '''**Processes** are the catalogue of how work flows (APQC 13.1).

**Kinds:** SYSTEM (seeded product paths, read-only) · HUMAN · AGENTIC · OTHER

**Status:** DRAFT · ACTIVE · RETIRED (no separate “Live” label — ACTIVE is the live catalogue state).

**SYSTEM seeds** include Purchase order, Quote→Order, Invoice settle, Service order, Inquiry, Hire, Project, HITL. Open a process for a **Mermaid** flowchart and step list.

**Desks:** use **How this works** on SCM/Sales/Finance/etc. to open the matching processCode.

**Admin:** Settings → Reseed SYSTEM processes (or Capability → Reseed).''',
            'sales.Customer'         : '''**Customer** master data: people or organisations you sell to.

**Fields to care about**
- **customerKind** — PERSON or ORGANIZATION
- email / phone / contactName
- taxId, currencyCode, active

**Workflow**
1. Create customer here (or via CSV import)
2. Use them on the **Sales desk** for quotes and orders
3. Confirming an order can create a **draft invoice**

**Tips:** list **Action** column uses **Eye / Pencil / Trash**; View and Edit open **modals**.

**Related:** Sales desk · Invoices · CSV import (Customers pack)''',
            'sales.CustomerQuote'    : '''**Customer quotes** capture proposed pricing before an order.

Prefer the **Sales desk** for create → send → accept/reject. This master-data view is useful for admin browse/edit.

Quote lines live under **QuoteLine**. Content rail holds customer briefs and notes.''',
            'sales.SalesOrder'       : '''**Sales orders** are confirmed commercial commitments.

Desk flow: quote accept → order → **confirm** (may post finance draft). Line detail: **OrderLine**.''',
            'sales.QuoteLine'        : '''Line items on a **CustomerQuote** (product/service ref, qty, price).

Usually edited via the Sales desk quote screen rather than raw master data.''',
            'sales.OrderLine'        : '''Line items on a **SalesOrder**.

Product vs service flags matter for Delivery and SCM hand-offs.''',
            'procurement.Vendor'     : '''**Vendor** (supplier) master for procurement.

Create here or import via CSV, then use on the **Buyers desk** for purchase orders.

Fields: contact, taxId, currency, active.

**Tips:** **Action** column **Eye / Pencil / Trash**; View/Edit open **modals** (same chrome as Buyers desk).''',
            'procurement.PurchaseOrder': '''**Purchase orders** to suppliers.

Desk flow: create → send → confirm → receive (may create payables). Lines: **PurchaseOrderLine**.

**Tips:** **Action** column **View (Eye) · Edit (Pencil) · Delete (Trash)** — Edit/Delete only while **DRAFT**; issued/confirmed POs show View only (blank Edit placeholder). View/Edit use **modals**; process steps (send/confirm/receive) live in the **view modal footer** on the Buyers desk.''',
            'catalogue.CatalogueItem': '''**Catalogue item** — unified goods + services master.

Nature GOODS (SKU/unit, buy/sell) or SERVICE. Used by sales lines, BOMs, production, stock, and delivery.''',
            'production.ProductionOrder': '''**Production / shop-floor orders**.

Desk flow: create or from-sales → release → start → complete. Lots and checks support light traceability.''',
            'finance.Account'        : '''**Chart of accounts** entry.

Seed a starter COA from **General Ledger** if empty. Codes should stay unique per tenant (npub).

Posting only through the ledger service — do not invent balances by hand.''',
            'finance.Invoice'        : '''**Invoices** are best driven from **Finance → Invoices** (issue / settle), not only this list.

Statuses: draft → issued → paid/void. Payment options support EUR + sats rails.''',
            'finance.JournalEntry'   : '''**Journal entries** are the ledger’s posted documents.

Create via General Ledger tools so debit/credit balance rules apply.''',
            'delivery.ServiceOrder'  : '''**Service / delivery orders** for fulfillment.

Use the **Delivery desk** for schedule, assign resource, start, complete.''',
            'service.Inquiry'        : '''**After-sales inquiries** (customer questions/cases).

Desk: start → resolve → close. Prefer AfterSales desk over raw CRUD for status flow.''',
            'service.Claims'         : '''**Claims** against warranties/service.

Validate → approve/reject → close. Approval may create a **draft credit** in Finance.''',
            'human.HRFile'           : '''**HR file** (employee/contractor record).

Sensitive name fields may be encrypted. Hire flow often starts from **Candidate**.''',
            'human.Candidate'        : '''**Recruiting candidates**.

Stages → advance → hire (creates user/HR file) or reject. Use the **HR desk**.''',
            'digital.ConfigItem'     : '''**Configuration items (CI)** in the IT registry.

Link to **System** records; changes and tickets reference CIs.''',
            'digital.ItTicket'       : '''**Internal IT tickets** (not customer AfterSales cases).

Desk: start → resolve → close.''',
            'agents.Agent'           : '''**AI / automation agents**.

Seed core agents, activate/pause/retire. Pair with Hives and HITL approvals.''',
            'capability.Project'     : '''**Projects** under portfolios/programs.

Capability hub: start → complete/cancel; project items hold work breakdown.''',
            'configuration.LegalEntity': '''**Legal entity** created by the setup wizard (ADR-029).

Edit carefully; preferred path for first-time setup is the **configuration wizard**.''',
            'configuration.GlobalConfig': '''Legacy **global config** bag. Prefer **CompanySetting** / Settings for new work.''',
            'helper.Countries'       : '''**Country** reference catalog (ISO-style codes). Seeded at bootstrap.''',
            'helper.Currencies'      : '''**Currency** reference catalog (ISO fiat). Company **Currencies we use** (Settings) shortens dropdowns. Bitcoin is the value rail, not a catalog row. Soft warnings flag document vs company / partner / catalogue mismatches.''',
            'generic.ContentType'    : '''**Content type** catalog for BODY companion entries (Note, Help, Risk, …).

Files use the FILE channel — not a ContentType.''',
    ].asImmutable()

    private static final List FEATURE_SEEDS = [
            [key: 'app.dashboard', title: 'CEO dashboard', route: '/dashboard', family: 'core',
             body: '''# CEO dashboard

Your cockpit for open work and process health.

- **My work** — open tasks and deadlines
- **Action - NOW** — 3×3 urgency × importance board (same board under **My work**)
- **APQC groups** — jump into Sales, Finance, and other desks
- **System health** — API / content signals

**Shortcuts:** **/** top-bar search (records) · **Ctrl+K** jump to pages · **?** or **Ctrl+/** this help drawer.

**See also:** Action - NOW · Settings (language & region) · Global search'''],
            [key: 'app.search', title: 'Global search', route: '/dashboard', family: 'core',
             body: '''# Global search (top bar)

Find **customers, vendors, quotes, orders, invoices, catalogue, POs, and tasks** from the top bar.

| Tool | Purpose |
|------|---------|
| **Top-bar search** | Business **records** (type · title · status · deep link) |
| **Ctrl+K / ⌘K** | Jump to **pages / desks** (navigation only) |
| **List filter `q`** | Narrow **one** desk or domain list |

**Tips**
- Type at least **2** characters (results debounce ~300ms)
- Press **/** when focus is not in a field to focus search
- Titles prefer business keys (invoice / quote / PO numbers), not raw ids
- Results stay inside the **active company**; types you cannot See are omitted

**See also:** CEO dashboard · My work'''],
            [key: 'setup.wizard', title: 'Configuration wizard', route: '/setup', family: 'core',
             body: '''# Configuration wizard

One-time tenant setup (default **Iron**).

Tour → company → site → banking → **Bitcoin** → template → finish.

Choose a **Bitcoin integration level** (Not now / Single address / BTCPay). Primary value rail (books currency) is separate.

Admin **Settings** can reset setup for demos.'''],
            [key: 'training.btc.basic', title: 'Basic Bitcoin literacy', route: '/system/integrations', family: 'finance',
             body: '''# Basic Bitcoin literacy (Level 1 nudge)

Before you enable Bitcoin receive in steakOS:

1. Learn the difference between **on-chain** and **Lightning**.
2. Know that steakOS does **not** hold your keys.
3. Prefer **BTCPay Server (Level 3)** for regular customer payments.
4. Level 1 keeps receive deferred — books may still use a BTC primary rail with **mock** settle.

Set levels under **System → Integrations** (or the setup **Bitcoin** step).

External learning: choose reputable public Bitcoin education; this topic is a checklist, not a course.'''],
            [key: 'training.btc.security-advanced', title: 'Advanced Bitcoin security', route: '/system/integrations', family: 'finance',
             body: '''# Advanced Bitcoin security (Level 2 nudge)

Level 2 uses a **single software-wallet address** for **occasional** payments only.

1. Back up your seed offline; never paste seeds into steakOS.
2. Watch for phishing and malware that swap addresses.
3. Address reuse and weak backups can cause permanent loss.
4. Plan to upgrade to **BTCPay Server (Level 3)** for Lightning / regular receive.

steakOS does not watch the chain in beta — settle is **manual mark paid**.

Manage Level 2 under **System → Integrations**.'''],
            [key: 'system.integrations', title: 'Integrations hub', route: '/system/integrations', family: 'core',
             body: '''# Integrations hub

**System → Integrations** is where operator integrations live (not Settings).

## What you configure here

| Area | What it does |
|------|----------------|
| **Nostr** | Local private relay level (0 / 1 / 2), relay URL, install/start, probe |
| **Bitcoin** | Receive level: Not now · Single address · BTCPay |
| **Hermes agents** | Mint / revoke opaque API tokens for operator-owned agent hosts |

## Bitcoin levels (quick)

1. **Not now** — receive deferred; training nudge for basic literacy  
2. **Single address** — occasional on-chain QR; manual mark paid; security warning  
3. **BTCPay** — live Lightning/on-chain via **Finance → Payments** (keys, probe, live enable)

CEO must acknowledge training when Admin upgrades to Level 2 or 3. Lightning marketing only at Level 3.

## Nostr (quick)

- Wizard step **after Bitcoin**, or this page  
- Level ≥ 1 auto-installs/starts project-local `nostr-rs-relay` (loopback)  
- **Never** paste `nsec` — link NIP-07 under **Security**  
- DEMO companies do not publish  

## Hermes / AI agents (quick)

1. Mint a `sos_…` token here (shown once)  
2. Paste into Hermes (or other host) with steakOS base URL  
3. Smoke: `GET /api/agent/me` with Bearer  
4. Optional HITL under **Agents**  

**Agent Basics** only in closed beta — not Agent Pro.

## Related Help

Search **Ctrl+K**: Bitcoin, BTCPay, Nostr, Hermes, AI / Agents.

Deep docs: `CONNECT-LIVE-PAYMENTS.md`, `ACTIVATE-AGENT.md`, `OPERATOR-RUNBOOK.md`, Nostr research under `docs/research/`.'''],
            [key: 'integrations.bitcoin', title: 'Bitcoin integration level', route: '/system/integrations', family: 'finance',
             body: '''# Bitcoin integration level

How this company **receives** Bitcoin — separate from the **primary value rail** (books currency).

| Level | UI name | Behaviour |
|-------|---------|-----------|
| `DEFERRED` | 1 — Not now | No live receive; mock settle OK |
| `SINGLE_ADDRESS` | 2 — Single address | One wallet address + QR; **manual** mark paid |
| `BTCPAY` | 3 — BTCPay | Operator BTCPay Server (Lightning + on-chain) |

**Where:** setup wizard **Bitcoin** step, or **System → Integrations**.

**Rules:** Admin sets level; CEO training ack on upgrade to 2/3; Admin-only downgrade from Level 3; Level 2 warning dismissible until next login.

**Live keys** for Level 3: **Finance → Payments** (probe → enable live).

See also **Live payments (BTCPay)** and training topics (basic / advanced security).'''],
            [key: 'integrations.nostr', title: 'Nostr integration', route: '/system/integrations', family: 'digital',
             body: '''# Nostr integration (OI-NOSTR01)

Private **local relay** (`nostr-rs-relay`) for company audit / notify — same ops idea as BTCPay (you run the node). **Per-user Alby login** is configured under **Security**, not here.

## Company relay / level

1. **System → Integrations → Nostr** (or setup wizard **Nostr** step after Bitcoin)  
2. Choose level: **0** not now · **1** login + private audit · **2** + notify (giftwrap later)  
3. Level ≥ 1: use **Install / start local relay** (project `var/nostr/`, loopback `ws://127.0.0.1:7777`)  
4. **Probe** until reachable  

## People signing in with Alby

Each human needs a steakOS user (password invite), then **Security → Link browser extension**. See **Sign in with Alby (Nostr)**. Never paste `nsec`.

## Honesty

- DEMO companies do not publish  
- Level ≥ 1 LIVE with company bot `nsec` (Admin API): real **EVENT** to the local relay with **NIP-42 AUTH** (JDK WebSocket + secp256k1 — not nostr-java)  
- Without bot key: intent is recorded and status stays honest (`stubbed`)  
- Level 2 **giftwrap** notify (NIP-17) is still residual — `publishNotifyStub` only  

Scripts: `scripts/install/nostr/install-nostr-relay.sh` · QUICKSTART §3b · OPERATOR-RUNBOOK.'''],
            [key: 'sales.desk', title: 'Sales desk', route: '/sales', family: 'sales',
             body: '''# Sales desk

Operational path for commercial work:

1. **Customer** (master data or create here)
2. **Quote** → send → accept/reject
3. **Order** → confirm (may create draft invoice)

Master data under Sales still available for admin lists.

**Currency:** set **Preferred currency** on the customer and **document currency** on the quote/order. Mismatches with company currency (top bar) or catalogue list-price currency show as soft warnings (save still allowed). Admin enables codes under **Settings → Currencies we use**.

''' + HOW_DESKS],
            [key: 'currency.catalog', title: 'Currencies we use', route: '/settings', family: 'core',
             body: '''# Currencies we use

steakOS keeps a shared ISO **currency list**. Each company enables a short **Currencies we use** set for dropdowns.

- **Company currency** shows in the top bar (before Help).
- **Preferred currency** on customers and vendors.
- **Price currency** on catalogue items (one list price per item in beta).
- New writes must use an enabled + in-use code; legacy orphan codes stay readable.
- Quote / sales order / purchase order may soft-warn when document, partner, and catalogue currencies differ — save is not blocked.

Admin only: Settings → Currencies we use.'''],
            [key: 'finance.primaryRail', title: 'Primary value rail & BTC FX', route: '/settings', family: 'finance',
             body: '''# Primary value rail & BTC FX

Your **primary value rail** is the money unit your books use (chosen in setup):

| Rail | Meaning |
|------|---------|
| **EUR** / **USD** | Fiat functional currency — invoices and journals in that currency |
| **BTC** | Bitcoin books (amounts in sats or BTC display); pick a **display fiat** (EUR/USD) for hints |

## What you see on forms

Beside amounts, steakOS may show a **compact FX hint** (for example sats beside euros). Hints come from a **server-side** BTC rate cache (CoinGecko → Kraken → Coinbase). A **stale** badge means the last good rate is shown because live quotes failed.

**Hints are not the books.** Issued documents keep their primary amount; tooltips can change when the market moves.

## Settlement

When you **settle** an invoice, steakOS records payment-time figures on the **Payment** / journal / treasury (including sats when you pay via Bitcoin rails). That settle rate is what the ledger uses — not the live tooltip.

## Dual treasury

Expect both a **fiat bank** position and a **Bitcoin wallet** position under Finance → Treasury (one chart of accounts).

## Honest limits (Basics)

- No automatic wallet mark-to-market (MTM) when BTC price moves  
- Changing the primary rail after setup is Advanced (not in closed beta)  
- Manual FX rate tables are not supported  

**Related:** Invoices · Treasury · Live payments (BTCPay) · Company profile'''],
            [key: 'finance.invoices', title: 'Invoices', route: '/finance/invoices', family: 'finance',
             body: '''# Invoices

1. Create draft  
2. **Issue**  
3. **Settle** when paid  

Amounts use your company **primary value rail** (EUR, USD, or BTC). Optional **FX hints** beside the amount are display-only — see **Primary value rail & BTC FX**.

Seed COA from General Ledger if accounts are missing.

**Live vs mock:** DEMO companies use **mock settle only**. LIVE companies choose a **Bitcoin integration level** (Settings / setup wizard). Lightning marketing claims only at **Level 3 (BTCPay)** — see **Live payments (BTCPay)**.

This surface stays a **justified split** desk: pick a row to open detail (no empty pane on load).

''' + HOW_DESKS],
            [key: 'finance.payments.live', title: 'Live payments (BTCPay)', route: '/finance/payments', family: 'finance',
             body: '''# Live payments (BTCPay)

steakOS does **not** custody your Bitcoin. You run **BTCPay Server**; steakOS records invoices and settlements.

## Bitcoin integration levels (OI-BTC01)

| Level | Meaning |
|-------|---------|
| **1 — Not now** | Receive deferred; mock settle |
| **2 — Single address** | Occasional on-chain to one wallet address; manual mark paid |
| **3 — BTCPay** | Operator BTCPay (Lightning + on-chain) |

Set the level in setup (**Bitcoin** step) or **System → Integrations**. Admin changes level; CEO must acknowledge training on upgrade to 2/3.

## LIVE company (Level 3)

1. Set level to **BTCPay**  
2. Open **Finance → Payments**  
3. Enter BTCPay base URL, store id, API key, webhook secret  
4. **Probe** the connection  
5. Enable **live**  
6. Issue an invoice → create inbound payment → pay in BTCPay → webhook (or reconcile)

Upgrade from Level 2: keep occasional address until BTCPay is probed and live.

## DEMO companies

Orange Steak House and Heard & Cook Architects are **forced to mock settle** so Reset and training films stay safe. Switch to your LIVE company for real rails.

## Fiat

Fiat / SEPA-style settle remains available. It does not replace Level-3 Lightning claims for commercial honesty.

## Docs

Operator guide: `docs/installation/CONNECT-LIVE-PAYMENTS.md` (accepted residuals are listed there).'''],
            [key: 'finance.ledger', title: 'General ledger', route: '/finance/transactions', family: 'finance',
             body: '''# General ledger

Double-entry kernel: seed COA, post balanced journals, trial balance, period close marker.'''],
            [key: 'finance.treasury', title: 'Treasury', route: '/finance/treasury', family: 'finance',
             body: '''# Treasury hub

Positions, payables, budgets, tax, payroll stub, control — open the section tabs on this hub.

**Dual positions:** a fiat bank and a Bitcoin wallet share one chart of accounts. Carrying values follow your **primary value rail** — see **Primary value rail & BTC FX**.

For **live Bitcoin receive**, see **Live payments (BTCPay)** (Finance → Payments).

''' + HOW_DESKS],
            [key: 'finance.postingRules', title: 'Posting rules (auto journals)', route: '/finance/posting-rules', family: 'finance',
             body: '''# Posting rules

steakOS posts balanced journals when money moves — **invoice settle**, **vendor pay**, and **payroll recognize / pay** — using a **company ruleset** (not hardcoded accounts). Under **Accrual**, **invoice issue** and **payable create/approve** also post.

## Accounting mode

| Mode | Meaning |
|------|---------|
| **Cash** (default / restaurant) | Books update when paid |
| **Accrual** | On **issue**: Dr AR / Cr Revenue; on **settle**: Dr Cash / Cr AR. On **payable open**: Dr Expense / Cr AP; on **pay**: Dr AP / Cr Cash |

Choose mode in the **setup Finance books** step or here. Settle/pay under Accrual clear AR/AP — they do not double-count revenue/expense.

## Who can change what

| Action | Admin | CEO |
|--------|:-----:|:---:|
| View rules / mode / change log | ● | ● |
| Edit rules / mode / reseed | ● | ○ |

Every Admin **Save** or **Reseed** appends an immutable **change log** and opens a Digital **IT ticket** (`CHANGE`, name prefix `[Finance policy]`). Journals are never wiped by reseed.

## Where

**Finance → Posting rules** (`/finance/posting-rules`)

Bad or missing rules **block** settle/pay (error `POSTING_RULES_*`) — no silent fallback.

**Related:** General ledger · Invoices · Treasury · Payroll'''],
            [key: 'scm.desk', title: 'Buyers Desk', route: '/scm/buyers', family: 'scm',
             body: '''# Buyers Desk

**Vendors · products · services · purchase orders**

Vendor → PO → send → confirm → receive (stock / Finance payable).

Shop floor (production, quality, inventory) is a **separate** desk: **Shop Floor Control** (`/scm/shopfloor`).

This desk is the **List with View/Edit dialogs** (no empty detail column).

''' + HOW_DESKS],
            [key: 'scm.shopfloor', title: 'Shop Floor Control', route: '/scm/shopfloor', family: 'scm',
             body: '''# Shop Floor Control

**Production orders · quality / lots · inventory on-hand**

Product/BOM → production order → release → complete · lot checks · stock.

Purchasing (vendors, POs) lives on the **Buyers Desk** (`/scm/buyers`).

Justified **split** layout: detail opens when you select a row.

''' + HOW_DESKS],
            [key: 'delivery.desk', title: 'Delivery desk', route: '/delivery', family: 'delivery',
             body: '''# Delivery desk

Services & resources → service orders (from sales or manual) → schedule → assign → start → complete.

''' + HOW_DESKS],
            [key: 'aftersales.desk', title: 'AfterSales desk', route: '/aftersales', family: 'service',
             body: '''# AfterSales desk

Inquiries, warranties, claims, RMAs. Claims may create Finance credit drafts; RMAs can post stock receipts.

''' + HOW_DESKS],
            [key: 'hr.desk', title: 'HR desk', route: '/hr', family: 'human',
             body: '''# HR desk

Candidates → hire · HR files · time · payroll periods · goals · policies · party access.

Justified **split** layout: detail opens when you select a row.

''' + HOW_DESKS],
            [key: 'digital.desk', title: 'Digital desk', route: '/digital', family: 'digital',
             body: '''# Digital / IT desk

Systems, data assets, solutions, IT changes, CIs, internal tickets.

Justified **split** layout: detail opens when you select a row.

''' + HOW_DESKS],
            [key: 'asset.desk', title: 'Assets desk', route: '/assets', family: 'asset',
             body: '''# Assets desk

Physical plant, vehicles, and tools (AssetBasics register).

- **Register** — name, tag, location, owner, status
- **IT managed** — permanent link to a Digital ConfigItem (edit hostname/IP on the Digital desk)
- **Financial depreciation** — when checked, Finance-module agents may pick the asset up and post depreciation values in a financial period
- **Asset requires maintenance** — when checked, physical maintenance is expected; attach the manufacturer schedule (XLSX or PDF) on the record via Object Companion. Later, agents may create shopfloor or maintenance activities to extend asset life

Flags are Basics intent markers; automatic depreciation posting and maintenance work orders deepen later.

''' + HOW_DESKS],
            [key: 'strategy.desk', title: 'Strategy desk', route: '/strategy', family: 'strategy',
             body: '''# Strategy desk

**Competitors** and **Strategy topics** — list with View/Edit dialogs.

Use tabs + **New** in the tab row; Action column **Eye · Pencil · Trash**.

''' + HOW_DESKS],
            [key: 'development.desk', title: 'Development desk', route: '/development', family: 'development',
             body: '''# Development desk

**Development projects** (NPD / revisions) — single-column desk with modal view/edit.

Tabs + **New** in the tab row; Action column **Eye · Pencil · Trash**.

''' + HOW_DESKS],
            [key: 'capability.hub', title: 'Capability hub', route: '/capability', family: 'capability',
             body: '''# Capability hub

Projects, processes, quality, changes, knowledge — use the section tabs.

**Process charts:** open **Processes**, pick a SYSTEM process, then hover/focus a step for What / Who / Where — see **Process charts**.

Justified **split** layout: detail opens when you select a row.

''' + HOW_DESKS],
            [key: 'process.chart', title: 'Process charts', route: '/capability/processes', family: 'capability',
             body: '''# Process charts

steakOS shows **SYSTEM** process diagrams as learning aids (not process mining).

1. Open **Capability → Processes** (defaults to SYSTEM)  
2. Select a process (or use How-it-works on a desk)  
3. **Hover** or **keyboard-focus** a step for What / Who / Where + pitfall  
4. On touch, **tap** to pin the tip; **Esc** closes  

Tips that say **planned / stub** are honest about unfinished depth.

Deep link example: `/capability/processes?code=FIN_INVOICE_SETTLE`  
Reseed tips: Settings or Capability → Reseed SYSTEM catalogue.'''],
            [key: 'agents.hub', title: 'AI / Agentic hub', route: '/agents', family: 'agents',
             body: '''# AI / Agentic resources

**Agents**, **Hives**, **HITL** approvals, **Memory**. Seed core agents on first use.

Closed beta claims **Agent Basics** only — not Agent Pro.

To connect an **operator-owned** runtime (Hermes or similar), see **Activate agent / Hermes** — mint a token under **System → Integrations**, paste into the host, smoke-read, optional HITL.

Open **Agents** from the nav or Ctrl+K → **AI / Agents**.

Justified **split** layout: detail opens when you select a row.

''' + HOW_DESKS],
            [key: 'agents.activate', title: 'Hermes agent (API tokens)', route: '/system/integrations', family: 'agents',
             body: '''# Hermes agent (API tokens)

**Agent Basics** only — not Agent Pro.

1. **System → Integrations → Hermes agents** — mint a `sos_…` token (shown once)  
2. Paste into your **operator-owned** agent host (e.g. Hermes) with steakOS `baseUrl`  
3. **Smoke read:** `GET /api/agent/me` and `/api/agent/l0/summary` with Bearer  
4. **Optional HITL:** agent proposes → **Agents → HITL** → human approve/reject  

Agents use the API (not the browser). They do **not** settle or pay money by default — humans keep money risk.

Guide: `docs/installation/ACTIVATE-AGENT.md` · deep profile: `HERMES-PROFILE.md`.

Also search Ctrl+K: **Hermes**, **AI**, **Agents**.'''],
            [key: 'system.import', title: 'Data import', route: '/system/import', family: 'core',
             body: '''# Data & import

CSV packs: **Customers**, **Vendors**, **COA**. Preview mapping, then commit. Opening-balance pack is preview-only.'''],
            [key: 'system.settings', title: 'Settings', route: '/settings', family: 'core',
             body: '''# Settings

**Language & region** is the first card — it controls **your screen language** (EN / DE / ES) and date/money formats for this account.

## Language & region

1. Open **System → Settings**
2. Choose a locale (e.g. German (Germany) `de_DE`)
3. Labels and formats update for your user

This is **not** the language of outbound PDFs to customers/vendors. That is **Communication language** on each Customer / Vendor master.

## Other cards on this page

- **Currencies we use** — short list for dropdowns (company currency stays on)
- Tax codes in use (when Finance pack is enabled)
- Demo / setup status and admin **reset setup** (Admin)

**Integrations** (Nostr, Bitcoin level, Hermes tokens) live under **System → Integrations**.

**See also:** Partner communication language · Currencies we use'''],
            [key: 'system.catalogs', title: 'Admin catalogs', route: '/system/catalogs', family: 'core',
             body: '''# Admin catalogs

Reference data, content types, legacy config domains — power-user lists, not daily desks.'''],
            [key: 'app.about', title: 'About steakOS', route: '/about', family: 'core',
             body: '''# About

Product line, SemVer, template tier, and golden/polish milestones.

**Security:** report privately to **security@steakos.org** — see Help **Report a security issue** and `docs/installation/SECURITY-DISCLOSURE.md`.'''],
            [key: 'security.disclose', title: 'Report a security issue', route: '/about', family: 'core',
             body: '''# Report a security issue

Email **security@steakos.org** privately. Do not post exploit details in public issues.

- Ack target: ≤ 3 business days  
- Triage target: ≤ 10 business days  
- Scope: steakOS core + documented install configs (not your BTCPay/Hermes unless our glue leaks data)

Full process: `docs/installation/SECURITY-DISCLOSURE.md` · machine-readable `/.well-known/security.txt` · root `SECURITY.md`.

We are **not** claiming CRA certification.'''],
            [key: 'app.tour', title: 'Onboarding tour', route: '/tour', family: 'core',
             body: '''# Onboarding tour

Open **`/tour/index.html`**. Template choice is remembered for the setup wizard.'''],

            // —— PDR-025 Roles + Users (substantial Help, R7) ——
            [key: 'help.roles.overview', title: 'Roles for a small team', route: '/roles', family: 'core',
             body: '''# Roles for a small team

steakOS is built for a **small company** (about 2–5 people). Give each person a **role profile** (Office, Ops, Finance…). Roles come with **defaults** for which desks they see and what they may change.

**Admin** and **CEO** adjust those defaults under **System → Roles** and invite people under **System → Users**.

You do not need an IT department. If unsure, **keep the presets** and only open extra desks when someone needs them.

## Quick start

1. Open **System → Users**
2. Add a person with profile **Office** (front desk) or **Finance** (bookkeeping)
3. Tell them the temporary password; they should set **Security → authenticator** soon
4. Confirm their left nav shows only the desks they need

## Where to go

- **Roles** (`/roles`) — change defaults for a profile
- **Users** (`/users`) — assign a profile to a person
- Help topics: presets · permission matrix · invite steps · safety

**Tip:** Open this drawer with **?** or **Ctrl+/** on any screen.'''],
            [key: 'help.roles.presets', title: 'Which role to pick', route: null, family: 'core',
             body: '''# Which role to pick

Presets are **starting fills**, not frozen forever. Change them on **System → Roles** when needed.

| Role | Typical person | Sees / does | Avoid when… |
|------|----------------|-------------|-------------|
| **Admin** | Installer / IT-minded co-owner | All enabled desks + Users, Import, Catalogs, Agents | Everyday business work (use CEO) |
| **CEO** | Owner / GM | All enabled business desks + Users / Import | You need a second full Admin (Admin mints Admin) |
| **Office** | Front-of-house / office | Sales, Delivery, After-sales (+ Capability light); **no settle money** | They must book payments |
| **Ops** | Kitchen / production / floor | SCM, Delivery, Assets; Sales **see** only | They own customer quotes end-to-end |
| **Finance** | Bookkeeper | Finance + Sales **see**; **settle** money | They should invite users (no Users) |
| **User** | Generic desk worker | Conservative Sales + Delivery (+ After-sales see) | You already know Office/Ops/Finance fits better |
| **Read only** | Visitor / auditor / trainee watch | Browse enabled desks; **writes blocked** | They need to create or edit anything |

## Restaurant / café examples

- Host / phone: **Office**
- Kitchen lead: **Ops**
- Accountant (part-time): **Finance**
- Owner: **CEO** (+ keep one **Admin** account separate)
- Trainee shadowing: **Read only** on the demo or live instance

**Rule:** one person → one profile. Prefer changing the profile over inventing a new role in v1.'''],
            [key: 'help.roles.matrix', title: 'Permission matrix (Advanced)', route: null, family: 'core',
             body: '''# Permission matrix (Advanced)

The matrix is the **source of truth**. Presets fill it; Advanced lets Admin/CEO tune cells.

## Desks (families)

For each desk (Sales, Finance, SCM…):

| Verb | Meaning |
|------|---------|
| **See** | Appears in nav; can open lists and read |
| **Create** | New records (POST) |
| **Change** | Edit / workflow actions (PUT/PATCH / start / complete…) |
| **Delete** | Delete / void where the product allows |
| **Work** | Shortcut = Create **or** Change (Simple view) |

Grants are intersected with the company’s **enabled families** (metal / template). A grant on a disabled desk does nothing.

## System actions

Examples: **Users / invite**, **Import**, **Catalogs**, **Agents**, **Setup**, **Settle money**, reset others’ 2FA.

Settle is a **money special** — Finance has it by default; Office does not.

## Safety

- **Deny wins** over allow when combining role + optional user override
- Protect the **last Admin**
- Confirm before **Apply to all users** with that profile
- Humans never get `ROLE_AI` from the Users screen

## API enforcement

Mutating `/api/**` calls check this matrix (see codes `MATRIX_DESK_FORBIDDEN`, `MATRIX_SYSTEM_FORBIDDEN`). Nav **See** uses the same effective permissions.'''],
            [key: 'help.users.invite', title: 'Invite a teammate', route: '/users', family: 'core',
             body: '''# Invite a teammate

Path: **System → Users**

1. Choose a **role profile** (start with Office / Ops / Finance — not Admin)
2. Set username + **temporary password** (12+ characters: lower, upper, digit, special)
3. Create the user
4. Share credentials out-of-band (chat / paper) — not in a public ticket
5. Ask them to sign in with that password and open **Security** to enrol an **authenticator** (Admin/CEO already have 2FA from setup)
6. **Optional — Alby / Nostr login:** after password sign-in, they open **Security → Link browser extension** with **Alby** unlocked. The Users table **Extension** column then shows Yes. Only then can they use **Sign in with browser extension** on the login page.

## Sign in with Alby (summary)

steakOS does **not** invent a Nostr identity at invite time. The account must exist first (username + password). The person links their own Alby public key under **Security** while logged in. Never paste an `nsec` (secret key) into steakOS.

Full steps: Help → **Sign in with Alby (Nostr)**.

## After invite

- Change profile anytime from the Users table
- **Disable** (preferred) instead of deleting — keeps history
- **Clear authenticator** only if their phone was lost (they re-enrol under Security)

## Who may invite

**Admin** and **CEO**. CEO **cannot** assign the **Admin** profile (cannot mint Admin).

Related: [Which role to pick] · [Safety: Admin & CEO] · [Sign in with Alby] · [Roles overview]'''],
            [key: 'help.security.nostrLogin', title: 'Sign in with Alby (Nostr)', route: '/security', family: 'core',
             body: '''# Sign in with Alby (Nostr)

Use a **NIP-07** browser extension (e.g. **Alby**) so you can approve a short login request instead of typing your password every time. Your **secret key stays in the extension** — steakOS never asks for `nsec`.

## What must be true

1. A steakOS **user account** already exists (Admin created you under **System → Users**, or you are the setup Admin).
2. You can sign in once with **username + temporary password**.
3. **Alby** (or similar) is installed in this browser and **unlocked**.
4. On **Security**, **Linked** shows **Yes** after you click **Link browser extension** (or Re-link).

Until step 4 succeeds, the login page button **Sign in with browser extension** returns *This login key is not linked to a steakOS user*.

## Setup checklist (person using Alby)

1. Install [Alby](https://getalby.com/) (or another NIP-07 extension) and create or import a Nostr identity there.
2. Sign in to steakOS with **username + password**.
3. Open **System → Security** (or the Security link in the shell).
4. Under **Sign in with browser extension**, click **Link browser extension**.
5. Approve the request in Alby.
6. Confirm **Linked: Yes**.
7. Sign out. On the login page, click **Sign in with browser extension** and approve again in Alby.

## Admin checklist (invite someone else)

1. **System → Users → Add user** — username, temporary password, role profile.
2. Tell them the password out-of-band.
3. Tell them to complete the **Setup checklist** above (password first, then link Alby).
4. On Users, the **Extension** column turns **Yes** when linking succeeded.

## Company Nostr level (wizard / Integrations)

The setup wizard **Nostr** step and **System → Integrations → Nostr** control the **company** relay / audit level (0 / 1 / 2). That is separate from per-user Alby login:

- **Level 0** — skip publish; Alby login still works after each user links under Security.
- **Level 1+** — also run the local private relay for audit events.

Operator public id in the wizard is optional company metadata — it does **not** replace linking each human account under Security.

## Safety

- **Never** paste `nsec` / seed / private key into steakOS, the wizard, or Users.
- One Alby identity can link to **one** steakOS user (already linked → conflict).
- Password login remains available after linking.
- Prefer Disable over delete when someone leaves; they can re-link Alby after a new invite if needed.

Related: [Invite a teammate] · [Nostr integration] · Security page.'''],
            [key: 'help.users.danger', title: 'Safety: Admin, CEO & disable', route: null, family: 'core',
             body: '''# Safety: Admin, CEO & disable

## Do

- Keep **Admin** and **CEO** as **separate logins**
- Prefer **Disable** over delete when someone leaves
- Use **Clear authenticator** only for lost devices
- Confirm before applying a role change to **all** users on that profile

## Don’t

- Share the Admin password
- Make every owner an Admin “just in case”
- Leave the last Admin disabled (product blocks this)
- Give humans agent (`ROLE_AI`) access from Users

## Last Admin / last CEO

- You cannot disable or demote the **last Admin**
- Demoting the last CEO is a business risk — keep at least one owner account

## Money

Only profiles with **Settle money** (Finance, Admin, CEO by default) may settle invoices / run payment specials. Office creating a customer is fine; Office settling is blocked.'''],
            [key: 'help.users.privacy', title: 'Privacy, HR & Read only', route: null, family: 'core',
             body: '''# Privacy, HR & Read only

- **HR** desks and people records are sensitive. Grant **See** on Human capital only when needed (Bronze+ privacy ack still applies where shipped).
- **Read only** is for visitors, auditors, and trainees who should **watch**, not change data.
- **Demo / training instances** may use Read only or Office freely — prefer not testing destructive Admin actions on live books.
- Content on records (files, notes) follows the same desk access as the host record.

When in doubt: **Read only** for observation, **Office** for daily front desk, escalate to Admin only for system work.'''],
            [key: 'help.roles.demo', title: 'Practice roles (demo / training)', route: null, family: 'core',
             body: '''# Practice roles (demo / training)

On a **demo restaurant** or training tenant:

| Goal | Use profile |
|------|-------------|
| Browse without breaking data | **Read only** (or seeded `demo` user) |
| Take orders / quotes | **Office** |
| Stock / kitchen flows | **Ops** |
| Invoices & settle | **Finance** |
| Invite people / change defaults | **CEO** (or Admin for catalogs / import) |

**Tips**

- Practice on demo first; then mirror the same profiles on the live company
- After practice, **disable** throwaway users
- Help drawer (**?**) on **Roles** and **Users** shows these articles in context'''],

            // OI-I18N02 — partner communication language
            [key: 'help.partners.locale', title: 'Partner communication language', route: null, family: 'core',
             body: '''# Communication language

On **Customer** and **Vendor** masters, set **Communication language** (e.g. German / English).

## What it controls

- **PDF labels** on quotes, orders, purchase orders, and invoices to that partner
- **Email** subject and body to that partner when messaging is enabled (same language)
- Draft previews follow the live setting; after send / confirm / issue the document **freezes** its language

## Defaults

Leave blank to use **English (en_US)** for outbound docs and email. This is separate from your screen language in Settings.

## Honest limits (v1)

- Catalogue item descriptions are not dual-language yet
- **Nostr partner DMs** still residual (PL-TR4 giftwrap) — email uses partner locale today'''],

            // OI-F23 / L14 — German VAT operator help
            [key: 'help.tax.ust', title: 'German VAT (USt) tax codes', route: '/settings', family: 'finance',
             body: '''# German VAT (USt) tax codes

steakOS ships a **DE/AT tax-code pack**. Enable codes under **System → Settings → Tax codes we use**, then pick them on quote / sales order / PO lines.

## Common DE codes

| Code | Rate | Use |
|------|------|-----|
| **DE_USt_STD** | 19% | Standard goods & services |
| **DE_USt_RED** | 7% | Reduced (operator chooses eligibility) |
| **DE_USt_0** | 0% | Explicit zero-rated / exempt line |
| **DE_RC** | 0% + reverse charge | B2B reverse charge — buyer accounts for VAT |
| **DE_IG** | 0% + IG | Intra-community supply (valid VAT ID) |
| **DE_EXPORT** | 0% | Export outside EU |

Austria uses the same pattern (`AT_USt_STD` 20%, `AT_USt_RED` 10%, …).

## Regime

- **Germany VAT / Austria VAT** — line tax required; default code applies when a line has no pick
- **Kleinunternehmer / None** — **no VAT** on sales documents (D7)

## Honest limits

- Operator owns rates — seed is a starting point, not legal advice
- Not UStVA / ELSTER filing
- US multi-state depth is out of this launch module

**See also:** Reverse charge & IG · Business PDFs · Tax tab on Finance hub'''],

            [key: 'help.tax.reverseCharge', title: 'Reverse charge & intra-community (IG)', route: '/sales', family: 'finance',
             body: '''# Reverse charge & intra-community (IG)

Use tax codes **DE_RC** / **AT_RC** (reverse charge) or **DE_IG** / **AT_IG** (intra-community) on sales lines when those rules apply.

## What steakOS does

- Line **output VAT is 0%** for RC / IG codes
- PDF and document notes add a **legal phrase** (EN + DE):
  - Reverse charge — VAT accounted for by the recipient / *Steuerschuldnerschaft des Leistungsempfängers*
  - Intra-community supply (0% VAT) / *Innergemeinschaftliche Lieferung*

## Operator checklist

1. Confirm the customer has a valid VAT ID when required
2. Pick **DE_RC** or **DE_IG** (or AT equivalents) on the line — do not leave the standard 19%/20% code
3. Download the PDF and check the note block

## Honest limits

- steakOS does **not** validate EU VIES IDs in v1
- Filing / Intrastat / OSS are out of scope for this module
- Wrong code choice is an operator error — not auto-corrected

**See also:** German VAT (USt) tax codes · Business PDFs'''],

            // A8 — European A4 commercial PDFs
            [key: 'help.docs.pdf', title: 'Business PDFs (A4)', route: '/sales', family: 'core',
             body: '''# Business PDFs (A4)

Quote, sales order, purchase order, and invoice screens offer **Download … PDF**.

## What you get

- **ISO A4** portrait business letter (European filing margins)
- **Multi-line** tables: description, qty, unit price, line total (long text wraps)
- **Totals** match the amounts on the screen for that document

Open the PDF in a new tab (inline). Print from the browser if needed.

## Language

PDF **labels** follow each partner’s **Communication language** (Customer / Vendor). English, German, and Spanish packs ship in the alpha; other locales fall back to English until more packs exist. Catalogue line descriptions stay as stored.

Draft PDFs follow the live partner setting. After you **send / confirm / issue**, the document freezes that language (changing the partner later does not rewrite history).

## Honest limits (alpha)

- Not e-invoice XML (ZUGFeRD / XRechnung)
- Not digitally signed
- Not a marketing template — a credible trade document'''],

            // Action - NOW / My work board (OI-I18N03 residual)
            [key: 'help.work.actionNow', title: 'Action - NOW', route: '/work', family: 'core',
             body: '''# Action - NOW

**My work** (`/work`) is the **Action - NOW** Eisenhower board for prioritising tasks (urgency × importance). A mini board also appears on the CEO dashboard.

## Toolbar

- **New task** — create a task (returns here afterward)
- **Refresh** — reload the board
- **Agentic Tasks** (violet toggle) — switch between the human open board and the agentic AI:* queue (see below)
- **Task list** — open the full Task domain list when you need search, sort, or bulk browsing

## How to read the board

- **Rows** = urgency (high → low)
- **Columns** = importance (low → high)
- Each cell groups tasks that share the same urgency × importance — open a task to edit urgency, importance, deadline, or assignee

## Human board (toggle off)

Shows **open** human work (`OPEN` / `IN_PROGRESS`, plus in-flight AI:* that still counts as open). Done / cancelled stay off the board.

## Agentic Tasks (toggle on)

Shows tasks that still have an agentic AI: status (for example AI:waiting-go or AI:done) and that a human has **not** yet confirmed as **DONE**. Violet chips mark agentic statuses. Turn the toggle **off** to return to the human board.

Hover the toggle for a short reminder of what on vs off means.

## Where else

- **Dashboard** — mini board + jump to My work
- Task form / modal — set **Urgency** and **Importance** (low / medium / high)

## Honest limits

- The board does not invent priorities — you set urgency and importance on each task
- Full list filters (overdue, assignee, …) live on the **Task** domain list, not on this board

**Tip:** Open this drawer with **?** or **Ctrl+/** from My work or the dashboard.'''],

            // NOT bound to /domains — only linked as related tip on master-data pages
            [key: 'content.rail', title: 'Content on records', route: null, family: 'core',
             body: '''# Object Companion (content rail)

On list/detail screens for a record:

- **Link** — external or internal URL  
- **File** — attachment  
- **Content** — typed BODY (Note, Help, Risk, …)

Master-data pages use the same rail as desks.'''],
            [key: 'app.desks', title: 'Lists & desks', route: '/help', family: 'core',
             body: '''# Lists & desks

How lists and desks work in steakOS. Examples: **Tasks** and **Buyers**.

''' + HOW_DESKS + '''

## Single vs split

| Pattern | When | Detail |
|---------|------|--------|
| **Single** | Sales, Buyers, Delivery, AfterSales, Assets, Strategy, Development | Full-width list; View/Edit in modals; process actions in view-modal footer |
| **Split** | Shop Floor, Digital, Finance invoices/transactions, CRM, Pricing, HR, Capability, Agents | Detail pane only after you select a row — never an empty white column on load |

## Action column

Fixed order **View · Edit · Delete**. Icons stay column-aligned because gated actions leave blank placeholders. Edit is blank when the record cannot be changed (issued PO, accepted quote, …).

**See also:** any desk Help drawer · Buyers Desk · Task master list'''],
    ].asImmutable()

    void ensureSeeded() {
        // Migrate legacy help key app.ui20 → app.desks
        HelpTopic legacyUi20 = HelpTopic.findByHelpKey('app.ui20')
        if (legacyUi20 && !HelpTopic.findByHelpKey('app.desks')) {
            legacyUi20.helpKey = 'app.desks'
            legacyUi20.title = 'Lists & desks'
            legacyUi20.save(failOnError: true)
        } else if (legacyUi20 && HelpTopic.findByHelpKey('app.desks')) {
            // Duplicate after rename — drop legacy system topic
            if (legacyUi20.systemDefault) {
                ObjectContent.findAllByHostPackageAndHostResourceAndHostId(HOST_PACKAGE, HOST_RESOURCE, legacyUi20.id).each { it.delete() }
                legacyUi20.delete(flush: true)
            }
        }
        int order = 0
        FEATURE_SEEDS.each { Map s ->
            HelpTopic topic = HelpTopic.findByHelpKey(s.key as String)
            String route = s.route as String
            if (!topic) {
                topic = new HelpTopic(
                        helpKey      : s.key as String,
                        title        : s.title as String,
                        routePattern : route,
                        family       : s.family as String,
                        sortOrder    : order,
                        systemDefault: Boolean.TRUE
                )
                topic.save(failOnError: true)
            } else if (topic.systemDefault) {
                // Keep route / title current (e.g. Integrations hub moves)
                boolean dirty = false
                if (topic.routePattern != route) {
                    topic.routePattern = route
                    dirty = true
                }
                if (topic.title != (s.title as String)) {
                    topic.title = s.title as String
                    dirty = true
                }
                if (dirty) topic.save(failOnError: true)
            }
            order++
            ensureBody(topic, s.body as String, true)
        }
    }

    /**
     * @param forceUpdate if true, refresh SYSTEM body text from seeds (improves existing installs)
     */
    private void ensureBody(HelpTopic topic, String markdown, boolean forceUpdate = false) {
        ObjectContent existing = ObjectContent.findByHostPackageAndHostResourceAndHostIdAndChannel(
                HOST_PACKAGE, HOST_RESOURCE, topic.id, 'BODY')
        if (existing) {
            if (forceUpdate && topic.systemDefault && markdown && existing.bodyText != markdown) {
                existing.bodyText = markdown
                existing.title = topic.title
                existing.contentTypeCode = HELP_TYPE
                existing.save(failOnError: false)
            }
            return
        }
        new ObjectContent(
                name            : topic.helpKey,
                npubOwner       : 'SYSTEM',
                hostPackage     : HOST_PACKAGE,
                hostResource    : HOST_RESOURCE,
                hostId          : topic.id,
                channel         : 'BODY',
                contentTypeCode : HELP_TYPE,
                title           : topic.title,
                bodyFormat      : 'MARKDOWN',
                bodyText        : markdown,
                pinned          : Boolean.TRUE
        ).save(failOnError: false)
    }

    List listTopics() {
        ensureSeeded()
        HelpTopic.list(sort: 'sortOrder', order: 'asc').collect { topicMap(it) }
    }

    Map resolveForRoute(String route) {
        resolveForRoute(route, null)
    }

    Map resolveForRoute(String route, String langHint) {
        resolveForContext(route, langHint, [:])
    }

    /**
     * ADR-044 — resolve with optional HelpContext fields.
     * Fallback order: desk+tab(+resource) topic key → desk topic → path exact/prefix → default.
     */
    Map resolveForContext(String route, String langHint, Map extra) {
        ensureSeeded()
        String lang = normalizeLang(langHint)
        Map ctx = parseContext(route)
        if (extra) {
            if (extra.desk) ctx.desk = extra.desk
            if (extra.tab) ctx.tab = extra.tab
            if (extra.resource) ctx.resource = extra.resource
            if (extra.objectId) ctx.objectId = extra.objectId
            if (extra.status) ctx.status = extra.status
            if (extra.processCode) ctx.processCode = extra.processCode
        }
        String path = ctx.path as String
        String desk = (ctx.desk ?: '') as String
        String tab = (ctx.tab ?: '') as String
        String resource = (ctx.resource ?: '') as String

        List<HelpTopic> all = HelpTopic.list(sort: 'sortOrder', order: 'asc')
                .findAll { it.routePattern || it.helpKey }

        HelpTopic best = null

        // 0) Structured keys: desk.tab.resource → desk.tab → desk
        if (desk) {
            List<String> candidates = []
            if (tab && resource) candidates << "desk.${desk}.${tab}.${resource}".toString()
            if (tab) candidates << "desk.${desk}.${tab}".toString()
            candidates << "desk.${desk}".toString()
            for (String key : candidates) {
                best = all.find { it.helpKey == key }
                if (best) break
            }
            // Also match routePattern like /sales?tab=quotes
            if (!best && tab) {
                String patterned = "${path}?tab=${tab}"
                best = all.find { it.routePattern == patterned }
            }
        }

        // 1) Exact seeded topic (prefer canonical page topic when several share a route)
        if (!best) {
            List<HelpTopic> exact = all.findAll { t -> t.routePattern && path == t.routePattern }
            if (exact) {
                if (path == '/settings' || path?.startsWith('/settings/')) {
                    best = exact.find { it.helpKey == 'system.settings' } ?: exact[0]
                } else if (path == '/work' || path?.startsWith('/work/')) {
                    best = exact.find { it.helpKey == 'help.work.actionNow' } ?: exact[0]
                } else if (path == '/dashboard' || path?.startsWith('/dashboard/')) {
                    best = exact.find { it.helpKey == 'app.dashboard' } ?: exact[0]
                } else if (path == '/sales' || path?.startsWith('/sales')) {
                    best = exact.find { it.helpKey == 'desk.sales' || it.helpKey?.startsWith('desk.sales') } ?: exact[0]
                } else {
                    best = exact[0]
                }
            }
        }

        // 2) Longest prefix among feature routes — but never treat bare /domains as a win
        //    over a more specific domain context
        if (!best && !(ctx.kind == 'domain')) {
            best = all.findAll { t ->
                t.routePattern && path.startsWith(t.routePattern) && t.routePattern != '/'
            }.max { it.routePattern.length() }
        }

        // 3) Domain master data → synthetic or domain-specific body
        if (ctx.kind == 'domain' && !best) {
            return resolveDomain(ctx, all, lang)
        }

        // 4) Feature partial match for desks already handled; fallback dashboard
        if (!best) {
            best = all.find { it.helpKey == 'app.dashboard' }
        }

        Map body = best ? bodyFor(best, lang) : null
        [
                route          : path,
                context        : ctx,
                topic          : best ? topicMap(best, lang) : null,
                body           : body,
                relatedTopics  : relatedFor(ctx, all, best, lang),
                suggestedActions: actionsFor(ctx),
                language       : lang,
                topics         : HelpTopic.list(sort: 'sortOrder', order: 'asc').collect { topicMap(it, lang) }
        ]
    }

    private static String normalizeLang(String hint) {
        if (!hint) return 'en'
        String h = hint.trim().toLowerCase()
        if (h.startsWith('de')) return 'de'
        if (h.startsWith('es')) return 'es'
        if (h.contains(',')) h = h.split(',')[0].trim()
        if (h.startsWith('de')) return 'de'
        if (h.startsWith('es')) return 'es'
        return 'en'
    }

    private Map resolveDomain(Map ctx, List<HelpTopic> seeded) {
        resolveDomain(ctx, seeded, 'en')
    }

    private Map resolveDomain(Map ctx, List<HelpTopic> seeded, String lang) {
        String pkg = ctx.package as String
        String res = ctx.resource as String
        String mode = ctx.mode as String
        String key = "${pkg}.${res}"
        Map fam = FAMILY_META[pkg] ?: [label: pkg, desk: null, blurb: 'Master data']
        String humanRes = humanizeResource(res, lang)
        String modeLabel = modeLabel(mode, lang)
        String title = "${humanRes} · ${modeLabel}"

        String specific = domainBlurb(key, lang)
        String bodyText = buildDomainMarkdown(title, pkg, res, humanRes, mode, modeLabel, fam, specific, ctx, lang)

        Map syntheticTopic = [
                id          : null,
                helpKey     : "domain.${pkg}.${res}",
                title       : title,
                routePattern: "/domains/${pkg}/${res}",
                family      : pkg,
                sortOrder   : 500,
                synthetic   : true
        ]

        List related = []
        if (fam.desk) {
            HelpTopic deskTopic = seeded.find { it.routePattern == fam.desk || (it.routePattern && fam.desk.startsWith(it.routePattern)) }
            if (deskTopic) related << topicMap(deskTopic)
        }
        HelpTopic companion = HelpTopic.findByHelpKey('content.rail')
        if (companion) related << topicMap(companion)
        HelpTopic importT = HelpTopic.findByHelpKey('system.import')
        if (importT && pkg in ['sales', 'procurement', 'finance']) related << topicMap(importT)

        [
                route           : ctx.path,
                context         : ctx,
                topic           : syntheticTopic,
                unbound         : isUnboundDataProduct(pkg, res),
                body            : [
                        id             : null,
                        title          : title,
                        bodyText       : bodyText,
                        contentTypeCode: HELP_TYPE,
                        pinned         : true,
                        synthetic      : true
                ],
                relatedTopics   : related,
                suggestedActions: actionsFor(ctx),
                language        : lang,
                topics          : HelpTopic.list(sort: 'sortOrder', order: 'asc').collect { topicMap(it) }
        ]
    }

    /**
     * Data products that sit alone (no desk / process binding) — reference / future use.
     * Keep in sync with frontend isUnboundDataProduct (domainRegistry.js).
     */
    private static final Set UNBOUND_DATA_PRODUCTS = [
            'sales.Market', 'sales.GoToMarket', 'sales.SalesPlan',
            'procurement.Contract', 'procurement.SourcingCategory', 'procurement.SourcingPlan',
            'service.Satisfaction',
            'finance.PaymentOption',
    ].asImmutable() as Set

    private static boolean isUnboundDataProduct(String pkg, String res) {
        pkg && res && ("${pkg}.${res}" in UNBOUND_DATA_PRODUCTS)
    }

    private static String domainBlurb(String key, String lang) {
        if (lang == 'de' && DOMAIN_BLURBS_DE[key]) return DOMAIN_BLURBS_DE[key]
        if (lang == 'es' && DOMAIN_BLURBS_ES[key]) return DOMAIN_BLURBS_ES[key]
        return DOMAIN_BLURBS[key]
    }

    private static final Map DOMAIN_BLURBS_DE = [
            'core.Task'      : '''**Aufgaben** sind der **einzige Arbeitstyp** (persönlich, prozessgebunden oder Agent).

**Felder:** Status (OPEN/IN_PROGRESS/DONE/CANCELLED), Dringlichkeit & Wichtigkeit (1–3), Fristen, Zuweisung (Mensch und/oder Agent), Content-Leiste.

**Tipps**
- Status ändern statt Historie zu löschen
- **Meine Arbeit** (`/work`) = Eisenhower **Aktivitäten - JETZT** (Schalter **Agentische Aufgaben** für AI:*)
- ProjectItem kann optional taskId verknüpfen
- Spalte **Aktion**: **Ansehen (Auge) · Bearbeiten (Stift) · Löschen (Papierkorb)** — Ansehen/Bearbeiten öffnen **Modals**''',
            'capability.Process': '''**Prozesse** sind der Katalog der Arbeitsflüsse (APQC 13.1).

**Arten:** SYSTEM (geseedet, nur lesen) · HUMAN · AGENTIC · OTHER. Status: DRAFT · ACTIVE · RETIRED.

SYSTEM-Seeds: Bestellung, Angebot→Auftrag, Rechnung, Serviceauftrag, Anfrage, Einstellung, Projekt, HITL. Mermaid-Diagramm auf der Prozess-Detailansicht; **So funktioniert’s** auf den Desks.''',
            'sales.Customer' : '''**Kunde** (Person oder Organisation).

**Wichtige Felder:** customerKind (PERSON/ORGANIZATION), E-Mail, Kontakt, taxId, Währung, active.

**Ablauf:** Kunde anlegen → im **Vertriebstisch** Angebot/Auftrag → Bestätigung kann eine **Entwurfsrechnung** erzeugen.

**Tipps:** Spalte **Aktion** mit **Auge / Stift / Papierkorb**; Ansehen und Bearbeiten öffnen **Modals**.

**Verwandt:** Vertriebstisch · Rechnungen · CSV-Import''',
            'sales.CustomerQuote': '''**Kundenangebote** vor dem Auftrag.

Bevorzugt im **Vertriebstisch**: anlegen → senden → annehmen/ablehnen. Positionen unter **QuoteLine**.''',
            'sales.SalesOrder': '''**Verkaufsaufträge** als verbindliche Bestellung.

Ablauf: Angebot annehmen → Auftrag → **bestätigen** (optional Finanz-Entwurf).''',
            'procurement.Vendor': '''**Lieferant** für den Einkauf.

Hier anlegen oder per CSV importieren, dann am **Einkaufs-Desk** für Bestellungen nutzen.

**Tipps:** **Aktion** **Auge / Stift / Papierkorb**; Ansehen/Bearbeiten öffnen **Modals**.''',
            'procurement.PurchaseOrder': '''**Bestellungen** an Lieferanten.

Ablauf: anlegen → senden → bestätigen → Wareneingang. **Aktion**: Bearbeiten/Löschen nur im Status **DRAFT**; danach nur Ansehen. Prozessschritte im **Ansichts-Modal**.''',
    ].asImmutable()

    private static final Map DOMAIN_BLURBS_ES = [
            'core.Task'      : '''Las **tareas** son el **único tipo de trabajo** (personal, ligado a un proceso o a un agente).

**Campos:** estado (OPEN/IN_PROGRESS/DONE/CANCELLED), urgencia e importancia (1–3), plazos, asignación (persona y/o agente), barra de contenido.

**Consejos**
- Cambie el estado en lugar de borrar el historial
- **Mi trabajo** (`/work`) = Eisenhower **Actividades - AHORA** (interruptor **Tareas agénticas** para AI:*)
- ProjectItem puede vincular opcionalmente un taskId
- Columna **Acción**: **Ver (Ojo) · Editar (Lápiz) · Eliminar (Papelera)** — Ver/Editar abren **modales**''',
            'capability.Process': '''Los **procesos** son el catálogo de flujos de trabajo (APQC 13.1).

**Tipos:** SYSTEM (sembrados, solo lectura) · HUMAN · AGENTIC · OTHER. Estado: DRAFT · ACTIVE · RETIRED.

Semillas SYSTEM: pedido de compra, oferta→pedido, factura, orden de servicio, consulta, contratación, proyecto, HITL. Diagrama Mermaid en el detalle del proceso; **Cómo funciona** en los escritorios.''',
            'sales.Customer' : '''**Cliente** (persona u organización).

**Campos importantes:** customerKind (PERSON/ORGANIZATION), correo, contacto, taxId, moneda, active.

**Flujo:** crear cliente → en el **escritorio de ventas** oferta/pedido → la confirmación puede generar una **factura borrador**.

**Consejos:** columna **Acción** con **Ojo / Lápiz / Papelera**; Ver y Editar abren **modales**.

**Relacionado:** escritorio de ventas · facturas · importación CSV''',
            'sales.CustomerQuote': '''**Ofertas de cliente** antes del pedido.

Prefiera el **escritorio de ventas**: crear → enviar → aceptar/rechazar. Líneas en **QuoteLine**.''',
            'sales.SalesOrder': '''**Pedidos de venta** como compromiso comercial vinculante.

Flujo: aceptar oferta → pedido → **confirmar** (opcional borrador financiero).''',
            'procurement.Vendor': '''**Proveedor** para compras.

Créelo aquí o importe por CSV; úselo en el **escritorio de compradores** para pedidos de compra.

**Consejos:** **Acción** **Ojo / Lápiz / Papelera**; Ver/Editar abren **modales**.''',
            'procurement.PurchaseOrder': '''**Pedidos de compra** a proveedores.

Flujo: crear → enviar → confirmar → recibir. **Acción**: Editar/Eliminar solo en **DRAFT**; después solo Ver. Pasos de proceso en el **modal de vista**.''',
    ].asImmutable()

    private static String buildDomainMarkdown(String title, String pkg, String res, String humanRes,
                                              String mode, String modeLabel, Map fam, String specific, Map ctx, String lang) {
        StringBuilder sb = new StringBuilder()
        sb.append("# ${title}\n\n")
        if (lang == 'de') {
            sb.append("**${humanRes}**-Stammdaten in **${fam.label}**.\n\n")
            sb.append(fam.blurb ?: '').append("\n\n")
            sb.append("## Was Sie gerade tun\n\n")
            switch (mode) {
                case 'create':
                    sb.append("Sie sind im **Anlegen**-Formular. Pflichtfelder ausfüllen und speichern.\n\n"); break
                case 'edit':
                    sb.append("Sie **bearbeiten** Datensatz **#${ctx.recordId}**. Speichern übernimmt die Änderungen. Content-Leiste für Notizen/Dateien.\n\n"); break
                default:
                    sb.append("Sie sehen die **Liste** der ${humanRes}-Datensätze. Zeile öffnen zum Bearbeiten oder neu anlegen.\n\n")
            }
            if (specific) {
                sb.append("## Über dieses Objekt\n\n").append(specific).append("\n\n")
            } else {
                sb.append("## Über dieses Objekt\n\nAdmin-Liste/Formular für **`${pkg}.${res}`**. Tagesarbeit oft bequemer im Fach-**Desk**.\n\n")
            }
            sb.append("## Paket\n\n")
            sb.append("- **Familie:** ${fam.label} (`${pkg}`)\n")
            sb.append("- **Ressource:** `${res}`\n")
            sb.append("- **Route:** `${ctx.path}`\n")
            if (fam.desk) sb.append("- **Operativer Desk:** ${fam.desk}\n")
            sb.append("\n## Content-Leiste\n\nLinks, Dateien und Notizen am Datensatz ohne die Seite zu verlassen.\n")
        } else if (lang == 'es') {
            sb.append("Datos maestros de **${humanRes}** en **${fam.label}**.\n\n")
            sb.append(fam.blurb ?: '').append("\n\n")
            sb.append("## Qué está haciendo\n\n")
            switch (mode) {
                case 'create':
                    sb.append("Está en el formulario de **crear**. Complete los campos obligatorios y guarde.\n\n"); break
                case 'edit':
                    sb.append("Está **editando** el registro **#${ctx.recordId}**. Guardar aplica los cambios. Use la barra de contenido para notas/archivos.\n\n"); break
                default:
                    sb.append("Está viendo la **lista** de registros de ${humanRes}. Abra una fila para editar o cree uno nuevo.\n\n")
            }
            if (specific) {
                sb.append("## Sobre este objeto\n\n").append(specific).append("\n\n")
            } else {
                sb.append("## Sobre este objeto\n\nLista/formulario Admin para **`${pkg}.${res}`**. El trabajo diario suele ser más cómodo en el **escritorio** de la familia.\n\n")
            }
            sb.append("## Paquete\n\n")
            sb.append("- **Familia:** ${fam.label} (`${pkg}`)\n")
            sb.append("- **Recurso:** `${res}`\n")
            sb.append("- **Ruta:** `${ctx.path}`\n")
            if (fam.desk) sb.append("- **Escritorio operativo:** ${fam.desk}\n")
            sb.append("\n## Barra de contenido\n\nEnlaces, archivos y notas en el registro sin salir de la página.\n")
        } else {
            sb.append("**${humanRes}** master data in **${fam.label}**.\n\n")
            sb.append(fam.blurb ?: '').append("\n\n")
            sb.append("## What you are doing\n\n")
            switch (mode) {
                case 'create':
                    sb.append("You are on the **create** form. Fill required fields and save to add a new ${humanRes}.\n\n"); break
                case 'edit':
                    sb.append("You are **editing** record **#${ctx.recordId}**. Changes apply after save. Use the content rail for notes/files.\n\n"); break
                default:
                    sb.append("You are on the **list** of ${humanRes} records. Open a row to edit, or create a new one.\n\n")
            }
            if (specific) {
                sb.append("## About this object\n\n").append(specific).append("\n\n")
            } else {
                sb.append("## About this object\n\nStandard admin list/form for **`${pkg}.${res}`**. Day-to-day work is often easier on the family **desk** when one exists.\n\n")
            }
            sb.append("## Package\n\n")
            sb.append("- **Family:** ${fam.label} (`${pkg}`)\n")
            sb.append("- **Resource:** `${res}`\n")
            sb.append("- **Route:** `${ctx.path}`\n")
            if (fam.desk) sb.append("- **Operational desk:** ${fam.desk}\n")
            sb.append("\n## Content rail\n\nAttach **links**, **files**, and typed **notes** without leaving the page.\n")
        }
        return sb.toString()
    }

    /**
     * Parse SPA path into context.
     */
    Map parseContext(String route) {
        String path = (route ?: '/').trim()
        if (!path.startsWith('/')) path = '/' + path
        // strip query/hash if any
        int q = path.indexOf('?')
        if (q >= 0) path = path.substring(0, q)
        int h = path.indexOf('#')
        if (h >= 0) path = path.substring(0, h)
        // normalize trailing slash (except root)
        if (path.length() > 1 && path.endsWith('/')) path = path[0..-2]

        Map ctx = [path: path, kind: 'feature', package: null, resource: null, mode: null, recordId: null]

        // /domains/{pkg}/{Resource}[/new|/id]
        def m = path =~ /^\/domains\/([^\/]+)\/([^\/]+)(?:\/(new|\d+))?$/
        if (m.matches()) {
            ctx.kind = 'domain'
            ctx.package = m[0][1]
            ctx.resource = m[0][2]
            String tail = m[0][3]
            if (tail == 'new') {
                ctx.mode = 'create'
            } else if (tail && tail.isLong()) {
                ctx.mode = 'edit'
                ctx.recordId = tail as Long
            } else {
                ctx.mode = 'list'
            }
            return ctx
        }

        if (path.startsWith('/finance/')) {
            ctx.package = 'finance'
            ctx.resource = path.substring('/finance/'.length())
        } else if (path.startsWith('/capability')) {
            ctx.package = 'capability'
        } else if (path.startsWith('/agents')) {
            ctx.package = 'agents'
        } else if (path == '/strategy' || path?.startsWith('/strategy/')) {
            ctx.package = 'strategy'
        } else if (path == '/development' || path?.startsWith('/development/')) {
            ctx.package = 'development'
        } else if (path == '/assets' || path?.startsWith('/assets/')) {
            ctx.package = 'asset'
        } else if (path in ['/sales', '/scm', '/scm/buyers', '/scm/shopfloor', '/delivery', '/aftersales', '/hr', '/digital']
                || path?.startsWith('/scm/')) {
            ctx.package = path.substring(1)
        }

        return ctx
    }

    private List relatedFor(Map ctx, List<HelpTopic> all, HelpTopic primary, String lang = 'en') {
        List out = []
        String path = ctx.path as String
        String pkg = ctx.package as String
        if (pkg && FAMILY_META[pkg]?.desk) {
            String desk = FAMILY_META[pkg].desk
            HelpTopic t = all.find { it.routePattern == desk }
            if (t && t.id != primary?.id) out << topicMap(t, lang)
        }
        // PDR-025 — Roles / Users cluster
        if (path == '/roles' || path?.startsWith('/roles/') || path == '/users' || path?.startsWith('/users/')) {
            [
                    'help.roles.overview',
                    'help.roles.presets',
                    'help.roles.matrix',
                    'help.users.invite',
                    'help.security.nostrLogin',
                    'help.users.danger',
                    'help.users.privacy',
                    'help.roles.demo'
            ].each { String key ->
                HelpTopic t = HelpTopic.findByHelpKey(key)
                if (t && t.id != primary?.id && !out.any { it.helpKey == key }) {
                    out << topicMap(t, lang)
                }
            }
            return out
        }
        // Security — 2FA + Alby / NIP-07 login
        if (path == '/security' || path?.startsWith('/security/')) {
            ['help.security.nostrLogin', 'help.users.invite', 'integrations.nostr'].each { String key ->
                HelpTopic t = HelpTopic.findByHelpKey(key)
                if (t && t.id != primary?.id && !out.any { it.helpKey == key }) {
                    out << topicMap(t, lang)
                }
            }
            return out
        }
        // Settings — language/region first, then money/tax cluster
        if (path == '/settings' || path?.startsWith('/settings')) {
            ['system.settings', 'help.partners.locale', 'currency.catalog',
             'finance.primaryRail', 'help.tax.ust', 'help.docs.pdf'].each { String key ->
                HelpTopic t = HelpTopic.findByHelpKey(key)
                if (t && t.id != primary?.id && !out.any { it.helpKey == key }) {
                    out << topicMap(t, lang)
                }
            }
        }
        // A2 / OI-FX01 — money rail + live payments cluster
        if (path?.startsWith('/finance')
                || path == '/company' || path?.startsWith('/company')) {
            ['finance.primaryRail', 'finance.payments.live', 'finance.invoices', 'finance.treasury',
             'finance.postingRules', 'help.docs.pdf', 'help.tax.ust', 'help.tax.reverseCharge'].each { String key ->
                HelpTopic t = HelpTopic.findByHelpKey(key)
                if (t && t.id != primary?.id && !out.any { it.helpKey == key }) {
                    out << topicMap(t, lang)
                }
            }
        }
        // Action - NOW / My work / dashboard
        if (path == '/work' || path?.startsWith('/work')
                || path == '/dashboard' || path?.startsWith('/dashboard')) {
            ['help.work.actionNow', 'app.dashboard'].each { String key ->
                HelpTopic t = HelpTopic.findByHelpKey(key)
                if (t && t.id != primary?.id && !out.any { it.helpKey == key }) {
                    out << topicMap(t, lang)
                }
            }
        }
        // A8 — European A4 PDF help on commercial desks
        if (path?.startsWith('/sales') || path?.startsWith('/scm') || path?.startsWith('/delivery') || path?.startsWith('/aftersales')) {
            HelpTopic pdfHelp = HelpTopic.findByHelpKey('help.docs.pdf')
            if (pdfHelp && pdfHelp.id != primary?.id && !out.any { it.helpKey == 'help.docs.pdf' }) {
                out << topicMap(pdfHelp, lang)
            }
            ['help.tax.ust', 'help.tax.reverseCharge'].each { String key ->
                HelpTopic t = HelpTopic.findByHelpKey(key)
                if (t && t.id != primary?.id && !out.any { it.helpKey == key }) {
                    out << topicMap(t, lang)
                }
            }
        }
        // OI-I18N02 — partner communication language on Customer/Vendor masters + commercial desks
        boolean partnerMaster = (ctx.resource in ['Customer', 'Vendor']) ||
                path?.contains('/Customer') || path?.contains('/Vendor')
        if (partnerMaster || path?.startsWith('/sales') || path?.startsWith('/scm')) {
            HelpTopic locHelp = HelpTopic.findByHelpKey('help.partners.locale')
            if (locHelp && locHelp.id != primary?.id && !out.any { it.helpKey == 'help.partners.locale' }) {
                out << topicMap(locHelp)
            }
        }
        // A3 — Agent activation cluster + Integrations hub
        if (path?.startsWith('/agents') || path == '/settings' || path?.startsWith('/settings')
                || path?.startsWith('/system/integrations')) {
            ['agents.activate', 'agents.hub', 'system.integrations', 'integrations.nostr',
             'integrations.bitcoin', 'finance.primaryRail', 'finance.payments.live'].each { String key ->
                HelpTopic t = HelpTopic.findByHelpKey(key)
                if (t && t.id != primary?.id && !out.any { it.helpKey == key }) {
                    out << topicMap(t, lang)
                }
            }
        }
        // A4 — Security disclosure + lists & desks guide
        if (path == '/about' || path?.startsWith('/about') || path == '/help' || path?.startsWith('/help')) {
            HelpTopic sec = HelpTopic.findByHelpKey('security.disclose')
            if (sec && sec.id != primary?.id && !out.any { it.helpKey == 'security.disclose' }) {
                out << topicMap(sec)
            }
            HelpTopic desks = HelpTopic.findByHelpKey('app.desks') ?: HelpTopic.findByHelpKey('app.ui20')
            if (desks && desks.id != primary?.id && !out.any { it.helpKey in ['app.desks', 'app.ui20'] }) {
                out << topicMap(desks, lang)
            }
        }
        // A7 — Process charts
        if (path?.startsWith('/capability')) {
            ['process.chart', 'capability.hub'].each { String key ->
                HelpTopic t = HelpTopic.findByHelpKey(key)
                if (t && t.id != primary?.id && !out.any { it.helpKey == key }) {
                    out << topicMap(t, lang)
                }
            }
        }
        HelpTopic companion = HelpTopic.findByHelpKey('content.rail')
        if (companion && companion.id != primary?.id) out << topicMap(companion)
        out
    }

    private List actionsFor(Map ctx) {
        List actions = []
        String path = ctx.path as String
        if (ctx.kind == 'domain') {
            String pkg = ctx.package
            String res = ctx.resource
            String base = "/domains/${pkg}/${res}"
            if (ctx.mode == 'list') {
                actions << [label: "Create ${humanize(res as String)}", path: "${base}/new"]
            }
            Map fam = FAMILY_META[pkg]
            if (fam?.desk) {
                actions << [label: "Open ${fam.label} desk", path: fam.desk]
            }
            if (pkg == 'sales' && res == 'Customer') {
                actions << [label: 'Import customers (CSV)', path: '/system/import']
            }
            if (pkg == 'procurement' && res == 'Vendor') {
                actions << [label: 'Import vendors (CSV)', path: '/system/import']
            }
            if (pkg == 'finance' && res == 'Account') {
                actions << [label: 'General ledger', path: '/finance/transactions']
            }
        } else if (path == '/system/import') {
            actions << [label: 'Customer master data', path: '/domains/sales/Customer']
            actions << [label: 'Vendor master data', path: '/domains/procurement/Vendor']
        } else if (path == '/roles' || path?.startsWith('/roles/')) {
            actions << [label: 'Invite users', path: '/users']
            actions << [label: 'Help center', path: '/help']
        } else if (path == '/users' || path?.startsWith('/users/')) {
            actions << [label: 'Manage role defaults', path: '/roles']
            actions << [label: 'Security (own 2FA)', path: '/security']
            actions << [label: 'Help center', path: '/help']
        } else if (path == '/work' || path?.startsWith('/work')) {
            actions << [label: 'New task', path: '/domains/core/Task/new']
            actions << [label: 'Dashboard', path: '/dashboard']
        } else if (path == '/settings' || path?.startsWith('/settings')) {
            actions << [label: 'Integrations', path: '/system/integrations']
            actions << [label: 'Help center', path: '/help']
        }
        actions << [label: 'Onboarding tour', path: '/tour/index.html', external: true]
        actions
    }

    Map getTopic(String helpKey) {
        getTopic(helpKey, null)
    }

    Map getTopic(String helpKey, String langHint) {
        ensureSeeded()
        String lang = normalizeLang(langHint)
        if (helpKey?.startsWith('domain.')) {
            String rest = helpKey.substring('domain.'.length())
            int dot = rest.indexOf('.')
            if (dot > 0) {
                String pkg = rest.substring(0, dot)
                String res = rest.substring(dot + 1)
                return resolveDomain([
                        path    : "/domains/${pkg}/${res}",
                        kind    : 'domain',
                        package : pkg,
                        resource: res,
                        mode    : 'list',
                        recordId: null
                ], HelpTopic.list(), lang)
            }
        }
        HelpTopic t = HelpTopic.findByHelpKey(helpKey)
        if (!t) return null
        Map body = bodyFor(t, lang)
        Map base = topicMap(t, lang)
        base + [body: body, language: lang]
    }

    private Map topicMap(HelpTopic t) {
        topicMap(t, 'en')
    }

    private Map topicMap(HelpTopic t, String lang) {
        String title = t.title
        if (lang == 'de' && FEATURE_TITLES_DE[t.helpKey]) {
            title = FEATURE_TITLES_DE[t.helpKey] as String
        } else if (lang == 'es' && FEATURE_TITLES_ES[t.helpKey]) {
            title = FEATURE_TITLES_ES[t.helpKey] as String
        }
        [
                id          : t.id,
                helpKey     : t.helpKey,
                title       : title,
                routePattern: t.routePattern,
                family      : t.family,
                sortOrder   : t.sortOrder
        ]
    }

    private Map bodyFor(HelpTopic t) {
        bodyFor(t, 'en')
    }

    private Map bodyFor(HelpTopic t, String lang) {
        // Prefer language-specific companion: name suffix .de / .es / default en
        ObjectContent oc = null
        if (lang == 'de') {
            oc = ObjectContent.findByHostPackageAndHostResourceAndHostIdAndChannelAndName(
                    HOST_PACKAGE, HOST_RESOURCE, t.id, 'BODY', t.helpKey + '.de')
        } else if (lang == 'es') {
            oc = ObjectContent.findByHostPackageAndHostResourceAndHostIdAndChannelAndName(
                    HOST_PACKAGE, HOST_RESOURCE, t.id, 'BODY', t.helpKey + '.es')
        }
        if (!oc) {
            oc = ObjectContent.findByHostPackageAndHostResourceAndHostIdAndChannel(
                    HOST_PACKAGE, HOST_RESOURCE, t.id, 'BODY')
        }
        if (!oc) return null
        String text = oc.bodyText
        if (lang == 'de' && FEATURE_BODIES_DE[t.helpKey]) {
            text = FEATURE_BODIES_DE[t.helpKey]
        } else if (lang == 'es' && FEATURE_BODIES_ES[t.helpKey]) {
            text = FEATURE_BODIES_ES[t.helpKey]
        }
        String title = oc.title
        if (lang == 'de' && FEATURE_TITLES_DE[t.helpKey]) {
            title = FEATURE_TITLES_DE[t.helpKey] as String
        } else if (lang == 'es' && FEATURE_TITLES_ES[t.helpKey]) {
            title = FEATURE_TITLES_ES[t.helpKey] as String
        }
        [
                id             : oc.id,
                title          : title,
                bodyText       : text,
                contentTypeCode: oc.contentTypeCode,
                pinned         : oc.pinned
        ]
    }

    private static final Map FEATURE_TITLES_DE = [
            'app.dashboard'      : 'CEO-Dashboard',
            'app.desks'           : 'Listen & Desks',
            'system.settings'    : 'Einstellungen',
            'help.work.actionNow': 'Aktivitäten - JETZT',
            'help.partners.locale': 'Partnersprache (Kommunikation)',
            'help.roles.overview': 'Rollen für ein kleines Team',
            'help.roles.presets' : 'Welche Rolle wählen',
            'help.roles.matrix'  : 'Berechtigungsmatrix (Erweitert)',
            'help.users.invite'  : 'Teammitglied einladen',
            'help.security.nostrLogin': 'Mit Alby anmelden (Nostr)',
            'help.users.danger'  : 'Sicherheit: Admin, CEO & Deaktivieren',
            'help.users.privacy' : 'Datenschutz, HR & Nur lesen',
            'help.roles.demo'    : 'Rollen üben (Demo / Training)',
            'help.tax.ust'       : 'Deutsche USt-Steuercodes',
            'help.tax.reverseCharge': 'Reverse Charge & innergemeinschaftliche Lieferung (IG)',
            'finance.postingRules': 'Buchungsregeln (Auto-Journale)',
            'sales.desk'         : 'Innendienst',
            'scm.desk'           : 'Einkaufs-Desk',
            'scm.shopfloor'      : 'Shopfloor-Steuerung',
            'delivery.desk'      : 'Delivery-Desk',
            'aftersales.desk'    : 'AfterSales-Desk',
            'hr.desk'            : 'HR-Desk',
            'digital.desk'       : 'Digital-Desk',
            'asset.desk'         : 'Asset-Arbeitsplatz',
            'strategy.desk'      : 'Strategie-Desk',
            'development.desk'   : 'Development-Desk',
            'capability.hub'     : 'Capability-Hub',
            'agents.hub'         : 'KI- / Agenten-Hub',
            'finance.treasury'   : 'Treasury',
            'finance.invoices'   : 'Rechnungen',
    ].asImmutable()

    private static final Map FEATURE_TITLES_ES = [
            'app.dashboard'      : 'Panel CEO',
            'app.desks'           : 'Listas y escritorios',
            'system.settings'    : 'Configuración',
            'help.work.actionNow': 'Actividades - AHORA',
            'help.partners.locale': 'Idioma de comunicación del socio',
            'help.roles.overview': 'Roles para un equipo pequeño',
            'help.roles.presets' : 'Qué rol elegir',
            'help.roles.matrix'  : 'Matriz de permisos (Avanzado)',
            'help.users.invite'  : 'Invitar a un miembro del equipo',
            'help.security.nostrLogin': 'Iniciar sesión con Alby (Nostr)',
            'help.users.danger'  : 'Seguridad: Admin, CEO y desactivar',
            'help.users.privacy' : 'Privacidad, RR. HH. y solo lectura',
            'help.roles.demo'    : 'Practicar roles (Demo / formación)',
            'sales.desk'         : 'Escritorio de ventas',
            'scm.desk'           : 'Escritorio de compradores',
            'scm.shopfloor'      : 'Control de taller',
            'delivery.desk'      : 'Escritorio de entrega',
            'aftersales.desk'    : 'Escritorio posventa',
            'hr.desk'            : 'Escritorio de RR. HH.',
            'digital.desk'       : 'Escritorio digital',
            'asset.desk'         : 'Escritorio de activos',
            'strategy.desk'      : 'Escritorio de estrategia',
            'development.desk'   : 'Escritorio de desarrollo',
            'capability.hub'     : 'Hub de capacidades',
            'agents.hub'         : 'Hub de IA / agentes',
            'finance.treasury'   : 'Tesorería',
            'finance.invoices'   : 'Facturas',
    ].asImmutable()

    private static final Map FEATURE_BODIES_DE = [
            'finance.primaryRail': '''# Primärschiene & BTC-FX

Ihre **Primärschiene** ist die Geldeinheit Ihrer Bücher (im Setup gewählt):

| Schiene | Bedeutung |
|---------|-----------|
| **EUR** / **USD** | Funktionale Fiat-Währung — Rechnungen und Journals in dieser Währung |
| **BTC** | Bitcoin-Bücher (Beträge in Sats oder BTC-Anzeige); wählen Sie ein **Anzeige-Fiat** (EUR/USD) für Hinweise |

## Was Sie auf Formularen sehen

Neben Beträgen kann steakOS einen **kompakten FX-Hinweis** zeigen (z. B. Sats neben Euro). Hinweise kommen aus einem **serverseitigen** BTC-Kurs-Cache (CoinGecko → Kraken → Coinbase). Ein Badge **veraltet** bedeutet: letzter guter Kurs, weil Live-Quotes fehlgeschlagen sind.

**Hinweise sind nicht die Bücher.** Ausgestellte Belege behalten den Primärbetrag; Tooltips können sich mit dem Markt ändern.

## Ausgleich

Beim **Ausgleichen** einer Rechnung erfasst steakOS Zahlungszeitpunkte auf **Zahlung** / Journal / Treasury (inkl. Sats bei Bitcoin-Schienen). Dieser Ausgleichskurs geht in die Bücher — nicht der Live-Tooltip.

## Doppelte Treasury

Erwarten Sie eine **Fiat-Bank**- und eine **Bitcoin-Wallet**-Position unter Finance → Treasury (ein Kontenplan).

## Ehrliche Grenzen (Basics)

- Kein automatisches Wallet-Mark-to-Market (MTM) bei BTC-Kursänderung  
- Primärschiene nach dem Setup ändern ist Advanced (nicht in der Closed Beta)  
- Keine manuellen FX-Tabellen  

**Verwandt:** Rechnungen · Treasury · Live-Zahlungen (BTCPay) · Firmenprofil''',
            'sales.desk'    : '''# Innendienst

Operativer Verkauf:

1. **Kunde** anlegen
2. **Angebot** → senden → annehmen/ablehnen
3. **Auftrag** → bestätigen (kann Entwurfsrechnung erzeugen)

Stammdaten unter Vertrieb bleiben für Admin-Listen verfügbar.

''' + HOW_DESKS_DE,
            'scm.desk'      : '''# Einkaufs-Desk

**Lieferanten · Produkte · Services · Bestellungen**

Lieferant → Bestellung → senden → bestätigen → Wareneingang.

Shopfloor ist ein **separater** Desk (`/scm/shopfloor`). Dies ist die **UI-2.0-Vorlage** (`layout=single` + Modals).

''' + HOW_DESKS_DE,
            'scm.shopfloor' : '''# Shopfloor-Steuerung

**Fertigungsaufträge · Qualität / Lose · Bestand**

Geteiltes Layout: Detail erst nach Zeilenauswahl.

''' + HOW_DESKS_DE,
            'delivery.desk' : '''# Delivery-Desk

Services & Ressourcen → Serviceaufträge → planen → zuweisen → starten → abschließen.

''' + HOW_DESKS_DE,
            'aftersales.desk': '''# AfterSales-Desk

Anfragen, Garantien, Claims, RMAs. Claims können Finanz-Gutschrift-Entwürfe erzeugen.

''' + HOW_DESKS_DE,
            'hr.desk'       : '''# HR-Desk

Kandidaten → Einstellung · Personalakten · Zeit · Payroll · Ziele.

Geteiltes Layout: Detail erst nach Zeilenauswahl.

''' + HOW_DESKS_DE,
            'digital.desk'  : '''# Digital-Desk

Systeme, Datenassets, Solutions, IT-Changes, CIs, interne Tickets.

Geteiltes Layout: Detail erst nach Zeilenauswahl.

''' + HOW_DESKS_DE,
            'strategy.desk' : '''# Strategie-Desk

**Wettbewerber** und **Strategiethemen** — einspaltige Liste mit Modal Ansehen/Bearbeiten.

''' + HOW_DESKS_DE,
            'development.desk': '''# Development-Desk

**Development-Projekte** (NPD / Revisionen) — einspaltiger Desk mit Modal Ansehen/Bearbeiten.

''' + HOW_DESKS_DE,
            'capability.hub': '''# Capability-Hub

Projekte, Prozesse, Qualität, Changes, Wissen — Abschnitt-Tabs.

Geteiltes Layout: Detail erst nach Zeilenauswahl.

''' + HOW_DESKS_DE,
            'agents.hub'    : '''# KI- / Agenten-Hub

**Agents**, **Hives**, **HITL**, **Memory**. Closed Beta: nur Agent Basics.

Geteiltes Layout: Detail erst nach Zeilenauswahl.

''' + HOW_DESKS_DE,
            'finance.treasury': '''# Treasury

Positionen, Verbindlichkeiten, Budgets, Steuer, Payroll-Stub — Tabs auf diesem Hub.

''' + HOW_DESKS_DE,
            'finance.invoices': '''# Rechnungen

1. Entwurf anlegen  
2. **Ausstellen**  
3. **Ausgleichen** bei Zahlung  

Geteiltes Layout: Detail erst nach Zeilenauswahl.

''' + HOW_DESKS_DE,
            'app.desks'      : '''# Listen & Desks

Operator-Chrome für Listen und Desks (ADR-050 / PDR-034). Vorlagen: **Aufgaben** (`/domains/core/Task`) und **Einkauf** (`/scm/buyers`).

''' + HOW_DESKS_DE + '''

## Einspaltig vs. geteilt

| Muster | Wann | Detail |
|--------|------|--------|
| **Single** | Vertrieb, Einkauf, Delivery, AfterSales, Assets, Strategie, Development | Volle Liste; Ansehen/Bearbeiten in Modals |
| **Split** | Shopfloor, Digital, Finance, CRM, Pricing, HR, Capability, Agents | Detail nur nach Zeilenauswahl |

**Siehe auch:** Hilfeschublade an jedem Desk · Einkaufs-Desk · Aufgabenliste''',
            'app.dashboard' : '''# CEO-Dashboard

Cockpit für offene Arbeit und Prozessgesundheit.

- **Meine Arbeit** — offene Aufgaben und Fristen
- **Aktivitäten - JETZT** — 3×3-Board Dringlichkeit × Wichtigkeit (dasselbe Board unter **Meine Arbeit**)
- **APQC-Gruppen** — Sprung in Vertrieb, Finanzen, …
- **Systemstatus**

**Kurzbefehle:** **Ctrl+K** · **?** oder **Ctrl+/** Hilfeschublade

**Siehe auch:** Aktivitäten - JETZT · Einstellungen (Sprache & Region)''',
            'system.settings': '''# Einstellungen

**Sprache & Region** ist die erste Karte — sie steuert **Ihre Bildschirmsprache** (EN / DE / ES) und Datums-/Währungsformate für dieses Konto.

## Sprache & Region

1. **System → Einstellungen** öffnen
2. Locale wählen (z. B. Deutsch (Deutschland) `de_DE`)
3. Beschriftungen und Formate gelten für Ihren Benutzer

Das ist **nicht** die Sprache ausgehender PDFs an Kunden/Lieferanten. Dafür gilt die **Kommunikationssprache** am Kunden- bzw. Lieferantenstamm.

## Weitere Karten auf dieser Seite

- **Verwendete Währungen** — Kurzliste für Dropdowns (Firmenwährung bleibt aktiv)
- Verwendete Steuercodes (wenn Finance-Pack freigeschaltet)
- Demo- / Setup-Status und Admin-**Setup zurücksetzen**

**Integrationen** (Nostr, Bitcoin-Stufe, Hermes-Tokens) liegen unter **System → Integrationen**.

**Siehe auch:** Partnersprache (Kommunikation) · Verwendete Währungen''',
            'help.work.actionNow': '''# Aktivitäten - JETZT

**Meine Arbeit** (`/work`) ist das Eisenhower-Board **Aktivitäten - JETZT** (Dringlichkeit × Wichtigkeit). Auf dem CEO-Dashboard gibt es dasselbe als Mini-Board.

## Werkzeugleiste

- **Neue Aufgabe** — Aufgabe anlegen (Rückkehr hierher)
- **Aktualisieren** — Board neu laden
- **Agentische Aufgaben** (violetter Schalter) — menschliches offenes Board ↔ agentische AI:*-Warteschlange
- **Aufgabenliste** — vollständige Task-Domainliste (Suche, Sortierung, Überblick)

## Board lesen

- **Zeilen** = Dringlichkeit (hoch → niedrig)
- **Spalten** = Wichtigkeit (niedrig → hoch)
- Jede Zelle gruppiert gleiche Dringlichkeit × Wichtigkeit — Aufgabe öffnen zum Bearbeiten

## Menschliches Board (Schalter aus)

**Offene** menschliche Arbeit (`OPEN` / `IN_PROGRESS`, plus laufende AI:*-Status die noch als offen gelten). Erledigt / abgebrochen bleiben weg.

## Agentische Aufgaben (Schalter an)

Aufgaben mit agentischem AI:-Status (z. B. AI:waiting-go oder AI:done), die ein Mensch noch **nicht** als **DONE** bestätigt hat. Violette Chips markieren agentische Status. Schalter **aus** = zurück zum menschlichen Board.

Mauszeiger über den Schalter zeigt kurz, was an/aus bedeutet.

## Wohin sonst

- **Dashboard** — Mini-Board + Sprung zu Meine Arbeit
- Aufgabenformular / Modal — **Dringlichkeit** und **Wichtigkeit** setzen

## Ehrliche Grenzen

- Das Board erfindet keine Prioritäten — Sie setzen sie an der Aufgabe
- Listenfilter (überfällig, Zuweisung, …) liegen in der **Task**-Domainliste, nicht auf diesem Board

**Tipp:** Hilfeschublade mit **?** oder **Ctrl+/** von Meine Arbeit oder dem Dashboard.''',
            'help.partners.locale': '''# Partnersprache (Kommunikation)

Am **Kunden**- und **Lieferanten**-Stamm **Kommunikationssprache** setzen (z. B. Deutsch / Englisch).

## Was sie steuert

- **PDF-Beschriftungen** auf Angeboten, Aufträgen, Bestellungen und Rechnungen an diesen Partner
- **E-Mail**-Betreff und -Text an diesen Partner, wenn Messaging aktiviert ist (gleiche Sprache)
- Entwurfsvorschauen folgen der Live-Einstellung; nach Senden / Bestätigen / Ausstellen **friert** der Beleg die Sprache ein

## Vorgaben

Leer lassen → **Englisch (en_US)** für ausgehende Belege und E-Mail. Das ist getrennt von Ihrer Bildschirmsprache unter **Einstellungen**.

## Ehrliche Grenzen (v1)

- Katalogbeschreibungen sind noch nicht zweisprachig
- **Nostr-Partner-DMs** noch Residual (PL-TR4 Giftwrap) — E-Mail nutzt heute die Partnersprache''',
            'help.tax.ust': '''# Deutsche USt-Steuercodes

steakOS liefert ein **DE/AT-Steuercode-Paket**. Codes unter **System → Einstellungen → Verwendete Steuercodes** aktivieren, dann auf Angebots-/Auftrags-/Bestellpositionen wählen.

## Häufige DE-Codes

| Code | Satz | Verwendung |
|------|------|------------|
| **DE_USt_STD** | 19% | Regelbesteuerung |
| **DE_USt_RED** | 7% | Ermäßigt (Betreiber entscheidet die Eignung) |
| **DE_USt_0** | 0% | Explizit steuerfrei / Nullsatz |
| **DE_RC** | 0% + Reverse Charge | B2B Reverse Charge — Empfänger schuldet die USt |
| **DE_IG** | 0% + IG | Innergemeinschaftliche Lieferung (gültige USt-IdNr.) |
| **DE_EXPORT** | 0% | Ausfuhr außerhalb der EU |

Österreich folgt demselben Muster (`AT_USt_STD` 20%, `AT_USt_RED` 10%, …).

## Regime

- **Deutschland USt / Österreich USt** — Positionssteuer erforderlich; Firmenstandard wenn keine Auswahl
- **Kleinunternehmer / Keine** — **keine USt** auf Verkaufsbelegen

## Ehrliche Grenzen

- Betreiber verantwortet Sätze — Seed ist Startpunkt, keine Rechtsberatung
- Kein UStVA-/ELSTER-Filing
- US-Mehrastaaten-Tiefe ist nicht Teil dieses Launch-Moduls

**Siehe auch:** Reverse Charge & IG · Geschäfts-PDFs · Steuer-Tab im Finance-Hub''',

            'help.tax.reverseCharge': '''# Reverse Charge & innergemeinschaftliche Lieferung (IG)

Steuercodes **DE_RC** / **AT_RC** (Reverse Charge) oder **DE_IG** / **AT_IG** (IG) auf Verkaufspositionen wählen, wenn diese Regeln greifen.

## Was steakOS macht

- **Ausgangs-USt** der Position ist **0%** bei RC-/IG-Codes
- PDF und Belegnotizen ergänzen einen **Rechtshinweis** (EN + DE):
  - Reverse charge — VAT accounted for by the recipient / *Steuerschuldnerschaft des Leistungsempfängers*
  - Intra-community supply (0% VAT) / *Innergemeinschaftliche Lieferung*

## Checkliste für Betreiber

1. Gültige USt-IdNr. des Kunden prüfen, wenn erforderlich
2. **DE_RC** oder **DE_IG** (bzw. AT) auf der Position wählen — nicht den Standard 19%/20%
3. PDF herunterladen und den Hinweisblock prüfen

## Ehrliche Grenzen

- Keine VIES-Prüfung der EU-USt-IdNr. in v1
- Filing / Intrastat / OSS außerhalb dieses Moduls
- Falscher Code ist Bedienfehler — keine Autokorrektur

**Siehe auch:** Deutsche USt-Steuercodes · Geschäfts-PDFs''',

            'finance.postingRules': '''# Buchungsregeln

steakOS bucht ausgeglichene Journale bei Geldbewegungen — **Rechnung begleichen**, **Lieferant zahlen** und **Lohn erkennen / zahlen** — über ein **Firmen-Regelsatz** (keine hartcodierten Konten). Unter **Accrual** buchen auch **Rechnungsausgabe** und **Verbindlichkeit anlegen/freigeben**.

## Buchungsmodus

| Modus | Bedeutung |
|-------|-----------|
| **Cash** (Standard / Restaurant) | Bücher bei Zahlung |
| **Accrual** | Bei **Ausgabe**: Soll AR / Haben Erlös; bei **Begleichen**: Soll Cash / Haben AR. Bei **Verbindlichkeit offen**: Soll Aufwand / Haben AP; bei **Zahlen**: Soll AP / Haben Cash |

Modus im Setup-Schritt **Finanzbücher** oder hier wählen. Begleichen/Zahlen unter Accrual lösen AR/AP auf — kein doppelter Erlös/Aufwand.

## Wer darf was

| Aktion | Admin | CEO |
|--------|:-----:|:---:|
| Regeln / Modus / Änderungslog sehen | ● | ● |
| Regeln / Modus / Reseed ändern | ● | ○ |

Jedes Admin-**Speichern** oder **Reseed** hängt ein unveränderliches **Änderungslog** an und öffnet ein Digital-**IT-Ticket** (`CHANGE`, Namenspräfix `[Finance policy]`). Reseed löscht keine Journale.

## Wo

**Finance → Buchungsregeln** (`/finance/posting-rules`)

Fehlende oder ungültige Regeln **blockieren** settle/pay (Fehler `POSTING_RULES_*`) — kein stiller Fallback.

**Siehe auch:** Hauptbuch · Forderungen · Treasury · Lohn''',

            'help.roles.overview': '''# Rollen für ein kleines Team

steakOS ist für ein **kleines Unternehmen** (etwa 2–5 Personen). Jeder Person ein **Rollenprofil** geben (Office, Ops, Finance…). Rollen liefern **Vorgaben**, welche Desks sichtbar sind und was geändert werden darf.

**Admin** und **CEO** passen Vorgaben unter **System → Rollen** an und laden Personen unter **System → Benutzer** ein.

Kein IT-Abteilung nötig. Im Zweifel **Vorgaben behalten** und nur bei Bedarf weitere Desks öffnen.

## Schnellstart

1. **System → Benutzer** öffnen
2. Person mit Profil **Office** (Empfang) oder **Finance** (Buchhaltung) anlegen
3. Temporäres Passwort mitteilen; bald **Sicherheit → Authenticator** einrichten lassen
4. Linke Navigation prüfen: nur die benötigten Desks

## Wohin

- **Rollen** (`/roles`) — Vorgaben eines Profils ändern
- **Benutzer** (`/users`) — Profil einer Person zuweisen
- Hilfethemen: Rollenwahl · Berechtigungsmatrix · Einladen · Sicherheit

**Tipp:** Hilfeschublade mit **?** oder **Ctrl+/**.''',
            'help.roles.presets': '''# Welche Rolle wählen

Vorgaben sind **Startwerte**, keine ewigen Sperren. Bei Bedarf unter **System → Rollen** anpassen.

| Rolle | Typische Person | Sieht / macht | Vermeiden, wenn… |
|------|-----------------|---------------|------------------|
| **Admin** | Installer / IT-affiner Mitinhaber | Alle freigeschalteten Desks + Benutzer, Import, Kataloge, Agenten | Alltag (dann CEO) |
| **CEO** | Inhaber / GF | Alle Business-Desks + Benutzer / Import | Zweiten vollen Admin brauchen (nur Admin legt Admin an) |
| **Office** | Empfang / Büro | Vertrieb, Delivery, After-Sales (+ Capability leicht); **kein Geld ausgleichen** | Zahlungen buchen müssen |
| **Ops** | Küche / Produktion / Fläche | SCM, Delivery, Assets; Vertrieb nur **sehen** | Angebote end-to-end verantworten |
| **Finance** | Buchhaltung | Finance + Vertrieb **sehen**; Geld **ausgleichen** | Benutzer einladen (kein Users) |
| **User** | Allgemeiner Desk | Konservativ Vertrieb + Delivery | Office/Ops/Finance passt besser |
| **Read only** | Gast / Auditor / Trainee | Desks browsen; **Schreiben gesperrt** | Anlegen oder Bearbeiten nötig |

## Gastronomie-Beispiele

- Empfang / Telefon: **Office**
- Küchenleitung: **Ops**
- Buchhaltung (Teilzeit): **Finance**
- Inhaber: **CEO** (+ ein separates **Admin**-Konto)
- Trainee: **Read only**

**Regel:** eine Person → ein Profil. Im v1 lieber Profil wechseln als neue Rolle erfinden.''',
            'help.roles.matrix': '''# Berechtigungsmatrix (Erweitert)

Die Matrix ist die **Wahrheit**. Vorgaben füllen sie; Erweitert lässt Admin/CEO Zellen feinjustieren.

## Desks (Familien)

Je Desk (Vertrieb, Finance, SCM…):

| Verb | Bedeutung |
|------|-----------|
| **See** | In der Navigation; Listen öffnen und lesen |
| **Create** | Neue Datensätze (POST) |
| **Change** | Bearbeiten / Workflow (PUT/PATCH / starten / abschließen…) |
| **Delete** | Löschen / stornieren, wo erlaubt |
| **Work** | Kurz = Create **oder** Change (Einfach-Ansicht) |

Rechte schneiden sich mit den **freigeschalteten Familien** (Metall / Vorlage).

## Systemaktionen

Beispiele: **Benutzer / einladen**, **Import**, **Kataloge**, **Agenten**, **Setup**, **Geld ausgleichen**, 2FA anderer zurücksetzen.

Ausgleichen ist ein **Geld-Spezial** — Finance hat es standardmäßig; Office nicht.

## Sicherheit

- **Deny gewinnt** über Allow
- Letzten **Admin** schützen
- Vor **Auf alle Benutzer mit diesem Profil anwenden** bestätigen
- Menschen bekommen kein `ROLE_AI` über Benutzer

## API

Schreibende `/api/**`-Aufrufe prüfen diese Matrix (`MATRIX_DESK_FORBIDDEN`, `MATRIX_SYSTEM_FORBIDDEN`).''',
            'help.users.invite': '''# Teammitglied einladen

Pfad: **System → Benutzer**

1. **Rollenprofil** wählen (Office / Ops / Finance — nicht Admin)
2. Benutzername + **temporäres Passwort** (12+ Zeichen: klein, groß, Ziffer, Sonderzeichen)
3. Anlegen und Zugangsdaten außerhalb öffentlicher Tickets weitergeben
4. Mit Passwort anmelden und unter **Sicherheit** den **Authenticator** einrichten
5. **Optional — Alby / Nostr:** unter **Sicherheit → Browser-Erweiterung verknüpfen** (Alby entsperrt). Spalte **Erweiterung** wird **Ja**. Danach Login-Seite: **Mit Browsererweiterung anmelden**.

steakOS legt bei der Einladung **keine** Nostr-Identität an. Die Person verknüpft den eigenen Alby-Schlüssel selbst. Niemals `nsec` einfügen.

Vollständig: Hilfe → **Mit Alby anmelden (Nostr)**.

## Danach

- Profil jederzeit in der Benutzertabelle ändern
- Lieber **Deaktivieren** als löschen — Historie bleibt
- **Authenticator löschen** nur bei verlorenem Gerät

## Wer darf einladen

**Admin** und **CEO**. CEO kann das Profil **Admin** nicht vergeben.''',
            'help.security.nostrLogin': '''# Mit Alby anmelden (Nostr)

**NIP-07**-Browsererweiterung (z. B. **Alby**): kurze Freigabe statt jedes Mal Passwort. Der **geheime Schlüssel bleibt in der Erweiterung** — steakOS fragt nie nach `nsec`.

## Voraussetzungen

1. steakOS-**Benutzerkonto** existiert (Einladung unter **System → Benutzer** oder Setup-Admin).
2. Einmal mit **Benutzername + Passwort** anmelden können.
3. **Alby** installiert und in diesem Browser **entsperrt**.
4. Unter **Sicherheit** nach **Browser-Erweiterung verknüpfen** steht **Verknüpft: Ja**.

Ohne Schritt 4 meldet die Login-Seite, dass der Schlüssel keinem Benutzer zugeordnet ist.

## Checkliste (Person mit Alby)

1. Alby installieren und Nostr-Identität anlegen/importieren.
2. Mit Passwort bei steakOS anmelden.
3. **System → Sicherheit** öffnen.
4. **Browser-Erweiterung verknüpfen** → in Alby freigeben.
5. **Verknüpft: Ja** prüfen.
6. Abmelden → Login-Seite **Mit Browsererweiterung anmelden**.

## Admin

1. Benutzer anlegen (Passwort + Profil).
2. Person bittet um Passwort-Login, dann Alby verknüpfen (siehe oben).
3. Spalte **Erweiterung** = Ja.

Firmen-Nostr-Level (Wizard / Integrationen) steuert Relay/Audit — das ersetzt **nicht** die persönliche Verknüpfung unter Sicherheit.

## Sicherheit

- Niemals `nsec` / Seed einfügen.
- Eine Alby-Identität ↔ ein steakOS-Benutzer.
- Passwort-Login bleibt möglich.''',
            'help.users.danger': '''# Sicherheit: Admin, CEO & Deaktivieren

## Tun

- **Admin** und **CEO** als **getrennte Logins** halten
- Beim Ausscheiden lieber **Deaktivieren** als löschen
- **Authenticator löschen** nur bei Geräteverlust
- Vor Rollenänderung für **alle** Nutzer dieses Profils bestätigen

## Nicht tun

- Admin-Passwort teilen
- Jeden Inhaber „sicherheitshalber“ zum Admin machen
- Letzten Admin deaktivieren (Produkt blockiert das)
- Menschen Agenten-Zugang (`ROLE_AI`) über Benutzer geben

## Letzter Admin / CEO

- Letzten **Admin** kann man nicht deaktivieren oder degradieren
- Mindestens ein Inhaber-Konto behalten

## Geld

Nur Profile mit **Geld ausgleichen** (Finance, Admin, CEO standardmäßig) dürfen Rechnungen ausgleichen. Office darf Kunden anlegen; Ausgleichen ist gesperrt.''',
            'help.users.privacy': '''# Datenschutz, HR & Nur lesen

- **HR**-Desks und Personenakten sind sensibel. **See** auf Human Capital nur bei Bedarf.
- **Read only** für Gäste, Auditoren und Trainees, die **zuschauen**, nicht ändern.
- **Demo-/Trainingsinstanzen** dürfen Read only oder Office frei nutzen — destruktive Admin-Aktionen nicht auf Live-Büchern üben.
- Inhalte an Datensätzen folgen dem Desk-Zugriff des Host-Datensatzes.

Im Zweifel: **Read only** zum Beobachten, **Office** für den Empfang, Admin nur für Systemarbeit.''',
            'help.roles.demo': '''# Rollen üben (Demo / Training)

Auf einem **Demo-Restaurant** oder Training-Mandanten:

| Ziel | Profil |
|------|--------|
| Browsen ohne Daten zu ruinieren | **Read only** (oder geseedeter `demo`-User) |
| Aufträge / Angebote | **Office** |
| Lager / Küche | **Ops** |
| Rechnungen & Ausgleichen | **Finance** |
| Einladen / Vorgaben ändern | **CEO** (oder Admin für Kataloge / Import) |

**Tipps**

- Zuerst Demo üben; dieselben Profile danach auf der Live-Firma
- Wegwerf-Benutzer danach **deaktivieren**
- Hilfeschublade (**?**) auf **Rollen** und **Benutzer** zeigt diese Artikel im Kontext''',
            'content.rail'  : '''# Object Companion (Content-Leiste)

Am Datensatz:

- **Link**
- **Datei**
- **Inhalt** (Notiz, Hilfe, Risiko, …)''',
            'asset.desk'    : '''# Asset-Arbeitsplatz

Physische Anlagen, Fahrzeuge und Werkzeuge (AssetBasics-Register).

- **Register** — Name, Kennzeichen, Ort, Verantwortlicher, Status
- **IT-verwaltet** — dauerhafter Link zu einem Digital-ConfigItem (Hostname/IP nur am Digital-Desk)
- **Finanzielle Abschreibung** — wenn aktiv, können Finanz-Agenten das Asset aufgreifen und Abschreibungswerte in einer Finanzperiode buchen
- **Asset erfordert Wartung** — wenn aktiv, ist physische Wartung vorgesehen; Hersteller-Wartungsplan (XLSX oder PDF) am Datensatz über Object Companion anhängen. Später können Agenten Shopfloor- oder Wartungsaktivitäten anlegen, um die Nutzungsdauer zu verlängern

Die Flags sind Basics-Absichtsmarker; automatische Abschreibungsbuchung und Wartungsaufträge vertiefen später.

''' + HOW_DESKS_DE,
    ].asImmutable()

    private static final Map FEATURE_BODIES_ES = [
            'finance.primaryRail': '''# Carril primario y BTC-FX

Su **carril primario** es la unidad monetaria de sus libros (elegida en el setup):

| Carril | Significado |
|--------|-------------|
| **EUR** / **USD** | Moneda funcional fiat — facturas y diarios en esta moneda |
| **BTC** | Libros en Bitcoin (importes en sats o visualización BTC); elija un **fiat de visualización** (EUR/USD) para las pistas |

## Qué ve en los formularios

Junto a los importes, steakOS puede mostrar una **pista FX compacta** (p. ej. sats junto a euros). Las pistas vienen de una caché de tipo BTC **en el servidor** (CoinGecko → Kraken → Coinbase). Un distintivo **obsoleto** significa: último tipo bueno, porque fallaron las cotizaciones en vivo.

**Las pistas no son los libros.** Los documentos emitidos conservan el importe primario; las tooltips pueden cambiar con el mercado.

## Liquidación

Al **liquidar** una factura, steakOS registra el momento del pago en **Payment** / diario / Treasury (incl. sats en carriles Bitcoin). Ese tipo de liquidación entra en los libros — no el tooltip en vivo.

## Tesorería dual

Espere una posición de **banco fiat** y otra de **cartera Bitcoin** bajo Finance → Treasury (un solo plan de cuentas).

## Límites honestos (Basics)

- Sin mark-to-market (MTM) automático de cartera al cambiar el tipo BTC  
- Cambiar el carril primario tras el setup es Advanced (no en la closed beta)  
- Sin tablas FX manuales  

**Relacionado:** facturas · Treasury · pagos en vivo (BTCPay) · perfil de empresa''',
            'sales.desk'    : '''# Escritorio de ventas

Venta operativa:

1. Crear **cliente**
2. **Oferta** → enviar → aceptar/rechazar
3. **Pedido** → confirmar (puede generar factura borrador)

Los datos maestros de ventas siguen disponibles para listas Admin.

''' + HOW_DESKS_ES,
            'scm.desk'      : '''# Escritorio de compradores

**Proveedores · productos · servicios · pedidos de compra**

Proveedor → OC → enviar → confirmar → recibir.

El taller es un escritorio **aparte** (`/scm/shopfloor`). Lista con diálogos Ver/Editar (sin columna de detalle vacía).

''' + HOW_DESKS_ES,
            'scm.shopfloor' : '''# Control de taller

**Órdenes de producción · calidad / lotes · inventario**

Layout **split**: el detalle solo al seleccionar una fila.

''' + HOW_DESKS_ES,
            'delivery.desk' : '''# Escritorio de entrega

Servicios y recursos → órdenes de servicio → programar → asignar → iniciar → completar.

''' + HOW_DESKS_ES,
            'aftersales.desk': '''# Escritorio posventa

Consultas, garantías, reclamaciones, RMAs. Las reclamaciones pueden crear borradores de abono en Finance.

''' + HOW_DESKS_ES,
            'hr.desk'       : '''# Escritorio de RR. HH.

Candidatos → contratación · expedientes · tiempo · nómina · objetivos.

Layout **split**: el detalle solo al seleccionar una fila.

''' + HOW_DESKS_ES,
            'digital.desk'  : '''# Escritorio digital

Sistemas, activos de datos, soluciones, cambios de TI, CIs, tickets internos.

Layout **split**: el detalle solo al seleccionar una fila.

''' + HOW_DESKS_ES,
            'strategy.desk' : '''# Escritorio de estrategia

**Competidores** y **temas de estrategia** — lista de una columna con modal Ver/Editar.

''' + HOW_DESKS_ES,
            'development.desk': '''# Escritorio de desarrollo

**Proyectos de desarrollo** (NPD / revisiones) — escritorio de una columna con modal Ver/Editar.

''' + HOW_DESKS_ES,
            'capability.hub': '''# Hub de capacidades

Proyectos, procesos, calidad, cambios, conocimiento — pestañas de sección.

Layout **split**: el detalle solo al seleccionar una fila.

''' + HOW_DESKS_ES,
            'agents.hub'    : '''# Hub de IA / agentes

**Agents**, **Hives**, **HITL**, **Memory**. Closed beta: solo Agent Basics.

Layout **split**: el detalle solo al seleccionar una fila.

''' + HOW_DESKS_ES,
            'finance.treasury': '''# Tesorería

Posiciones, pagos, presupuestos, impuestos, nómina stub — pestañas de este hub.

''' + HOW_DESKS_ES,
            'finance.invoices': '''# Facturas

1. Crear borrador  
2. **Emitir**  
3. **Liquidar** al pagar  

Layout **split**: el detalle solo al seleccionar una fila.

''' + HOW_DESKS_ES,
            'app.desks'      : '''# Listas y escritorios

Chrome de operador para listas y escritorios (ADR-050 / PDR-034). Plantillas: **Tareas** (`/domains/core/Task`) y **Compradores** (`/scm/buyers`).

''' + HOW_DESKS_ES + '''

## Una columna vs. split

| Patrón | Cuándo | Detalle |
|--------|--------|---------|
| **Single** | Ventas, Compradores, Entrega, Posventa, Activos, Estrategia, Desarrollo | Lista completa; Ver/Editar en modales |
| **Split** | Taller, Digital, Finance, CRM, Pricing, RR. HH., Capability, Agents | Detalle solo tras seleccionar una fila |

**Ver también:** cajón de ayuda en cada escritorio · Compradores · lista de tareas''',
            'app.dashboard' : '''# Panel CEO

Cabina de mando para trabajo abierto y salud de procesos.

- **Mi trabajo** — tareas abiertas y plazos
- **Actividades - AHORA** — tablero 3×3 urgencia × importancia (el mismo bajo **Mi trabajo**)
- **Grupos APQC** — salto a ventas, finanzas, …
- **Estado del sistema**

**Atajos:** **Ctrl+K** · **?** o **Ctrl+/** cajón de ayuda

**Ver también:** Actividades - AHORA · Configuración (idioma y región)''',
            'system.settings': '''# Configuración

**Idioma y región** es la primera tarjeta — controla **el idioma de su pantalla** (EN / DE / ES) y los formatos de fecha/dinero de esta cuenta.

## Idioma y región

1. Abra **System → Configuración**
2. Elija una configuración regional (p. ej. Español (España) `es_ES`)
3. Etiquetas y formatos se aplican a su usuario

Esto **no** es el idioma de los PDF salientes a clientes/proveedores. Eso es el **Idioma de comunicación** en cada maestro Cliente / Proveedor.

## Otras tarjetas en esta página

- **Monedas que usamos** — lista corta para desplegables (la moneda de la empresa permanece activa)
- Códigos fiscales en uso (cuando el pack Finance está habilitado)
- Estado demo / setup y **restablecer setup** de Admin

**Integraciones** (Nostr, nivel Bitcoin, tokens Hermes) están en **System → Integraciones**.

**Ver también:** Idioma de comunicación del socio · Monedas que usamos''',
            'help.work.actionNow': '''# Actividades - AHORA

**Mi trabajo** (`/work`) es el tablero Eisenhower **Actividades - AHORA** (urgencia × importancia). En el panel CEO aparece el mismo mini tablero.

## Barra de herramientas

- **Nueva tarea** — crear una tarea (vuelve aquí)
- **Actualizar** — recargar el tablero
- **Tareas agénticas** (interruptor violeta) — tablero humano abierto ↔ cola AI:*
- **Lista de tareas** — lista de dominio Task completa (búsqueda, orden, exploración)

## Cómo leer el tablero

- **Filas** = urgencia (alta → baja)
- **Columnas** = importancia (baja → alta)
- Cada celda agrupa la misma urgencia × importancia — abra una tarea para editarla

## Tablero humano (interruptor apagado)

Trabajo humano **abierto** (`OPEN` / `IN_PROGRESS`, más AI:* en curso que aún cuenta como abierto). Hechas / canceladas no salen.

## Tareas agénticas (interruptor encendido)

Tareas con estado agéntico AI: (por ejemplo AI:waiting-go o AI:done) que un humano **aún no** ha confirmado como **DONE**. Chips violetas marcan estados agénticos. Apague el interruptor para volver al tablero humano.

Pase el ratón por el interruptor para un recordatorio breve de encendido / apagado.

## Dónde más

- **Dashboard** — mini tablero + salto a Mi trabajo
- Formulario / modal de tarea — fijar **Urgencia** e **Importancia**

## Límites honestos

- El tablero no inventa prioridades — usted las fija en cada tarea
- Filtros de lista (atrasadas, asignado, …) viven en la lista de dominio **Task**, no en este tablero

**Consejo:** abra este cajón con **?** o **Ctrl+/** desde Mi trabajo o el panel.''',
            'help.partners.locale': '''# Idioma de comunicación del socio

En los maestros **Cliente** y **Proveedor**, fije el **Idioma de comunicación** (p. ej. alemán / inglés / español).

## Qué controla

- **Etiquetas PDF** en ofertas, pedidos, órdenes de compra y facturas a ese socio
- **Correo** asunto y cuerpo a ese socio cuando el mensajería está activado (mismo idioma)
- Las vistas previas de borrador siguen el ajuste en vivo; tras enviar / confirmar / emitir el documento **congela** su idioma

## Valores por defecto

Déjelo en blanco para usar **inglés (en_US)** en documentos y correo salientes. Es independiente del idioma de pantalla en **Configuración**.

## Límites honestos (v1)

- Las descripciones de catálogo aún no son bilingües
- Los **DM Nostr al socio** siguen residuales (PL-TR4 giftwrap) — el correo ya usa el idioma del socio''',
            'help.roles.overview': '''# Roles para un equipo pequeño

steakOS está pensado para una **empresa pequeña** (unas 2–5 personas). Asigne a cada persona un **perfil de rol** (Office, Ops, Finance…). Los roles aportan **valores por defecto** sobre qué escritorios se ven y qué se puede cambiar.

**Admin** y **CEO** ajustan los valores por defecto en **System → Roles** e invitan personas en **System → Users**.

No hace falta un departamento de TI. En caso de duda, **mantenga los valores por defecto** y abra más escritorios solo si hace falta.

## Inicio rápido

1. Abra **System → Users**
2. Cree la persona con perfil **Office** (recepción) o **Finance** (contabilidad)
3. Comunique la contraseña temporal; haga que configure pronto **Security → Authenticator**
4. Revise la navegación izquierda: solo los escritorios necesarios

## Dónde

- **Roles** (`/roles`) — cambiar los valores por defecto de un perfil
- **Users** (`/users`) — asignar un perfil a una persona
- Temas de ayuda: elección de rol · matriz de permisos · invitar · seguridad

**Consejo:** cajón de ayuda con **?** o **Ctrl+/**.''',
            'help.roles.presets': '''# Qué rol elegir

Los valores por defecto son **puntos de partida**, no bloqueos eternos. Ajústelos en **System → Roles** si hace falta.

| Rol | Persona típica | Ve / hace | Evite si… |
|-----|----------------|-----------|-----------|
| **Admin** | Instalador / socio con afinidad TI | Todos los escritorios habilitados + Users, Import, catálogos, agentes | Uso diario (entonces CEO) |
| **CEO** | Propietario / dirección | Todos los escritorios de negocio + Users / Import | Necesita un segundo Admin completo (solo Admin crea Admin) |
| **Office** | Recepción / oficina | Ventas, Delivery, After-Sales (+ Capability ligero); **no liquida dinero** | Debe registrar pagos |
| **Ops** | Cocina / producción / sala | SCM, Delivery, Assets; ventas solo **ver** | Debe responsabilizarse de ofertas de punta a punta |
| **Finance** | Contabilidad | Finance + ventas **ver**; dinero **liquidar** | Invitar usuarios (sin Users) |
| **User** | Escritorio general | Conservador: ventas + Delivery | Office/Ops/Finance encaja mejor |
| **Read only** | Invitado / auditor / aprendiz | Explorar escritorios; **escritura bloqueada** | Debe crear o editar |

## Ejemplos de hostelería

- Recepción / teléfono: **Office**
- Jefe de cocina: **Ops**
- Contabilidad (parcial): **Finance**
- Propietario: **CEO** (+ una cuenta **Admin** separada)
- Aprendiz: **Read only**

**Regla:** una persona → un perfil. En v1 prefiera cambiar de perfil antes que inventar un rol nuevo.''',
            'help.roles.matrix': '''# Matriz de permisos (Avanzado)

La matriz es la **verdad**. Los valores por defecto la rellenan; Avanzado permite a Admin/CEO afinar celdas.

## Escritorios (familias)

Por cada escritorio (ventas, Finance, SCM…):

| Verbo | Significado |
|-------|-------------|
| **See** | En la navegación; abrir y leer listas |
| **Create** | Nuevos registros (POST) |
| **Change** | Editar / flujo de trabajo (PUT/PATCH / iniciar / completar…) |
| **Delete** | Borrar / anular, donde esté permitido |
| **Work** | Atajo = Create **o** Change (vista simple) |

Los derechos se cruzan con las **familias habilitadas** (metal / plantilla).

## Acciones de sistema

Ejemplos: **Users / invitar**, **Import**, **catálogos**, **agentes**, **Setup**, **liquidar dinero**, restablecer 2FA de otros.

Liquidar es un **especial de dinero** — Finance lo tiene por defecto; Office no.

## Seguridad

- **Deny gana** sobre Allow
- Proteja al último **Admin**
- Confirme antes de **aplicar a todos los usuarios con este perfil**
- Las personas no reciben `ROLE_AI` vía Users

## API

Las llamadas de escritura a `/api/**` comprueban esta matriz (`MATRIX_DESK_FORBIDDEN`, `MATRIX_SYSTEM_FORBIDDEN`).''',
            'help.users.invite': '''# Invitar a un miembro del equipo

Ruta: **System → Users**

1. Elija el **perfil de rol** (Office / Ops / Finance — no Admin)
2. Nombre de usuario + **contraseña temporal** (12+ caracteres: minúscula, mayúscula, dígito, carácter especial)
3. Cree y entregue las credenciales fuera de tickets públicos
4. Inicie sesión con esa contraseña y configure el **Authenticator** en **Security**
5. **Opcional — Alby / Nostr:** **Security → Vincular extensión del navegador** (Alby desbloqueada). La columna **Extensión** pasa a **Sí**. Después, en el login: **Entrar con extensión del navegador**.

steakOS **no** crea una identidad Nostr al invitar. La persona vincula su propia clave Alby. Nunca pegue `nsec`.

Completo: Ayuda → **Iniciar sesión con Alby (Nostr)**.

## Después

- Cambie el perfil en cualquier momento en la tabla de usuarios
- Prefiera **desactivar** a borrar — el historial se conserva
- **Borrar Authenticator** solo si se perdió el dispositivo

## Quién puede invitar

**Admin** y **CEO**. El CEO no puede asignar el perfil **Admin**.''',
            'help.security.nostrLogin': '''# Iniciar sesión con Alby (Nostr)

Extensión **NIP-07** (p. ej. **Alby**): aprobar una solicitud breve en lugar de escribir la contraseña cada vez. La **clave secreta permanece en la extensión** — steakOS nunca pide `nsec`.

## Requisitos

1. Ya existe una **cuenta** de steakOS (invitación en **System → Users** o Admin del setup).
2. Puede iniciar sesión una vez con **usuario + contraseña**.
3. **Alby** instalada y **desbloqueada** en este navegador.
4. En **Security**, tras **Vincular extensión**, **Vinculado: Sí**.

Sin el paso 4, el login indica que la clave no está vinculada a un usuario.

## Lista (persona con Alby)

1. Instale Alby y cree/importe una identidad Nostr.
2. Entre en steakOS con contraseña.
3. Abra **System → Security**.
4. **Vincular extensión del navegador** → apruebe en Alby.
5. Confirme **Vinculado: Sí**.
6. Cierre sesión → **Entrar con extensión del navegador**.

## Admin

1. Cree el usuario (contraseña + perfil).
2. La persona inicia con contraseña y vincula Alby.
3. Columna **Extensión** = Sí.

El nivel Nostr de la empresa (asistente / Integraciones) controla el relay/auditoría — **no** sustituye la vinculación personal en Security.

## Seguridad

- Nunca pegue `nsec` / semilla.
- Una identidad Alby ↔ un usuario steakOS.
- El login con contraseña sigue disponible.''',
            'help.users.danger': '''# Seguridad: Admin, CEO y desactivar

## Haga

- Mantenga **Admin** y **CEO** como **inicios de sesión separados**
- Al salir, prefiera **desactivar** a borrar
- **Borrar Authenticator** solo si se perdió el dispositivo
- Confirme antes de cambiar el rol para **todos** los usuarios de ese perfil

## No haga

- Compartir la contraseña de Admin
- Convertir a cada propietario en Admin «por si acaso»
- Desactivar al último Admin (el producto lo bloquea)
- Dar acceso de agente (`ROLE_AI`) a personas vía Users

## Último Admin / CEO

- No se puede desactivar ni degradar al último **Admin**
- Conserve al menos una cuenta de propietario

## Dinero

Solo los perfiles con **liquidar dinero** (Finance, Admin, CEO por defecto) pueden liquidar facturas. Office puede crear clientes; liquidar está bloqueado.''',
            'help.users.privacy': '''# Privacidad, RR. HH. y solo lectura

- Los escritorios de **HR** y los expedientes de personas son sensibles. **See** en Human Capital solo si hace falta.
- **Read only** para invitados, auditores y aprendices que **observan**, no cambian.
- Las instancias **Demo / formación** pueden usar Read only u Office con libertad — no practique acciones Admin destructivas sobre libros en vivo.
- El contenido de los registros sigue el acceso de escritorio del registro anfitrión.

En caso de duda: **Read only** para observar, **Office** para recepción, Admin solo para trabajo de sistema.''',
            'help.roles.demo': '''# Practicar roles (Demo / formación)

En un **restaurante Demo** o un inquilino de formación:

| Objetivo | Perfil |
|----------|--------|
| Explorar sin arruinar datos | **Read only** (o el usuario `demo` sembrado) |
| Pedidos / ofertas | **Office** |
| Almacén / cocina | **Ops** |
| Facturas y liquidar | **Finance** |
| Invitar / cambiar valores por defecto | **CEO** (o Admin para catálogos / Import) |

**Consejos**

- Practique primero en Demo; luego los mismos perfiles en la empresa en vivo
- **Desactive** después los usuarios desechables
- El cajón de ayuda (**?**) en **Roles** y **Users** muestra estos artículos en contexto''',
            'content.rail'  : '''# Object Companion (barra de contenido)

En el registro:

- **Enlace**
- **Archivo**
- **Contenido** (nota, ayuda, riesgo, …)''',
            'asset.desk'    : '''# Escritorio de activos

Planta física, vehículos y herramientas (registro AssetBasics).

- **Registro** — nombre, etiqueta, ubicación, responsable, estado
- **Gestionado por TI** — enlace permanente a un ConfigItem Digital (edite hostname/IP en el escritorio Digital)
- **Depreciación financiera** — si está marcado, los agentes de Finanzas pueden tomar el activo y contabilizar depreciación en un periodo financiero
- **El activo requiere mantenimiento** — si está marcado, se espera mantenimiento físico; adjunte el plan del fabricante (XLSX o PDF) en el registro con Object Companion. Más adelante los agentes podrán crear actividades de taller o mantenimiento para alargar la vida del activo

Los flags son marcadores de intención Basics; la contabilización automática de depreciación y las órdenes de mantenimiento se profundizan más adelante.

''' + HOW_DESKS_ES,
    ].asImmutable()

    private static String humanize(String resource) {
        humanizeResource(resource, 'en')
    }

    private static String humanizeResource(String resource, String lang) {
        if (!resource) {
            if (lang == 'de') return 'Datensatz'
            if (lang == 'es') return 'Registro'
            return 'Record'
        }
        if (lang == 'de') {
            Map deNames = [
                    Customer: 'Kunde', CustomerQuote: 'Kundenangebot', QuoteLine: 'Angebotsposition',
                    SalesOrder: 'Verkaufsauftrag', OrderLine: 'Auftragsposition',
                    Task: 'Aufgabe', Vendor: 'Lieferant', Account: 'Konto',
            ]
            if (deNames[resource]) return deNames[resource] as String
        }
        if (lang == 'es') {
            Map esNames = [
                    Customer: 'Cliente', CustomerQuote: 'Oferta de cliente', QuoteLine: 'Línea de oferta',
                    SalesOrder: 'Pedido de venta', OrderLine: 'Línea de pedido',
                    Task: 'Tarea', Vendor: 'Proveedor', Account: 'Cuenta',
            ]
            if (esNames[resource]) return esNames[resource] as String
        }
        String s = resource.replaceAll(/([a-z])([A-Z])/, '$1 $2')
        s = s.replaceAll(/([A-Z]+)([A-Z][a-z])/, '$1 $2')
        return s
    }

    private static String modeLabel(String mode, String lang = 'en') {
        if (lang == 'de') {
            switch (mode) {
                case 'create': return 'Anlegen'
                case 'edit': return 'Bearbeiten'
                default: return 'Liste'
            }
        }
        if (lang == 'es') {
            switch (mode) {
                case 'create': return 'Crear'
                case 'edit': return 'Editar'
                default: return 'Lista'
            }
        }
        switch (mode) {
            case 'create': return 'Create'
            case 'edit': return 'Edit'
            default: return 'List'
        }
    }
}
