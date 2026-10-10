/**
 * Heard & Cook Architects — filmed commercial walkthrough (Playwright).
 * Writes to demos/architects/ (walkthrough.gif/mp4, frames/, manifest.json).
 * Dense frames, word-wrapped hint boxes, 2.2s/2.4s per frame.
 *
 * Usage: cd frontend && node scripts/capture-hc-walkthrough.mjs
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

const OUT = resolveDemoOutDir(__dirname, 'architects')
const FRAMES = path.join(OUT, 'frames')
const BASE = process.env.STEAKOS_URL || 'http://127.0.0.1:9001'
const USER = process.env.STEAKOS_USER || 'admin'
const PASS = process.env.STEAKOS_PASS || 'Schn1tz3l3ck3$'
const COMPANY_LABEL = 'Heard & Cook Architects'

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

async function pickCompany(page) {
  await page.waitForTimeout(800)
  const pickerBtn = page.locator(`button:has-text("${COMPANY_LABEL}")`)
  if (page.url().includes('select-company') || (await page.locator('text=Choose company').count()) > 0) {
    await shot(page, 'Company picker', 'Multiple companies on this install — pick the architecture demo', 'text=Choose company')
    await shot(page, 'Heard & Cook option', 'Demo · Architecture — IRB studio seed, separate from the restaurant', `button:has-text("${COMPANY_LABEL}")`)
    await pickerBtn.first().click()
    await page.waitForTimeout(1200)
    await shot(page, 'Company selected', 'Session scoped to Heard & Cook Architects', null)
    return
  }
  const companySelect = page.locator('select[aria-label="Active company"]')
  if ((await companySelect.count()) > 0) {
    await shot(page, 'Top-bar company switch', 'Select Heard & Cook Architects (Demo · Architecture)', 'select[aria-label="Active company"]')
    const opts = await companySelect.locator('option').allTextContents()
    const idx = opts.findIndex((t) => /Heard|Cook|Architect/i.test(t))
    if (idx >= 0) {
      await companySelect.selectOption({ index: idx })
      await page.waitForTimeout(1500)
      await shot(page, 'Architecture demo active', 'Top bar shows Heard & Cook', 'select[aria-label="Active company"]')
    }
  }
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

  await page.goto(`${BASE}/login/auth`, { waitUntil: 'networkidle' })
  await shot(page, 'Open steakOS login', 'Admin sign-in to film the architecture demo company', '#username')
  await page.click('#username')
  await shot(page, 'Focus username', 'Same install credentials — separate company data', '#username')
  await page.fill('#username', USER)
  await shot(page, 'Username filled', `Signed in as ${USER}`, '#username')
  await page.click('#password')
  await shot(page, 'Focus password', 'Dev password meets the 12+ mixed complexity policy', '#password')
  await page.fill('#password', PASS)
  await shot(page, 'Credentials ready', 'Submit — TOTP disabled for filming only', '#submit')
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

  await pickCompany(page)
  await shot(page, 'Architecture demo loaded', 'Banner / top bar shows Heard & Cook Architects (IRB, FL · USD books)', '.banner-info, .content')

  // Capability
  await page.goto(`${BASE}/capability/projects`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  await shot(page, 'Capability · Projects', 'Seeded studio projects for the architecture kitchen-sink', '.content')
  await shot(page, 'Project list', 'Billionaire Kitchen Studio + Gulf Blvd Retail Pavilion', 'table.data tbody tr')
  const projRow = page.locator('table.data tbody tr').filter({ hasText: /Billionaire Kitchen/i }).first()
  if (await projRow.count()) {
    await shot(page, 'Billionaire Kitchen row', 'Active UHNW kitchen concept project', 'table.data tbody tr')
    await projRow.click()
    await page.waitForTimeout(700)
    await shot(page, 'Billionaire Kitchen Studio', 'Project detail for the Sand Key kitchen story', null)
    await shot(page, 'Project context set', 'Sales quote will use the matching UHNW client next', null)
  }

  // HR
  await page.goto(`${BASE}/hr`, { waitUntil: 'networkidle' }).catch(() => {})
  await page.waitForTimeout(700)
  await shot(page, 'HR desk', 'Studio roster lives under Candidates (hired, no logins)', '.content')
  const candTab = page.locator('button, [role="tab"], a').filter({ hasText: /Candidate/i }).first()
  if (await candTab.count()) {
    await clickShot(
      page,
      'button:has-text("Candidate"), [role="tab"]:has-text("Candidate"), a:has-text("Candidate")',
      'Open Candidates',
      'Maya Heard, Julian Cook, Sofia Reyes and the studio team',
      'Candidates list',
      'Demo personas only — not interactive logins',
    )
  }
  await shot(page, 'Studio personas', 'Architecture office story cast for eval demos', 'table.data tbody tr')

  // Sales
  await page.goto(`${BASE}/sales`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
  await shot(page, 'Sales desk', 'Architecture commercial rail — clients, quotes, orders (USD)', '.content')

  const custTab = page.locator('button, a, [role="tab"]').filter({ hasText: /Customer/i }).first()
  if (await custTab.count()) {
    await clickShot(
      page,
      'button:has-text("Customer"), [role="tab"]:has-text("Customer")',
      'Open Customers',
      'Use seeded Estate of Sand Key or create a film client',
      'Customers list',
      'Seed includes Clearwater Bay, Sand Key, Gulf Blvd…',
    )
  }

  const seededClient = 'Estate of Sand Key (UHNW)'
  let clientName = seededClient
  const seedRow = page.locator('table.data tbody tr').filter({ hasText: /Sand Key/i }).first()
  if (await seedRow.count()) {
    await shot(page, 'Sand Key in list', 'UHNW kitchen client from the demo seed pack', 'table.data tbody tr')
    await seedRow.click()
    await page.waitForTimeout(500)
    await shot(page, 'Estate of Sand Key selected', 'Family office client for the Billionaire Kitchen quote', null)
  } else {
    const nameInput = page.locator('form.form-grid input').first()
    await nameInput.waitFor({ state: 'visible' })
    clientName = `Film Client ${Date.now().toString().slice(-6)}`
    await shot(page, 'Client form empty', 'Seeded Sand Key missing — create a film client', 'form.form-grid input')
    await nameInput.fill(clientName)
    await shot(page, 'Client name entered', `Name: ${clientName}`, 'form.form-grid input')
    const email = page.locator('input[type="email"]').first()
    if (await email.count()) {
      try {
        await email.fill('film.client@heardcook.example')
        await shot(page, 'Client email entered', 'Optional contact on the client record', 'input[type="email"]')
      } catch {
        /* */
      }
    }
    await shot(page, 'Ready to save client', 'Submit creates the client in this demo only', 'button[type="submit"]')
    await page.locator('form.form-grid').filter({ has: page.locator('button[type="submit"]') }).first().locator('button[type="submit"]').click()
    await page.waitForTimeout(1200)
    await shot(page, 'Client created', 'Ready for a kitchen concept quote', null)
  }

  const quotesTab = page.locator('button, [role="tab"]').filter({ hasText: /^Quotes$|Quotes/i }).first()
  if (await quotesTab.count()) {
    await clickShot(
      page,
      'button:has-text("Quotes"), [role="tab"]:has-text("Quotes")',
      'Open Quotes',
      'Kitchen concept + layout services + Project Demo Video DVD',
      'Quote form',
      'Bind Sand Key, name the pack, add three lines',
    )
  }
  await page.waitForTimeout(400)
  await shot(page, 'New quote canvas', 'Architecture quote — services + media product', '.form-grid')

  const combo = page.locator('input[placeholder*="Customer" i], input[aria-label*="Customer" i], .customer-select input, [class*="CustomerSelect"] input').first()
  if (await combo.count()) {
    await combo.click()
    await shot(page, 'Customer picker open', 'Search Estate of Sand Key', null)
    await combo.fill(clientName.slice(0, 14))
    await page.waitForTimeout(500)
    await shot(page, 'Customer search', `Filtering for ${clientName.slice(0, 14)}…`, null)
    await page.keyboard.press('ArrowDown')
    await shot(page, 'Highlight client', 'Enter to bind the UHNW client', null)
    await page.keyboard.press('Enter')
    await shot(page, 'Client on quote', 'Quote owned by Estate of Sand Key', null)
  } else {
    const sel = page.locator('select').first()
    if (await sel.count()) {
      const options = await sel.locator('option').allTextContents()
      const idx = options.findIndex((t) => t.includes(clientName.slice(0, 10)) || /Sand Key/i.test(t))
      if (idx > 0) {
        await sel.selectOption({ index: idx })
        await shot(page, 'Client selected', 'Native select bound to Sand Key', 'select')
      }
    }
  }

  const qName = page.locator('label:has-text("Name") input, input[placeholder*="name" i]').first()
  if (await qName.count()) {
    try {
      await qName.fill('Sand Key — Billionaire Kitchen film pack')
      await shot(page, 'Quote named', 'Sand Key — Billionaire Kitchen film pack', null)
    } catch {
      /* */
    }
  }

  const addLineBtn = page.locator('button:has-text("Add line")').first()
  if (await addLineBtn.count()) {
    await shot(page, 'Add line control', 'Grow to three lines: 2 services + 1 goods DVD', 'button:has-text("Add line")')
    await addLineBtn.click()
    await page.waitForTimeout(200)
    await shot(page, 'Second line added', 'Next: third line for the video DVD', 'button:has-text("Add line")')
    await addLineBtn.click()
    await page.waitForTimeout(200)
    await shot(page, 'Three lines ready', 'Fill catalogue / qty / price on each card', '.doc-line-card')
  }

  const lineCards = page.locator('.doc-line-card')
  const lineData = [
    { name: 'Billionaire Kitchen Concept Studio', search: 'Billionaire Kitchen Concept', qty: '1', price: '95000' },
    { name: 'Kitchen Layout & Millwork Narrative', search: 'Kitchen Layout', qty: '1', price: '22000' },
    { name: 'Project Demo Video DVD', search: 'Project Demo Video', qty: '2', price: '49' },
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
      await page.waitForTimeout(500)
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
    if (await desc.count()) await desc.fill(lineData[i].name)
    const inputs = card.locator('input')
    const ic = await inputs.count()
    if (ic >= 3) {
      await inputs.nth(ic - 2).fill(lineData[i].qty)
      await inputs.nth(ic - 1).fill(lineData[i].price)
      await shot(page, `Line ${i + 1} qty & price`, `Qty ${lineData[i].qty} @ ${lineData[i].price} USD`, null)
    }
  }

  await shot(page, 'Quote complete (2 services + 1 product)', 'Kitchen Concept + Layout + Video DVD — save draft', 'button[type="submit"]')
  await page.locator('form.form-grid').filter({ hasText: /Quote|Customer|Lines/i }).locator('button[type="submit"]').first().click()
  await page.waitForTimeout(1800)
  await shot(page, 'Quote saved', 'Draft quote selected — next Accept → Order', 'button:has-text("Accept")')

  const acceptBtn = page.locator('button:has-text("Accept")').first()
  if (await acceptBtn.count()) {
    await clickShot(
      page,
      'button:has-text("Accept")',
      'Accept quote',
      'Converts the architecture quote into a DRAFT sales order',
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
      'Select the draft from Accept',
    )
  }
  await page.waitForTimeout(600)
  const orderRow = page.locator('table.data tbody tr').first()
  if (await orderRow.count()) {
    await shot(page, 'Order row visible', 'Click the draft order to load Confirm', 'table.data tbody tr')
    await orderRow.click()
    await page.waitForTimeout(700)
    await shot(page, 'Order detail', 'Confirm = commercial hand-off to Finance (USD)', 'button:has-text("Confirm")')
  }

  const confirmBtn = page.locator('button:has-text("Confirm")').first()
  if (await confirmBtn.count()) {
    await clickShot(
      page,
      'button:has-text("Confirm")',
      'Confirm order',
      'Creates DRAFT invoice — settlement is next in Finance',
      'Order confirmed',
      'Finance invoice link appears when ready',
    )
  }
  await shot(page, 'Post-confirm sales', 'Commercial path complete — Delivery context, then Finance settle', 'a:has-text("invoice")')

  await page.goto(`${BASE}/delivery`, { waitUntil: 'networkidle' }).catch(() => {})
  await page.waitForTimeout(700)
  await shot(
    page,
    'Delivery desk (context)',
    'Service fulfillment lives here; Sales Confirm already handed docs to Finance',
    null,
  )
  await shot(page, 'Delivery honesty note', 'No separate auto Service Order in this cut — Confirm is the product hand-off', null)

  await page.goto(`${BASE}/finance/invoices`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1000)
  await shot(page, 'Finance invoices (USD)', 'Open the draft created from the sales order', 'table.data tbody tr')
  const invRow = page.locator('table.data tbody tr').first()
  if (await invRow.count()) {
    await shot(page, 'Invoice list loaded', 'Newest draft should be the Sand Key film pack', 'table.data tbody tr')
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
      'Client can now be marked paid on a mock/SEPA rail',
      'Invoice issued',
      'Settle / record payment is available',
    )
  }

  const settleForm = page.locator('form').filter({ hasText: /settle|payment|Record payment/i }).first()
  if (await settleForm.count()) {
    await shot(page, 'Settle form', 'Mock rail posts Dr cash / Cr revenue for Heard & Cook only', 'button[type="submit"]')
    await settleForm.locator('button[type="submit"]').click()
    await page.waitForTimeout(1800)
  } else {
    const settleBtn = page.locator('button:has-text("Settle"), button:has-text("Record")').first()
    if (await settleBtn.count()) {
      await clickShot(
        page,
        'button:has-text("Settle"), button:has-text("Record")',
        'Settle payment',
        'Posts the automatic journal — restaurant demo untouched',
        'Settlement submitted',
        'Waiting for PAID status',
      )
    }
  }

  await shot(page, 'Payment settled', 'Journal entry created — books updated for Heard & Cook Architects only', '.banner-info, .content')
  await page.goto(`${BASE}/capability/projects`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(500)
  await shot(page, 'Back on Projects', 'Architecture story arc complete for this film', null)
  await shot(page, 'Walkthrough complete', 'Demo only — Reset Heard & Cook anytime in Settings (Orange Steak House untouched)', null)

  await browser.close()

  fs.writeFileSync(
    path.join(OUT, 'manifest.json'),
    JSON.stringify({ company: COMPANY_LABEL, base: BASE, user: USER, client: clientName, shots }, null, 2),
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
