# Heard & Cook Architects — filmed walkthrough

**Outputs:** [`walkthrough.gif`](./walkthrough.gif) · [`walkthrough.mp4`](./walkthrough.mp4) · [`frames/`](./frames/) · [`manifest.json`](./manifest.json)

**Story:** Login → Heard & Cook → Capability (Billionaire Kitchen Studio) → HR roster → Estate of Sand Key → quote → Accept → Confirm → Finance issue/settle (USD).

**Timing:** 2.2s/frame (GIF) · 2.4s/frame (MP4) · ~67 dense frames.

Detailed operator guide text lives in the private companion (usertesting docs), not in this application monorepo.

## Re-capture

```bash
# API :6060 + frontend :9001 up; optionally disable Admin TOTP for filming
cd frontend
node scripts/capture-hc-walkthrough.mjs
```

Writes here under `demos/architects/`.
