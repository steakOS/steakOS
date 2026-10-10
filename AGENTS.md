# steakOS — Project Instructions for AI Agents

## What this is

steakOS is an AI-augmented, APQC-aligned ERP for Bitcoin users: peer-to-peer trade, sovereign data and keys, Nostr for selective audit and messaging, and Apache Grails 7+ on the backend with a React SPA for operator UX.

Build for simplicity, sovereignty, auditability, and incremental delivery.

**Product name:** `steakOS` (camel OS). Prefer this spelling in formal text.  
**Family name:** always **GRRC**, never GRC.  
**Alpha languages:** English and German unless the operator expands scope.  
**License:** core **Apache-2.0** (`LICENSE`, `NOTICE`). New first-party source should include the standard header — run `python3 scripts/add-license-headers.py` if missing.

**Follow Apache Grails conventions** wherever they apply. Prefer the framework’s way over custom structure unless an accepted product decision explicitly overrides it (the React SPA is the intentional UX override).

## Two trees (do not mix)

| Tree | Path (this machine) | Role | Publish |
|------|---------------------|------|---------|
| **Application monorepo** | `/home/matt/Dev/grails/steakOS` | Runnable product source | Canonical hub on the operator LAN forge; public mirrors later |
| **Private companion** | `/home/matt/Dev/grails/steakOS-dev` | Design records, masterplans, branding assets, research archive, extra fixtures | Hub-only private repo — **not** for public internet mirrors |

This application tree **must not** point at paths under the companion (`docs/`, `artifacts/`, `archive/`, `testdata/`). Those folders live only in `steakOS-dev`.

Architecture and product locks live in the operator’s decision store and the private companion. Agents working in this tree: read **code and these instructions**; when a lasting design choice is needed, update the companion / decision store — do not recreate a public `docs/adr` or `docs/pdr` tree here.

## Repository layout (application)

| Path | Role |
|------|------|
| `README.md` · `content/` | Public getting-started story + screenshots / brand |
| `deploy/` | Dockerfile and packaging entrypoints |
| `grails-app/`, `src/`, Gradle wrappers | Application code (Grails 7.1.3) |
| `frontend/` | React + Vite SPA (dev `:9001` → API `:6060`) |
| `frontend/public/tour/` | In-app onboarding tour (served at `/tour/`) |
| `db/` | PostgreSQL bootstrap SQL |
| `scripts/` | Install, release, smoke, sync helpers |
| `demos/` | Filmed walkthrough media for demo companies |
| `var/` | Local runtime data (relay, logs) — not for public history |
| `dist/`, `build/`, `.gradle/` | Generated — keep out of commits |

## Distribution and hygiene (intent)

- **Canonical history** for this application lives on the operator’s LAN forge hub (Umbrel Forgejo). Build machines push there first. Public forges are mirrors and discovery, not the source of truth.
- **Git one-pager (companion):** `steakOS-dev/docs/installation/STEAKOS-GIT.md` — remotes, daily loop, pull/push habits.
- **Session habit:** at session start and on **EXIT** / wrap-up, remind the operator to **`git pull --ff-only`** before work and **`git push`** when done (both trees if both changed). Auth: `source ~/.config/forgejo/load.sh`.
- **Secrets never enter Git.** Use env vars, host secret stores, and local files outside the monorepo. Commit only `.env.example` / templates / variable names. Fail closed if a required secret is missing.
- **Do not commit** live credentials, key material, wallet files, or local override configs. Prefer the root `.gitignore` secrets block.
- **Signing keys (nsec / seeds)** stay on the user device or browser extension — never in the monorepo or agent memory.

## Architecture snapshot (binding intent)

Summaries for agents — implement toward these; call out gaps instead of silent big-bang rewrites.

