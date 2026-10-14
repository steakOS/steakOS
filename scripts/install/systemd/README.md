# systemd --user units (OI-V06)

Packaged **user** units for durable API + Vite on a Linux operator host.

| Unit | Port | Role |
|------|------|------|
| `steakos-api.service` | **6060** | Grails `bootJar` (reload off, heap capped — ADR-036) |
| `steakos-vite.service` | **9001** | Vite SPA (`npm run dev`, proxies API) |

Companion prose: `steakOS-dev/docs/installation/OPERATOR-RUNBOOK.md` §4.

## Install (copy into `~/.config/systemd/user`)

```bash
cd /path/to/steakOS
./gradlew bootJar                    # once per SemVer bump
./scripts/install/systemd/install-user-units.sh
# optional — start now:
./scripts/install/systemd/install-user-units.sh --enable
```

Manual enable:

```bash
systemctl --user daemon-reload
systemctl --user enable --now steakos-api.service steakos-vite.service
loginctl enable-linger "$USER"       # survive logout / reboot
systemctl --user status steakos-api steakos-vite
journalctl --user -u steakos-api -u steakos-vite -f
```

## Overrides

Optional env files (created empty parent by installer):

- `~/.config/steakos/api.env` — `STEAKOS_JAR=…`, `JAVA_OPTS=…`, `GRAILS_ENV=…`
- `~/.config/steakos/vite.env`

Or: `systemctl --user edit steakos-api`.

## Notes

- **Not enabled by default** on install hosts — enabling is an operator choice.
- Dist package on `:9090` still uses `bin/steakos` (see QUICKSTART); these units are for **source / closed-beta lab** trees.
- After a version bump: rebuild `bootJar`, then `systemctl --user restart steakos-api`.
