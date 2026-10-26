import { mkdirSync } from 'node:fs'
import { AxeBuilder } from '@axe-core/playwright'
import { chromium } from 'playwright-core'

const base = process.env.SUREVOTE_SMOKE_URL ?? 'http://127.0.0.1:8080'
const chrome = process.env.SUREVOTE_SMOKE_CHROME ?? '/usr/bin/google-chrome'
const shots = process.env.SUREVOTE_SMOKE_SHOTS ?? ''
const timeout = 15000
const widths = [320, 390, 1280]
const shotWidths = { mobile: 390, desktop: 1280 }
const blocking = new Set(['serious', 'critical'])
const axeTags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']
const failures = []
const audited = []

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

async function audit(page, name) {
  const result = await new AxeBuilder({ page }).withTags(axeTags).analyze()
  const serious = result.violations.filter((violation) => blocking.has(violation.impact))
  const other = result.violations.length - serious.length
  audited.push(name)
  if (result.passes.length === 0) {
    failures.push('axe ' + name + ': no rules ran')
  }
  for (const violation of serious) {
    const targets = violation.nodes.map((node) => node.target.join(' ')).slice(0, 5)
    failures.push('axe ' + name + ': ' + violation.impact + ' ' + violation.id + ' ' + JSON.stringify(targets))
  }
  console.log('ok axe ' + name + ' (' + result.passes.length + ' rules passed, ' + serious.length + ' serious or critical, ' + other + ' other)')
}

async function layout(page, name) {
  const original = page.viewportSize()
  for (const width of widths) {
    await page.setViewportSize({ width, height: 900 })
    await page.waitForTimeout(100)
    const sizes = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth])
    if (sizes[0] > sizes[1]) {
      failures.push('horizontal scroll on ' + name + ' at ' + width + 'px: ' + sizes[0] + ' > ' + sizes[1])
    }
  }
  if (shots) {
    for (const [label, width] of Object.entries(shotWidths)) {
      await page.setViewportSize({ width, height: 900 })
      await page.waitForTimeout(100)
      await page.screenshot({ path: shots + '/' + name + '-' + label + '.png', fullPage: true })
    }
  }
  await page.setViewportSize(original)
  console.log('ok layout ' + name)
}

async function check(page, name) {
  await audit(page, name)
  await layout(page, name)
}

async function visit(page, path, name, heading) {
  await page.goto(base + '/app' + path)
  await page.getByRole('heading', { level: 1, name: heading }).waitFor({ timeout })
  await page.waitForTimeout(200)
  await check(page, name)
}

async function expectFocusOnHeading(page, link, heading) {
  await page.getByRole('navigation').getByRole('link', { name: link, exact: true }).click()
  await page.getByRole('heading', { level: 1, name: heading }).waitFor({ timeout })
  await page.waitForFunction(() => document.activeElement?.tagName === 'H1', null, { timeout })
  console.log('ok focus moves to the heading on ' + link)
}

async function cardShot(page, locator, name) {
  if (!shots) {
    return
  }
  for (const [label, width] of Object.entries(shotWidths)) {
    await page.setViewportSize({ width, height: 900 })
    await locator.scrollIntoViewIfNeeded()
    await locator.screenshot({ path: shots + '/' + name + '-' + label + '.png' })
  }
  await page.setViewportSize({ width: 1280, height: 900 })
}

async function mobileMenu(page) {
  await page.setViewportSize({ width: 390, height: 900 })
  const menu = page.getByRole('button', { name: 'Menu', exact: true })
  await menu.click()
  await page.getByRole('button', { name: 'Close the menu' }).waitFor({ timeout })
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Workspace' }).waitFor({ timeout })
  await page.keyboard.press('Escape')
  await page.waitForFunction(() => !document.getElementById('primary-nav')?.classList.contains('is-visible'), null, {
    timeout,
  })
  const focused = await page.evaluate(() => document.activeElement?.textContent)
  if (focused !== 'Menu') {
    failures.push('focus did not return to the menu button, it is on ' + JSON.stringify(focused))
  }
  await page.setViewportSize({ width: 1280, height: 900 })
  console.log('ok mobile menu')
}

async function errorSummary(page) {
  const button = page.getByRole('button', { name: 'Create election with outside keys', exact: true })
  await page.waitForFunction(
    (name) => [...document.querySelectorAll('button')].some((b) => b.textContent === name && !b.disabled),
    'Create election with outside keys',
    { timeout },
  )
  await button.click()
  const summary = page.getByRole('heading', { name: 'There are 3 problems with this form' })
  await summary.waitFor({ timeout })
  await page
    .waitForFunction(() => document.activeElement?.classList.contains('usa-alert--error'), null, { timeout: 3000 })
    .catch(() => failures.push('error summary did not take focus'))
  console.log('ok error summary')
}