| Theme | Intent |
|-------|--------|
| Vision | Bitcoin-native, AI-augmented ERP (APQC); simplicity, sovereignty, safety, auditability, local-first; Nostr + Bitcoin first-class |
| Identity | npub as primary person id; NIP-07 where available; classic Spring Security remains bootstrap where needed |
| Data | PostgreSQL + GORM; company tenancy by owner npub / RLS direction; database is source of truth for state |
| Process | Lightweight GORM state machines + JSONB dynamic steps — no heavy external BPM engine for now |
| Events | Domain events / outbox between modules; Nostr for selective audit and notify; Grails audit for money paths |
| Modules | Small **core** + APQC family plugins over time (Basics / Advanced / Pro). Cross-family = events only |
| Roles | C / D / M / A / AI / P from day one; AI writes via proposal + human approval |
| Frontend | React / Vite SPA + D3; REST to Grails (`9001` → `6060` in dev) |
| Payments | Self-hosted Bitcoin payment processor + provider interface (mock on DEMO, live on LIVE); journal truth in Postgres; no custody |
| AI | Hermes / gateway; HITL for money risk; Agent Basics until Pro is productized |
| Config | Setup wizard → legal entity / site / settings; metal templates Bronze open, Silver/Gold gated |
| Compliance posture | Manufacturer security mailbox `security@steakos.org`; operators own their instance hardening |
| Tax (launch) | German / AT VAT codes on commercial docs are invite-blocking; US multi-state later |

**Guiding priority:** Simplicity → Privacy/Sovereignty → Safety → Auditability → UX → Nostr+Bitcoin → AI-native → Local-first → Composability → Bitcoin-aligned economics.

### Planned plugin families

| Family | Variants (max 3) |
|--------|------------------|
| Strategy, Development, SupplyChain, Delivery, AfterSales, HR, IT, Asset, **GRRC**, Stakeholder, Capability, Agent | Basics + Pro (typical) |
| **Sales**, **Finance** | Basics + **Advanced** + Pro |

Plugin ids use the family name (`GRRCBasics`, `FinanceAdvanced`, …). Do not create empty Gradle plugin modules without an implementation decision.

## Grails standards (required default)

Use the official Grails application structure and CLI/Gradle generators. Do not invent parallel layouts.

### Application structure

| Location | Purpose |
|----------|---------|
| `grails-app/domain/` | GORM domain classes |
| `grails-app/controllers/` | Controllers (+ `UrlMappings`) |
| `grails-app/services/` | Transactional services |
| `grails-app/views/` | GSP views (controller/action naming) |
| `grails-app/taglib/` | Tag libraries |
| `grails-app/i18n/` | Message bundles |
| `grails-app/conf/` | `application.yml`, `application.groovy`, logging, Spring beans |
| `grails-app/init/` | `Application.groovy`, `BootStrap.groovy` |
| `grails-app/assets/` | Asset Pipeline (JS/CSS/images) |
| `src/test/groovy/` | Unit tests (Spock) |
| `src/integration-test/groovy/` | Integration tests |

### Domain traits

Code: `src/main/groovy/steakos/traits/` — `NpubOwned`, `Auditable`, `NostrPublishable`, `RoleRestricted`, `AccessControl`, `AiTagged`, `DynamicExtensible`, `Classifiable`, `Versioned`, `Networked`, `Attachable`, `Reputation`.

Use `DomainTraitConstraints.apply*(delegate)` in domain `static constraints`. Role enum: `steakos.security.RoleLevel`. Trait catalogue and policy live in the private companion — do not fork policy only in code.

### Generation and naming

- Prefer Grails commands over hand-rolled skeletons (`./grailsw create-domain-class`, `create-service`, `create-controller`, …)
- **Package base:** `steakos`
- Prefer Grails interceptors / Spring Security rules over one-off servlet filters

### Domain and persistence (GORM)

- Model data as **GORM domain classes** by default
- Multi-step writes in **services** (transactional by default)
- Respect environment `dataSource` / `dbCreate` in `application.yml` (**production: `dbCreate: none`**)
- Do not reintroduce H2 as the default runtime DB

### Controllers, services, views

- **Thin controllers** / **fat services**
- Prefer `respond` and i18n (`g:message`) for product UI

### Configuration

- Primary: `grails-app/conf/application.yml`
- Externalize secrets via env vars and host secret stores; never commit production credentials

