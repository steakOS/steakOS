/**
 * Capture README screenshots from the local lab UI (exact routes).
 * Run from frontend/: node scripts/capture-readme-shots.mjs
 */
import { chromium } from 'playwright'
import fs from 'fs'
import path from 'path'

const OUT = path.resolve('../content/screenshots')
const BASE = process.env.STEAKOS_UI || 'http://127.0.0.1:9001'
const USER = process.env.STEAKOS_USER || 'admin'
/** Lab default matches PasswordPolicy.DEV_DEFAULT_PASSWORD — override via STEAKOS_PASS. */
const PASS = process.env.STEAKOS_PASS || 'Schn1tz3l3ck3$'

fs.mkdirSync(OUT, { recursive: true })

async function shot(page, file) {
  const dest = path.join(OUT, file)
  await page.screenshot({ path: dest, fullPage: false })
  console.log('wrote', dest, page.url())
}

async function closeOverlays(page) {
  for (let i = 0; i < 3; i++) {
    await page.keyboard.press('Escape').catch(() => {})
    await page.waitForTimeout(200)
  }
  const closers = [
    'button[aria-label="Close"]',
    'button:has-text("Close")',
    '.help-drawer button:has-text("Close")',
    '[data-testid="help-close"]'
  ]
  for (const sel of closers) {
    const loc = page.locator(sel).first()
    if (await loc.isVisible({ timeout: 400 }).catch(() => false)) {
      await loc.click().catch(() => {})
      await page.waitForTimeout(250)
    }
  }
}

async function goto(page, p) {
  await page.goto(BASE + p, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForTimeout(1200)
  await closeOverlays(page)
  await page.waitForTimeout(400)
}

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  deviceScaleFactor: 1
})
const page = await context.newPage()

try {
  // Login
  await goto(page, '/')
  await page.locator('input[name="username"], input#username, input[autocomplete="username"]').first().waitFor({ timeout: 20000 })
  await shot(page, '01-login.png')
  await page.locator('input[name="username"], input#username, input[autocomplete="username"]').first().fill(USER)
  await page.locator('input[name="password"], input#password, input[type="password"]').first().fill(PASS)
  await page.locator('button[type="submit"], button:has-text("Sign in"), button:has-text("Log in")').first().click()
  await page.waitForTimeout(2000)
  await closeOverlays(page)

  // Company / settings (company switch lives under Settings)
  await goto(page, '/settings')
  await shot(page, '02-select-company.png')

  // Action-NOW
  await goto(page, '/dashboard')
  await closeOverlays(page)
  await shot(page, '03-dashboard.png')

  // Help center (training / introduction stand-in)
  await goto(page, '/help')
  await shot(page, '04-training-tour.png')
  // Open first topic if present
  const topic = page.locator('a, button').filter({ hasText: /getting started|introduction|tour|welcome|desk/i }).first()
  if (await topic.isVisible({ timeout: 1500 }).catch(() => false)) {
    await topic.click()
    await page.waitForTimeout(800)
  }
  await shot(page, '04b-training-tour-step.png')

  // Setup wizard
  await goto(page, '/setup')
  await page.waitForTimeout(1000)
  await shot(page, '05-setup-wizard.png')

  // Sales desk
  await goto(page, '/sales')
  await closeOverlays(page)
  await shot(page, '06-ops-desk.png')

  // Finance invoices
  await goto(page, '/finance/invoices')
  await closeOverlays(page)
  await shot(page, '06b-finance-desk.png')

  // About
  await goto(page, '/about')
  await closeOverlays(page)
  await shot(page, '07-about.png')

  // Mobile
  await page.setViewportSize({ width: 390, height: 844 })
  await goto(page, '/dashboard')
  await closeOverlays(page)
  await shot(page, '09-mobile-dashboard.png')

  await context.clearCookies()
  await page.goto(BASE + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  await shot(page, '08-mobile-login.png')

  console.log('done')
} catch (e) {
  console.error('CAPTURE_FAILED', e)
  process.exitCode = 1
} finally {
  await browser.close()
}
