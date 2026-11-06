# screenshots/

Live product captures for the root `README.md` (founder-friendly captions live under each image there).

Regenerate from a running lab UI (`:9001` + API):

```bash
cd frontend
STEAKOS_UI=http://127.0.0.1:9001 STEAKOS_USER=admin STEAKOS_PASS='…' \
  node scripts/capture-readme-shots.mjs
```

Prefer the Playwright MCP session when the SPA already has a cookie — the script’s form login can race Vite ↔ Grails redirects.

| File | README role | Scene |
|------|-------------|--------|
| `01-login.png` | Sign in | Login |
| `02-select-company.png` | Company & settings | Settings / company profile |
| `03-dashboard.png` | Hero + Action-NOW | CEO dashboard |
| `04-training-tour.png` | Help | Help center |
| `04b-training-tour-step.png` | Tour | Product tour step |
| `05-setup-wizard.png` | Setup | Configuration wizard |
| `06-ops-desk.png` | Sales | Sales quotes list |
| `06b-finance-desk.png` | Finance | Invoices |
| `07-about.png` | About | Version / trust links |
| `08-mobile-login.png` | Phone | Login ~390×844 |
| `09-mobile-dashboard.png` | Phone | Action-NOW ~390×844 |