### Testing

- Spock + Grails testing support under `src/test/groovy` and `src/integration-test/groovy`
- Run `./gradlew test` when tests exist or were added

## Current stack (as implemented)

- **App:** Apache Grails **7.1.3**, package `steakos`
- **Language:** Groovy on JVM 17+ (dev JDK may be 21)
- **DB:** PostgreSQL — `steakos_dev`, `steakos_test`, `steakos_prod`
- **Security:** Spring Security Core — `User`, `Role`, `UserRole`
- **DB bootstrap:** `db/setup-postgresql.sql`
- **SPA:** `frontend/` Vite on **9001**, API on **6060**

## Environments and ports

| Env | Grails name | HTTP port | Database |
|-----|-------------|-----------|----------|
| dev | `development` / `dev` | **6060** | `steakos_dev` |
| test | `test` | **7070** | `steakos_test` |
| prod | `production` / `prod` | **9090** | `steakos_prod` |

- DB overrides: `STEAKOS_DB_USER`, `STEAKOS_DB_PASSWORD`, `STEAKOS_DB_HOST`, `STEAKOS_DB_PORT`
- Production: `dbCreate: none`

## How to run

```bash
cd ~/Dev/grails/steakOS
# PostgreSQL up; DBs from db/setup-postgresql.sql
./gradlew bootRun          # development → http://localhost:6060
# SPA (separate terminal):
cd frontend && npm run dev # → http://localhost:9001
./gradlew test
```

Optional local APM (not needed for daily ops; agent under companion `steakOS-dev/tools/`): `./scripts/setup-glowroot.sh && ./gradlew bootRun -Pglowroot` (UI `:4000`).

**Operator / LAN dist (no Gradle on target):** `./scripts/release/pack-dist.sh` then host `dist/out/`; clients use `scripts/install/install.sh` or `install.ps1`. Full install prose lives in the private companion.

Show boot logs when starting servers. Stop cleanly when asked.

## Working rules for agents

1. **Grails first:** conventions, generators, plugins before custom infrastructure
2. **Read before inventing:** this file + existing `grails-app/` / `frontend/` before new modules or deps; lasting design → private companion / decision store
3. **Minimal diffs:** only what the task requires
4. **No dependency churn** without an explicit request
5. **Environments:** ports and DB names above; no default `8080` or H2
6. **No private paths in this tree:** do not link to `docs/`, `artifacts/`, `archive/`, or `testdata/` here
7. **Security:** default-deny for new endpoints; extend rules deliberately; secrets outside Git
8. **Destructive ops:** confirm before dropping DBs, force-push, or deleting large trees
9. **UI changes:** verify in the browser when SPA behavior or layout changed
10. **Typos in product labels:** ask before “fixing” DE menu names or branded strings
11. **Hub sync:** remind pull at start and push on EXIT / when finishing work — Forgejo is SSoT (see `.grok/rules/steakos-git.md`)

## Closed-beta framing (when relevant)

Bronze pack, invite-only (small group). Live Bitcoin settlement required on LIVE companies; DEMO stays mock. German VAT operator path is a launch must. Do not claim Silver/Gold depth or Agent Pro until those packs are productized.

## Out of scope unless asked

- Rewriting brand/name systems
- Migrating off Grails without an explicit decision
- Bulk rewriting private companion archives
- Full ERP modules without a scoped request
- Replacing GORM without an explicit decision
- Publishing the private companion to public mirrors

## Quick map

| Task | Start here |
|------|------------|
| Domain / DB | `grails-app/domain` → `application.yml` |
| Auth | `application.groovy` → `User` / `Role` / `UserRole` |
| HTTP | `UrlMappings.groovy` + controllers |
| Business logic | `grails-app/services` |
| SPA | `frontend/src` |
| Run | ports table + `./gradlew bootRun` + Vite |
| Design / metals / backlog | Private companion `steakOS-dev` + decision store |
| Git / hub workflow | Companion `docs/installation/STEAKOS-GIT.md` · `.grok/rules/steakos-git.md` |
