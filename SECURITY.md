# Security policy

## Supported software

steakOS core is open source under the **Apache License 2.0** (see `LICENSE` and `NOTICE`).

## Reporting a vulnerability

Please report security issues **privately** — do not open a public issue with exploit details or exploit PoCs.

1. Email **[security@steakos.org](mailto:security@steakos.org)**.  
2. Include: affected version/commit if known, description, impact, and safe reproduction steps.  
3. Allow reasonable time for triage before public disclosure.

**Machine-readable:** `/.well-known/security.txt` (also under `src/main/resources/static/.well-known/` for the running app)

We aim to acknowledge reports within **3 business days** and complete severity triage within **10 business days** (honest targets, not a contractual SLA). Critical or actively exploited issues are prioritised in line with good open-source practice and manufacturer obligations under emerging cyber-resilience expectations.

## Secrets and repository hygiene

- Never commit live credentials, private keys, wallet material, or production connection strings.
- Prefer environment variables and host secret stores. Commit only templates (for example `.env.example`) and variable **names**.
- If a required secret is missing at runtime, fail closed — do not fall back to a committed default password.
- Production startup exits unless the database password, field-encryption secret, and remember-me key are set in the environment. The installer writes them to `config/steakos.env`.

## Operator responsibilities

Self-host operators must secure their deployment (TLS, passwords, backups, access control, and lawful processing of personal data for their jurisdiction). Operator trust and GDPR configuration guidance lives in the private companion documentation set, not in this application monorepo.

## Scope

This policy covers the **steakOS software** and documented install configurations. Customer instance data, third-party payment or agent hosts, and proprietary extensions have their own operators and licenses — unless steakOS glue leaks credentials or tenant data.
