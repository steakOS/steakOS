#!/usr/bin/env node
/**
 * Resume-safe EN→target locale machine-translate (concept: I18N-LOCALE-MT-RESILIENCE).
 *
 * - Single-flight lock (no parallel MT jobs)
 * - Per-file atomic write + stamps under .locale-translate-state/
 * - Min interval between calls + exponential backoff on HTTP 429
 * - Placeholder + glossary protection
 *
 * Usage:
 *   node scripts/locale-translate.mjs --lang=es --paths=src/locales/es
 *   node scripts/locale-translate.mjs --lang=es --paths=public/tour/es --force
 *   node scripts/locale-translate.mjs --lang=es --mark-done=src/locales/es/processes.json
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = path.resolve(__dirname, '..')
const LOCK_PATH = path.join(FRONTEND_ROOT, '.locale-translate.lock')
const STATE_ROOT = path.join(FRONTEND_ROOT, '.locale-translate-state')

const PLACEHOLDER_RE = /(\{\{[^}]+\}\}|\{[a-zA-Z0-9_.]+\}|\{[0-9]+\})/g
const SKIP_KEYS = new Set(['code', 'id', 'icon', 'href', 'url', 'path', 'key', 'ns', 'tierCodes'])
const GLOSSARY = [
  'steakOS', 'APQC', 'BTC', 'BTCPay', 'Nostr', 'Hermes', 'DEMO', 'LIVE',
  'Bronze', 'Silver', 'Gold', 'Admin', 'CEO', 'Office', 'Ops', 'Finance',
  'SYSTEM', 'HUMAN', 'AGENTIC', 'HITL'
]

function parseArgs(argv) {
  const out = {
    lang: null,
    paths: [],
    force: false,
    markDone: [],
    minIntervalMs: 250,
    maxAttempts: 8,
    from: 'en'
  }
  for (const a of argv) {
    if (a === '--force') out.force = true
    else if (a.startsWith('--lang=')) out.lang = a.slice(7)
    else if (a.startsWith('--from=')) out.from = a.slice(7)
    else if (a.startsWith('--paths=')) out.paths.push(...a.slice(8).split(',').filter(Boolean))
    else if (a.startsWith('--mark-done=')) out.markDone.push(a.slice(12))
    else if (a.startsWith('--min-interval-ms=')) out.minIntervalMs = Number(a.slice(18)) || 250
    else if (a.startsWith('--max-attempts=')) out.maxAttempts = Number(a.slice(15)) || 8
    else if (!a.startsWith('-')) out.paths.push(a)
  }
  return out
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

function acquireLock() {
  try {
    const fd = fs.openSync(LOCK_PATH, 'wx')
    const payload = {
      pid: process.pid,
      startedAt: new Date().toISOString(),
      cwd: process.cwd()
    }
    fs.writeFileSync(fd, JSON.stringify(payload, null, 2))
    fs.closeSync(fd)
  } catch (e) {
    if (e.code === 'EEXIST') {
      let who = ''
      try { who = fs.readFileSync(LOCK_PATH, 'utf8') } catch { /* ignore */ }
      console.error('REFUSED: another locale-translate is running (single-flight lock).')
      console.error('Lock file:', LOCK_PATH)
      if (who) console.error(who)
      console.error('If the process is dead: rm', LOCK_PATH)
      process.exit(2)
    }
    throw e
  }
}

function releaseLock() {
  try { fs.unlinkSync(LOCK_PATH) } catch { /* ignore */ }
}

function stampPath(lang, fileAbs) {
  const rel = path.relative(FRONTEND_ROOT, fileAbs).replace(/[\\/]/g, '__')
  return path.join(STATE_ROOT, lang, `${rel}.stamp`)
}

function isStamped(lang, fileAbs) {
  return fs.existsSync(stampPath(lang, fileAbs))
}

