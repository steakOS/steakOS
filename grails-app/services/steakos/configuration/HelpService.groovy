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
            grrc        : [label: 'Governance', desk: '/grrc', blurb: 'Risks and compliance items'],
            stakeholder : [label: 'Stakeholder', desk: '/stakeholder', blurb: 'External parties and communications'],
            generic     : [label: 'Content catalog', desk: '/help', blurb: 'System content types'],
            configuration: [label: 'Configuration', desk: '/settings', blurb: 'Company and user settings'],
            helper      : [label: 'Reference data', desk: '/system/catalogs', blurb: 'Countries, currencies, statuses'],
    ].asImmutable()


    /** Optional rich copy for high-traffic master data (key = package.Resource) */
    private static final Map DOMAIN_BLURBS = [
            'core.Task': '''# Task

## Process overview
No single SYSTEM processCode — tasks are the shared work-item type for personal, process-linked, and agent-assigned work. The Action - NOW board (`/work`) is the human prioritisation queue.

## Actions
- Create/edit on domain list or **New task** from Action - NOW
- Change **status** rather than deleting history
- Agents complete with **AI:done**; human sets **DONE** after review

## Fields
name, description; status (humans: OPEN · IN_PROGRESS · DONE · CANCELLED; agents: AI:* only); urgency/importance 1–3; deadlineAt; assigneeHumanNpub and/or assigneeAgentId; content rail.

## Special situations
Agents must not finish with human DONE. Empty Action - NOW means no open work in the current filter.

## Related
- Action - NOW · CEO dashboard
- AI / Agentic hub when agent-assigned
''',
            'capability.Process': '''# Process

## Process overview
Catalogue of how work flows (APQC 13.1 kinds). SYSTEM seeds are read-only product paths (PO, Quote→Order, Invoice settle, Service order, Inquiry, Hire, Project, HITL, …).

## Actions
- Browse SYSTEM/HUMAN/AGENTIC on Capability → Processes
- Open a process for Mermaid + steps; use desk **Process** pill for the wired processCode
- Admin: Reseed SYSTEM processes from Settings or Capability

## Fields
kind (SYSTEM · HUMAN · AGENTIC · OTHER); status DRAFT · ACTIVE · RETIRED; code; step tips.

## Special situations
SYSTEM entries are not editable. Tips marked planned/stub are honest residuals.

## Related
- Process charts · Capability hub (CAP_PROJECT)
''',

            'capability.Standard': '''# Standard

## Process overview
Metric / KPI master data for the Capability **Standards** desk (`/capability/standards`). Prefer the desk for create and activate/deactivate.

## Actions
- Create/edit on Capability → Standards
- Toggle ACTIVE / INACTIVE on the detail pane

## Fields
name, description, metricCode, targetValue, unit, standardStatus.

## Special situations
Create from domain list redirects to the Standards desk.

## Related
- Capability Standards desk · Quality · CAP_PROJECT
''',
            'sales.Customer': '''# Customer

## Process overview
Master data for **SALES_QUOTE_TO_ORDER**. Create here or via CSV, then quote/order on the Sales desk.

## Actions
- Create/edit customer on this domain or Sales desk
- Use on quotes/orders; confirm order may create draft invoice (**FIN_INVOICE_SETTLE**)

## Fields
customerKind PERSON/ORGANIZATION; email/phone/contactName; taxId; currencyCode; communication language; active.

## Special situations
Missing customer blocks quoting. Soft currency warnings do not block save.

## Related
- Sales desk · Invoices · CSV import
''',
            'sales.CustomerQuote': '''# Customer quote

## Process overview
Part of **SALES_QUOTE_TO_ORDER** — proposed pricing before an order. Prefer the Sales desk for send/accept/reject.

## Actions
- Create/send/accept/reject on Sales desk
- Browse/edit lines under QuoteLine when needed

## Fields
Customer, document currency, lines (product/service, qty, price, tax).

## Special situations
Accepted quotes lock edit on the desk. Admin domain view is for browse/repair.

## Related
- Sales desk · Sales orders · QuoteLine
''',
            'sales.SalesOrder': '''# Sales order

## Process overview
Confirmed commercial commitment in **SALES_QUOTE_TO_ORDER**. Confirm may post a finance draft toward **FIN_INVOICE_SETTLE**.

## Actions
- Prefer Sales desk: accept quote → order → confirm
- Line detail via OrderLine

## Fields
Customer, currency, lines; status through confirm.

## Special situations
Product vs service flags matter for Delivery and SCM hand-offs.

## Related
- Sales desk · Invoices · Delivery desk
''',
            'sales.QuoteLine': '''# Quote line

## Process overview
Line items on a CustomerQuote within **SALES_QUOTE_TO_ORDER**.

## Actions
- Edit via Sales desk quote screen (preferred)

## Fields
Product/service ref, qty, price, tax code.

## Special situations
Raw master-data edits are admin-only convenience.

## Related
- Customer quote · Sales desk
''',
            'sales.OrderLine': '''# Order line

## Process overview
Line items on a SalesOrder within **SALES_QUOTE_TO_ORDER**.

## Actions
- Edit via Sales desk order flow when unlocked

## Fields
Product/service flags, qty, price — flags drive Delivery/SCM hand-offs.

## Special situations
Locked after confirm as the desk rules dictate.

## Related
- Sales order · Delivery desk · Buyers/Shop floor
''',
            'procurement.Vendor': '''# Vendor

## Process overview
Supplier master for **SCM_PO_FULFILL**. Create here or CSV, then use on the Buyers desk.

## Actions
- Create/import vendor; open on Buyers for POs

## Fields
Contact, taxId, currency, communication language, active.

## Special situations
No vendor blocks PO create.

## Related
- Buyers desk · CSV import
''',
            'procurement.PurchaseOrder': '''# Purchase order

## Process overview
**SCM_PO_FULFILL**: create → send → confirm → receive (stock / payable toward **FIN_PAYABLE**).

## Actions
- Prefer Buyers desk; process steps in view-modal footer
- Edit/Delete only while **DRAFT**

## Fields
Vendor, lines (catalogue, qty, price), status.

## Special situations
Issued/confirmed POs are View-only on the desk.

## Related
- Buyers desk · Treasury payables
''',
            'catalogue.CatalogueItem': '''# Catalogue item

## Process overview
Unified goods + services master used by sales lines, BOMs, production, stock, and delivery — supports multiple desk processes.

## Actions
- Create/edit GOODS or SERVICE items for use on documents

## Fields
Nature GOODS (SKU/unit, buy/sell) or SERVICE; price currency; active.

## Special situations
Missing catalogue items block document lines.

## Related
- Sales desk · Buyers desk · Shop Floor Control
''',
            'production.ProductionOrder': '''# Production order

## Process overview
**SCM_MAKE_TO_STOCK** on Shop Floor: create or from-sales → release → start → complete.

## Actions
- Prefer Shop Floor Control desk for lifecycle actions

## Fields
Product/BOM, qty, status, lots/checks.

## Special situations
Missing BOM or stock can block release/complete.

## Related
- Shop Floor Control · Buyers desk
''',
            'finance.Account': '''# Account

## Process overview
Chart of accounts entry for **FIN_LEDGER**. Seed starter COA from General Ledger if empty.

## Actions
- Seed COA; maintain codes — post only via ledger service

## Fields
Code (unique per tenant), name, type.

## Special situations
Do not invent balances by hand.

## Related
- General ledger · Posting rules
''',
            'finance.Invoice': '''# Invoice

## Process overview
**FIN_INVOICE_SETTLE** — prefer Finance → Invoices for issue/settle. Upstream often Sales confirm.

## Actions
- Draft → issue → settle (or void)

## Fields
Amounts in primary value rail; status; payment options (EUR + sats rails).

## Special situations
DEMO mock settle only. Posting-rule failures block settle.

## Related
- Invoices desk · Sales desk · Treasury
''',
            'finance.JournalEntry': '''# Journal entry

## Process overview
Posted documents for **FIN_LEDGER**. Create via General Ledger tools so balance rules apply.

## Actions
- Post balanced journals from ledger tools

## Fields
Debit/credit lines; period.

## Special situations
Unbalanced journals are rejected.

## Related
- General ledger · Posting rules
''',
            'delivery.ServiceOrder': '''# Service order

## Process overview
**DEL_SERVICE_ORDER** — schedule, assign resource, start, complete on the Delivery desk.

## Actions
- Prefer Delivery desk lifecycle actions

## Fields
Service, resource, schedule, status.

## Special situations
Unassigned/unscheduled orders block start.

## Related
- Delivery desk · Sales desk
''',
            'service.Inquiry': '''# Inquiry

## Process overview
**AS_INQUIRY** — start → resolve → close on AfterSales desk.

## Actions
- Prefer AfterSales desk over raw CRUD for status flow

## Fields
Customer, case text, status.

## Special situations
Empty customer blocks create.

## Related
- AfterSales desk
''',
            'service.Claims': '''# Claims

## Process overview
After-sales claims against warranties/service — validate → approve/reject → close; may create Finance credit draft (toward **FIN_INVOICE_SETTLE**).

## Actions
- Prefer AfterSales desk for approval path

## Fields
Warranty/service link, amounts, status.

## Special situations
Approval may need Finance access for credits.

## Related
- AfterSales desk · Invoices
''',
            'human.HRFile': '''# HR file

## Process overview
Employee/contractor record supporting **HR_CANDIDATE_HIRE** outcomes and HR desk tabs.

## Actions
- Maintain via HR desk; hire often starts from Candidate

## Fields
Identity fields (may be encrypted); status; links to user.

## Special situations
Sensitive — grant Human capital See only when needed.

## Related
- HR desk · Candidate
''',
            'human.Candidate': '''# Candidate

## Process overview
**HR_CANDIDATE_HIRE** — stages → advance → hire or reject on the HR desk.

## Actions
- Prefer HR desk for advance/hire/reject

## Fields
Contact, stage, status.

## Special situations
Hire creates user/HR file — needs Users permission path.

## Related
- HR desk · Users invite
''',
            'digital.ConfigItem': '''# Config item

## Process overview
CI registry supporting **DIG_IT_TICKET** / **DIG_IT_CHANGE**. Link to System; tickets and changes reference CIs.

## Actions
- Maintain on Digital desk; IT-managed assets link here from Assets

## Fields
System link, hostname/IP and CI attributes.

## Special situations
Hostname/IP edits stay on Digital when assets are IT managed.

## Related
- Digital desk · Assets desk
''',
            'digital.ItTicket': '''# IT ticket

## Process overview
**DIG_IT_TICKET** — internal tickets (not AfterSales): start → resolve → close.

## Actions
- Prefer Digital desk lifecycle

## Fields
Subject, status, CI links.

## Special situations
Finance policy reseeds may open CHANGE-prefixed tickets.

## Related
- Digital desk · DIG_IT_CHANGE
''',
            'agents.Agent': '''# Agent

## Process overview
Automation agents paired with **AR_HITL_APPROVAL**, Hives, and Memory on the Agents hub.

## Actions
- Seed core agents; activate/pause/retire; HITL approve/reject proposals

## Fields
Identity, status, hive links.

## Special situations
Agent Basics only; agents must not settle/pay by default.

## Related
- Agents hub · Hermes activate
''',
            'capability.Project': '''# Project

## Process overview
**CAP_PROJECT** under portfolios/programs on the Capability hub.

## Actions
- Start → complete/cancel; project items hold WBS; optional taskId link

## Fields
Portfolio/program links, status, items.

## Special situations
SYSTEM process catalogue is separate under Processes tab.

## Related
- Capability hub · Development desk
''',
            'configuration.LegalEntity': '''# Legal entity

## Process overview
Administrative legal entity from setup wizard (ADR-029) — no desk SYSTEM processCode.

## Actions
- Prefer configuration wizard for first-time setup; edit carefully later

## Fields
Legal name and entity attributes from setup.

## Special situations
Wrong edits can break company identity on documents.

## Related
- Configuration wizard · Settings
''',
            'configuration.GlobalConfig': '''# Global config

## Process overview
Legacy config bag — prefer CompanySetting / Settings for new work. No desk SYSTEM processCode.

## Actions
- Avoid new writes here when Settings covers the need

## Fields
Legacy key/value bag.

## Special situations
May exist on older tenants only.

## Related
- Settings
''',
            'helper.Countries': '''# Countries

## Process overview
Reference catalog (ISO-style) seeded at bootstrap — no desk SYSTEM processCode.

## Actions
- Browse as needed for address/country dropdowns

## Fields
Country codes/names.

## Special situations
Seeded — prefer not ad-hoc deletes.

## Related
- Admin catalogs
''',
            'helper.Currencies': '''# Currencies

## Process overview
ISO fiat reference catalog. Company **Currencies we use** shortens dropdowns for commercial processes.

## Actions
- Enable company short list under Settings; Bitcoin is value rail, not a catalog row

## Fields
ISO codes; in-use flags.

## Special situations
Soft warnings on document vs company/partner/catalogue mismatches.

## Related
- Currencies we use · Primary value rail
''',
            'generic.ContentType': '''# Content type

## Process overview
Catalog for BODY companion entries (Note, Help, Risk, …). Files use FILE channel — not a ContentType.

## Actions
- Browse types used by Object Companion

## Fields
Type codes for typed BODY content.

## Special situations
Do not confuse with FILE attachments.

## Related
- Content on records
'''
    ].asImmutable()

    private static final List FEATURE_SEEDS = [
            [key: 'app.dashboard', title: 'CEO dashboard', route: '/dashboard', family: 'core',
             body: '''# CEO dashboard

## Process overview
No single SYSTEM processCode owns this screen. It aggregates open work, the Action - NOW board, APQC desk jumps, and system health so you can pick the next process to run on a desk.

## Actions
- Open **My work** / **Action - NOW** — continue human or agentic tasks
- Jump an **APQC group** tile — enter that family’s desk
- Review **System health** signals — decide whether ops are blocked

## Fields
No create/edit form on this screen. Cards show task counts, board cells, and health status.

## Special situations
Empty boards mean no open work in the current filter — create a task or open a desk process. Role **See** limits which desk tiles appear.

## Related
- Action - NOW (`/work`)
- Sales desk · Buyers desk · Finance invoices
- [Onboarding tour](/tour/)
'''],
            [key: 'app.search', title: 'Global search', route: '/dashboard', family: 'core',
             body: '''# Global search (top bar)

## Process overview
No SYSTEM processCode. Search finds records across companies you can See so you can open the right desk or domain document.

## Actions
- Type in the **top-bar search** — find customers, vendors, quotes, orders, invoices, catalogue, POs, tasks
- **Ctrl+K / ⌘K** — jump to pages/desks (navigation only)
- Open a result — deep-link into that record’s desk or domain view

## Fields
Search box needs at least **2** characters (debounce ~300ms). Results show type, title, status, and link.

## Special situations
Results stay inside the **active company**. Types you cannot See are omitted. List filter `q` on a desk only narrows that one list — it is not global search.

## Related
- CEO dashboard
- My work / Action - NOW
'''],
            [key: 'setup.wizard', title: 'Configuration wizard', route: '/setup', family: 'core',
             body: '''# Configuration wizard

## Process overview
Administrative first-run setup — not a desk SYSTEM processCode. Walks Tour → company → site → banking → Bitcoin → template → finish (default **Iron**).

## Actions
- Complete each wizard step and continue
- Choose **Bitcoin integration level** (Not now / Single address / BTCPay Server (experimental))
- Finish to unlock desks for the new company

## Fields
Company/legal entity, site, banking, Bitcoin level, and template tier. Primary value rail (books currency) is chosen separately from Bitcoin receive level.

## Special situations
Admin **Settings** can reset setup for demos. DEMO companies stay on mock settle. Incomplete wizard blocks normal desk use.

## Related
- Integrations hub · Bitcoin integration level
- Settings
- [Onboarding tour](/tour/)
'''],
            [key: 'training.btc.basic', title: 'Basic Bitcoin literacy', route: '/system/integrations', family: 'finance',
             body: '''# Basic Bitcoin literacy (Level 1 nudge)

## Process overview
Administrative training checklist before enabling Bitcoin receive — no desk SYSTEM processCode. Aligns with Integrations Bitcoin level **1 — Not now**.

## Actions
- Read the literacy checklist on this topic
- Set Bitcoin level under **System → Integrations** (or setup Bitcoin step)

## Fields
No form fields on this help topic. Level choice lives on Integrations.

## Special situations
steakOS does **not** hold keys. Level 1 defers live receive; books may still use a BTC primary rail with **mock** settle. Prefer **BTCPay Server (experimental)** for regular customer payments.

## Related
- Bitcoin integration level · Live payments (BTCPay Server · experimental)
- Advanced Bitcoin security
'''],
            [key: 'training.btc.security-advanced', title: 'Advanced Bitcoin security', route: '/system/integrations', family: 'finance',
             body: '''# Advanced Bitcoin security (Level 2 nudge)

## Process overview
Security checklist for Bitcoin Level **2 — Single address** (occasional payments). Administrative — no desk SYSTEM processCode.

## Actions
- Follow the backup / phishing checklist
- Manage Level 2 under **System → Integrations**
- Plan upgrade to **BTCPay Server (experimental)** for Lightning / regular receive

## Fields
Single software-wallet address configured on Integrations — never paste seeds into steakOS.

## Special situations
Address reuse and weak backups can cause permanent loss. steakOS does not watch the chain in beta — settle is **manual mark paid**. CEO training ack required on upgrade to Level 2/3.

## Related
- Bitcoin integration level · Live payments (BTCPay Server · experimental)
- Basic Bitcoin literacy
'''],
            [key: 'system.integrations', title: 'Integrations hub', route: '/system/integrations', family: 'core',
             body: '''# Integrations hub

## Process overview
Administrative surface for operator integrations (Nostr, Bitcoin receive level, Hermes tokens) — **no desk SYSTEM processCode**. Live payment ops continue under Finance → Payments after Level 3.

## Actions
- Configure **Nostr** relay level, install/start, probe
- Set **Bitcoin** receive level (Not now · Single address · BTCPay Server (experimental))
- **Mint / revoke** Hermes agent API tokens
- Probe connections before enabling live

## Fields
Relay URL / level; Bitcoin level; BTCPay keys live under Finance → Payments; Hermes `sos_…` token (shown once).

## Special situations
CEO must acknowledge training when Admin upgrades Bitcoin to Level 2 or 3. DEMO companies do not publish Nostr and stay on mock settle. Never paste `nsec`. Agent Basics only in closed beta.

## Related
- Live payments (BTCPay Server · experimental) · Bitcoin integration level · Nostr integration
- Hermes agent (API tokens) · AI / Agentic hub
'''],
            [key: 'integrations.bitcoin', title: 'Bitcoin integration level', route: '/system/integrations', family: 'finance',
             body: '''# Bitcoin integration level

## Process overview
Administrative control of how the company **receives** Bitcoin — separate from the **primary value rail**. No desk SYSTEM processCode; settle flow is **FIN_INVOICE_SETTLE** on Invoices once payment exists.

## Actions
- Set level in setup **Bitcoin** step or **System → Integrations**
- For Level 3: open **Finance → Payments**, probe, enable live

## Fields
| Level | UI | Behaviour |
|-------|-----|-----------|
| DEFERRED | 1 — Not now | No live receive; mock settle OK |
| SINGLE_ADDRESS | 2 — Single address | One address + QR; manual mark paid |
| BTCPAY | 3 — BTCPay Server (experimental) | Operator-run BTCPay Server (experimental) (Lightning + on-chain) |

## Special situations
Admin sets level; CEO training ack on upgrade to 2/3; Admin-only downgrade from Level 3. Lightning marketing claims only at Level 3.

## Related
- Live payments (BTCPay Server · experimental) · Invoices (FIN_INVOICE_SETTLE)
- Basic / Advanced Bitcoin training
'''],
            [key: 'integrations.nostr', title: 'Nostr integration', route: '/system/integrations', family: 'digital',
             body: '''# Nostr integration (OI-NOSTR01)

## Process overview
Administrative company relay / audit level for private local Nostr — no desk SYSTEM processCode. Per-user Alby login is under **Security**, not here.

## Actions
- Set level **0 / 1 / 2** on Integrations or setup Nostr step
- **Install / start** local relay (project `var/nostr/`, loopback)
- **Probe** until reachable
- People link Alby under **Security → Link browser extension**

## Fields
Company relay level and URL; optional company bot metadata. Never paste `nsec`.

## Special situations
DEMO companies do not publish. Without bot key, publish stays honest (`stubbed`). Level 2 giftwrap notify is still residual.

## Related
- Sign in with Alby (Nostr)
- Integrations hub
'''],
            [key: 'sales.desk', title: 'Sales desk', route: '/sales', family: 'sales',
             body: '''# Sales desk

## Process overview
SYSTEM process **SALES_QUOTE_TO_ORDER**: customer → quote → send → accept/reject → order → confirm. Confirming an order can create a draft invoice and connects downstream to **FIN_INVOICE_SETTLE**.

## Actions
- **New** customer / quote / order — start the commercial path
- Row **View / Edit / Delete** — inspect or change drafts; Edit blanks when the record is locked (e.g. accepted quote)
- View-modal footer process steps — send, accept/reject, confirm
- Green **Process** pill — opens the SALES_QUOTE_TO_ORDER process modal

## Fields
- Customer: kind (PERSON/ORGANIZATION), contact, preferred currency, taxId, active
- Quote / order: partner, document currency, lines (catalogue ref, qty, price, tax code)
- Soft currency warnings when document, partner, and catalogue currencies differ (save still allowed)

## Special situations
Role **See / Work** on Sales required. Office can create quotes but cannot settle money. Empty customer list blocks quoting until you create or import a customer.

## Related
- Invoices (FIN_INVOICE_SETTLE)
- Delivery desk (DEL_SERVICE_ORDER) when orders need fulfillment
- Currencies we use · Partner communication language
- [Onboarding tour](/tour/)
'''],
            [key: 'currency.catalog', title: 'Currencies we use', route: '/settings', family: 'core',
             body: '''# Currencies we use

## Process overview
Administrative Settings surface — no desk SYSTEM processCode. Enabled codes feed dropdowns on commercial documents that later run SALES_QUOTE_TO_ORDER / SCM_PO_FULFILL / FIN_INVOICE_SETTLE.

## Actions
- Admin: enable/disable codes under **Settings → Currencies we use**
- Operators: pick enabled codes on customers, vendors, catalogue, and documents

## Fields
Shared ISO list; company **Currencies we use** short set; company currency in the top bar; preferred currency on partners; price currency on catalogue items.

## Special situations
New writes need an enabled in-use code; legacy orphans stay readable. Soft mismatch warnings do not block save.

## Related
- Sales desk · Buyers desk · Invoices
- Primary value rail & BTC FX
'''],
            [key: 'finance.primaryRail', title: 'Primary value rail & BTC FX', route: '/settings', family: 'finance',
             body: '''# Primary value rail & BTC FX

## Process overview
Administrative books-currency choice (setup / Settings) — not a desk SYSTEM processCode. Settlement amounts still post through **FIN_INVOICE_SETTLE** and treasury positions.

## Actions
- Choose primary rail in setup (EUR / USD / BTC + display fiat)
- Read FX hints on forms; settle invoices to lock payment-time rates

## Fields
Primary rail; display fiat for BTC books; compact FX hint and stale badge from server rate cache.

## Special situations
Hints are not the books. No automatic wallet MTM. Changing rail after setup is Advanced (not closed beta). Manual FX tables unsupported.

## Related
- Invoices (FIN_INVOICE_SETTLE) · Treasury (FIN_PAYABLE)
- Live payments (BTCPay Server · experimental)
'''],
            [key: 'finance.invoices', title: 'Invoices', route: '/finance/invoices', family: 'finance',
             body: '''# Invoices

## Process overview
SYSTEM process **FIN_INVOICE_SETTLE**: draft → **issue** → **settle** when paid. Upstream often **SALES_QUOTE_TO_ORDER** (confirm may create draft invoice).

## Actions
- Create draft invoice (or open sales-created draft)
- **Issue** then **Settle** on the detail
- Select a row for split detail (no empty pane on load)
- Green **Process** pill — opens FIN_INVOICE_SETTLE

## Fields
Amounts in company **primary value rail**; optional FX hints (display-only); payment option / rail including Bitcoin when configured; status draft/issued/paid/void.

## Special situations
DEMO companies: **mock settle only**. LIVE Bitcoin receive follows Integrations level; Lightning claims only at Level 3. Missing COA/posting rules block settle (`POSTING_RULES_*`).

## Related
- Sales desk (SALES_QUOTE_TO_ORDER)
- Treasury · Posting rules · Live payments (BTCPay Server · experimental)
- [Onboarding tour](/tour/)
'''],
            [key: 'finance.payments.live', title: 'Live payments (BTCPay Server · experimental)', route: '/finance/payments', family: 'finance',
             body: '''# Live payments (BTCPay Server · experimental)

## Process overview
Administrative LIVE receive configuration for Bitcoin Level 3. Money movement on invoices still follows **FIN_INVOICE_SETTLE**; this screen wires BTCPay so settle can be live instead of mock.

## Actions
- Set level to **BTCPay Server (experimental)** on Integrations
- Enter base URL, store id, API key, webhook secret
- **Probe** then enable **live**
- Issue invoice → inbound payment → pay in BTCPay → webhook or reconcile

## Fields
BTCPay connection fields above; live toggle. Fiat/SEPA settle remains available separately.

## Special situations
DEMO companies forced to mock settle. steakOS does not custody keys. Upgrade from Level 2 keeps occasional address until BTCPay is live.

## Related
- Invoices (FIN_INVOICE_SETTLE)
- Bitcoin integration level · Treasury
'''],
            [key: 'finance.ledger', title: 'General ledger', route: '/finance/transactions', family: 'finance',
             body: '''# General ledger

## Process overview
SYSTEM-oriented finance kernel **FIN_LEDGER**: seed chart of accounts, post balanced journals, trial balance, period-close marker. Not the invoice desk — postings often originate from **FIN_INVOICE_SETTLE** / payables.

## Actions
- Seed COA if empty
- Post balanced journals via ledger tools
- Run trial balance / period close marker

## Fields
Account codes (unique per tenant); journal lines with debit/credit; period markers.

## Special situations
Do not invent balances by hand — posting only through the ledger service. Bad posting rules block operational settle/pay upstream.

## Related
- Invoices (FIN_INVOICE_SETTLE) · Posting rules · Treasury
'''],
            [key: 'finance.treasury', title: 'Treasury', route: '/finance/treasury', family: 'finance',
             body: '''# Treasury hub

## Process overview
Treasury positions and payables lean on **FIN_PAYABLE** (and related treasury position views). Tabs cover positions, payables, budgets, tax, payroll stub, control. Dual fiat bank + Bitcoin wallet positions share one COA.

## Actions
- Open section tabs on this hub
- Work payables / positions as each tab allows
- Green **Process** pill — opens FIN_PAYABLE (treasury context)

## Fields
Position carrying values follow **primary value rail**; payable amounts and status; budget/tax/payroll stub fields per tab.

## Special situations
Live Bitcoin receive is configured under Live payments (BTCPay Server · experimental), not only here. Posting-rule failures block vendor pay.

## Related
- Invoices (FIN_INVOICE_SETTLE) · General ledger · Buyers desk
- Primary value rail & BTC FX
- [Onboarding tour](/tour/)
'''],
            [key: 'finance.postingRules', title: 'Posting rules (auto journals)', route: '/finance/posting-rules', family: 'finance',
             body: '''# Posting rules

## Process overview
Administrative company ruleset for auto journals — no desk SYSTEM processCode of its own. Rules fire when **FIN_INVOICE_SETTLE**, vendor pay, and payroll recognize/pay run (and under Accrual on issue / payable open).

## Actions
- View rules / mode / change log (Admin + CEO)
- Edit rules / mode / reseed (Admin only)
- Save or Reseed — appends change log and opens a Digital IT ticket (`CHANGE`, prefix `[Finance policy]`)

## Fields
Accounting mode **Cash** vs **Accrual**; per-event account mappings in the ruleset.

## Special situations
Bad or missing rules **block** settle/pay (`POSTING_RULES_*`) — no silent fallback. Reseed never wipes journals.

## Related
- General ledger · Invoices · Treasury · Payroll
'''],
            [key: 'scm.desk', title: 'Buyers Desk', route: '/scm/buyers', family: 'scm',
             body: '''# Buyers Desk

## Process overview
SYSTEM process **SCM_PO_FULFILL**: vendor → purchase order → send → confirm → receive (stock / Finance payable). Shop floor make-to-stock is a separate process on Shop Floor Control (**SCM_MAKE_TO_STOCK**).

## Actions
- **New** vendor / product / service / PO — start procurement
- Row **View / Edit / Delete** — Edit/Delete only while PO is **DRAFT**
- View-modal footer — send, confirm, receive
- Green **Process** pill — opens SCM_PO_FULFILL

## Fields
Vendor contact, taxId, currency, active. PO header + lines (catalogue, qty, price). Receive posts stock / payable as configured.

## Special situations
Issued/confirmed POs show View only. Missing vendors or catalogue items block PO create. Finance payable hand-off needs posting rules healthy.

## Related
- Shop Floor Control (SCM_MAKE_TO_STOCK)
- Treasury / payables (FIN_PAYABLE)
- [Onboarding tour](/tour/)
'''],
            [key: 'scm.shopfloor', title: 'Shop Floor Control', route: '/scm/shopfloor', family: 'scm',
             body: '''# Shop Floor Control

## Process overview
SYSTEM process **SCM_MAKE_TO_STOCK**: product/BOM → production order → release → start → complete, with lot checks and inventory on-hand. Purchasing stays on Buyers (**SCM_PO_FULFILL**).

## Actions
- **New** production order (or from sales demand where offered)
- Select a row — open detail (split layout)
- Release / start / complete and lot / quality actions on the detail
- Green **Process** pill — opens SCM_MAKE_TO_STOCK

## Fields
Product/BOM reference, qty, status, lot/check fields, on-hand inventory views.

## Special situations
Detail pane stays empty until you select a row. Missing BOM or stock can block release/complete. Role Work on production/SCM required.

## Related
- Buyers Desk (SCM_PO_FULFILL)
- Assets desk when plant tools are involved
- [Onboarding tour](/tour/)
'''],
            [key: 'delivery.desk', title: 'Delivery desk', route: '/delivery', family: 'delivery',
             body: '''# Delivery desk

## Process overview
SYSTEM process **DEL_SERVICE_ORDER**: services & resources → service order (from sales or manual) → schedule → assign → start → complete.

## Actions
- **New** service / resource / service order
- Row View / Edit; schedule, assign, start, complete on the process path
- Green **Process** pill — opens DEL_SERVICE_ORDER

## Fields
Service and resource masters; order schedule window, assigned resource, status.

## Special situations
No free resource or unscheduled order blocks start. Sales-origin orders need a confirmed sales order upstream.

## Related
- Sales desk (SALES_QUOTE_TO_ORDER)
- AfterSales desk (AS_INQUIRY, AS_RMA)
- [Onboarding tour](/tour/)
'''],
            [key: 'aftersales.desk', title: 'AfterSales desk', route: '/aftersales', family: 'service',
             body: '''# AfterSales desk

## Process overview
SYSTEM processes **AS_INQUIRY** (customer questions/cases: start → resolve → close) and **AS_RMA** (returns). Claims may create Finance credit drafts; RMAs can post stock receipts. Tab-aware: Inquiry vs RMA use their own processCode.

## Actions
- **New** inquiry / warranty / claim / RMA
- Advance status (start, resolve/approve, close) on the active tab
- Green **Process** pill — opens AS_INQUIRY or AS_RMA for the current tab

## Fields
Customer link, case text, warranty refs, claim amounts, RMA lines / stock disposition.

## Special situations
Claim approval may need Finance access for credit drafts. Empty customer or missing warranty can block claim create.

## Related
- Sales desk · Delivery desk
- Invoices / credits (FIN_INVOICE_SETTLE)
- [Onboarding tour](/tour/)
'''],
            [key: 'hr.desk', title: 'HR desk', route: '/hr', family: 'human',
             body: '''# HR desk

## Process overview
SYSTEM process **HR_CANDIDATE_HIRE**: candidates → advance stages → hire (creates user/HR file) or reject. Same desk also holds HR files, time, payroll periods, goals, policies, party access (supporting HUMAN processes).

## Actions
- **New** candidate / HR file / time / goal as tabs allow
- Select row for split detail; advance / hire / reject on candidates
- Green **Process** pill — opens HR_CANDIDATE_HIRE

## Fields
Candidate stages and contact; HR file identity (sensitive fields may be encrypted); time and payroll period markers.

## Special situations
Hire needs Users permission path for account creation. HR **See** is sensitive — grant only when needed. Detail pane empty until row select.

## Related
- Users / invite · Roles
- [Onboarding tour](/tour/)
'''],
            [key: 'digital.desk', title: 'Digital desk', route: '/digital', family: 'digital',
             body: '''# Digital / IT desk

## Process overview
SYSTEM processes **DIG_IT_TICKET** (internal tickets: start → resolve → close) and **DIG_IT_CHANGE**. Systems, data assets, solutions, and CIs support those flows.

## Actions
- **New** system / CI / change / ticket
- Select row for split detail; start / resolve / close tickets; progress changes
- Green **Process** pill — opens DIG_IT_TICKET or DIG_IT_CHANGE for the active context

## Fields
CI link to System; ticket subject/status; change record fields.

## Special situations
Tickets here are **internal IT**, not AfterSales customer cases. Detail empty until row select. Agent-opened Finance policy tickets may appear as CHANGE-named items.

## Related
- Agents hub (AR_HITL_APPROVAL) when agents propose changes
- Integrations hub
- [Onboarding tour](/tour/)
'''],
            [key: 'asset.desk', title: 'Assets desk', route: '/assets', family: 'asset',
             body: '''# Assets desk

## Process overview
SYSTEM process **AST_ASSET_LIFECYCLE**: register physical plant, vehicles, and tools; maintain status and optional IT / finance / maintenance flags.

## Actions
- **New** asset on the register
- View / Edit / Delete; attach maintenance schedule via content rail
- Green **Process** pill — opens AST_ASSET_LIFECYCLE

## Fields
Name, tag, location, owner, status. **IT managed** links a Digital ConfigItem. **Financial depreciation** and **Asset requires maintenance** are Basics intent flags.

## Special situations
Hostname/IP edits stay on the Digital desk when IT managed. Automatic depreciation posting and maintenance work orders deepen later — flags only mark intent in Basics.

## Related
- Digital desk (DIG_IT_TICKET / CIs)
- Shop Floor Control · Finance ledger
- [Onboarding tour](/tour/)
'''],
            [key: 'strategy.desk', title: 'Strategy desk', route: '/strategy', family: 'strategy',
             body: '''# Strategy desk

## Process overview
SYSTEM processes **STR_STRATEGY_ITEM** (strategy topics) and **STR_COMPETITOR_WATCH** (competitors). Attach Link/File/Content on either.

## Actions
- Tabs + **New** for competitors or strategy topics
- View / Edit / Delete; content rail on the record
- Green **Process** pill — opens the tab’s processCode

## Fields
Competitor: name, type, sources, status. Strategy topic: name, type, description, kind, status.

## Special situations
Empty lists are fine for early companies — create the first competitor or topic to start. No money movement on this desk.

## Related
- Capability hub (CAP_PROJECT)
- [Onboarding tour](/tour/)
'''],
            [key: 'development.desk', title: 'Development desk', route: '/development', family: 'development',
             body: '''# Development desk

## Process overview
SYSTEM process **DEV_PROJECT_DELIVER**: NPD / revision development projects from create through delivery milestones on this desk.

## Actions
- **New** development project
- View / Edit / Delete; advance project status on the modal/detail
- Green **Process** pill — opens DEV_PROJECT_DELIVER

## Fields
Project name, type (NPD / revision), description, status, linked artefacts via content rail.

## Special situations
Day-to-day portfolio work may also appear under Capability projects (**CAP_PROJECT**) — use this desk for product/development delivery specifically.

## Related
- Capability hub (CAP_PROJECT)
- [Onboarding tour](/tour/)
'''],
            [key: 'grrc.desk', title: 'Governance desk', route: '/grrc', family: 'grrc',
             body: '''# Governance desk

## Process overview
SYSTEM processes **GRC_RISK_TREAT** (Risks tab: open → mitigate → close or accept) and **GRC_COMPLIANCE_ITEM** (Compliance tab: draft → activate → retire). Silver Governance surface; pairs with Stakeholder (**STK_PARTY_ENGAGE**).

## Actions
- Tabs **Risks** / **Compliance** — switch processCode on the green **Process** pill
- **New** risk or compliance item — start the tab’s path
- Row View / Edit / Delete; close or accept risk; activate or retire compliance
- Green **Process** pill — opens GRC_RISK_TREAT or GRC_COMPLIANCE_ITEM for the active tab

## Fields
- Risk: name, severity, likelihood, owner, treatment note, status (OPEN → …), description
- Compliance: name, item kind (policy / obligation / control), owner, effective from, status (DRAFT → ACTIVE → RETIRED), description
- Content rail on the selected record

## Special situations
Requires **Silver** (or higher) metal selection. Empty lists are fine until the first risk or obligation is registered. No money movement on this desk.

## Related
- Stakeholder desk (STK_PARTY_ENGAGE)
- Capability hub (CAP_PROJECT)
- [Onboarding tour](/tour/)
'''],
            [key: 'stakeholder.desk', title: 'Stakeholder desk', route: '/stakeholder', family: 'stakeholder',
             body: '''# Stakeholder desk

## Process overview
SYSTEM process **STK_PARTY_ENGAGE**: register external parties (active → watch → inactive) and log communications. Complements Governance (**GRC_RISK_TREAT** / **GRC_COMPLIANCE_ITEM**).

## Actions
- Tabs **Parties** / **Communications**
- **New** party or communication log
- Row View / Edit / Delete; change party status (ACTIVE / WATCH / INACTIVE)
- Green **Process** pill — opens STK_PARTY_ENGAGE

## Fields
- Party: name, party kind (regulator, community, media, partner, NGO, investor, other), status, contact, ethics note, description
- Communication: party, subject, summary, channel, direction, occurred at, status
- Content rail on the selected record

## Special situations
Requires **Silver** (or higher). Communications need an existing party. Ethics notes are operator guidance — not a legal archive.

## Related
- Governance desk (GRC_RISK_TREAT / GRC_COMPLIANCE_ITEM)
- [Onboarding tour](/tour/)
'''],
            [key: 'capability.hub', title: 'Capability hub', route: '/capability', family: 'capability',
             body: '''# Capability hub

## Process overview
SYSTEM process **CAP_PROJECT** plus the process **catalogue** (SYSTEM / HUMAN / AGENTIC). Section tabs cover portfolios, programs, projects, processes, quality, standards, changes, knowledge.

## Actions
- Section tabs — switch surfaces including **Standards** (`/capability/standards`)
- Select a row for split detail; start / complete / cancel projects
- Open a SYSTEM process — Mermaid chart and steps (see Process charts)
- Green **Process** pill — opens CAP_PROJECT (or the active process context)

## Fields
Project portfolio fields; process kind/status (SYSTEM read-only seeds); quality, standards/KPIs, and change records as tabs provide.

## Special situations
SYSTEM catalogue entries are read-only. Reseed from Settings or Capability → Reseed. Detail empty until row select.

## Related
- Process charts
- Standards (`/capability/standards`)
- Development desk (DEV_PROJECT_DELIVER)
- [Onboarding tour](/tour/)
'''],

            [key: 'capability.standards', title: 'Standards & KPIs', route: '/capability/standards', family: 'capability',
             body: '''# Standards & KPIs

## Process overview
Capability **Standards** register (APQC 13.7 thin) for metrics and targets used with improvement work. Complements **Quality** and **CAP_PROJECT** portfolio delivery — not a separate money process.

## Actions
- **New** — create a standard / KPI (name, metric code, target, unit)
- Row **View / Edit** — inspect; detail pane saves field updates
- **Activate / Deactivate** — toggle ACTIVE ↔ INACTIVE
- Green **Process** pill — CAP_PROJECT (hub default)
- Content rail — attach notes/files on the selected standard

## Fields
Name; description; metricCode; targetValue; unit; standardStatus (ACTIVE | INACTIVE).

## Special situations
Empty list is fine until the first KPI is defined. Deactivated standards stay in history — they are not deleted. Domain list `/domains/capability/Standard` redirects create to this desk.

## Related
- Capability hub (CAP_PROJECT)
- Quality tab
- [Onboarding tour](/tour/)
'''],
            [key: 'process.chart', title: 'Process charts', route: '/capability/processes', family: 'capability',
             body: '''# Process charts

## Process overview
Learning aid for **SYSTEM** catalogue processes (**CAP_PROJECT** family / process catalogue) — not process mining. Deep link example: `/capability/processes?code=FIN_INVOICE_SETTLE`.

## Actions
- Open **Capability → Processes** (defaults to SYSTEM)
- Select a process (or use How-it-works / Process pill on a desk)
- Hover or keyboard-focus a step for What / Who / Where + pitfall; tap to pin on touch; Esc closes

## Fields
Process code, kind (SYSTEM/HUMAN/AGENTIC/OTHER), status (DRAFT/ACTIVE/RETIRED), Mermaid chart, step tips.

## Special situations
Tips marked **planned / stub** are honest about unfinished depth. SYSTEM seeds are read-only; reseed from Settings or Capability.

## Related
- Capability hub
- Any desk Process pill for that desk’s processCode
'''],
            [key: 'agents.hub', title: 'AI / Agentic hub', route: '/agents', family: 'agents',
             body: '''# AI / Agentic resources

## Process overview
SYSTEM process **AR_HITL_APPROVAL** for human-in-the-loop approvals. Hub also covers Agents, Hives, and Memory. Closed beta claims **Agent Basics** only — not Agent Pro.

## Actions
- Seed core agents on first use; activate / pause / retire
- **HITL** tab — approve or reject agent proposals
- Green **Process** pill — opens AR_HITL_APPROVAL
- Connect operator runtime via Hermes token (Integrations)

## Fields
Agent identity/status; hive membership; HITL proposal payload; memory entries.

## Special situations
Agents use the API (not the browser). They do **not** settle or pay money by default — humans keep money risk. Detail empty until row select.

## Related
- Hermes agent (API tokens) · Integrations hub
- Digital desk (agent-opened IT tickets)
- [Onboarding tour](/tour/)
'''],
            [key: 'agents.activate', title: 'Hermes agent (API tokens)', route: '/system/integrations', family: 'agents',
             body: '''# Hermes agent (API tokens)

## Process overview
Administrative path to connect an operator-owned agent host — pairs with **AR_HITL_APPROVAL** on the Agents hub. No separate desk SYSTEM processCode on Integrations itself.

## Actions
- **System → Integrations → Hermes agents** — mint a `sos_…` token (shown once)
- Paste into the host with steakOS baseUrl
- Smoke: `GET /api/agent/me` and `/api/agent/l0/summary` with Bearer
- Optional: agent proposes → **Agents → HITL** → human approve/reject

## Fields
Opaque API token (once); base URL on the host. No browser session for the agent.

## Special situations
Agent Basics only. Revoke tokens if a host is compromised. Agents must not be given settle/pay powers by default.

## Related
- AI / Agentic hub (AR_HITL_APPROVAL)
- Integrations hub
'''],
            [key: 'system.import', title: 'Data import', route: '/system/import', family: 'core',
             body: '''# Data & import

## Process overview
Administrative CSV import — no desk SYSTEM processCode. Packs feed masters used later by Sales / Buyers / Finance processes.

## Actions
- Choose pack (**Customers**, **Vendors**, **COA**)
- Preview mapping, then commit
- Opening-balance pack is preview-only

## Fields
CSV columns mapped to master fields per pack.

## Special situations
Import permission required (Admin/CEO typical). Bad rows fail preview — fix the file before commit.

## Related
- Sales desk · Buyers desk · General ledger
'''],
            [key: 'system.settings', title: 'Settings', route: '/settings', family: 'core',
             body: '''# Settings

## Process overview
Administrative company/user settings — **no desk SYSTEM processCode**. Language, currencies, tax codes, and demo/setup controls live here; Integrations is a separate hub.

## Actions
- Set **Language & region** for your screen locale
- Admin: **Currencies we use**, tax codes in use, reset setup
- Open **System → Integrations** for Nostr / Bitcoin / Hermes (not on this page)

## Fields
Locale; currency short list; tax codes; demo/setup status.

## Special situations
Screen language is not partner PDF language — that is Communication language on Customer/Vendor. Reset setup is Admin and destructive for demos.

## Related
- Currencies we use · German VAT tax codes
- Partner communication language · Integrations hub
'''],
            [key: 'system.catalogs', title: 'Admin catalogs', route: '/system/catalogs', family: 'core',
             body: '''# Admin catalogs

## Process overview
Administrative reference-data lists — no desk SYSTEM processCode. Power-user catalogues, not daily operational desks.

## Actions
- Browse/edit reference and content-type domains as permitted
- Prefer family desks for day-to-day process work

## Fields
Varies by catalog resource (countries, currencies, content types, legacy config).

## Special situations
Catalog permission required. Editing reference data can affect dropdowns on process desks.

## Related
- Settings · Capability process catalogue
'''],
            [key: 'app.about', title: 'About steakOS', route: '/about', family: 'core',
             body: '''# About

## Process overview
Product metadata screen — no SYSTEM processCode. Shows line, SemVer, template tier, and milestones.

## Actions
- Read version / tier information
- Follow security disclosure guidance when needed

## Fields
None editable for process work.

## Special situations
Security issues go privately to **security@steakos.org** — not public exploit posts.

## Related
- Report a security issue
'''],
            [key: 'security.disclose', title: 'Report a security issue', route: '/about', family: 'core',
             body: '''# Report a security issue

## Process overview
Administrative security intake — no desk SYSTEM processCode. Private email process for steakOS core and documented install configs.

## Actions
- Email **security@steakos.org** privately
- Do not post exploit details in public issues

## Fields
N/A on-screen; include product version and repro in email.

## Special situations
Ack target ≤ 3 business days; triage ≤ 10. Not CRA certification. Third-party BTCPay/Hermes hosts are out of scope unless steakOS glue leaks data.

## Related
- About steakOS
'''],
            [key: 'app.tour', title: 'Onboarding tour', route: '/tour', family: 'core',
             body: '''# Onboarding tour

## Process overview
Product learning surface at `/tour/` — not a desk SYSTEM processCode. Template choice here is remembered for the setup wizard.

## Actions
- Open **`/tour/index.html`**
- Complete tour modules; continue to setup when prompted

## Fields
Tour progress / template preference as the tour app stores them.

## Special situations
Tour is optional after first run; desks remain available based on setup completion and roles.

## Related
- Configuration wizard
- CEO dashboard
'''],
            [key: 'help.roles.overview', title: 'Roles for a small team', route: '/roles', family: 'core',
             body: '''# Roles for a small team

## Process overview
Administrative access model for a small company (about 2–5 people) — no desk SYSTEM processCode. Roles decide which desks/processes a person can See and Work.

## Actions
- Open **System → Roles** — adjust profile defaults
- Open **System → Users** — assign a profile and invite
- Keep presets unless someone needs an extra desk

## Fields
Role profile (Admin, CEO, Office, Ops, Finance, User, Read only); desk See/Work verbs; system actions (Users, Import, Settle money, …).

## Special situations
No IT department required. Deny wins over allow. Humans never get ROLE_AI from Users.

## Related
- Which role to pick · Permission matrix · Invite a teammate
- Safety: Admin, CEO & disable
'''],
            [key: 'help.roles.presets', title: 'Which role to pick', route: null, family: 'core',
             body: '''# Which role to pick

## Process overview
Administrative guidance for choosing a role profile — no desk SYSTEM processCode. Presets are starting fills under **System → Roles**.

## Actions
- Pick a profile when inviting under Users
- Change defaults on Roles when the team’s needs change

## Fields
Profile name and typical desk grants (see matrix Help for verbs).

## Special situations
One person → one profile. Prefer changing the profile over inventing a new role in v1. CEO cannot mint Admin.

## Related
- Roles for a small team · Permission matrix · Practice roles
'''],
            [key: 'help.roles.matrix', title: 'Permission matrix (Advanced)', route: null, family: 'core',
             body: '''# Permission matrix (Advanced)

## Process overview
Administrative source of truth for desk and system verbs — no desk SYSTEM processCode. Intersects with company-enabled families.

## Actions
- View/tune matrix cells (Admin/CEO Advanced)
- Apply profile changes carefully (confirm before apply-to-all)

## Fields
Per desk: See, Create, Change, Delete, Work. System actions: Users/invite, Import, Catalogs, Agents, Setup, Settle money, reset 2FA.

## Special situations
Deny wins. Protect the last Admin. Settle money is a Finance special — Office does not have it by default. API returns MATRIX_*_FORBIDDEN when blocked.

## Related
- Roles overview · Which role to pick · Safety
'''],
            [key: 'help.users.invite', title: 'Invite a teammate', route: '/users', family: 'core',
             body: '''# Invite a teammate

## Process overview
Administrative user provisioning — no desk SYSTEM processCode. Creates the account that later may run desk processes per role.

## Actions
- **System → Users** — choose profile, username, temporary password, create
- Share credentials out-of-band
- Ask them to enrol authenticator under Security; optional Alby link

## Fields
Username; temporary password (12+ with complexity); role profile; Extension column after Alby link.

## Special situations
Admin and CEO may invite; CEO cannot assign Admin. Prefer Disable over delete. Never paste nsec.

## Related
- Sign in with Alby · Roles overview · Safety
'''],
            [key: 'help.security.nostrLogin', title: 'Sign in with Alby (Nostr)', route: '/security', family: 'core',
             body: '''# Sign in with Alby (Nostr)

## Process overview
Administrative per-user NIP-07 login — no desk SYSTEM processCode. Company relay level on Integrations is separate.

## Actions
- Password sign-in once → **Security → Link browser extension** → approve in Alby
- Later: login page **Sign in with browser extension**

## Fields
Linked Yes/No on Security; Users Extension column.

## Special situations
Until Linked, extension login fails. Never paste nsec. One Alby identity ↔ one steakOS user. Password login remains available.

## Related
- Invite a teammate · Nostr integration
'''],
            [key: 'help.users.danger', title: 'Safety: Admin, CEO & disable', route: null, family: 'core',
             body: '''# Safety: Admin, CEO & disable

## Process overview
Administrative safety rules — no desk SYSTEM processCode. Protects control of roles that gate every desk process.

## Actions
- Keep Admin and CEO as separate logins
- Prefer Disable over delete; Clear authenticator only for lost devices
- Confirm before apply-to-all role changes

## Fields
N/A — behavioural rules on Users/Roles screens.

## Special situations
Cannot disable/demote the last Admin. Only Settle money profiles may settle invoices. Do not grant ROLE_AI to humans from Users.

## Related
- Invite a teammate · Roles matrix
'''],
            [key: 'help.users.privacy', title: 'Privacy, HR & Read only', route: null, family: 'core',
             body: '''# Privacy, HR & Read only

## Process overview
Administrative privacy guidance — no desk SYSTEM processCode. Especially relevant before granting **See** on Human capital (**HR_CANDIDATE_HIRE** desk).

## Actions
- Grant HR See only when needed
- Use **Read only** for visitors/auditors/trainees who must not write

## Fields
N/A — access posture guidance.

## Special situations
Content on records follows host desk access. Avoid destructive Admin practice on live books.

## Related
- HR desk · Roles presets
'''],
            [key: 'help.roles.demo', title: 'Practice roles (demo / training)', route: null, family: 'core',
             body: '''# Practice roles (demo / training)

## Process overview
Administrative training guidance on demo tenants — no desk SYSTEM processCode. Practice the same profiles you will use on live desks.

## Actions
- Use Read only / Office / Ops / Finance / CEO as the goal table suggests
- Disable throwaway users after practice

## Fields
N/A.

## Special situations
Prefer demo before live. Help drawer on Roles/Users shows these articles in context.

## Related
- Which role to pick · Roles overview
'''],
            [key: 'help.partners.locale', title: 'Partner communication language', route: null, family: 'core',
             body: '''# Communication language

## Process overview
Partner master field used when commercial documents run (SALES_QUOTE_TO_ORDER, SCM_PO_FULFILL, FIN_INVOICE_SETTLE). Not your screen language in Settings.

## Actions
- Set **Communication language** on Customer / Vendor masters
- Download PDFs / send email — labels follow the partner language

## Fields
Communication language (e.g. German / English). Blank → English (en_US) for outbound docs.

## Special situations
Draft previews follow live setting; after send/confirm/issue the document freezes language. Catalogue descriptions not dual-language yet. Nostr partner DMs still residual.

## Related
- Business PDFs · Settings (screen language) · Sales desk
'''],
            [key: 'help.tax.ust', title: 'German VAT (USt) tax codes', route: '/settings', family: 'finance',
             body: '''# German VAT (USt) tax codes

## Process overview
Administrative tax-code pack (Settings) used on sales/PO lines during commercial processes — no desk SYSTEM processCode of its own.

## Actions
- Enable codes under **Settings → Tax codes we use**
- Pick codes on quote / sales order / PO lines

## Fields
DE_USt_STD 19%, DE_USt_RED 7%, DE_USt_0, DE_RC, DE_IG, DE_EXPORT (AT equivalents similarly). Regime: Germany/Austria VAT vs Kleinunternehmer/None.

## Special situations
Operator owns rates — seed is not legal advice. Not UStVA/ELSTER filing. Wrong code is operator error.

## Related
- Reverse charge & IG · Business PDFs · Finance tax tab
'''],
            [key: 'help.tax.reverseCharge', title: 'Reverse charge & intra-community (IG)', route: '/sales', family: 'finance',
             body: '''# Reverse charge & intra-community (IG)

## Process overview
Tax-code usage on sales lines within **SALES_QUOTE_TO_ORDER** (and related documents) when RC/IG rules apply.

## Actions
- Confirm valid VAT ID when required
- Pick DE_RC / DE_IG (or AT) on the line
- Download PDF and check the legal note block

## Fields
RC/IG codes force 0% output VAT and add EN+DE legal phrases on PDF/notes.

## Special situations
No VIES validation in v1. Filing/Intrastat/OSS out of scope. Wrong code not auto-corrected.

## Related
- German VAT tax codes · Business PDFs · Sales desk
'''],
            [key: 'help.docs.pdf', title: 'Business PDFs (A4)', route: '/sales', family: 'core',
             body: '''# Business PDFs (A4)

## Process overview
Document output for commercial processes (quotes, orders, POs, invoices) — supports SALES_QUOTE_TO_ORDER, SCM_PO_FULFILL, FIN_INVOICE_SETTLE artefacts. Not a SYSTEM processCode itself.

## Actions
- **Download … PDF** on quote, sales order, PO, invoice screens
- Print from the browser if needed

## Fields
A4 portrait layout; multi-line tables; totals matching on-screen amounts. Labels follow partner Communication language.

## Special situations
Not e-invoice XML, not digitally signed. Language freezes after send/confirm/issue.

## Related
- Partner communication language · Sales desk · Invoices
'''],
            [key: 'help.work.actionNow', title: 'Action - NOW', route: '/work', family: 'core',
             body: '''# Action - NOW

## Process overview
Human work queue on the Eisenhower board — **no SYSTEM processCode**. Tasks may be personal, process-linked, or agent-assigned; agentic AI:* items wait for human confirmation to **DONE**.

## Actions
- **New task** — create and return here
- **Refresh** — reload the board
- **Agentic Tasks** toggle — human open board ↔ AI:* queue
- **Task list** — full domain list for search/sort
- Open a cell/task — edit urgency, importance, deadline, assignee

## Fields
Urgency and importance (low/medium/high); status (human OPEN/IN_PROGRESS/DONE/CANCELLED; agents AI:* → human DONE); deadline; assignees.

## Special situations
Board does not invent priorities. Done/cancelled stay off the human board. Agents finish with **AI:done**; a human sets **DONE** after review.

## Related
- CEO dashboard
- AI / Agentic hub (AR_HITL_APPROVAL)
- [Onboarding tour](/tour/)
'''],
            [key: 'content.rail', title: 'Content on records', route: null, family: 'core',
             body: '''# Object Companion (content rail)

## Process overview
No SYSTEM processCode. Companion channel on records used while running desk processes — links, files, and typed BODY content without leaving the page.

## Actions
- Add **Link**, **File**, or **Content** (Note, Help, Risk, …) on the open record
- Open/remove attachments from the rail

## Fields
URL for links; file blob; content type code + body for typed notes.

## Special situations
Access follows the host record’s desk permissions. Files use the FILE channel — not a ContentType row.

## Related
- Any operational desk where the rail appears
'''],
            [key: 'app.desks', title: 'Lists & desks', route: '/help', family: 'core',
             body: '''# Lists & desks

## Process overview
No single SYSTEM process owns this topic. Desks each run their own processCode (for example SALES_QUOTE_TO_ORDER, SCM_PO_FULFILL, FIN_INVOICE_SETTLE). Open Help (**?**) on a desk for that screen’s process, actions, and fields.

## Actions
- Open a desk from the nav
- Open **Help** on that desk — Process overview, Actions, Fields
- Use the green **Process** pill on a desk — process modal for that desk’s code

## Fields
None on this pointer topic.

## Special situations
If a desk is missing from nav, check Roles **See** and company-enabled families.

## Related
- Sales desk · Buyers desk · Shop Floor Control · Delivery · AfterSales
- HR · Digital · Assets · Strategy · Development · Capability · Agents
- Finance invoices · Treasury · Action - NOW
- [Onboarding tour](/tour/)
''']
    
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
            'core.Task': '''# Aufgabe

## Prozessüberblick
Kein einzelnes SYSTEM-processCode — Aufgaben sind der gemeinsame Arbeitstyp. Board **Aktivitäten - JETZT** (`/work`).

## Aktionen
- Anlegen/bearbeiten; Status ändern statt Historie löschen
- Agenten: **AI:done**; Mensch setzt danach **DONE**

## Felder
Status, Dringlichkeit/Wichtigkeit 1–3, Fristen, Zuweisung, Content-Leiste.

## Besondere Situationen
Agenten dürfen nicht mit menschlichem DONE abschließen.

## Verwandt
- Aktivitäten - JETZT · CEO-Dashboard · Agenten-Hub
''',
            'capability.Process': '''# Prozess

## Prozessüberblick
Katalog der Arbeitsflüsse. SYSTEM-Seeds nur lesen (Bestellung, Angebot→Auftrag, Rechnung, …).

## Aktionen
- Capability → Prozesse; Desk-**Prozess**-Pille für processCode
- Admin: SYSTEM neu seeden

## Felder
Art SYSTEM/HUMAN/AGENTIC/OTHER; Status DRAFT/ACTIVE/RETIRED; Code; Schritte.

## Besondere Situationen
SYSTEM nicht editierbar; Tipps planned/stub sind ehrlich.

## Verwandt
- Prozessdiagramme · Capability-Hub
''',

            'capability.Standard': '''# Standard

## Process overview
Metric / KPI master data for the Capability **Standards** desk (`/capability/standards`). Prefer the desk for create and activate/deactivate.

## Actions
- Create/edit on Capability → Standards
- Toggle ACTIVE / INACTIVE on the detail pane

## Fields
name, description, metricCode, targetValue, unit, standardStatus.

## Special situations
Create from domain list redirects to the Standards desk.

## Related
- Capability Standards desk · Quality · CAP_PROJECT
''',
            'sales.Customer': '''# Kunde

## Prozessüberblick
Stammdaten für **SALES_QUOTE_TO_ORDER**.

## Aktionen
- Anlegen/CSV; im Vertriebs-Desk Angebot/Auftrag

## Felder
customerKind, Kontakt, taxId, Währung, Kommunikationssprache, active.

## Besondere Situationen
Ohne Kunde kein Angebot.

## Verwandt
- Vertriebs-Desk · Rechnungen · CSV-Import
''',
            'sales.CustomerQuote': '''# Kundenangebot

## Prozessüberblick
Teil von **SALES_QUOTE_TO_ORDER** — bevorzugt am Vertriebs-Desk.

## Aktionen
- Anlegen → senden → annehmen/ablehnen

## Felder
Kunde, Belegwährung, Positionen.

## Besondere Situationen
Angenommenes Angebot sperrt Bearbeiten am Desk.

## Verwandt
- Vertriebs-Desk · Verkaufsauftrag
''',
            'sales.SalesOrder': '''# Verkaufsauftrag

## Prozessüberblick
**SALES_QUOTE_TO_ORDER** — Bestätigung kann Finanz-Entwurf erzeugen (**FIN_INVOICE_SETTLE**).

## Aktionen
- Desk: Angebot annehmen → Auftrag → bestätigen

## Felder
Kunde, Währung, Positionen, Status.

## Besondere Situationen
Produkt/Service-Flags für Delivery/SCM.

## Verwandt
- Vertriebs-Desk · Rechnungen · Delivery
''',
            'procurement.Vendor': '''# Lieferant

## Prozessüberblick
Stammdaten für **SCM_PO_FULFILL**.

## Aktionen
- Anlegen/CSV; am Einkaufs-Desk für Bestellungen

## Felder
Kontakt, taxId, Währung, active.

## Besondere Situationen
Ohne Lieferant keine Bestellung.

## Verwandt
- Einkaufs-Desk · CSV-Import
''',
            'procurement.PurchaseOrder': '''# Bestellung

## Prozessüberblick
**SCM_PO_FULFILL**: anlegen → senden → bestätigen → Wareneingang (**FIN_PAYABLE**).

## Aktionen
- Einkaufs-Desk; Bearbeiten/Löschen nur **DRAFT**

## Felder
Lieferant, Positionen, Status.

## Besondere Situationen
Ausgestellt/bestätigt nur Ansehen.

## Verwandt
- Einkaufs-Desk · Treasury
'''
    ].asImmutable()

    private static final Map DOMAIN_BLURBS_ES = [
            'core.Task': '''# Tarea

## Resumen del proceso
Sin processCode SYSTEM único — tipo de trabajo compartido. Tablero **Actividades - AHORA** (`/work`).

## Acciones
- Crear/editar; cambiar estado en lugar de borrar historial
- Agentes: **AI:done**; humano confirma **DONE**

## Campos
Estado, urgencia/importancia 1–3, plazos, asignación, barra de contenido.

## Situaciones especiales
Los agentes no deben cerrar con DONE humano.

## Relacionado
- Actividades - AHORA · Panel CEO · Hub de agentes
''',
            'capability.Process': '''# Proceso

## Resumen del proceso
Catálogo de flujos. Semillas SYSTEM de solo lectura.

## Acciones
- Capability → Procesos; pastilla **Proceso** en el escritorio
- Admin: reseeding SYSTEM

## Campos
Tipo SYSTEM/HUMAN/AGENTIC/OTHER; estado; código; pasos.

## Situaciones especiales
SYSTEM no editable; tips planned/stub son honestos.

## Relacionado
- Diagramas de proceso · Hub de capacidades
''',

            'capability.Standard': '''# Standard

## Process overview
Metric / KPI master data for the Capability **Standards** desk (`/capability/standards`). Prefer the desk for create and activate/deactivate.

## Actions
- Create/edit on Capability → Standards
- Toggle ACTIVE / INACTIVE on the detail pane

## Fields
name, description, metricCode, targetValue, unit, standardStatus.

## Special situations
Create from domain list redirects to the Standards desk.

## Related
- Capability Standards desk · Quality · CAP_PROJECT
''',
            'sales.Customer': '''# Cliente

## Resumen del proceso
Maestro para **SALES_QUOTE_TO_ORDER**.

## Acciones
- Crear/CSV; ofertas/pedidos en escritorio de ventas

## Campos
customerKind, contacto, taxId, moneda, idioma de comunicación, active.

## Situaciones especiales
Sin cliente no hay oferta.

## Relacionado
- Escritorio de ventas · Facturas · Importación CSV
''',
            'sales.CustomerQuote': '''# Oferta de cliente

## Resumen del proceso
Parte de **SALES_QUOTE_TO_ORDER** — preferir escritorio de ventas.

## Acciones
- Crear → enviar → aceptar/rechazar

## Campos
Cliente, moneda del documento, líneas.

## Situaciones especiales
Oferta aceptada bloquea edición en el escritorio.

## Relacionado
- Escritorio de ventas · Pedido de venta
''',
            'sales.SalesOrder': '''# Pedido de venta

## Resumen del proceso
**SALES_QUOTE_TO_ORDER** — confirmar puede crear borrador financiero (**FIN_INVOICE_SETTLE**).

## Acciones
- Escritorio: aceptar oferta → pedido → confirmar

## Campos
Cliente, moneda, líneas, estado.

## Situaciones especiales
Flags producto/servicio para Delivery/SCM.

## Relacionado
- Escritorio de ventas · Facturas · Delivery
''',
            'procurement.Vendor': '''# Proveedor

## Resumen del proceso
Maestro para **SCM_PO_FULFILL**.

## Acciones
- Crear/CSV; usar en escritorio de compradores

## Campos
Contacto, taxId, moneda, active.

## Situaciones especiales
Sin proveedor no hay OC.

## Relacionado
- Escritorio de compradores · Importación CSV
''',
            'procurement.PurchaseOrder': '''# Pedido de compra

## Resumen del proceso
**SCM_PO_FULFILL**: crear → enviar → confirmar → recibir (**FIN_PAYABLE**).

## Acciones
- Escritorio de compradores; Editar/Eliminar solo **DRAFT**

## Campos
Proveedor, líneas, estado.

## Situaciones especiales
Emitido/confirmado solo Ver.

## Relacionado
- Escritorio de compradores · Tesorería
'''
    ].asImmutable()

    /** ADR-055 domain/master help — fixed section order. */
    private static String buildDomainMarkdown(String title, String pkg, String res, String humanRes,
                                              String mode, String modeLabel, Map fam, String specific, Map ctx, String lang) {
        StringBuilder sb = new StringBuilder()
        sb.append("# ${title}\n\n")
        if (lang == 'de') {
            sb.append("## Prozessüberblick\n\n")
            sb.append("Kein Desk-SYSTEM-processCode auf dieser Stammdaten-Oberfläche. ")
            sb.append("**${humanRes}** in **${fam.label}**")
            if (fam.desk) sb.append(" — operative Arbeit oft unter ${fam.desk}")
            sb.append(".\n\n")
            if (fam.blurb) sb.append(fam.blurb).append("\n\n")
            sb.append("## Aktionen\n\n")
            switch (mode) {
                case 'create':
                    sb.append("- Pflichtfelder ausfüllen und **speichern** (Anlegen).\n\n"); break
                case 'edit':
                    sb.append("- Datensatz **#${ctx.recordId}** bearbeiten und speichern.\n")
                    sb.append("- Content-Leiste für Links/Dateien/Notizen.\n\n"); break
                default:
                    sb.append("- Liste der ${humanRes}-Datensätze öffnen; Zeile bearbeiten oder neu anlegen.\n\n")
            }
            sb.append("## Felder\n\n")
            if (specific) {
                sb.append(specific).append("\n\n")
            } else {
                sb.append("Admin-Liste/Formular für **`${pkg}.${res}`**. Materialfelder je Ressource.\n\n")
            }
            sb.append("## Besondere Situationen\n\n")
            sb.append("- Tagesarbeit oft bequemer im Fach-**Desk**, wenn vorhanden.\n")
            sb.append("- Rechte folgen Desk-**See/Work** und Matrix.\n\n")
            sb.append("## Verwandt\n\n")
            if (fam.desk) sb.append("- Operativer Desk: ${fam.desk}\n")
            sb.append("- Familie **${fam.label}** (`${pkg}`) · Ressource `${res}` · Route `${ctx.path}`\n")
        } else if (lang == 'es') {
            sb.append("## Resumen del proceso\n\n")
            sb.append("Sin processCode SYSTEM de escritorio en esta superficie maestra. ")
            sb.append("**${humanRes}** en **${fam.label}**")
            if (fam.desk) sb.append(" — el trabajo diario suele estar en ${fam.desk}")
            sb.append(".\n\n")
            if (fam.blurb) sb.append(fam.blurb).append("\n\n")
            sb.append("## Acciones\n\n")
            switch (mode) {
                case 'create':
                    sb.append("- Completar campos obligatorios y **guardar** (crear).\n\n"); break
                case 'edit':
                    sb.append("- Editar registro **#${ctx.recordId}** y guardar.\n")
                    sb.append("- Barra de contenido para enlaces/archivos/notas.\n\n"); break
                default:
                    sb.append("- Ver la lista de ${humanRes}; abrir una fila o crear uno nuevo.\n\n")
            }
            sb.append("## Campos\n\n")
            if (specific) {
                sb.append(specific).append("\n\n")
            } else {
                sb.append("Lista/formulario Admin para **`${pkg}.${res}`**. Campos materiales según el recurso.\n\n")
            }
            sb.append("## Situaciones especiales\n\n")
            sb.append("- El trabajo diario suele ser más cómodo en el **escritorio** de la familia.\n")
            sb.append("- Los permisos siguen **See/Work** del escritorio y la matriz.\n\n")
            sb.append("## Relacionado\n\n")
            if (fam.desk) sb.append("- Escritorio operativo: ${fam.desk}\n")
            sb.append("- Familia **${fam.label}** (`${pkg}`) · Recurso `${res}` · Ruta `${ctx.path}`\n")
        } else {
            sb.append("## Process overview\n\n")
            sb.append("No desk SYSTEM processCode on this master-data surface. ")
            sb.append("**${humanRes}** in **${fam.label}**")
            if (fam.desk) sb.append(" — day-to-day work is often on ${fam.desk}")
            sb.append(".\n\n")
            if (fam.blurb) sb.append(fam.blurb).append("\n\n")
            sb.append("## Actions\n\n")
            switch (mode) {
                case 'create':
                    sb.append("- Fill required fields and **save** to create.\n\n"); break
                case 'edit':
                    sb.append("- Edit record **#${ctx.recordId}** and save.\n")
                    sb.append("- Content rail for links/files/notes.\n\n"); break
                default:
                    sb.append("- Browse the ${humanRes} list; open a row to edit or create a new one.\n\n")
            }
            sb.append("## Fields\n\n")
            if (specific) {
                sb.append(specific).append("\n\n")
            } else {
                sb.append("Admin list/form for **`${pkg}.${res}`**. Material fields vary by resource.\n\n")
            }
            sb.append("## Special situations\n\n")
            sb.append("- Day-to-day work is often easier on the family **desk** when one exists.\n")
            sb.append("- Access follows desk **See/Work** and the permission matrix.\n\n")
            sb.append("## Related\n\n")
            if (fam.desk) sb.append("- Operational desk: ${fam.desk}\n")
            sb.append("- Family **${fam.label}** (`${pkg}`) · Resource `${res}` · Route `${ctx.path}`\n")
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
        // ADR-055: onboarding tour belongs only as last Related link in topic bodies — not a standing suggested action.
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
            'grrc.desk'          : 'Governance-Desk',
            'stakeholder.desk'   : 'Stakeholder-Desk',
            'capability.hub'     : 'Capability-Hub',
            'capability.standards': 'Standards / KPIs',
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
            'grrc.desk'          : 'Escritorio de gobernanza',
            'stakeholder.desk'   : 'Escritorio de stakeholders',
            'capability.hub'     : 'Hub de capacidades',
            'capability.standards': 'Estándares / KPI',
            'agents.hub'         : 'Hub de IA / agentes',
            'finance.treasury'   : 'Tesorería',
            'finance.invoices'   : 'Facturas',
    ].asImmutable()

    private static final Map FEATURE_BODIES_DE = [
            'app.dashboard': '''# CEO-Dashboard

## Prozessüberblick
Kein einzelnes SYSTEM-processCode. Aggregiert offene Arbeit, Aktivitäten - JETZT, APQC-Sprünge und Systemstatus.

## Aktionen
- **Meine Arbeit** / **Aktivitäten - JETZT** öffnen
- APQC-Kachel → Desk der Familie
- Systemstatus prüfen

## Felder
Keine Erfassungsformulare — Karten mit Zählern und Board-Zellen.

## Besondere Situationen
Leeres Board = keine offene Arbeit im Filter. Rolle **See** begrenzt sichtbare Desks.

## Verwandt
- Aktivitäten - JETZT · Vertrieb · Einkauf · Rechnungen
- [Onboarding-Tour](/tour/)
''',
            'app.desks': '''# Listen & Desks

## Prozessüberblick
Kein einzelnes SYSTEM-processCode. Jeder Desk hat eigenen processCode. Hilfe (**?**) am Desk zeigt Prozess, Aktionen und Felder.

## Aktionen
- Desk in der Navigation öffnen
- **Hilfe** am Desk öffnen
- Grüne **Prozess**-Pille — Prozessmodal

## Felder
Keine auf diesem Hinweis-Thema.

## Besondere Situationen
Fehlt ein Desk in der Nav: Rollen-**See** und freigeschaltete Familien prüfen.

## Verwandt
- Vertrieb · Einkauf · Shopfloor · Delivery · AfterSales · HR · Digital · Assets
- Strategie · Development · Capability · Agenten · Rechnungen · Treasury · Aktivitäten - JETZT
- [Onboarding-Tour](/tour/)
''',
            'sales.desk': '''# Innendienst

## Prozessüberblick
SYSTEM-Prozess **SALES_QUOTE_TO_ORDER**: Kunde → Angebot → senden → annehmen/ablehnen → Auftrag → bestätigen. Verbindet sich mit **FIN_INVOICE_SETTLE**.

## Aktionen
- **Neu** Kunde/Angebot/Auftrag
- Zeile Ansehen/Bearbeiten/Löschen — Bearbeiten gesperrt wenn Beleg verriegelt
- Prozessschritte im Ansichts-Modal
- Grüne **Prozess**-Pille — SALES_QUOTE_TO_ORDER

## Felder
Kunde (Art, Kontakt, Währung, taxId); Angebot/Auftrag (Belegwährung, Positionen, Steuer).

## Besondere Situationen
Office darf anlegen, aber kein Geld ausgleichen. Ohne Kunde kein Angebot.

## Verwandt
- Rechnungen (FIN_INVOICE_SETTLE) · Delivery (DEL_SERVICE_ORDER)
- [Onboarding-Tour](/tour/)
''',
            'scm.desk': '''# Einkaufs-Desk

## Prozessüberblick
SYSTEM-Prozess **SCM_PO_FULFILL**: Lieferant → Bestellung → senden → bestätigen → Wareneingang. Shopfloor separat (**SCM_MAKE_TO_STOCK**).

## Aktionen
- **Neu** Lieferant/Produkt/Service/Bestellung
- Bearbeiten/Löschen nur im Status **DRAFT**
- Modal-Fuß: senden, bestätigen, Wareneingang
- Grüne **Prozess**-Pille — SCM_PO_FULFILL

## Felder
Lieferant Kontakt/Währung; Bestellkopf und Positionen.

## Besondere Situationen
Ausgestellte Bestellungen nur Ansehen. Fehlende Buchungsregeln blockieren Verbindlichkeiten.

## Verwandt
- Shopfloor (SCM_MAKE_TO_STOCK) · Treasury (FIN_PAYABLE)
- [Onboarding-Tour](/tour/)
''',
            'scm.shopfloor': '''# Shopfloor-Steuerung

## Prozessüberblick
SYSTEM-Prozess **SCM_MAKE_TO_STOCK**: Produkt/BOM → Fertigungsauftrag → freigeben → starten → abschließen.

## Aktionen
- Fertigungsauftrag neu / aus Bedarf
- Zeile wählen — Detail (Split)
- Freigeben/Start/Complete, Lose/Qualität
- Grüne **Prozess**-Pille — SCM_MAKE_TO_STOCK

## Felder
Produkt/BOM, Menge, Status, Lose, Bestand.

## Besondere Situationen
Detail erst nach Zeilenauswahl. Fehlende BOM/Bestand kann blockieren.

## Verwandt
- Einkaufs-Desk · Assets
- [Onboarding-Tour](/tour/)
''',
            'delivery.desk': '''# Delivery-Desk

## Prozessüberblick
SYSTEM-Prozess **DEL_SERVICE_ORDER**: Services/Ressourcen → Serviceauftrag → planen → zuweisen → starten → abschließen.

## Aktionen
- Neu Service/Ressource/Auftrag; planen, zuweisen, starten, abschließen
- Grüne **Prozess**-Pille — DEL_SERVICE_ORDER

## Felder
Service, Ressource, Zeitfenster, Status.

## Besondere Situationen
Ohne Ressource/Plan kein Start. Verkaufsaufträge brauchen Bestätigung upstream.

## Verwandt
- Vertriebs-Desk · AfterSales
- [Onboarding-Tour](/tour/)
''',
            'aftersales.desk': '''# AfterSales-Desk

## Prozessüberblick
SYSTEM-Prozesse **AS_INQUIRY** und **AS_RMA**. Claims können Finanz-Gutschriften erzeugen; RMAs Wareneingang.

## Aktionen
- Neu Anfrage/Garantie/Claim/RMA; Status voranbringen
- Grüne **Prozess**-Pille — AS_INQUIRY oder AS_RMA je Tab

## Felder
Kunde, Falltext, Claim-Beträge, RMA-Positionen.

## Besondere Situationen
Claim-Freigabe kann Finance-Zugriff brauchen.

## Verwandt
- Vertrieb · Delivery · Rechnungen
- [Onboarding-Tour](/tour/)
''',
            'hr.desk': '''# HR-Desk

## Prozessüberblick
SYSTEM-Prozess **HR_CANDIDATE_HIRE**: Kandidaten → Stufen → Einstellung oder Ablehnung. Dazu Personalakten, Zeit, Payroll, Ziele.

## Aktionen
- Neu je Tab; Split-Detail nach Zeilenauswahl; einstellen/ablehnen
- Grüne **Prozess**-Pille — HR_CANDIDATE_HIRE

## Felder
Kandidatenstufen; HR-Akte (sensible Felder ggf. verschlüsselt); Zeit/Payroll.

## Besondere Situationen
Einstellung braucht Users-Pfad. HR-See nur bei Bedarf.

## Verwandt
- Benutzer einladen · Rollen
- [Onboarding-Tour](/tour/)
''',
            'digital.desk': '''# Digital-Desk

## Prozessüberblick
SYSTEM-Prozesse **DIG_IT_TICKET** und **DIG_IT_CHANGE**. Systeme, CIs, Datenassets unterstützen sie.

## Aktionen
- Neu System/CI/Change/Ticket; Start/Resolve/Close
- Grüne **Prozess**-Pille — DIG_IT_TICKET oder DIG_IT_CHANGE

## Felder
CI-System-Link; Ticket-Betreff/Status; Change-Felder.

## Besondere Situationen
Interne IT-Tickets — nicht AfterSales. Detail erst nach Auswahl.

## Verwandt
- Agenten-Hub · Integrationen
- [Onboarding-Tour](/tour/)
''',
            'asset.desk': '''# Asset-Arbeitsplatz

## Prozessüberblick
SYSTEM-Prozess **AST_ASSET_LIFECYCLE**: Register für Anlagen, Fahrzeuge, Werkzeuge.

## Aktionen
- Neu im Register; Ansehen/Bearbeiten; Wartungsplan per Content-Leiste
- Grüne **Prozess**-Pille — AST_ASSET_LIFECYCLE

## Felder
Name, Kennzeichen, Ort, Owner, Status; Flags IT-verwaltet, Abschreibung, Wartung.

## Besondere Situationen
Hostname/IP nur am Digital-Desk. Automatische Abschreibung/Wartungsaufträge kommen später.

## Verwandt
- Digital-Desk · Shopfloor · Hauptbuch
- [Onboarding-Tour](/tour/)
''',
            'strategy.desk': '''# Strategie-Desk

## Prozessüberblick
SYSTEM-Prozesse **STR_STRATEGY_ITEM** und **STR_COMPETITOR_WATCH**.

## Aktionen
- Tabs + Neu; Ansehen/Bearbeiten; Content-Leiste
- Grüne **Prozess**-Pille — processCode des Tabs

## Felder
Wettbewerber: Name, Typ, Quellen, Status. Thema: Name, Typ, Beschreibung, Art, Status.

## Besondere Situationen
Leere Listen sind früh ok. Keine Geldbewegung.

## Verwandt
- Capability-Hub (CAP_PROJECT)
- [Onboarding-Tour](/tour/)
''',
            'development.desk': '''# Development-Desk

## Prozessüberblick
SYSTEM-Prozess **DEV_PROJECT_DELIVER**: NPD-/Revisionsprojekte.

## Aktionen
- Neu Projekt; Status voranbringen
- Grüne **Prozess**-Pille — DEV_PROJECT_DELIVER

## Felder
Name, Typ, Beschreibung, Status; Anhänge über Content-Leiste.

## Besondere Situationen
Portfolio-Arbeit auch unter Capability (**CAP_PROJECT**).

## Verwandt
- Capability-Hub
- [Onboarding-Tour](/tour/)
''',
            'grrc.desk': '''# Governance-Desk

## Prozessüberblick
SYSTEM-Prozesse **GRC_RISK_TREAT** (Risiken: öffnen → mindern → schließen/akzeptieren) und **GRC_COMPLIANCE_ITEM** (Compliance: Entwurf → aktiv → außer Kraft). Silver; ergänzt Stakeholder (**STK_PARTY_ENGAGE**).

## Aktionen
- Tabs Risiken / Compliance — processCode der grünen **Prozess**-Pille
- Neu Risiko oder Compliance-Eintrag; Ansehen/Bearbeiten; Risiko schließen/akzeptieren; Compliance aktivieren/außer Kraft setzen
- Grüne **Prozess**-Pille — GRC_RISK_TREAT oder GRC_COMPLIANCE_ITEM

## Felder
Risiko: Name, Schwere, Wahrscheinlichkeit, Owner, Maßnahmennotiz, Status, Beschreibung. Compliance: Name, Art, Owner, wirksam ab, Status, Beschreibung. Content-Leiste am Datensatz.

## Besondere Situationen
Erfordert **Silver** (oder höher). Leere Listen sind ok. Keine Geldbewegung.

## Verwandt
- Stakeholder-Desk (STK_PARTY_ENGAGE)
- Capability-Hub
- [Onboarding-Tour](/tour/)
''',
            'stakeholder.desk': '''# Stakeholder-Desk

## Prozessüberblick
SYSTEM-Prozess **STK_PARTY_ENGAGE**: Parteien registrieren (aktiv → beobachten → inaktiv) und Kommunikation protokollieren. Ergänzt Governance.

## Aktionen
- Tabs Parteien / Kommunikation; Neu; Ansehen/Bearbeiten; Status ACTIVE/WATCH/INACTIVE
- Grüne **Prozess**-Pille — STK_PARTY_ENGAGE

## Felder
Partei: Name, Art, Status, Kontakt, Ethiknotiz, Beschreibung. Kommunikation: Partei, Betreff, Kanal, Richtung, Zeitpunkt, Status. Content-Leiste.

## Besondere Situationen
Erfordert **Silver**. Kommunikation braucht eine bestehende Partei. Ethiknotizen sind Operator-Hinweise — kein Rechtsarchiv.

## Verwandt
- Governance-Desk (GRC_RISK_TREAT / GRC_COMPLIANCE_ITEM)
- [Onboarding-Tour](/tour/)
''',

            'capability.standards': '''# Standards / KPIs

## Prozessüberblick
Capability-**Standards**-Register (APQC 13.7 dünn) für Kennzahlen und Zielwerte. Ergänzt **Qualität** und **CAP_PROJECT** — kein Geldprozess.

## Aktionen
- **Neu** — Standard/KPI anlegen
- Zeile Ansehen/Bearbeiten; Detail speichert Felder
- **Aktivieren / Deaktivieren** — ACTIVE ↔ INACTIVE
- Grüne **Prozess**-Pille — CAP_PROJECT
- Content-Leiste am Datensatz

## Felder
Name, Beschreibung, metricCode, targetValue, unit, standardStatus.

## Besondere Situationen
Leere Liste ist ok. Deaktivierte Standards bleiben erhalten.

## Verwandt
- Capability-Hub · Qualität
- [Onboarding-Tour](/tour/)
''',
            'capability.hub': '''# Capability-Hub

## Prozessüberblick
SYSTEM-Prozess **CAP_PROJECT** plus Prozesskatalog. Tabs: Portfolios, Programme, Projekte, Prozesse, Qualität, Standards, Changes, Wissen.

## Aktionen
- Tabs wechseln; Projekt starten/abschließen; SYSTEM-Prozess öffnen (Mermaid)
- Grüne **Prozess**-Pille — CAP_PROJECT

## Felder
Projektfelder; Prozessart/Status; Qualitäts-/Change-Datensätze.

## Besondere Situationen
SYSTEM nur lesen; Reseed unter Einstellungen/Capability.

## Verwandt
- Prozessdiagramme · Development-Desk
- [Onboarding-Tour](/tour/)
''',
            'agents.hub': '''# KI- / Agenten-Hub

## Prozessüberblick
SYSTEM-Prozess **AR_HITL_APPROVAL**. Agents, Hives, Memory. Closed Beta: nur Agent Basics.

## Aktionen
- Agents seeden/aktivieren; HITL freigeben/ablehnen
- Grüne **Prozess**-Pille — AR_HITL_APPROVAL

## Felder
Agent-Status; Hive; HITL-Vorschlag; Memory.

## Besondere Situationen
Agenten zahlen/settlen nicht standardmäßig. Detail erst nach Auswahl.

## Verwandt
- Hermes-Tokens · Integrationen · Digital-Desk
- [Onboarding-Tour](/tour/)
''',
            'finance.invoices': '''# Rechnungen

## Prozessüberblick
SYSTEM-Prozess **FIN_INVOICE_SETTLE**: Entwurf → ausstellen → ausgleichen. Upstream oft **SALES_QUOTE_TO_ORDER**.

## Aktionen
- Entwurf anlegen; **Ausstellen** / **Ausgleichen**; Zeile für Split-Detail
- Grüne **Prozess**-Pille — FIN_INVOICE_SETTLE

## Felder
Beträge in Primärschiene; FX-Hinweise nur Anzeige; Status; Zahlungsoptionen.

## Besondere Situationen
DEMO nur Mock-Settle. Fehlende Buchungsregeln blockieren (`POSTING_RULES_*`).

## Verwandt
- Vertriebs-Desk · Treasury · Buchungsregeln · Live-Zahlungen
- [Onboarding-Tour](/tour/)
''',
            'finance.treasury': '''# Treasury

## Prozessüberblick
**FIN_PAYABLE** und Treasury-Positionen. Tabs: Positionen, Verbindlichkeiten, Budgets, Steuer, Payroll-Stub. Fiat-Bank + Bitcoin-Wallet, ein Kontenplan.

## Aktionen
- Abschnitt-Tabs; Verbindlichkeiten/Positionen bearbeiten
- Grüne **Prozess**-Pille — FIN_PAYABLE

## Felder
Positionswerte nach Primärschiene; Verbindlichkeitsstatus; Tab-Felder.

## Besondere Situationen
Live-Bitcoin unter Live-Zahlungen (BTCPay Server · experimental). Buchungsregeln können Vendor-Pay blockieren.

## Verwandt
- Rechnungen · Hauptbuch · Einkauf · Primärschiene
- [Onboarding-Tour](/tour/)
''',
            'finance.primaryRail': '''# Primärschiene & BTC-FX

## Prozessüberblick
Administrative Bücherwährung — kein Desk-SYSTEM-processCode. Ausgleich läuft über **FIN_INVOICE_SETTLE**.

## Aktionen
- Primärschiene im Setup wählen; FX-Hinweise lesen; Rechnung ausgleichen für Zahlungskurs

## Felder
Schiene EUR/USD/BTC; Anzeige-Fiat; FX-Hinweis und Badge veraltet.

## Besondere Situationen
Hinweise sind nicht die Bücher. Kein automatisches MTM. Schiene wechseln nach Setup = Advanced.

## Verwandt
- Rechnungen · Treasury · Live-Zahlungen (BTCPay Server · experimental)
''',
            'finance.postingRules': '''# Buchungsregeln

## Prozessüberblick
Administrativer Firmen-Regelsatz — kein eigenes Desk-processCode. Greift bei **FIN_INVOICE_SETTLE**, Vendor-Pay, Payroll.

## Aktionen
- Regeln/Modus sehen (Admin+CEO); ändern/Reseed (nur Admin)
- Speichern/Reseed → Änderungslog + Digital-IT-Ticket `[Finance policy]`

## Felder
Modus Cash vs Accrual; Kontenzuordnungen je Ereignis.

## Besondere Situationen
Fehlerhafte Regeln **blockieren** settle/pay (`POSTING_RULES_*`). Reseed löscht keine Journale.

## Verwandt
- Hauptbuch · Rechnungen · Treasury
''',
            'system.settings': '''# Einstellungen

## Prozessüberblick
Administrative Oberfläche — **kein Desk-SYSTEM-processCode**. Sprache, Währungen, Steuercodes, Demo/Setup.

## Aktionen
- Sprache & Region setzen; Admin: Währungen/Steuern/Setup zurücksetzen
- Integrationen unter **System → Integrationen**

## Felder
Locale; Währungskurzliste; Steuercodes; Setup-Status.

## Besondere Situationen
Bildschirmsprache ≠ Partner-PDF-Sprache. Setup-Reset ist destruktiv (Demos).

## Verwandt
- Verwendete Währungen · USt-Steuercodes · Partnersprache · Integrationen
''',
            'help.work.actionNow': '''# Aktivitäten - JETZT

## Prozessüberblick
Menschliche Arbeitswarteschlange (Eisenhower) — **kein SYSTEM-processCode**. Agentische AI:*-Aufgaben warten auf menschliche **DONE**-Bestätigung.

## Aktionen
- Neue Aufgabe · Aktualisieren · Schalter Agentische Aufgaben · Aufgabenliste
- Zelle/Aufgabe öffnen — Dringlichkeit, Wichtigkeit, Frist, Zuweisung

## Felder
Dringlichkeit/Wichtigkeit; Status (OPEN/IN_PROGRESS/DONE/CANCELLED bzw. AI:*); Frist; Zuweisung.

## Besondere Situationen
Board erfindet keine Prioritäten. Agenten enden mit AI:done; Mensch setzt DONE.

## Verwandt
- CEO-Dashboard · Agenten-Hub
- [Onboarding-Tour](/tour/)
''',
            'content.rail': '''# Object Companion (Content-Leiste)

## Prozessüberblick
Kein SYSTEM-processCode. Begleitkanal an Datensätzen während Desk-Prozessen.

## Aktionen
- Link, Datei oder Inhalt (Notiz, Hilfe, Risiko, …) hinzufügen

## Felder
URL; Datei; Content-Typ + Text.

## Besondere Situationen
Zugriff folgt dem Host-Datensatz. Dateien = FILE-Kanal, kein ContentType.

## Verwandt
- Beliebiger operativer Desk mit Leiste
''',
            'help.partners.locale': '''# Partnersprache (Kommunikation)

## Prozessüberblick
Partnerfeld für Belege in SALES_QUOTE_TO_ORDER / SCM_PO_FULFILL / FIN_INVOICE_SETTLE — nicht die Bildschirmsprache.

## Aktionen
- Kommunikationssprache am Kunden/Lieferanten setzen; PDF/E-Mail folgen

## Felder
Kommunikationssprache; leer → Englisch (en_US).

## Besondere Situationen
Nach Senden/Bestätigen/Ausstellen friert die Sprache ein. Katalogtexte noch nicht zweisprachig.

## Verwandt
- Geschäfts-PDFs · Einstellungen · Vertriebs-Desk
''',
            'help.tax.ust': '''# Deutsche USt-Steuercodes

## Prozessüberblick
Administratives Steuercode-Paket (Einstellungen) für Positionen in Verkaufs-/Einkaufsprozessen — kein eigenes Desk-processCode.

## Aktionen
- Codes unter Einstellungen aktivieren; auf Positionen wählen

## Felder
DE_USt_STD 19%, RED 7%, 0, RC, IG, EXPORT (AT analog). Regime DE/AT USt vs Kleinunternehmer/Keine.

## Besondere Situationen
Keine Rechtsberatung; kein ELSTER. Falscher Code = Bedienfehler.

## Verwandt
- Reverse Charge & IG · Geschäfts-PDFs
''',
            'help.tax.reverseCharge': '''# Reverse Charge & IG

## Prozessüberblick
Steuercodes auf Verkaufspositionen in **SALES_QUOTE_TO_ORDER**, wenn RC/IG greift.

## Aktionen
- USt-IdNr. prüfen; DE_RC/DE_IG (oder AT) wählen; PDF-Hinweis prüfen

## Felder
RC/IG → 0% Ausgangs-USt + Rechtshinweis EN+DE.

## Besondere Situationen
Kein VIES in v1; kein Intrastat/OSS; keine Autokorrektur.

## Verwandt
- Deutsche USt-Steuercodes · Geschäfts-PDFs · Vertrieb
''',
            'help.roles.overview': '''# Rollen für ein kleines Team

## Prozessüberblick
Administratives Zugangsmodell — kein Desk-SYSTEM-processCode. Rollen steuern See/Work auf Desks.

## Aktionen
- System → Rollen (Vorgaben) · System → Benutzer (zuweisen/einladen)

## Felder
Rollenprofil; Desk-Verben; Systemaktionen (Users, Import, Geld ausgleichen, …).

## Besondere Situationen
Deny gewinnt. Kein ROLE_AI über Benutzer.

## Verwandt
- Welche Rolle · Matrix · Einladen · Sicherheit
''',
            'help.roles.presets': '''# Welche Rolle wählen

## Prozessüberblick
Administrative Rollenwahl — kein Desk-SYSTEM-processCode. Vorgaben unter System → Rollen.

## Aktionen
- Profil beim Einladen wählen; Vorgaben bei Bedarf anpassen

## Felder
Profilname und typische Desk-Rechte.

## Besondere Situationen
Eine Person → ein Profil. CEO legt keinen Admin an.

## Verwandt
- Rollen-Überblick · Matrix · Demo-Rollen
''',
            'help.roles.matrix': '''# Berechtigungsmatrix (Erweitert)

## Prozessüberblick
Administrative Wahrheit für Desk- und Systemverben — kein Desk-SYSTEM-processCode.

## Aktionen
- Zellen sehen/feinjustieren (Admin/CEO); Apply-to-all nur mit Bestätigung

## Felder
See/Create/Change/Delete/Work; Systemaktionen inkl. Geld ausgleichen.

## Besondere Situationen
Deny gewinnt; letzten Admin schützen; API MATRIX_*_FORBIDDEN.

## Verwandt
- Rollen-Überblick · Sicherheit
''',
            'help.users.invite': '''# Teammitglied einladen

## Prozessüberblick
Administrative Benutzeranlage — kein Desk-SYSTEM-processCode.

## Aktionen
- System → Benutzer: Profil, Name, Temp-Passwort; Authenticator; optional Alby

## Felder
Benutzername; Temp-Passwort; Profil; Spalte Erweiterung.

## Besondere Situationen
CEO vergibt kein Admin. Lieber deaktivieren als löschen. Nie nsec einfügen.

## Verwandt
- Alby-Login · Rollen · Sicherheit
''',
            'help.security.nostrLogin': '''# Mit Alby anmelden (Nostr)

## Prozessüberblick
Administrativer NIP-07-Login — kein Desk-SYSTEM-processCode. Firmen-Relay unter Integrationen ist getrennt.

## Aktionen
- Passwort-Login → Sicherheit → Browser-Erweiterung verknüpfen → später Extension-Login

## Felder
Verknüpft Ja/Nein; Users-Spalte Erweiterung.

## Besondere Situationen
Ohne Verknüpfung schlägt Extension-Login fehl. Nie nsec. Eine Alby-ID ↔ ein Benutzer.

## Verwandt
- Einladen · Nostr-Integration
''',
            'help.users.danger': '''# Sicherheit: Admin, CEO & Deaktivieren

## Prozessüberblick
Administrative Sicherheitsregeln — kein Desk-SYSTEM-processCode.

## Aktionen
- Admin/CEO getrennt halten; Deaktivieren bevorzugen; Apply-to-all bestätigen

## Felder
N/A — Verhaltensregeln.

## Besondere Situationen
Letzten Admin nicht deaktivieren. Nur Settle-Profile gleichen Rechnungen aus.

## Verwandt
- Einladen · Matrix
''',
            'help.users.privacy': '''# Datenschutz, HR & Nur lesen

## Prozessüberblick
Administrative Datenschutzhinweise — relevant vor **See** auf Human Capital (**HR_CANDIDATE_HIRE**).

## Aktionen
- HR-See nur bei Bedarf; Read only für Zuschauer

## Felder
N/A.

## Besondere Situationen
Inhalte folgen Host-Desk-Rechten. Keine destruktiven Admin-Übungen auf Live-Büchern.

## Verwandt
- HR-Desk · Rollenwahl
''',
            'help.roles.demo': '''# Rollen üben (Demo / Training)

## Prozessüberblick
Administrative Übung auf Demo-Mandanten — kein Desk-SYSTEM-processCode.

## Aktionen
- Read only / Office / Ops / Finance / CEO je Ziel; Wegwerf-User deaktivieren

## Felder
N/A.

## Besondere Situationen
Zuerst Demo, dann Live spiegeln.

## Verwandt
- Welche Rolle · Rollen-Überblick
''',
            'app.search': '''# Globale Suche (obere Leiste)

## Prozessüberblick
Kein SYSTEM-processCode. Suche findet Datensätze in Unternehmen mit **See**, damit Sie den richtigen Desk oder Beleg öffnen.

## Aktionen
- In der **Suchleiste oben** tippen — Kunden, Lieferanten, Angebote, Aufträge, Rechnungen, Katalog, Bestellungen, Aufgaben
- **Ctrl+K / ⌘K** — zu Seiten/Desks springen (nur Navigation)
- Treffer öffnen — Deep-Link in Desk oder Domänenansicht

## Felder
Suchfeld braucht mindestens **2** Zeichen (Debounce ~300ms). Treffer zeigen Typ, Titel, Status und Link.

## Besondere Situationen
Ergebnisse bleiben im **aktiven Mandanten**. Typen ohne **See** entfallen. Listenfilter `q` auf einem Desk ist keine globale Suche.

## Verwandt
- CEO-Dashboard
- Meine Arbeit / Aktivitäten - JETZT
''',
            'setup.wizard': '''# Konfigurationsassistent

## Prozessüberblick
Administratives Erst-Setup — kein Desk-SYSTEM-processCode. Ablauf: Tour → Firma → Standort → Banking → Bitcoin → Vorlage → Fertig (Standard **Iron**).

## Aktionen
- Jeden Assistentenschritt abschließen und weiter
- **Bitcoin-Integrationsstufe** wählen (Nicht jetzt / Einzeladresse / BTCPay Server (experimental))
- Fertigstellen, um Desks für die neue Firma freizuschalten

## Felder
Firma/Rechtsträger, Standort, Banking, Bitcoin-Stufe und Vorlagen-Tier. Primärschiene (Bücherwährung) ist getrennt von der Bitcoin-Empfangsstufe.

## Besondere Situationen
Admin kann Setup unter **Einstellungen** für Demos zurücksetzen. DEMO-Firmen bleiben auf Mock-Settle. Unvollständiger Assistent blockiert normalen Desk-Betrieb.

## Verwandt
- Integrationen · Bitcoin-Integrationsstufe
- Einstellungen
- [Onboarding-Tour](/tour/)
''',
            'training.btc.basic': '''# Bitcoin-Grundkenntnisse (Stufe-1-Hinweis)

## Prozessüberblick
Administrative Checkliste vor dem Bitcoin-Empfang — kein Desk-SYSTEM-processCode. Entspricht Integrationsstufe **1 — Nicht jetzt**.

## Aktionen
- Literacy-Checkliste zu diesem Thema lesen
- Bitcoin-Stufe unter **System → Integrationen** (oder Setup-Schritt Bitcoin) setzen

## Felder
Keine Formularfelder auf diesem Hilfethema. Stufenwahl liegt unter Integrationen.

## Besondere Situationen
steakOS hält **keine** Schlüssel. Stufe 1 verschiebt Live-Empfang; Bücher können trotzdem BTC-Primärschiene mit **Mock**-Settle nutzen. Für regelmäßige Kundenzahlungen **BTCPay Server (experimental)** bevorzugen.

## Verwandt
- Bitcoin-Integrationsstufe · Live-Zahlungen (BTCPay Server · experimental)
- Erweiterte Bitcoin-Sicherheit
''',
            'training.btc.security-advanced': '''# Erweiterte Bitcoin-Sicherheit (Stufe-2-Hinweis)

## Prozessüberblick
Sicherheits-Checkliste für Bitcoin-Stufe **2 — Einzeladresse** (gelegentliche Zahlungen). Administrativ — kein Desk-SYSTEM-processCode.

## Aktionen
- Backup-/Phishing-Checkliste befolgen
- Stufe 2 unter **System → Integrationen** verwalten
- Upgrade auf **BTCPay Server (experimental)** für Lightning / regelmäßigen Empfang planen

## Felder
Eine Software-Wallet-Adresse unter Integrationen — niemals Seeds in steakOS einfügen.

## Besondere Situationen
Adress-Wiederverwendung und schwache Backups können zu dauerhaftem Verlust führen. steakOS beobachtet die Chain in der Beta nicht — Settle ist **manuell als bezahlt markieren**. CEO-Schulungsbestätigung nötig bei Upgrade auf Stufe 2/3.

## Verwandt
- Bitcoin-Integrationsstufe · Live-Zahlungen (BTCPay Server · experimental)
- Bitcoin-Grundkenntnisse
''',
            'system.integrations': '''# Integrationen-Hub

## Prozessüberblick
Administrative Fläche für Betreiber-Integrationen (Nostr, Bitcoin-Empfangsstufe, Hermes-Tokens) — **kein Desk-SYSTEM-processCode**. Live-Zahlungsbetrieb danach unter Finance → Payments (Stufe 3).

## Aktionen
- **Nostr**-Relay-Stufe konfigurieren, installieren/starten, prüfen
- **Bitcoin**-Empfangsstufe setzen (Nicht jetzt · Einzeladresse · BTCPay Server (experimental))
- Hermes-Agent-API-Tokens **erzeugen / widerrufen**
- Verbindungen prüfen, bevor Live aktiviert wird

## Felder
Relay-URL / Stufe; Bitcoin-Stufe; BTCPay-Schlüssel unter Finance → Payments; Hermes-Token `sos_…` (einmalig sichtbar).

## Besondere Situationen
CEO muss Schulung bestätigen, wenn Admin Bitcoin auf Stufe 2 oder 3 anhebt. DEMO publiziert kein Nostr und bleibt auf Mock-Settle. Niemals `nsec` einfügen. Agent Basics nur in Closed Beta.

## Verwandt
- Live-Zahlungen (BTCPay Server · experimental) · Bitcoin-Integrationsstufe · Nostr-Integration
- Hermes-Agent (API-Tokens) · KI- / Agenten-Hub
''',
            'integrations.bitcoin': '''# Bitcoin-Integrationsstufe

## Prozessüberblick
Administrative Steuerung, wie die Firma Bitcoin **empfängt** — getrennt von der **Primärschiene**. Kein Desk-SYSTEM-processCode; Settle läuft über **FIN_INVOICE_SETTLE** auf Rechnungen, sobald Zahlung vorliegt.

## Aktionen
- Stufe im Setup-Schritt **Bitcoin** oder unter **System → Integrationen** setzen
- Für Stufe 3: **Finance → Payments** öffnen, prüfen, Live aktivieren

## Felder
| Stufe | UI | Verhalten |
|-------|-----|-----------|
| DEFERRED | 1 — Nicht jetzt | Kein Live-Empfang; Mock-Settle OK |
| SINGLE_ADDRESS | 2 — Einzeladresse | Eine Adresse + QR; manuell als bezahlt |
| BTCPAY | 3 — BTCPay Server (experimental) | Betreiber-BTCPay Server (experimental) (Lightning + On-Chain) |

## Besondere Situationen
Admin setzt Stufe; CEO-Schulungsbestätigung bei Upgrade auf 2/3; Downgrade von Stufe 3 nur Admin. Lightning-Marketing nur bei Stufe 3.

## Verwandt
- Live-Zahlungen (BTCPay Server · experimental) · Rechnungen (FIN_INVOICE_SETTLE)
- Bitcoin-Grundkenntnisse / Erweiterte Sicherheit
''',
            'integrations.nostr': '''# Nostr-Integration (OI-NOSTR01)

## Prozessüberblick
Administrative Firmen-Relay- / Audit-Stufe für privates lokales Nostr — kein Desk-SYSTEM-processCode. Per-User-Alby-Login liegt unter **Sicherheit**, nicht hier.

## Aktionen
- Stufe **0 / 1 / 2** unter Integrationen oder Setup-Schritt Nostr setzen
- Lokales Relay **installieren / starten** (Projekt `var/nostr/`, Loopback)
- **Prüfen**, bis erreichbar
- Personen verknüpfen Alby unter **Sicherheit → Browser-Erweiterung verknüpfen**

## Felder
Firmen-Relay-Stufe und URL; optionale Bot-Metadaten. Niemals `nsec` einfügen.

## Besondere Situationen
DEMO publiziert nicht. Ohne Bot-Key bleibt Publish ehrlich (`stubbed`). Stufe-2-Giftwrap-Notify ist noch residual.

## Verwandt
- Mit Alby anmelden (Nostr)
- Integrationen-Hub
''',
            'currency.catalog': '''# Verwendete Währungen

## Prozessüberblick
Administrative Einstellungsfläche — kein Desk-SYSTEM-processCode. Freigeschaltete Codes speisen Dropdowns auf Belegen, die später SALES_QUOTE_TO_ORDER / SCM_PO_FULFILL / FIN_INVOICE_SETTLE durchlaufen.

## Aktionen
- Admin: Codes unter **Einstellungen → Verwendete Währungen** aktivieren/deaktivieren
- Operatoren: freigeschaltete Codes auf Kunden, Lieferanten, Katalog und Belegen wählen

## Felder
Gemeinsame ISO-Liste; Firmen-Kurzsatz **Verwendete Währungen**; Firmenwährung in der oberen Leiste; bevorzugte Partnerwährung; Preiswährung am Katalogartikel.

## Besondere Situationen
Neue Schreibvorgänge brauchen einen freigeschalteten Code; Legacy-Waisen bleiben lesbar. Soft-Mismatch-Warnungen blockieren Speichern nicht.

## Verwandt
- Vertrieb · Einkauf · Rechnungen
- Primärschiene & BTC-FX
''',
            'finance.payments.live': '''# Live-Zahlungen (BTCPay Server · experimental)

## Prozessüberblick
Administrative LIVE-Empfangskonfiguration für Bitcoin-Stufe 3. Geldbewegung auf Rechnungen folgt weiterhin **FIN_INVOICE_SETTLE**; dieser Screen verdrahtet BTCPay, damit Settle live statt Mock sein kann.

## Aktionen
- Stufe auf **BTCPay Server (experimental)** unter Integrationen setzen
- Basis-URL, Store-ID, API-Key, Webhook-Secret eintragen
- **Prüfen**, dann **Live** aktivieren
- Rechnung ausstellen → Zahlung eingehend → in BTCPay bezahlen → Webhook oder Abgleich

## Felder
BTCPay-Verbindungsfelder oben; Live-Schalter. Fiat/SEPA-Settle bleibt separat verfügbar.

## Besondere Situationen
DEMO-Firmen erzwungen auf Mock-Settle. steakOS verwahrt keine Schlüssel. Upgrade von Stufe 2 behält Gelegenheitsadresse, bis BTCPay live ist.

## Verwandt
- Rechnungen (FIN_INVOICE_SETTLE)
- Bitcoin-Integrationsstufe · Treasury
''',
            'finance.ledger': '''# Hauptbuch

## Prozessüberblick
SYSTEM-orientierter Finance-Kern **FIN_LEDGER**: Kontenplan seeden, ausgeglichene Journale buchen, Saldenliste, Periodenabschluss-Marker. Nicht der Rechnungs-Desk — Buchungen entstehen oft aus **FIN_INVOICE_SETTLE** / Verbindlichkeiten.

## Aktionen
- Kontenplan seeden, wenn leer
- Ausgeglichene Journale über Ledger-Werkzeuge buchen
- Saldenliste / Periodenabschluss-Marker ausführen

## Felder
Kontencodes (eindeutig je Mandant); Journalzeilen Soll/Haben; Periodenmarker.

## Besondere Situationen
Keine Salden von Hand erfinden — nur über den Ledger-Service. Schlechte Buchungsregeln blockieren Settle/Pay upstream.

## Verwandt
- Rechnungen (FIN_INVOICE_SETTLE) · Buchungsregeln · Treasury
''',
            'process.chart': '''# Prozessdiagramme

## Prozessüberblick
Lernhilfe für **SYSTEM**-Katalogprozesse (**CAP_PROJECT**-Familie / Prozesskatalog) — kein Process Mining. Deep-Link-Beispiel: `/capability/processes?code=FIN_INVOICE_SETTLE`.

## Aktionen
- **Capability → Prozesse** öffnen (Standard SYSTEM)
- Prozess wählen (oder How-it-works / Prozess-Pille am Desk)
- Hover oder Tastaturfokus auf Schritt für Was / Wer / Wo + Fallstrick; Tippen pinnt auf Touch; Esc schließt

## Felder
Prozesscode, Art (SYSTEM/HUMAN/AGENTIC/OTHER), Status (DRAFT/ACTIVE/RETIRED), Mermaid-Diagramm, Schritt-Tipps.

## Besondere Situationen
Tipps mit **planned / stub** sind ehrlich über unfertige Tiefe. SYSTEM-Seeds sind schreibgeschützt; Reseed aus Einstellungen oder Capability.

## Verwandt
- Capability-Hub
- Prozess-Pille am jeweiligen Desk für dessen processCode
''',
            'agents.activate': '''# Hermes-Agent (API-Tokens)

## Prozessüberblick
Administrativer Weg, einen betreibereigenen Agent-Host anzubinden — gehört zu **AR_HITL_APPROVAL** am Agenten-Hub. Kein eigenes Desk-SYSTEM-processCode auf Integrationen selbst.

## Aktionen
- **System → Integrationen → Hermes-Agenten** — `sos_…`-Token erzeugen (einmalig sichtbar)
- In den Host mit steakOS-baseUrl einfügen
- Smoke: `GET /api/agent/me` und `/api/agent/l0/summary` mit Bearer
- Optional: Agent schlägt vor → **Agenten → HITL** → Mensch genehmigt/ablehnt

## Felder
Opakes API-Token (einmalig); Basis-URL am Host. Keine Browser-Session für den Agenten.

## Besondere Situationen
Nur Agent Basics. Tokens widerrufen bei kompromittiertem Host. Agenten standardmäßig keine Settle/Pay-Rechte geben.

## Verwandt
- KI- / Agenten-Hub (AR_HITL_APPROVAL)
- Integrationen-Hub
''',
            'system.import': '''# Daten & Import

## Prozessüberblick
Administrativer CSV-Import — kein Desk-SYSTEM-processCode. Packs speisen Stammdaten für spätere Sales-/Buyers-/Finance-Prozesse.

## Aktionen
- Pack wählen (**Customers**, **Vendors**, **COA**)
- Mapping prüfen, dann committen
- Opening-Balance-Pack ist nur Vorschau

## Felder
CSV-Spalten auf Stammdatenfelder je Pack gemappt.

## Besondere Situationen
Import-Berechtigung nötig (typisch Admin/CEO). Schlechte Zeilen scheitern in der Vorschau — Datei vor Commit korrigieren.

## Verwandt
- Vertrieb · Einkauf · Hauptbuch
''',
            'system.catalogs': '''# Admin-Kataloge

## Prozessüberblick
Administrative Referenzdatenlisten — kein Desk-SYSTEM-processCode. Power-User-Kataloge, keine täglichen Betriebs-Desks.

## Aktionen
- Referenz- und Content-Type-Domänen nach Berechtigung browsen/bearbeiten
- Für den Tagesprozess die Familien-Desks bevorzugen

## Felder
Variiert je Katalogressource (Länder, Währungen, Content-Typen, Legacy-Config).

## Besondere Situationen
Katalog-Berechtigung erforderlich. Änderungen an Referenzdaten wirken auf Dropdowns an Prozess-Desks.

## Verwandt
- Einstellungen · Capability-Prozesskatalog
''',
            'app.about': '''# Über steakOS

## Prozessüberblick
Produkt-Metadatenbildschirm — kein SYSTEM-processCode. Zeigt Linie, SemVer, Vorlagen-Tier und Meilensteine.

## Aktionen
- Versions-/Tier-Informationen lesen
- Bei Bedarf Sicherheitshinweise zur Meldung befolgen

## Felder
Nichts editierbar für Prozessarbeit.

## Besondere Situationen
Sicherheitsprobleme privat an **security@steakos.org** — keine öffentlichen Exploit-Posts.

## Verwandt
- Sicherheitsproblem melden
''',
            'security.disclose': '''# Sicherheitsproblem melden

## Prozessüberblick
Administrative Sicherheitsaufnahme — kein Desk-SYSTEM-processCode. Privater E-Mail-Prozess für steakOS-Kern und dokumentierte Install-Konfigurationen.

## Aktionen
- Privat an **security@steakos.org** schreiben
- Keine Exploit-Details in öffentlichen Issues posten

## Felder
N/A auf dem Bildschirm; Produktversion und Repro in der E-Mail angeben.

## Besondere Situationen
Ack-Ziel ≤ 3 Werktage; Triage ≤ 10. Keine CRA-Zertifizierung. Dritt-Hosts BTCPay/Hermes außer Scope, außer steakOS-Glue leakt Daten.

## Verwandt
- Über steakOS
''',
            'app.tour': '''# Onboarding-Tour

## Prozessüberblick
Produkt-Lernfläche unter `/tour/` — kein Desk-SYSTEM-processCode. Vorlagenwahl hier wird für den Setup-Assistenten gemerkt.

## Aktionen
- **`/tour/index.html`** öffnen
- Tour-Module absolvieren; bei Aufforderung zum Setup weiter

## Felder
Tour-Fortschritt / Vorlagenpräferenz, wie die Tour-App speichert.

## Besondere Situationen
Tour nach dem ersten Lauf optional; Desks bleiben je nach Setup-Abschluss und Rollen verfügbar.

## Verwandt
- Konfigurationsassistent
- CEO-Dashboard
''',
            'help.docs.pdf': '''# Geschäfts-PDFs (A4)

## Prozessüberblick
Dokumentausgabe für kommerzielle Prozesse (Angebote, Aufträge, Bestellungen, Rechnungen) — unterstützt Artefakte von SALES_QUOTE_TO_ORDER, SCM_PO_FULFILL, FIN_INVOICE_SETTLE. Kein eigenes SYSTEM-processCode.

## Aktionen
- **… PDF herunterladen** auf Angebot-, Auftrags-, Bestell- und Rechnungsbildschirmen
- Bei Bedarf aus dem Browser drucken

## Felder
A4-Hochformat; mehrzeilige Tabellen; Summen wie auf dem Bildschirm. Beschriftungen folgen der Partner-Kommunikationssprache.

## Besondere Situationen
Kein E-Invoice-XML, keine digitale Signatur. Sprache friert nach Senden/Bestätigen/Ausstellen ein.

## Verwandt
- Partnersprache · Vertrieb · Rechnungen
'''
    ].asImmutable()

    private static final Map FEATURE_BODIES_ES = [
            'app.dashboard': '''# Panel CEO

## Resumen del proceso
Sin processCode SYSTEM único. Agrega trabajo abierto, Actividades - AHORA, saltos APQC y salud del sistema.

## Acciones
- Abrir **Mi trabajo** / **Actividades - AHORA**
- Baldosa APQC → escritorio de la familia
- Revisar señales de salud

## Campos
Sin formularios — tarjetas con contadores y celdas del tablero.

## Situaciones especiales
Tablero vacío = sin trabajo abierto en el filtro. **See** limita escritorios visibles.

## Relacionado
- Actividades - AHORA · Ventas · Compradores · Facturas
- [Tour de incorporación](/tour/)
''',
            'app.desks': '''# Listas y escritorios

## Resumen del proceso
Sin processCode SYSTEM único. Cada escritorio tiene su código. Ayuda (**?**) muestra proceso, acciones y campos.

## Acciones
- Abrir un escritorio · abrir **Ayuda** · pastilla verde **Proceso**

## Campos
Ninguno en este tema puntero.

## Situaciones especiales
Si falta un escritorio en la nav: revisar **See** y familias habilitadas.

## Relacionado
- Ventas · Compradores · Taller · Entrega · Posventa · RR. HH. · Digital · Activos
- Estrategia · Desarrollo · Capability · Agentes · Facturas · Tesorería · Actividades - AHORA
- [Tour de incorporación](/tour/)
''',
            'sales.desk': '''# Escritorio de ventas

## Resumen del proceso
Proceso SYSTEM **SALES_QUOTE_TO_ORDER**: cliente → oferta → enviar → aceptar/rechazar → pedido → confirmar. Conecta con **FIN_INVOICE_SETTLE**.

## Acciones
- **Nuevo** cliente/oferta/pedido
- Ver/Editar/Eliminar — Editar bloqueado si el documento está cerrado
- Pasos de proceso en el modal de vista
- Pastilla verde **Proceso** — SALES_QUOTE_TO_ORDER

## Campos
Cliente (tipo, contacto, moneda, taxId); oferta/pedido (moneda del documento, líneas, impuesto).

## Situaciones especiales
Office puede crear pero no liquidar dinero. Sin cliente no hay oferta.

## Relacionado
- Facturas (FIN_INVOICE_SETTLE) · Entrega (DEL_SERVICE_ORDER)
- [Tour de incorporación](/tour/)
''',
            'scm.desk': '''# Escritorio de compradores

## Resumen del proceso
Proceso SYSTEM **SCM_PO_FULFILL**: proveedor → OC → enviar → confirmar → recibir. Taller aparte (**SCM_MAKE_TO_STOCK**).

## Acciones
- **Nuevo** proveedor/producto/servicio/OC
- Editar/Eliminar solo en **DRAFT**
- Pie del modal: enviar, confirmar, recibir
- Pastilla verde **Proceso** — SCM_PO_FULFILL

## Campos
Proveedor contacto/moneda; cabecera y líneas de OC.

## Situaciones especiales
OC emitidas solo Ver. Reglas de asiento rotas bloquean payables.

## Relacionado
- Taller (SCM_MAKE_TO_STOCK) · Tesorería (FIN_PAYABLE)
- [Tour de incorporación](/tour/)
''',
            'scm.shopfloor': '''# Control de taller

## Resumen del proceso
Proceso SYSTEM **SCM_MAKE_TO_STOCK**: producto/BOM → orden de producción → liberar → iniciar → completar.

## Acciones
- Nueva orden; seleccionar fila (detalle partido); liberar/iniciar/completar
- Pastilla verde **Proceso** — SCM_MAKE_TO_STOCK

## Campos
Producto/BOM, cantidad, estado, lotes, existencias.

## Situaciones especiales
Detalle solo tras seleccionar fila. BOM/stock faltante puede bloquear.

## Relacionado
- Compradores · Activos
- [Tour de incorporación](/tour/)
''',
            'delivery.desk': '''# Escritorio de entrega

## Resumen del proceso
Proceso SYSTEM **DEL_SERVICE_ORDER**: servicios/recursos → orden → planificar → asignar → iniciar → completar.

## Acciones
- Nuevo servicio/recurso/orden; planificar, asignar, iniciar, completar
- Pastilla verde **Proceso** — DEL_SERVICE_ORDER

## Campos
Servicio, recurso, ventana, estado.

## Situaciones especiales
Sin recurso/plan no se inicia. Pedidos de venta necesitan confirmación upstream.

## Relacionado
- Ventas · Posventa
- [Tour de incorporación](/tour/)
''',
            'aftersales.desk': '''# Escritorio posventa

## Resumen del proceso
Procesos SYSTEM **AS_INQUIRY** y **AS_RMA**. Claims pueden crear abonos; RMAs reciben stock.

## Acciones
- Nueva consulta/garantía/claim/RMA; avanzar estado
- Pastilla verde **Proceso** — AS_INQUIRY o AS_RMA según pestaña

## Campos
Cliente, texto del caso, importes, líneas RMA.

## Situaciones especiales
Aprobar claim puede requerir acceso Finance.

## Relacionado
- Ventas · Entrega · Facturas
- [Tour de incorporación](/tour/)
''',
            'hr.desk': '''# Escritorio de RR. HH.

## Resumen del proceso
Proceso SYSTEM **HR_CANDIDATE_HIRE**: candidatos → etapas → contratar o rechazar. También expedientes, tiempo, nómina, metas.

## Acciones
- Nuevo por pestaña; detalle partido; contratar/rechazar
- Pastilla verde **Proceso** — HR_CANDIDATE_HIRE

## Campos
Etapas de candidato; expediente (campos sensibles); tiempo/nómina.

## Situaciones especiales
Contratar necesita ruta Users. See de RR. HH. solo si hace falta.

## Relacionado
- Invitar usuario · Roles
- [Tour de incorporación](/tour/)
''',
            'digital.desk': '''# Escritorio digital

## Resumen del proceso
Procesos SYSTEM **DIG_IT_TICKET** y **DIG_IT_CHANGE**. Sistemas y CIs los sostienen.

## Acciones
- Nuevo sistema/CI/cambio/ticket; start/resolve/close
- Pastilla verde **Proceso** — DIG_IT_TICKET o DIG_IT_CHANGE

## Campos
Enlace CI–sistema; asunto/estado del ticket; campos de cambio.

## Situaciones especiales
Tickets de TI interna, no posventa. Detalle tras seleccionar fila.

## Relacionado
- Hub de agentes · Integraciones
- [Tour de incorporación](/tour/)
''',
            'asset.desk': '''# Escritorio de activos

## Resumen del proceso
Proceso SYSTEM **AST_ASSET_LIFECYCLE**: registro de planta, vehículos y herramientas.

## Acciones
- Nuevo en el registro; ver/editar; plan de mantenimiento en la barra de contenido
- Pastilla verde **Proceso** — AST_ASSET_LIFECYCLE

## Campos
Nombre, etiqueta, ubicación, owner, estado; flags IT, depreciación, mantenimiento.

## Situaciones especiales
Hostname/IP solo en Digital. Depreciación/OT automáticas llegan después.

## Relacionado
- Digital · Taller · Libro mayor
- [Tour de incorporación](/tour/)
''',
            'strategy.desk': '''# Escritorio de estrategia

## Resumen del proceso
Procesos SYSTEM **STR_STRATEGY_ITEM** y **STR_COMPETITOR_WATCH**.

## Acciones
- Pestañas + Nuevo; ver/editar; barra de contenido
- Pastilla verde **Proceso** — código de la pestaña

## Campos
Competidor: nombre, tipo, fuentes, estado. Tema: nombre, tipo, descripción, kind, estado.

## Situaciones especiales
Listas vacías están bien al inicio. Sin movimiento de dinero.

## Relacionado
- Hub de capacidades (CAP_PROJECT)
- [Tour de incorporación](/tour/)
''',
            'development.desk': '''# Escritorio de desarrollo

## Resumen del proceso
Proceso SYSTEM **DEV_PROJECT_DELIVER**: proyectos NPD / revisiones.

## Acciones
- Nuevo proyecto; avanzar estado
- Pastilla verde **Proceso** — DEV_PROJECT_DELIVER

## Campos
Nombre, tipo, descripción, estado; adjuntos en la barra de contenido.

## Situaciones especiales
Trabajo de portafolio también en Capability (**CAP_PROJECT**).

## Relacionado
- Hub de capacidades
- [Tour de incorporación](/tour/)
''',
            'grrc.desk': '''# Escritorio de gobernanza

## Resumen del proceso
Procesos SYSTEM **GRC_RISK_TREAT** (riesgos: abrir → mitigar → cerrar/aceptar) y **GRC_COMPLIANCE_ITEM** (cumplimiento: borrador → activo → retirar). Silver; complementa Stakeholder (**STK_PARTY_ENGAGE**).

## Acciones
- Pestañas Riesgos / Cumplimiento — processCode de la pastilla verde **Proceso**
- Nuevo riesgo o ítem; ver/editar; cerrar/aceptar riesgo; activar/retirar cumplimiento
- Pastilla verde **Proceso** — GRC_RISK_TREAT o GRC_COMPLIANCE_ITEM

## Campos
Riesgo: nombre, severidad, probabilidad, owner, nota de tratamiento, estado, descripción. Cumplimiento: nombre, tipo, owner, vigente desde, estado, descripción. Barra de contenido.

## Situaciones especiales
Requiere **Silver** (o superior). Listas vacías están bien. Sin movimiento de dinero.

## Relacionado
- Escritorio de stakeholders (STK_PARTY_ENGAGE)
- Hub de capacidades
- [Tour de incorporación](/tour/)
''',
            'stakeholder.desk': '''# Escritorio de stakeholders

## Resumen del proceso
Proceso SYSTEM **STK_PARTY_ENGAGE**: registrar partes (activo → vigilancia → inactivo) y registrar comunicaciones. Complementa Gobernanza.

## Acciones
- Pestañas Partes / Comunicaciones; Nuevo; ver/editar; estado ACTIVE/WATCH/INACTIVE
- Pastilla verde **Proceso** — STK_PARTY_ENGAGE

## Campos
Parte: nombre, tipo, estado, contacto, nota ética, descripción. Comunicación: parte, asunto, canal, dirección, fecha, estado. Barra de contenido.

## Situaciones especiales
Requiere **Silver**. Las comunicaciones necesitan una parte existente. Las notas éticas son orientación del operador — no un archivo legal.

## Relacionado
- Escritorio de gobernanza (GRC_RISK_TREAT / GRC_COMPLIANCE_ITEM)
- [Tour de incorporación](/tour/)
''',

            'capability.standards': '''# Estándares / KPI

## Resumen del proceso
Registro de **estándares** Capability (APQC 13.7 ligero) para métricas y objetivos. Complementa **Calidad** y **CAP_PROJECT** — sin movimiento de dinero.

## Acciones
- **Nuevo** — crear estándar/KPI
- Ver/editar; el detalle guarda campos
- **Activar / Desactivar** — ACTIVE ↔ INACTIVE
- Pastilla verde **Proceso** — CAP_PROJECT
- Barra de contenido

## Campos
Nombre, descripción, metricCode, targetValue, unit, standardStatus.

## Situaciones especiales
Lista vacía está bien. Los desactivados se conservan.

## Relacionado
- Hub de capacidades · Calidad
- [Tour de incorporación](/tour/)
''',
            'capability.hub': '''# Hub de capacidades

## Resumen del proceso
Proceso SYSTEM **CAP_PROJECT** más catálogo de procesos. Pestañas: portafolios, programas, proyectos, procesos, calidad, estándares, cambios, conocimiento.

## Acciones
- Cambiar pestañas; iniciar/completar proyectos; abrir proceso SYSTEM (Mermaid)
- Pastilla verde **Proceso** — CAP_PROJECT

## Campos
Campos de proyecto; tipo/estado de proceso; calidad/cambios.

## Situaciones especiales
SYSTEM solo lectura; reseed en Ajustes/Capability.

## Relacionado
- Diagramas de proceso · Desarrollo
- [Tour de incorporación](/tour/)
''',
            'agents.hub': '''# Hub de IA / agentes

## Resumen del proceso
Proceso SYSTEM **AR_HITL_APPROVAL**. Agents, Hives, Memory. Closed beta: solo Agent Basics.

## Acciones
- Sembrar/activar agentes; aprobar/rechazar HITL
- Pastilla verde **Proceso** — AR_HITL_APPROVAL

## Campos
Estado del agente; hive; propuesta HITL; memoria.

## Situaciones especiales
Los agentes no liquidan/pagan por defecto. Detalle tras seleccionar fila.

## Relacionado
- Tokens Hermes · Integraciones · Digital
- [Tour de incorporación](/tour/)
''',
            'finance.invoices': '''# Facturas

## Resumen del proceso
Proceso SYSTEM **FIN_INVOICE_SETTLE**: borrador → emitir → liquidar. Upstream a menudo **SALES_QUOTE_TO_ORDER**.

## Acciones
- Crear borrador; **Emitir** / **Liquidar**; fila para detalle partido
- Pastilla verde **Proceso** — FIN_INVOICE_SETTLE

## Campos
Importes en carril primario; pistas FX solo visuales; estado; opciones de pago.

## Situaciones especiales
DEMO solo mock settle. Reglas de asiento rotas bloquean (`POSTING_RULES_*`).

## Relacionado
- Ventas · Tesorería · Reglas de asiento · Pagos en vivo
- [Tour de incorporación](/tour/)
''',
            'finance.treasury': '''# Tesorería

## Resumen del proceso
**FIN_PAYABLE** y posiciones de tesorería. Pestañas: posiciones, payables, presupuestos, impuestos, nómina stub. Banco fiat + monedero Bitcoin, un plan contable.

## Acciones
- Pestañas; trabajar payables/posiciones
- Pastilla verde **Proceso** — FIN_PAYABLE

## Campos
Valores según carril primario; estado de payables; campos por pestaña.

## Situaciones especiales
Bitcoin en vivo en Pagos (BTCPay Server · experimental). Reglas de asiento pueden bloquear pago a proveedor.

## Relacionado
- Facturas · Libro mayor · Compradores · Carril primario
- [Tour de incorporación](/tour/)
''',
            'finance.primaryRail': '''# Carril primario y BTC-FX

## Resumen del proceso
Elección administrativa de moneda de libros — sin processCode SYSTEM de escritorio. La liquidación sigue **FIN_INVOICE_SETTLE**.

## Acciones
- Elegir carril en setup; leer pistas FX; liquidar factura para fijar tipo de pago

## Campos
Carril EUR/USD/BTC; fiat de visualización; pista FX y badge obsoleto.

## Situaciones especiales
Las pistas no son los libros. Sin MTM automático. Cambiar carril tras setup es Advanced.

## Relacionado
- Facturas · Tesorería · Pagos en vivo (BTCPay Server · experimental)
''',
            'system.settings': '''# Configuración

## Resumen del proceso
Superficie administrativa — **sin processCode SYSTEM de escritorio**. Idioma, monedas, impuestos, demo/setup.

## Acciones
- Idioma y región; Admin: monedas/impuestos/reset setup
- Integraciones en **Sistema → Integraciones**

## Campos
Locale; lista corta de monedas; códigos fiscales; estado de setup.

## Situaciones especiales
Idioma de pantalla ≠ idioma PDF del socio. Reset de setup es destructivo (demos).

## Relacionado
- Monedas que usamos · Idioma del socio · Integraciones
''',
            'help.work.actionNow': '''# Actividades - AHORA

## Resumen del proceso
Cola de trabajo humana (Eisenhower) — **sin processCode SYSTEM**. Ítems AI:* esperan **DONE** humano.

## Acciones
- Nueva tarea · Actualizar · interruptor Tareas agénticas · Lista de tareas
- Abrir celda/tarea — urgencia, importancia, plazo, asignación

## Campos
Urgencia/importancia; estado; plazo; asignación.

## Situaciones especiales
El tablero no inventa prioridades. Agentes terminan con AI:done; humano pone DONE.

## Relacionado
- Panel CEO · Hub de agentes
- [Tour de incorporación](/tour/)
''',
            'content.rail': '''# Object Companion (barra de contenido)

## Resumen del proceso
Sin processCode SYSTEM. Canal compañero en registros durante procesos de escritorio.

## Acciones
- Añadir Enlace, Archivo o Contenido (Nota, Ayuda, Riesgo, …)

## Campos
URL; archivo; tipo de contenido + texto.

## Situaciones especiales
El acceso sigue al registro anfitrión. Archivos = canal FILE, no ContentType.

## Relacionado
- Cualquier escritorio operativo con la barra
''',
            'help.partners.locale': '''# Idioma de comunicación del socio

## Resumen del proceso
Campo maestro usado en documentos de SALES_QUOTE_TO_ORDER / SCM_PO_FULFILL / FIN_INVOICE_SETTLE — no el idioma de pantalla.

## Acciones
- Fijar idioma de comunicación en Cliente/Proveedor; PDF/correo lo siguen

## Campos
Idioma de comunicación; vacío → inglés (en_US).

## Situaciones especiales
Tras enviar/confirmar/emitir el idioma se congela. Descripciones de catálogo aún no bilingües.

## Relacionado
- PDFs comerciales · Configuración · Ventas
''',
            'help.roles.overview': '''# Roles para un equipo pequeño

## Resumen del proceso
Modelo de acceso administrativo — sin processCode SYSTEM de escritorio.

## Acciones
- Sistema → Roles · Sistema → Usuarios (asignar/invitar)

## Campos
Perfil de rol; verbos de escritorio; acciones de sistema.

## Situaciones especiales
Deny gana. Sin ROLE_AI desde Usuarios.

## Relacionado
- Qué rol elegir · Matriz · Invitar · Seguridad
''',
            'help.roles.presets': '''# Qué rol elegir

## Resumen del proceso
Guía administrativa de perfiles — sin processCode SYSTEM de escritorio.

## Acciones
- Elegir perfil al invitar; ajustar valores por defecto en Roles

## Campos
Nombre de perfil y permisos típicos.

## Situaciones especiales
Una persona → un perfil. CEO no crea Admin.

## Relacionado
- Roles · Matriz · Roles de práctica
''',
            'help.roles.matrix': '''# Matriz de permisos (Avanzado)

## Resumen del proceso
Fuente de verdad administrativa de verbos — sin processCode SYSTEM de escritorio.

## Acciones
- Ver/ajustar celdas (Admin/CEO); confirmar apply-to-all

## Campos
See/Create/Change/Delete/Work; acciones de sistema incl. liquidar dinero.

## Situaciones especiales
Deny gana; proteger último Admin; API MATRIX_*_FORBIDDEN.

## Relacionado
- Roles · Seguridad
''',
            'help.users.invite': '''# Invitar a un miembro del equipo

## Resumen del proceso
Alta administrativa de usuarios — sin processCode SYSTEM de escritorio.

## Acciones
- Sistema → Usuarios: perfil, nombre, contraseña temporal; authenticator; Alby opcional

## Campos
Usuario; contraseña temporal; perfil; columna Extensión.

## Situaciones especiales
CEO no asigna Admin. Preferir desactivar. Nunca pegar nsec.

## Relacionado
- Alby · Roles · Seguridad
''',
            'help.security.nostrLogin': '''# Iniciar sesión con Alby (Nostr)

## Resumen del proceso
Login NIP-07 por usuario — sin processCode SYSTEM de escritorio. El relay de empresa está en Integraciones.

## Acciones
- Login con contraseña → Seguridad → Vincular extensión → luego login con extensión

## Campos
Vinculado Sí/No; columna Extensión en Usuarios.

## Situaciones especiales
Sin vínculo falla el login por extensión. Nunca nsec. Una identidad Alby ↔ un usuario.

## Relacionado
- Invitar · Integración Nostr
''',
            'help.users.danger': '''# Seguridad: Admin, CEO y desactivar

## Resumen del proceso
Reglas administrativas de seguridad — sin processCode SYSTEM de escritorio.

## Acciones
- Mantener Admin y CEO separados; preferir desactivar; confirmar apply-to-all

## Campos
N/A.

## Situaciones especiales
No desactivar el último Admin. Solo perfiles con liquidar pueden liquidar facturas.

## Relacionado
- Invitar · Matriz
''',
            'help.users.privacy': '''# Privacidad, RR. HH. y solo lectura

## Resumen del proceso
Guía administrativa de privacidad — relevante antes de **See** en capital humano (**HR_CANDIDATE_HIRE**).

## Acciones
- Conceder See de RR. HH. solo si hace falta; Read only para observadores

## Campos
N/A.

## Situaciones especiales
El contenido sigue el acceso del registro anfitrión.

## Relacionado
- Escritorio RR. HH. · Qué rol elegir
''',
            'help.roles.demo': '''# Practicar roles (Demo / formación)

## Resumen del proceso
Guía administrativa en tenants demo — sin processCode SYSTEM de escritorio.

## Acciones
- Usar Read only / Office / Ops / Finance / CEO según el objetivo; desactivar usuarios de práctica

## Campos
N/A.

## Situaciones especiales
Practicar en demo antes de live.

## Relacionado
- Qué rol elegir · Roles
''',
            'app.search': '''# Búsqueda global (barra superior)

## Resumen del proceso
Sin processCode SYSTEM. La búsqueda encuentra registros en empresas con **See** para abrir el escritorio o documento correcto.

## Acciones
- Escribir en la **búsqueda de la barra superior** — clientes, proveedores, ofertas, pedidos, facturas, catálogo, OC, tareas
- **Ctrl+K / ⌘K** — saltar a páginas/escritorios (solo navegación)
- Abrir un resultado — enlace profundo al escritorio o vista de dominio

## Campos
La caja necesita al menos **2** caracteres (debounce ~300ms). Resultados muestran tipo, título, estado y enlace.

## Situaciones especiales
Los resultados quedan en la **empresa activa**. Se omiten tipos sin **See**. El filtro `q` de una lista de escritorio no es búsqueda global.

## Relacionado
- Panel CEO
- Mi trabajo / Actividades - AHORA
''',
            'setup.wizard': '''# Asistente de configuración

## Resumen del proceso
Configuración administrativa inicial — sin processCode SYSTEM de escritorio. Recorre Tour → empresa → sitio → banca → Bitcoin → plantilla → fin (por defecto **Iron**).

## Acciones
- Completar cada paso del asistente y continuar
- Elegir **nivel de integración Bitcoin** (Ahora no / Dirección única / BTCPay Server (experimental))
- Finalizar para desbloquear escritorios de la nueva empresa

## Campos
Empresa/entidad legal, sitio, banca, nivel Bitcoin y nivel de plantilla. El rail primario (moneda de libros) se elige aparte del nivel de recepción Bitcoin.

## Situaciones especiales
Admin puede restablecer el setup en **Configuración** para demos. Empresas DEMO se quedan en settle mock. Un asistente incompleto bloquea el uso normal de escritorios.

## Relacionado
- Integraciones · Nivel de integración Bitcoin
- Configuración
- [Tour de incorporación](/tour/)
''',
            'training.btc.basic': '''# Alfabetización básica en Bitcoin (aviso nivel 1)

## Resumen del proceso
Lista de comprobación administrativa antes de habilitar recepción Bitcoin — sin processCode SYSTEM de escritorio. Alinea con el nivel de Integraciones **1 — Ahora no**.

## Acciones
- Leer la lista de alfabetización de este tema
- Fijar el nivel Bitcoin en **Sistema → Integraciones** (o paso Bitcoin del setup)

## Campos
Sin campos de formulario en este tema de ayuda. La elección de nivel vive en Integraciones.

## Situaciones especiales
steakOS **no** custodia claves. El nivel 1 aplaza la recepción en vivo; los libros pueden usar rail primario BTC con settle **mock**. Preferir **BTCPay Server (experimental)** para pagos regulares de clientes.

## Relacionado
- Nivel de integración Bitcoin · Pagos en vivo (BTCPay Server · experimental)
- Seguridad avanzada de Bitcoin
''',
            'training.btc.security-advanced': '''# Seguridad avanzada de Bitcoin (aviso nivel 2)

## Resumen del proceso
Lista de seguridad para Bitcoin nivel **2 — Dirección única** (pagos ocasionales). Administrativa — sin processCode SYSTEM de escritorio.

## Acciones
- Seguir la lista de copia de seguridad / phishing
- Gestionar el nivel 2 en **Sistema → Integraciones**
- Planear el salto a **BTCPay Server (experimental)** para Lightning / recepción regular

## Campos
Una dirección de monedero de software en Integraciones — nunca pegar semillas en steakOS.

## Situaciones especiales
Reutilizar direcciones y copias débiles pueden causar pérdida permanente. steakOS no observa la cadena en beta — el settle es **marcar pagado manualmente**. Se requiere acuse de formación del CEO al subir a nivel 2/3.

## Relacionado
- Nivel de integración Bitcoin · Pagos en vivo (BTCPay Server · experimental)
- Alfabetización básica en Bitcoin
''',
            'system.integrations': '''# Hub de integraciones

## Resumen del proceso
Superficie administrativa para integraciones del operador (Nostr, nivel de recepción Bitcoin, tokens Hermes) — **sin processCode SYSTEM de escritorio**. Las operaciones de pago en vivo continúan en Finance → Payments tras el nivel 3.

## Acciones
- Configurar nivel de relay **Nostr**, instalar/iniciar, probar
- Fijar nivel de recepción **Bitcoin** (Ahora no · Dirección única · BTCPay Server (experimental))
- **Crear / revocar** tokens API de agentes Hermes
- Probar conexiones antes de activar en vivo

## Campos
URL / nivel del relay; nivel Bitcoin; claves BTCPay en Finance → Payments; token Hermes `sos_…` (visible una vez).

## Situaciones especiales
El CEO debe acusar la formación cuando Admin sube Bitcoin a nivel 2 o 3. DEMO no publica Nostr y permanece en settle mock. Nunca pegar `nsec`. Solo Agent Basics en beta cerrada.

## Relacionado
- Pagos en vivo (BTCPay Server · experimental) · Nivel de integración Bitcoin · Integración Nostr
- Agente Hermes (tokens API) · Hub de IA / agentes
''',
            'integrations.bitcoin': '''# Nivel de integración Bitcoin

## Resumen del proceso
Control administrativo de cómo la empresa **recibe** Bitcoin — separado del **rail primario de valor**. Sin processCode SYSTEM de escritorio; el flujo de settle es **FIN_INVOICE_SETTLE** en Facturas cuando existe el pago.

## Acciones
- Fijar el nivel en el paso **Bitcoin** del setup o en **Sistema → Integraciones**
- Para nivel 3: abrir **Finance → Payments**, probar, activar en vivo

## Campos
| Nivel | UI | Comportamiento |
|-------|-----|----------------|
| DEFERRED | 1 — Ahora no | Sin recepción en vivo; settle mock OK |
| SINGLE_ADDRESS | 2 — Dirección única | Una dirección + QR; marcar pagado manual |
| BTCPAY | 3 — BTCPay Server (experimental) | BTCPay del operador (Lightning + on-chain) |

## Situaciones especiales
Admin fija el nivel; acuse de formación del CEO al subir a 2/3; downgrade desde nivel 3 solo Admin. Afirmaciones de marketing Lightning solo en nivel 3.

## Relacionado
- Pagos en vivo (BTCPay Server · experimental) · Facturas (FIN_INVOICE_SETTLE)
- Formación básica / avanzada de Bitcoin
''',
            'integrations.nostr': '''# Integración Nostr (OI-NOSTR01)

## Resumen del proceso
Nivel administrativo de relay / auditoría de la empresa para Nostr local privado — sin processCode SYSTEM de escritorio. El login Alby por usuario está en **Seguridad**, no aquí.

## Acciones
- Fijar nivel **0 / 1 / 2** en Integraciones o paso Nostr del setup
- **Instalar / iniciar** relay local (proyecto `var/nostr/`, loopback)
- **Probar** hasta que sea alcanzable
- Las personas vinculan Alby en **Seguridad → Vincular extensión del navegador**

## Campos
Nivel y URL del relay de empresa; metadatos opcionales del bot. Nunca pegar `nsec`.

## Situaciones especiales
DEMO no publica. Sin clave de bot, publicar permanece honesto (`stubbed`). Giftwrap notify de nivel 2 sigue residual.

## Relacionado
- Iniciar sesión con Alby (Nostr)
- Hub de integraciones
''',
            'currency.catalog': '''# Monedas que usamos

## Resumen del proceso
Superficie administrativa de Configuración — sin processCode SYSTEM de escritorio. Los códigos habilitados alimentan desplegables en documentos comerciales que luego ejecutan SALES_QUOTE_TO_ORDER / SCM_PO_FULFILL / FIN_INVOICE_SETTLE.

## Acciones
- Admin: habilitar/deshabilitar códigos en **Configuración → Monedas que usamos**
- Operadores: elegir códigos habilitados en clientes, proveedores, catálogo y documentos

## Campos
Lista ISO compartida; conjunto corto de empresa **Monedas que usamos**; moneda de empresa en la barra superior; moneda preferida en socios; moneda de precio en ítems de catálogo.

## Situaciones especiales
Las escrituras nuevas necesitan un código habilitado; huérfanos legacy siguen legibles. Avisos suaves de desajuste no bloquean guardar.

## Relacionado
- Escritorio de ventas · Escritorio de compradores · Facturas
- Rail primario y FX BTC
''',
            'finance.payments.live': '''# Pagos en vivo (BTCPay Server · experimental)

## Resumen del proceso
Configuración administrativa de recepción LIVE para Bitcoin nivel 3. El movimiento de dinero en facturas sigue **FIN_INVOICE_SETTLE**; esta pantalla cablea BTCPay para que el settle pueda ser en vivo en lugar de mock.

## Acciones
- Fijar nivel **BTCPay** en Integraciones
- Introducir URL base, store id, API key, secreto de webhook
- **Probar** y luego activar **en vivo**
- Emitir factura → pago entrante → pagar en BTCPay → webhook o conciliar

## Campos
Campos de conexión BTCPay anteriores; interruptor en vivo. Settle fiat/SEPA sigue disponible por separado.

## Situaciones especiales
Empresas DEMO forzadas a settle mock. steakOS no custodia claves. El salto desde nivel 2 mantiene la dirección ocasional hasta que BTCPay esté en vivo.

## Relacionado
- Facturas (FIN_INVOICE_SETTLE)
- Nivel de integración Bitcoin · Tesorería
''',
            'finance.ledger': '''# Libro mayor

## Resumen del proceso
Núcleo financiero orientado a SYSTEM **FIN_LEDGER**: sembrar plan de cuentas, asentar diarios equilibrados, balance de comprobación, marcador de cierre de periodo. No es el escritorio de facturas — los asientos suelen originarse en **FIN_INVOICE_SETTLE** / payables.

## Acciones
- Sembrar COA si está vacío
- Asentar diarios equilibrados con herramientas del libro
- Ejecutar balance de comprobación / marcador de cierre

## Campos
Códigos de cuenta (únicos por tenant); líneas de diario debe/haber; marcadores de periodo.

## Situaciones especiales
No inventar saldos a mano — solo a través del servicio de libro. Reglas de asiento malas bloquean settle/pay aguas arriba.

## Relacionado
- Facturas (FIN_INVOICE_SETTLE) · Reglas de asiento · Tesorería
''',
            'finance.postingRules': '''# Reglas de asiento

## Resumen del proceso
Conjunto de reglas administrativas de la empresa para diarios automáticos — sin processCode SYSTEM de escritorio propio. Se disparan en **FIN_INVOICE_SETTLE**, pago a proveedor y nómina (y bajo Accrual al emitir / abrir payable).

## Acciones
- Ver reglas / modo / registro de cambios (Admin + CEO)
- Editar reglas / modo / reseed (solo Admin)
- Guardar o Reseed — añade al registro y abre ticket Digital IT (`CHANGE`, prefijo `[Finance policy]`)

## Campos
Modo contable **Cash** vs **Accrual**; mapeos de cuentas por evento en el ruleset.

## Situaciones especiales
Reglas malas o ausentes **bloquean** settle/pay (`POSTING_RULES_*`) — sin fallback silencioso. Reseed nunca borra diarios.

## Relacionado
- Libro mayor · Facturas · Tesorería · Nómina
''',
            'process.chart': '''# Diagramas de proceso

## Resumen del proceso
Ayuda de aprendizaje para procesos del catálogo **SYSTEM** (familia **CAP_PROJECT** / catálogo de procesos) — no es process mining. Ejemplo de enlace profundo: `/capability/processes?code=FIN_INVOICE_SETTLE`.

## Acciones
- Abrir **Capability → Procesos** (por defecto SYSTEM)
- Seleccionar un proceso (o How-it-works / pastilla Proceso en un escritorio)
- Pasar el ratón o enfocar con teclado un paso para Qué / Quién / Dónde + trampa; tocar para fijar en touch; Esc cierra

## Campos
Código de proceso, tipo (SYSTEM/HUMAN/AGENTIC/OTHER), estado (DRAFT/ACTIVE/RETIRED), diagrama Mermaid, consejos de paso.

## Situaciones especiales
Consejos marcados **planned / stub** son honestos sobre profundidad incompleta. Las semillas SYSTEM son de solo lectura; reseed desde Configuración o Capability.

## Relacionado
- Hub de capacidades
- Pastilla Proceso de cualquier escritorio para su processCode
''',
            'agents.activate': '''# Agente Hermes (tokens API)

## Resumen del proceso
Ruta administrativa para conectar un host de agente del operador — se empareja con **AR_HITL_APPROVAL** en el hub de agentes. Sin processCode SYSTEM de escritorio propio en Integraciones.

## Acciones
- **Sistema → Integraciones → Agentes Hermes** — crear token `sos_…` (visible una vez)
- Pegarlo en el host con baseUrl de steakOS
- Smoke: `GET /api/agent/me` y `/api/agent/l0/summary` con Bearer
- Opcional: el agente propone → **Agentes → HITL** → humano aprueba/rechaza

## Campos
Token API opaco (una vez); URL base en el host. Sin sesión de navegador para el agente.

## Situaciones especiales
Solo Agent Basics. Revocar tokens si un host se compromete. Por defecto no dar a los agentes poderes de settle/pay.

## Relacionado
- Hub de IA / agentes (AR_HITL_APPROVAL)
- Hub de integraciones
''',
            'system.import': '''# Datos e importación

## Resumen del proceso
Importación CSV administrativa — sin processCode SYSTEM de escritorio. Los packs alimentan maestros usados luego por procesos de Ventas / Compradores / Finanzas.

## Acciones
- Elegir pack (**Customers**, **Vendors**, **COA**)
- Previsualizar mapeo y luego confirmar
- El pack de saldos de apertura es solo vista previa

## Campos
Columnas CSV mapeadas a campos maestros por pack.

## Situaciones especiales
Se requiere permiso de importación (típico Admin/CEO). Filas malas fallan en la vista previa — corregir el archivo antes de confirmar.

## Relacionado
- Escritorio de ventas · Escritorio de compradores · Libro mayor
''',
            'system.catalogs': '''# Catálogos de administración

## Resumen del proceso
Listas administrativas de datos de referencia — sin processCode SYSTEM de escritorio. Catálogos de power-user, no escritorios operativos diarios.

## Acciones
- Explorar/editar dominios de referencia y tipos de contenido según permiso
- Preferir escritorios de familia para el trabajo diario de proceso

## Campos
Varía por recurso de catálogo (países, monedas, tipos de contenido, config legacy).

## Situaciones especiales
Se requiere permiso de catálogo. Editar datos de referencia puede afectar desplegables en escritorios de proceso.

## Relacionado
- Configuración · Catálogo de procesos Capability
''',
            'app.about': '''# Acerca de

## Resumen del proceso
Pantalla de metadatos del producto — sin processCode SYSTEM. Muestra línea, SemVer, nivel de plantilla e hitos.

## Acciones
- Leer información de versión / nivel
- Seguir la guía de divulgación de seguridad cuando haga falta

## Campos
Ninguno editable para trabajo de proceso.

## Situaciones especiales
Los problemas de seguridad van en privado a **security@steakos.org** — no publicaciones públicas de exploits.

## Relacionado
- Informar de un problema de seguridad
''',
            'security.disclose': '''# Informar de un problema de seguridad

## Resumen del proceso
Entrada administrativa de seguridad — sin processCode SYSTEM de escritorio. Proceso de correo privado para el núcleo steakOS y configs de instalación documentadas.

## Acciones
- Escribir en privado a **security@steakos.org**
- No publicar detalles de exploits en issues públicas

## Campos
N/A en pantalla; incluir versión del producto y repro en el correo.

## Situaciones especiales
Objetivo de acuse ≤ 3 días laborables; triage ≤ 10. No es certificación CRA. Hosts de terceros BTCPay/Hermes fuera de alcance salvo que el pegamento steakOS filtre datos.

## Relacionado
- Acerca de steakOS
''',
            'app.tour': '''# Tour de incorporación

## Resumen del proceso
Superficie de aprendizaje del producto en `/tour/` — sin processCode SYSTEM de escritorio. La elección de plantilla aquí se recuerda para el asistente de configuración.

## Acciones
- Abrir **`/tour/index.html`**
- Completar módulos del tour; continuar al setup cuando se indique

## Campos
Progreso del tour / preferencia de plantilla según los almacene la app del tour.

## Situaciones especiales
El tour es opcional tras la primera ejecución; los escritorios siguen disponibles según el setup y los roles.

## Relacionado
- Asistente de configuración
- Panel CEO
''',
            'help.tax.ust': '''# Códigos fiscales USt alemanes

## Resumen del proceso
Paquete administrativo de códigos fiscales (Configuración) usado en líneas de venta/OC durante procesos comerciales — sin processCode SYSTEM de escritorio propio.

## Acciones
- Habilitar códigos en **Configuración → Códigos fiscales que usamos**
- Elegir códigos en líneas de oferta / pedido de venta / OC

## Campos
DE_USt_STD 19%, DE_USt_RED 7%, DE_USt_0, DE_RC, DE_IG, DE_EXPORT (equivalentes AT similares). Régimen: IVA Alemania/Austria vs Kleinunternehmer/Ninguno.

## Situaciones especiales
El operador es dueño de los tipos — la semilla no es asesoramiento legal. No es presentación UStVA/ELSTER. Código incorrecto = error del operador.

## Relacionado
- Reverse charge e IG · PDFs comerciales · Pestaña fiscal de Finanzas
''',
            'help.tax.reverseCharge': '''# Reverse charge e intracomunitario (IG)

## Resumen del proceso
Uso de códigos fiscales en líneas de venta dentro de **SALES_QUOTE_TO_ORDER** (y documentos relacionados) cuando aplican reglas RC/IG.

## Acciones
- Confirmar NIF-IVA válido cuando se requiera
- Elegir DE_RC / DE_IG (o AT) en la línea
- Descargar PDF y comprobar el bloque de nota legal

## Campos
Los códigos RC/IG fuerzan 0% de IVA de salida y añaden frases legales EN+DE en PDF/notas.

## Situaciones especiales
Sin validación VIES en v1. Presentación/Intrastat/OSS fuera de alcance. Código incorrecto no se auto-corrige.

## Relacionado
- Códigos fiscales USt alemanes · PDFs comerciales · Escritorio de ventas
''',
            'help.docs.pdf': '''# PDFs comerciales (A4)

## Resumen del proceso
Salida de documentos para procesos comerciales (ofertas, pedidos, OC, facturas) — soporta artefactos de SALES_QUOTE_TO_ORDER, SCM_PO_FULFILL, FIN_INVOICE_SETTLE. No es un processCode SYSTEM propio.

## Acciones
- **Descargar … PDF** en pantallas de oferta, pedido de venta, OC y factura
- Imprimir desde el navegador si hace falta

## Campos
Diseño A4 vertical; tablas multilínea; totales que coinciden con pantallas. Etiquetas siguen el idioma de comunicación del socio.

## Situaciones especiales
No es XML de factura electrónica ni firma digital. El idioma se congela tras enviar/confirmar/emitir.

## Relacionado
- Idioma de comunicación del socio · Escritorio de ventas · Facturas
'''
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
