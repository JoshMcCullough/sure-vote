import { chromium } from 'playwright-core'

const base = process.env.SUREVOTE_SMOKE_URL ?? 'http://127.0.0.1:8080'
const chrome = process.env.SUREVOTE_SMOKE_CHROME ?? '/usr/bin/google-chrome'
const timeout = 15000

const steps = [
  'Create locality',
  'Create address',
  'Create precinct',
  'Create seat',
  'Create term',
  'Create demonstration',
  'Create race',
  'Create candidate person',
  'Create voter',
  'Create candidate',
  'Add to the race',
  'Add ballot style',
  'Create place',
  'Create demonstration device',
  'Assign precinct',
  'Activate',
]

async function submit(page, label) {
  const button = page.getByRole('button', { name: label, exact: true })
  await button.waitFor({ state: 'visible', timeout })
  await page.waitForFunction(
    (name) => [...document.querySelectorAll('button')].some((b) => b.textContent === name && !b.disabled),
    label,
    { timeout },
  )
  const response = page.waitForResponse((r) => r.request().method() === 'POST' && !r.url().includes('/auth/'), {
    timeout,
  })
  await button.click()
  const done = await response
  if (done.status() >= 300) {
    throw new Error(label + ' returned ' + done.status() + ': ' + (await done.text()))
  }
}

async function run() {
  const browser = await chromium.launch({ executablePath: chrome, headless: true, args: ['--no-sandbox'] })
  const failures = []
  try {
    const page = await browser.newPage()
    page.on('pageerror', (error) => failures.push('page error: ' + error.message))
    await page.goto(base + '/app/workspace')
    await page.getByText('Demo tenant').first().waitFor({ timeout })
    for (const label of steps) {
      await submit(page, label)
      console.log('ok ' + label)
    }
    await submit(page, 'Cast ballot')
    const code = (await page.locator('p.code').textContent({ timeout }))?.trim() ?? ''
    if (code.length !== 43) {
      throw new Error('access code has length ' + code.length)
    }
    console.log('ok Cast ballot')
    await submit(page, 'Close')
    await page.getByText('Status historical').waitFor({ timeout })
    console.log('ok Close')
    await page.getByRole('button', { name: 'Load report' }).click()
    const row = page.locator('table.report tbody tr').first()
    await row.waitFor({ timeout })
    const cells = await row.locator('td').allTextContents()
    if (cells[1] !== 'Polling place' || cells[3] !== '1' || cells[4] !== '1' || cells[5] !== 'Yes') {
      throw new Error('unexpected reconciliation row ' + JSON.stringify(cells))
    }
    console.log('ok Reconciliation ' + JSON.stringify(cells))
    if (process.env.SUREVOTE_SMOKE_SCREENSHOT) {
      await page.locator('section[aria-labelledby="reconciliation-title"]').screenshot({
        path: process.env.SUREVOTE_SMOKE_SCREENSHOT,
      })
    }
    await page.getByRole('link', { name: 'Open the public lookup' }).click()
    await page.getByRole('button', { name: 'Look up' }).click()
    await page.getByText('Recorded ballot').waitFor({ timeout })
    console.log('ok Lookup')
  } catch (error) {
    const page = browser.contexts()[0]?.pages()[0]
    if (page) {
      const banner = await page.locator('.banner').allTextContents()
      await page.screenshot({ path: 'smoke-failure.png', fullPage: true })
      failures.push('banner: ' + JSON.stringify(banner) + ', screenshot smoke-failure.png')
    }
    failures.push(String(error))
  } finally {
    await browser.close()
  }
  if (failures.length > 0) {
    throw new Error(failures.join('\n'))
  }
}

run().then(
  () => console.log('smoke passed'),
  (error) => {
    console.error(error)
    process.exitCode = 1
  },
)
