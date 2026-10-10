# Orange Steak House — filmed walkthrough

**Outputs:** [`walkthrough.gif`](./walkthrough.gif) · [`walkthrough.mp4`](./walkthrough.mp4) · [`frames/`](./frames/) · [`manifest.json`](./manifest.json)

**Story:** Login → Orange Steak House → customer → quote (2 goods + 1 service) → Accept → Confirm → Finance issue/settle.

**Timing:** 2.2s/frame (GIF) · 2.4s/frame (MP4) · ~64 dense frames.

Detailed operator guide text lives in the private companion (usertesting docs), not in this application monorepo.

## Re-capture

```bash
# API :6060 + frontend :9001 up; optionally disable Admin TOTP for filming
cd frontend
node scripts/capture-osh-walkthrough.mjs
```

Writes here under `demos/steakhouse/`.