function writeStamp(lang, fileAbs, meta = {}) {
  const sp = stampPath(lang, fileAbs)
  fs.mkdirSync(path.dirname(sp), { recursive: true })
  fs.writeFileSync(sp, JSON.stringify({
    file: path.relative(FRONTEND_ROOT, fileAbs),
    at: new Date().toISOString(),
    ...meta
  }, null, 2) + '\n')
}

function collectFiles(entry, out = []) {
  const abs = path.resolve(FRONTEND_ROOT, entry)
  if (!fs.existsSync(abs)) throw new Error(`path not found: ${abs}`)
  const st = fs.statSync(abs)
  if (st.isDirectory()) {
    for (const name of fs.readdirSync(abs).sort()) {
      if (name.startsWith('.')) continue
      collectFiles(path.join(abs, name), out)
    }
  } else if (abs.endsWith('.json')) {
    out.push(abs)
  }
  return out
}

function protect(s) {
  const tokens = []
  let protectedStr = s
  for (const g of GLOSSARY) {
    const re = new RegExp(g.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')
    protectedStr = protectedStr.replace(re, (m) => {
      const i = tokens.length
      tokens.push(m)
      return `⟦${i}⟧`
    })
  }
  protectedStr = protectedStr.replace(PLACEHOLDER_RE, (m) => {
    const i = tokens.length
    tokens.push(m)
    return `⟦${i}⟧`
  })
  return { protectedStr, tokens }
}

function restore(s, tokens) {
  return String(s || '')
    .replace(/⟦\s*(\d+)\s*⟧/g, (_, n) => tokens[Number(n)] ?? _)
    .replace(/\[\[\s*(\d+)\s*\]\]/g, (_, n) => tokens[Number(n)] ?? _)
}

let lastCallAt = 0
const stats = { translated: 0, cached: 0, skipped: 0, failed: 0, files: 0 }
const cache = new Map()

async function gtxTranslate(text, from, to, maxAttempts, minIntervalMs) {
  if (text.length > 1600) {
    const parts = []
    let buf = []
    let n = 0
    for (const para of text.split('\n')) {
      if (n + para.length + 1 > 1500 && buf.length) {
        parts.push(buf.join('\n'))
        buf = [para]
        n = para.length
      } else {
        buf.push(para)
        n += para.length + 1
      }
    }
    if (buf.length) parts.push(buf.join('\n'))
    const out = []
    for (const p of parts) out.push(await gtxTranslate(p, from, to, maxAttempts, minIntervalMs))
    return out.join('\n')
  }

  const url = 'https://translate.googleapis.com/translate_a/single?' + new URLSearchParams({
    client: 'gtx',
    sl: from,
    tl: to,
    dt: 't',
    q: text
  })

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const waitMin = Math.max(0, minIntervalMs - (Date.now() - lastCallAt))
    if (waitMin) await sleep(waitMin)
    lastCallAt = Date.now()
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': 'steakOS-locale-translate/1.0' },
        signal: AbortSignal.timeout(25000)
      })
      if (res.status === 429) {
        const backoff = Math.min(120_000, 2000 * (2 ** (attempt - 1)))
        console.error(`429 rate limit — backoff ${Math.round(backoff / 1000)}s (attempt ${attempt}/${maxAttempts})`)
        await sleep(backoff)
        continue
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      const chunks = []
      for (const row of data[0] || []) {
        if (row && row[0]) chunks.push(row[0])
      }
      return chunks.join('')
    } catch (e) {
      const msg = e.message || String(e)
      const backoff = Math.min(60_000, 800 * attempt * attempt)
      console.error(`retry ${attempt}/${maxAttempts}: ${msg} — wait ${Math.round(backoff / 1000)}s`)
      await sleep(backoff)
    }
  }
  throw new Error(`translate failed after ${maxAttempts} attempts`)
}