async function english(page) {
  await page.goto(base + '/app/?lang=en')
  await page.getByRole('heading', { level: 1 }).waitFor({ timeout })
  await check(page, 'home')
  await mobileMenu(page)
  await expectFocusOnHeading(page, 'Sign in', 'Sign in')
  await check(page, 'sign-in')
  await visit(page, '/admin', 'admin', 'Officials')
  await visit(page, '/missing', 'not-found', 'Page not found')
  await page.goto(base + '/app/workspace')
  await page.getByText('Demo tenant').first().waitFor({ timeout })
  await check(page, 'workspace-start')
  for (const label of steps.slice(0, 1)) {
    await submit(page, label)
  }
  await errorSummary(page)
  await audit(page, 'workspace-error-summary')
  for (const label of steps.slice(1)) {
    await submit(page, label)
    console.log('ok ' + label)
  }
  await submit(page, 'Cast ballot')
  const code = (await page.locator('[data-access-code]').textContent({ timeout }))?.trim() ?? ''
  if (code.length !== 43) {
    throw new Error('access code has length ' + code.length)
  }
  console.log('ok Cast ballot')
  await page.getByRole('button', { name: 'Copy access code' }).click()
  await page.getByRole('status').filter({ hasText: 'Access code copied to the clipboard.' }).waitFor({ timeout })
  const pasted = await page.evaluate(() => navigator.clipboard.readText())
  if (pasted !== code) {
    failures.push('clipboard holds ' + pasted.length + ' characters, expected the access code')
  }
  await page.getByRole('button', { name: 'Spell out the code' }).click()
  await page.getByText(/^Group 1: /).waitFor({ timeout })
  console.log('ok Copy and spell out')
  await check(page, 'workspace-access-code')
  await cardShot(page, page.locator('section').filter({ has: page.locator('[data-access-code]') }), 'access-code-card')
  await submit(page, 'Close')
  await page.getByText('Status: historical').waitFor({ timeout })
  console.log('ok Close')
  await page.getByRole('button', { name: 'Load report' }).click()
  const row = page.locator('table[data-report] tbody tr').first()
  await row.waitFor({ timeout })
  const cells = await row.locator('th, td').allTextContents()
  if (cells[1] !== 'Polling place' || cells[3] !== '1' || cells[4] !== '1' || cells[5] !== 'Yes') {
    throw new Error('unexpected reconciliation row ' + JSON.stringify(cells))
  }
  console.log('ok Reconciliation ' + JSON.stringify(cells))
  await check(page, 'reconciliation')
  await cardShot(page, page.locator('section[aria-labelledby="reconciliation-title"]'), 'reconciliation-card')
  await page.getByRole('link', { name: 'Open the public lookup' }).click()
  await page.getByRole('button', { name: 'Look up' }).click()
  await page.getByRole('heading', { name: 'Recorded ballot' }).waitFor({ timeout })
  console.log('ok Lookup')
  await check(page, 'lookup')
  return { code, lookup: page.url() }
}

async function spanish(page, found) {
  await page.goto(base + '/app/')
  await page.getByRole('button', { name: 'Español' }).click()
  await page.getByRole('heading', { level: 1, name: 'Abra una boleta o configure una elección' }).waitFor({ timeout })
  const lang = await page.evaluate(() => document.documentElement.lang)
  if (lang !== 'es') {
    failures.push('html lang is ' + lang + ' after switching to Spanish')
  }
  await check(page, 'es-home')
  await visit(page, '/sign-in', 'es-sign-in', 'Iniciar sesión')
  await visit(page, '/admin', 'es-admin', 'Funcionarios')
  await visit(page, '/missing', 'es-not-found', 'Página no encontrada')
  await visit(page, '/workspace', 'es-workspace', 'Configurar una elección')
  await page.getByRole('button', { name: 'Cargar el informe' }).click()
  await page.locator('table[data-report] tbody tr').first().waitFor({ timeout })
  await check(page, 'es-reconciliation')
  await page.goto(found.lookup)
  await page.getByLabel('Código de acceso').fill(found.code)
  await page.getByRole('button', { name: 'Consultar' }).click()
  await page.getByRole('heading', { name: 'Boleta registrada' }).waitFor({ timeout })
  await check(page, 'es-lookup')
  await page.getByRole('button', { name: 'English' }).click()
  await page.getByRole('heading', { level: 1 }).waitFor({ timeout })
}

async function run() {
  if (shots) {
    mkdirSync(shots, { recursive: true })
  }
  const browser = await chromium.launch({ executablePath: chrome, headless: true, args: ['--no-sandbox'] })
  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'en-US' })
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: base })
    const page = await context.newPage()
    page.on('pageerror', (error) => failures.push('page error: ' + error.message))
    const found = await english(page)
    await spanish(page, found)
  } catch (error) {
    const page = browser.contexts()[0]?.pages()[0]
    if (page) {
      const banner = await page.locator('[data-error-banner]').allTextContents()
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
  console.log('axe checked ' + audited.length + ' page states')
}

run().then(
  () => console.log('smoke passed'),
  (error) => {
    console.error(error)
    process.exitCode = 1
  },
)
