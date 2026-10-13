#!/usr/bin/env node
/**
 * Copy EN locale JSON tree to a new language folder (no translate).
 * Usage: node scripts/locale-scaffold.mjs --lang=es
 *        node scripts/locale-scaffold.mjs --lang=es --also-tour
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = path.resolve(__dirname, '..')

function parseArgs(argv) {
  const out = { lang: null, alsoTour: false, force: false }
  for (const a of argv) {
    if (a.startsWith('--lang=')) out.lang = a.slice(7)
    else if (a === '--also-tour') out.alsoTour = true
    else if (a === '--force') out.force = true
  }
  return out
}

function copyDir(src, dest, force) {
  if (fs.existsSync(dest) && !force) {
    console.error(`exists (use --force): ${dest}`)
    return false
  }
  fs.mkdirSync(dest, { recursive: true })
  for (const name of fs.readdirSync(src)) {
    if (name.startsWith('.')) continue
    const s = path.join(src, name)
    const d = path.join(dest, name)
    if (fs.statSync(s).isDirectory()) copyDir(s, d, true)
    else fs.copyFileSync(s, d)
  }
  return true
}

function main() {
  const opts = parseArgs(process.argv.slice(2))
  if (!opts.lang || opts.lang === 'en') {
    console.error('Usage: node scripts/locale-scaffold.mjs --lang=es [--also-tour] [--force]')
    process.exit(1)
  }
  const pairs = [
    [path.join(FRONTEND_ROOT, 'src/locales/en'), path.join(FRONTEND_ROOT, 'src/locales', opts.lang)]
  ]
  if (opts.alsoTour) {
    pairs.push([
      path.join(FRONTEND_ROOT, 'public/tour/en'),
      path.join(FRONTEND_ROOT, 'public/tour', opts.lang)
    ])
  }
  for (const [src, dest] of pairs) {
    if (!fs.existsSync(src)) throw new Error(`missing ${src}`)
    if (copyDir(src, dest, opts.force)) console.log(`scaffolded ${path.relative(FRONTEND_ROOT, dest)}`)
  }
}

main()
