/**
 * Orange Steak House — filmed commercial walkthrough (Playwright).
 * Writes to demos/steakhouse/ (walkthrough.gif/mp4, frames/, manifest.json).
 * Dense frames, word-wrapped hint boxes, 2.2s/2.4s per frame.
 *
 * Usage: cd frontend && node scripts/capture-osh-walkthrough.mjs
 */
import { chromium } from 'playwright'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

async function loadAnnotate() {
  const candidates = [
    path.join(__dirname, 'annotate-frame.mjs'),
    path.resolve(__dirname, '../../docs/usertesting/scripts/annotate-frame.mjs'),
    path.resolve(__dirname, '../docs/usertesting/scripts/annotate-frame.mjs'),
  ]
  for (const c of candidates) {
    if (fs.existsSync(c)) return import(pathToFileURL(c).href)
  }
  throw new Error('annotate-frame.mjs not found')
}

const { annotateFrame, buildGifMp4, resolveDemoOutDir } = await loadAnnotate()

const OUT = resolveDemoOutDir(__dirname, 'steakhouse')
const FRAMES = path.join(OUT, 'frames')
const BASE = process.env.STEAKOS_URL || 'http://127.0.0.1:9001'
const USER = process.env.STEAKOS_USER || 'admin'
const PASS = process.env.STEAKOS_PASS || 'Schn1tz3l3ck3$'

fs.mkdirSync(FRAMES, { recursive: true })
for (const f of fs.readdirSync(FRAMES)) {
  if (f.endsWith('.png') || f === 'list.txt') fs.unlinkSync(path.join(FRAMES, f))
}

let step = 0
const shots = []

async function shot(page, title, hint, cursorSel = null) {
  step += 1
  const raw = path.join(FRAMES, `raw-${String(step).padStart(2, '0')}.png`)
  const out = path.join(FRAMES, `step-${String(step).padStart(2, '0')}.png`)
  await page.waitForTimeout(280)
  await page.screenshot({ path: raw, fullPage: false })

  let cx = 720
  let cy = 450
  if (cursorSel) {
    try {
      const box = await page.locator(cursorSel).first().boundingBox()
      if (box) {
        cx = Math.round(box.x + box.width / 2)
        cy = Math.round(box.y + Math.min(box.height / 2, 28))
      }
    } catch {
      /* keep center */
    }
  }

  annotateFrame(raw, out, { step, title, hint, cx, cy })
  shots.push({ step, title, hint, file: out })
  console.log(`shot ${step}: ${title}`)
  return out
}

async function clickShot(page, selector, title, hint, afterTitle, afterHint) {
  await page.locator(selector).first().waitFor({ state: 'visible', timeout: 15000 })
  await shot(page, title, hint, selector)
  await page.locator(selector).first().click()
  await page.waitForTimeout(600)
  if (afterTitle) await shot(page, afterTitle, afterHint || 'Page updating…', null)
}

