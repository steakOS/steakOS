# deploy/systemd

Packaging pointer for **OI-V06** systemd --user units.

Canonical templates + install helper live under:

→ [`scripts/install/systemd/`](../../scripts/install/systemd/)

| File | Purpose |
|------|---------|
| `steakos-api.service` | bootJar API `:6060` |
| `steakos-vite.service` | Vite SPA `:9001` |
| `steakos-*-start.sh` | Reliable `ExecStart` (jar resolve / `npm install`) |
| `install-user-units.sh` | Copy into `~/.config/systemd/user` (optional `--enable`) |

See also: companion `docs/installation/OPERATOR-RUNBOOK.md` · root `deploy/README.md`.
