#!/usr/bin/env node
/**
 * Structural key parity: EN vs target locale JSON trees.
 * Usage: node scripts/locale-parity.mjs --lang=es
 *        node scripts/locale-parity.mjs --lang=de --paths=src/locales
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = path.resolve(__dirname, '..')

function parseArgs(argv) {
  const out = { lang: null, base: 'en', paths: ['src/locales'] }
  for (const a of argv) {
    if (a.startsWith('--lang=')) out.lang = a.slice(7)
    else if (a.startsWith('--base=')) out.base = a.slice(7)
    else if (a.startsWith('--paths=')) out.paths = a.slice(8).split(',').filter(Boolean)
  }
  return out
}

function keys(obj, prefix = '', out = new Set()) {
  if (Array.isArray(obj)) {
    obj.forEach((v, i) => keys(v, `${prefix}[${i}]`, out))
    return out
  }
  if (obj && typeof obj === 'object') {
    for (const [k, v] of Object.entries(obj)) {
      const p = prefix ? `${prefix}.${k}` : k
      out.add(p)
      keys(v, p, out)
    }
  }
  return out
}

function listJson(dir) {
  if (!fs.existsSync(dir)) return []
  return fs.readdirSync(dir).filter((n) => n.endsWith('.json')).sort()
}

function main() {
  const opts = parseArgs(process.argv.slice(2))
  if (!opts.lang) {
    console.error('Usage: node scripts/locale-parity.mjs --lang=es')
    process.exit(1)
  }

  let drift = 0
  for (const root of opts.paths) {
    const baseDir = path.join(FRONTEND_ROOT, root, opts.base)
    const langDir = path.join(FRONTEND_ROOT, root, opts.lang)
    const baseFiles = listJson(baseDir)
    const langFiles = new Set(listJson(langDir))

    for (const name of baseFiles) {
      if (!langFiles.has(name)) {
        console.error(`MISSING FILE ${root}/${opts.lang}/${name}`)
        drift++
        continue
      }
      const en = JSON.parse(fs.readFileSync(path.join(baseDir, name), 'utf8'))
      const xx = JSON.parse(fs.readFileSync(path.join(langDir, name), 'utf8'))
      const ke = keys(en)
      const kx = keys(xx)
      const missing = [...ke].filter((k) => !kx.has(k)).sort()
      const extra = [...kx].filter((k) => !ke.has(k)).sort()
      if (missing.length || extra.length) {
        console.error(`${name}: missing=${missing.length} extra=${extra.length}`)
        missing.slice(0, 8).forEach((k) => console.error(`  - ${k}`))
        extra.slice(0, 8).forEach((k) => console.error(`  + ${k}`))
        drift += missing.length + extra.length
      } else {
        console.log(`OK ${name} (${ke.size} keys)`)
      }
    }
    for (const name of langFiles) {
      if (!baseFiles.includes(name)) {
        console.error(`EXTRA FILE ${root}/${opts.lang}/${name}`)
        drift++
      }
    }
  }

  if (drift) {
    console.error(`PARITY FAIL drift=${drift}`)
    process.exit(1)
  }
  console.log(`PARITY OK lang=${opts.lang}`)
}

main()
