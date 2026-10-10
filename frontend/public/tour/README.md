# steakOS onboarding tour (in-app)

**Canonical location:** `frontend/public/tour/`  
Served by the frontend dev server at **http://localhost:9001/tour/** and by the API jar from `src/main/resources/static/tour/`.

## Open

| Environment | URL |
|-------------|-----|
| Local frontend | http://localhost:9001/tour/ |
| App shell | System → Onboarding tour |
| Setup wizard | Step 0 → Open onboarding tour |
| Help center | Start the tour |

## Content

| File | Role |
|------|------|
| `index.html` | Tour SPA (Tailwind/Mermaid CDNs) |
| `welcome.json` … `finish.json` | Sections |
| `templates/*.json` | Bronze–Gold detail paths |
| `journey.json` | Journey section |

Template choice writes `localStorage.steakos.preferredTemplate` (`BRONZE`…`GOLD`) for the config wizard. Iron/Copper are legacy-only and must not appear as operator metal choices.

## Editing

1. Edit JSON / `index.html` here (`frontend/public/tour/`).
2. Sync into the API static folder when packaging the jar:

```bash
rsync -a --delete frontend/public/tour/ src/main/resources/static/tour/
```

Historical branding or tour drafts belong in the private companion assets tree — not under this monorepo.

## Production note

When only the frontend host serves `/tour/`, that path is enough.  
When the API jar serves static files, keep `src/main/resources/static/tour/` in sync (or add a build copy task later).
