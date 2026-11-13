# deploy/

Packaging entrypoints for steakOS.

- `Dockerfile` — API image on Eclipse Temurin 21, running as user `steakos`. After `./gradlew bootJar`, build with `./scripts/docker-tag.sh --build`.
- [`systemd/`](./systemd/) — pointer to packaged **systemd --user** units for API `:6060` + Vite `:9001` (OI-V06); canonical files under `scripts/install/systemd/`
