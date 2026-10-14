# deploy/

Packaging entrypoints for steakOS.

- `Dockerfile` — container image build for the API (and related packaging)
- [`systemd/`](./systemd/) — pointer to packaged **systemd --user** units for API `:6060` + Vite `:9001` (OI-V06); canonical files under `scripts/install/systemd/`