async function main() {
  console.log('OUT=', OUT)
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  })
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
  })
  const page = await context.newPage()
  page.setDefaultTimeout(20000)

  // --- Login (dense) ---
  await page.goto(`${BASE}/login/auth`, { waitUntil: 'networkidle' })
  await shot(page, 'Open steakOS login', 'Admin sign-in for a full write walkthrough on the restaurant demo', '#username')
  await page.click('#username')
  await shot(page, 'Focus username', 'Type the Admin username', '#username')
  await page.fill('#username', USER)
  await shot(page, 'Username filled', `Signed in as ${USER}`, '#username')
  await page.click('#password')
  await shot(page, 'Focus password', 'Dev password meets the 12+ mixed complexity policy', '#password')
  await page.fill('#password', PASS)
  await shot(page, 'Credentials ready', 'Submit to authenticate — TOTP off for filming only', '#submit')
  await Promise.all([
    page.waitForURL(/\/(select-company|dashboard|work|sales|totp|$)/, { timeout: 30000 }).catch(() => {}),
    page.click('#submit'),
  ])
  await page.waitForTimeout(1500)
  await shot(page, 'Login submitted', 'Waiting for company picker or app shell', null)
  if (!page.url().includes('9001') && page.url().includes('6060')) {
    await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
    await shot(page, 'Follow to Vite app', 'SPA loads on :9001 after Grails session cookie', null)
  }
  await page.waitForTimeout(1000)

  if (page.url().includes('/totp')) {
    await shot(page, 'TOTP gate (unexpected)', 'Disable totp_enabled for filming or enter a code', null)
    throw new Error('TOTP required — clear admin totp_enabled for headless filming')
  }

  // Company picker
  await page.waitForTimeout(800)
  if (page.url().includes('select-company') || (await page.locator('text=Choose company').count()) > 0) {
    await shot(page, 'Company picker', 'This install has multiple companies — pick the safe demo', 'text=Choose company')
    await shot(page, 'Orange Steak House option', 'Demo · Restaurant — kitchen-sink seed, not live books', 'button:has-text("Orange Steak House")')
    await page.locator('button:has-text("Orange Steak House")').first().click()
    await page.waitForTimeout(1200)
    await shot(page, 'Company selected', 'Session scoped to Orange Steak House', null)
  } else {
    const companySelect = page.locator('select[aria-label="Active company"]')
    if ((await companySelect.count()) > 0) {
      await shot(page, 'Top-bar company switch', 'Confirm Demo restaurant is active', 'select[aria-label="Active company"]')
      const opts = await companySelect.locator('option').allTextContents()
      const demoOpt = opts.findIndex((t) => /Orange|Demo/i.test(t))
      if (demoOpt >= 0) {
        await companySelect.selectOption({ index: demoOpt })
        await page.waitForTimeout(1500)
        await shot(page, 'Demo company active', 'Top bar shows Orange Steak House', 'select[aria-label="Active company"]')
      }
    }
  }

  await shot(page, 'Demo shell loaded', 'Banner / context confirms restaurant showcase data', '.banner-info, .content')

  // --- Sales hub ---
  await page.goto(`${BASE}/sales`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await shot(page, 'Sales desk', 'Commercial rail: customers, quotes, orders', '.content')

  const custTab = page.locator('button, a, [role="tab"]').filter({ hasText: /Customer/i }).first()
  if (await custTab.count()) {
    await clickShot(
      page,
      'button:has-text("Customer"), [role="tab"]:has-text("Customer")',
      'Open Customers tab',
      'Create a new guest for this private dining story',
      'Customers list',
      'Form above the table creates a new customer',
    )
  }

  const nameInput = page.locator('form.form-grid input').first()
  await nameInput.waitFor({ state: 'visible' })
  const custName = `Film Guest ${Date.now().toString().slice(-6)}`
  await shot(page, 'Customer form empty', 'Fill name (and email if present) then save', 'form.form-grid input')
  await nameInput.click()
  await shot(page, 'Name field focused', 'Guest name for the Friday private dining pack', 'form.form-grid input')
  await nameInput.fill(custName)
  await shot(page, 'Guest name entered', `Name: ${custName}`, 'form.form-grid input')

  const email = page.locator('input[type="email"]').first()
  if (await email.count()) {
    try {
      await email.fill('film.guest@orangesteak.example')
      await shot(page, 'Email entered', 'Optional contact on the customer record', 'input[type="email"]')
    } catch {
      /* optional */
    }
  }

  await shot(page, 'Ready to save customer', 'Submit creates the customer in this demo only', 'form.form-grid button[type="submit"]')
  await page.locator('form.form-grid').filter({ has: page.locator('button[type="submit"]') }).first().locator('button[type="submit"]').click()
  await page.waitForTimeout(1200)
  await shot(page, 'Customer created', 'Guest appears in the list — next: Quotes', 'table.data tbody tr')

  // --- Quotes ---
  const quotesTab = page.locator('button, [role="tab"]').filter({ hasText: /^Quotes$|Quotes/i }).first()
  if (await quotesTab.count()) {
    await clickShot(
      page,
      'button:has-text("Quotes"), [role="tab"]:has-text("Quotes")',
      'Open Quotes tab',
      'Build a quote with 2 goods + 1 service',
      'Quote form',
      'Pick customer, name the quote, add catalogue lines',
    )
  }
  await page.waitForTimeout(400)
  await shot(page, 'New quote canvas', 'Wire customer + lines before saving the draft', '.form-grid')

  const combo = page.locator('input[placeholder*="Customer" i], input[aria-label*="Customer" i], .customer-select input, [class*="CustomerSelect"] input').first()
  if (await combo.count()) {
    await combo.click()
    await shot(page, 'Customer picker open', 'Search for the film guest just created', null)
    await combo.fill(custName.slice(0, 12))
    await page.waitForTimeout(500)
    await shot(page, 'Customer search', `Filtering for ${custName.slice(0, 12)}…`, null)
    await page.keyboard.press('ArrowDown')
    await shot(page, 'Highlight customer', 'Press Enter to bind the customer to this quote', null)
    await page.keyboard.press('Enter')
    await shot(page, 'Customer on quote', 'Quote is owned by the film guest', null)
  } else {
    const sel = page.locator('select').first()
    if (await sel.count()) {
      const options = await sel.locator('option').allTextContents()
      const idx = options.findIndex((t) => t.includes(custName.slice(0, 8)))
      if (idx > 0) {
        await sel.selectOption({ index: idx })
        await shot(page, 'Customer selected', 'Native select bound to film guest', 'select')
      }
    }
  }

  const qName = page.locator('label:has-text("Name") input, input[placeholder*="name" i]').first()
  if (await qName.count()) {
    try {
      await qName.fill('Private dining Friday — film pack')
      await shot(page, 'Quote named', 'Private dining Friday — film pack', null)
    } catch {
      /* */
    }
  }

  const addLineBtn = page.locator('button:has-text("Add line")').first()
  if (await addLineBtn.count()) {
    await shot(page, 'Add line control', 'Grow the quote to three commercial lines', 'button:has-text("Add line")')
    await addLineBtn.click()
    await page.waitForTimeout(200)
    await shot(page, 'Second line added', 'One more line for the service pairing', 'button:has-text("Add line")')
    await addLineBtn.click()
    await page.waitForTimeout(200)
    await shot(page, 'Three lines ready', 'Fill catalogue / qty / price on each card', '.doc-line-card')
  }

  const lineCards = page.locator('.doc-line-card')
  const lineData = [
    { name: 'Ribeye Steak 300g', search: 'Ribeye', qty: '4', price: '42' },
    { name: 'Filet Mignon 200g', search: 'Filet', qty: '2', price: '48' },
    { name: 'Wine Pairing Flight', search: 'Wine Pairing', qty: '1', price: '55' },
  ]
  const n = Math.min(await lineCards.count(), 3)
  for (let i = 0; i < n; i++) {
    const card = lineCards.nth(i)
    await shot(page, `Line ${i + 1} empty`, `Fill ${lineData[i].name}`, null)
    const catIn = card.locator('input').first()
    if (await catIn.count()) {
      await catIn.click()
      await catIn.fill('')
      await catIn.fill(lineData[i].search)
      await page.waitForTimeout(450)
      await shot(page, `Line ${i + 1} catalogue search`, `Looking up ${lineData[i].search}`, null)
      const opt = page
        .locator('[role="option"], .dropdown-item, li')
        .filter({ hasText: new RegExp(lineData[i].search.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i') })
        .first()
      if (await opt.count()) {
        await opt.click()
        await page.waitForTimeout(200)
        await shot(page, `Line ${i + 1} catalogue picked`, lineData[i].name, null)
      }
    }
    const desc = card.locator('input').nth(1)
    if (await desc.count()) {
      await desc.fill(lineData[i].name)
    }
    const inputs = card.locator('input')
    const ic = await inputs.count()
    if (ic >= 3) {
      await inputs.nth(ic - 2).fill(lineData[i].qty)
      await inputs.nth(ic - 1).fill(lineData[i].price)
      await shot(page, `Line ${i + 1} qty & price`, `Qty ${lineData[i].qty} @ ${lineData[i].price}`, null)
    }
  }

  await shot(page, 'Quote complete (2 goods + 1 service)', 'Ribeye, Filet (goods) + Wine Pairing (service) — save draft', 'button[type="submit"]')
  await page.locator('form.form-grid').filter({ hasText: /Quote|Customer|Lines/i }).locator('button[type="submit"]').first().click()
  await page.waitForTimeout(1800)
  await shot(page, 'Quote saved', 'Draft quote is selected — next Accept → Order', 'button:has-text("Accept")')

  const acceptBtn = page.locator('button:has-text("Accept")').first()
  if (await acceptBtn.count()) {
    await clickShot(
      page,
      'button:has-text("Accept")',
      'Accept quote',
      'Converts the quote into a DRAFT sales order',
      'Quote accepted',
      'Order created — open Orders to confirm',
    )
  }

  const ordersTab = page.locator('button, [role="tab"]').filter({ hasText: /Orders/i }).first()
  if (await ordersTab.count()) {
    await clickShot(
      page,
      'button:has-text("Orders"), [role="tab"]:has-text("Orders")',
      'Open Orders',
      'Confirm order to create a Finance draft invoice',
      'Orders list',
      'Select the draft order from Accept',
    )
  }
  await page.waitForTimeout(600)
  const orderRow = page.locator('table.data tbody tr').first()
  if (await orderRow.count()) {
    await shot(page, 'Order row visible', 'Click the draft order to load actions', 'table.data tbody tr')
    await orderRow.click()
    await page.waitForTimeout(700)
    await shot(page, 'Order detail', 'Confirm = commercial hand-off to Finance', 'button:has-text("Confirm")')
  }

  const confirmBtn = page.locator('button:has-text("Confirm")').first()
  if (await confirmBtn.count()) {
    await clickShot(
      page,
      'button:has-text("Confirm")',
      'Confirm order',
      'Creates DRAFT invoice — settlement comes next in Finance',
      'Order confirmed',
      'Finance invoice link appears when the draft is ready',
    )
  }
  await shot(page, 'Post-confirm sales', 'Commercial path complete — optional Delivery desk, then Finance', 'a:has-text("invoice")')

  await page.goto(`${BASE}/delivery`, { waitUntil: 'networkidle' }).catch(() => {})
  await page.waitForTimeout(700)
  await shot(
    page,
    'Delivery desk (context)',
    'Service-order fulfillment lives here; Sales Confirm already handed commercial docs to Finance',
    null,
  )
  await shot(page, 'Delivery honesty note', 'No separate Sales→Delivery auto SO in this cut — Confirm is the product hand-off', null)

  // Finance
  await page.goto(`${BASE}/finance/invoices`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1000)
  await shot(page, 'Finance invoices', 'Open the draft created from the sales order', 'table.data tbody tr')
  const invRow = page.locator('table.data tbody tr').first()
  if (await invRow.count()) {
    await shot(page, 'Invoice list loaded', 'Newest draft should be the film pack', 'table.data tbody tr')
    await invRow.click()
    await page.waitForTimeout(700)
    await shot(page, 'Draft invoice selected', 'Issue opens payment rails, then settle posts the journal', 'button:has-text("Issue")')
  }

  const issueBtn = page.locator('button:has-text("Issue")').first()
  if (await issueBtn.count()) {
    await clickShot(
      page,
      'button:has-text("Issue")',
      'Issue invoice',
      'Customer can now be marked paid on a mock/SEPA rail',
      'Invoice issued',
      'Settle / record payment is available',
    )
  }

  const settleForm = page.locator('form').filter({ hasText: /settle|payment|Record payment/i }).first()
  if (await settleForm.count()) {
    await shot(page, 'Settle form', 'Mock rail posts Dr cash / Cr revenue automatically', 'button[type="submit"]')
    await settleForm.locator('button[type="submit"]').click()
    await page.waitForTimeout(1800)
  } else {
    const settleBtn = page.locator('button:has-text("Settle"), button:has-text("Record")').first()
    if (await settleBtn.count()) {
      await clickShot(
        page,
        'button:has-text("Settle"), button:has-text("Record")',
        'Settle payment',
        'Posts the automatic journal for Orange Steak House',
        'Settlement submitted',
        'Waiting for PAID status',
      )
    }
  }

  await shot(page, 'Payment settled', 'Journal entry created — books updated for Orange Steak House only', '.banner-info, .content')
  await page.goto(`${BASE}/sales`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(500)
  await shot(page, 'Back on Sales', 'Full Quote → Order → Invoice → Settle path filmed', null)
  await shot(page, 'Walkthrough complete', 'Demo only — Admin can Reset Orange Steak House anytime in Settings', null)

  await browser.close()

  fs.writeFileSync(
    path.join(OUT, 'manifest.json'),
    JSON.stringify({ company: 'Orange Steak House', base: BASE, user: USER, customer: custName, shots }, null, 2),
  )

  buildGifMp4(
    shots.map((s) => s.file),
    {
      gifPath: path.join(OUT, 'walkthrough.gif'),
      mp4Path: path.join(OUT, 'walkthrough.mp4'),
      listFile: path.join(FRAMES, 'list.txt'),
    },
  )

  console.log('Done. Frames:', shots.length)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