async function tr(text, ctx) {
  if (typeof text !== 'string') return text
  const trimmed = text.trim()
  if (!trimmed) return text
  if (/^[A-Z0-9_./:-]{1,39}$/.test(trimmed)) {
    stats.skipped++
    return text
  }
  if (cache.has(text)) {
    stats.cached++
    return cache.get(text)
  }
  const { protectedStr, tokens } = protect(text)
  try {
    const raw = await gtxTranslate(protectedStr, ctx.from, ctx.to, ctx.maxAttempts, ctx.minIntervalMs)
    const out = restore(raw, tokens)
    cache.set(text, out)
    stats.translated++
    if (stats.translated % 25 === 0) console.error(`… ${stats.translated} strings`)
    return out
  } catch (e) {
    stats.failed++
    console.error(`FAIL leave EN: ${e.message}`)
    cache.set(text, text)
    return text
  }
}

async function walk(node, key, ctx) {
  if (Array.isArray(node)) {
    const out = []
    for (const item of node) out.push(await walk(item, key, ctx))
    return out
  }
  if (node && typeof node === 'object') {
    const out = {}
    for (const [k, v] of Object.entries(node)) {
      if (SKIP_KEYS.has(k) && typeof v === 'string') {
        out[k] = v
        stats.skipped++
      } else {
        out[k] = await walk(v, k, ctx)
      }
    }
    return out
  }
  if (typeof node === 'string') return tr(node, ctx)
  return node
}
async function translateFile(fileAbs, opts) {
  if (!opts.force && isStamped(opts.lang, fileAbs)) {
    console.error(`SKIP stamped ${path.relative(FRONTEND_ROOT, fileAbs)}`)
    return
  }
  console.error(`FILE ${path.relative(FRONTEND_ROOT, fileAbs)}`)
  const raw = JSON.parse(fs.readFileSync(fileAbs, 'utf8'))
  const out = await walk(raw, '', {
    from: opts.from,
    to: opts.lang,
    maxAttempts: opts.maxAttempts,
    minIntervalMs: opts.minIntervalMs
  })
  const tmp = `${fileAbs}.tmp`
  fs.writeFileSync(tmp, JSON.stringify(out, null, 2) + '\n')
  fs.renameSync(tmp, fileAbs)
  writeStamp(opts.lang, fileAbs, {
    translated: stats.translated,
    failed: stats.failed
  })
  stats.files++
  console.error(`OK ${path.relative(FRONTEND_ROOT, fileAbs)}`)
}

async function main() {
  const opts = parseArgs(process.argv.slice(2))
  if (!opts.lang) {
    console.error('Usage: node scripts/locale-translate.mjs --lang=es --paths=src/locales/es[,public/tour/es]')
    process.exit(1)
  }

  if (opts.markDone.length) {
    for (const p of opts.markDone) {
      const abs = path.resolve(FRONTEND_ROOT, p)
      writeStamp(opts.lang, abs, { manual: true })
      console.error('marked done', path.relative(FRONTEND_ROOT, abs))
    }
    return
  }

  if (!opts.paths.length) {
    console.error('Provide --paths=… (e.g. src/locales/es)')
    process.exit(1)
  }

  acquireLock()
  process.on('exit', releaseLock)
  process.on('SIGINT', () => { releaseLock(); process.exit(130) })
  process.on('SIGTERM', () => { releaseLock(); process.exit(143) })

  const files = []
  for (const p of opts.paths) collectFiles(p, files)
  files.sort((a, b) => {
    const score = (f) => (f.includes(`${path.sep}common.json`) ? 0 : f.includes(`${path.sep}locales${path.sep}`) ? 1 : 2)
    return score(a) - score(b) || a.localeCompare(b)
  })

  console.error(`locale-translate lang=${opts.lang} files=${files.length} minIntervalMs=${opts.minIntervalMs}`)
  for (const f of files) {
    await translateFile(f, opts)
  }
  console.error(`DONE files=${stats.files} translated=${stats.translated} cached=${stats.cached} skipped=${stats.skipped} failed=${stats.failed}`)
  if (stats.failed > 0) process.exitCode = 1
}

main().catch((e) => {
  console.error(e)
  releaseLock()
  process.exit(1)
})
