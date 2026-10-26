import { mkdirSync } from 'node:fs'
import { AxeBuilder } from '@axe-core/playwright'
import { chromium } from 'playwright-core'

export const base = process.env.SUREVOTE_SMOKE_URL ?? 'http://127.0.0.1:8080'
export const timeout = 15000
const chrome = process.env.SUREVOTE_SMOKE_CHROME ?? '/usr/bin/google-chrome'
const shots = process.env.SUREVOTE_SMOKE_SHOTS ?? ''
const widths = [320, 390, 1280]
const shotWidths = { mobile: 390, desktop: 1280 }
const blocking = new Set(['serious', 'critical'])
const axeTags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']
export const failures = []
const audited = []

export async function audit(page, name) {
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

export async function layout(page, name) {
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

export async function check(page, name) {
  await audit(page, name)
  await layout(page, name)
}

export async function visit(page, path, name, heading) {
  await page.goto(base + path)
  await page.getByRole('heading', { level: 1, name: heading }).waitFor({ timeout })
  await page.waitForTimeout(200)
  await check(page, name)
}

export async function expectFocusOnHeading(page, link, heading) {
  await page.getByRole('navigation').getByRole('link', { name: link, exact: true }).click()
  await page.getByRole('heading', { level: 1, name: heading }).waitFor({ timeout })
  await page.waitForFunction(() => document.activeElement?.tagName === 'H1', null, { timeout })
  console.log('ok focus moves to the heading on ' + link)
}

export async function cardShot(page, locator, name) {
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

export async function mobileMenu(page, link) {
  await page.setViewportSize({ width: 390, height: 900 })
  const menu = page.getByRole('button', { name: 'Menu', exact: true })
  await menu.click()
  await page.getByRole('button', { name: 'Close the menu' }).waitFor({ timeout })
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: link }).waitFor({ timeout })
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

export async function recordedChoice(page, race, choice) {
  const group = page.locator('[data-race-votes]').filter({ has: page.getByRole('heading', { name: race, exact: true }) })
  await group.getByRole('listitem').filter({ hasText: choice }).first().waitFor({ timeout })
  const items = await page.locator('[data-race-votes] li').allTextContents()
  if (items.includes('Y')) {
    failures.push('lookup shows the raw value Y: ' + JSON.stringify(items))
  }
  console.log('ok recorded choice ' + JSON.stringify(items))
}

export async function expectStatus(page, method, path, status) {
  const response = await page.request.fetch(base + path, { method, failOnStatusCode: false })
  if (response.status() !== status) {
    failures.push(method + ' ' + path + ' returned ' + response.status() + ', expected ' + status)
  }
  else {
    console.log('ok ' + method + ' ' + path + ' is ' + status)
  }
}

export async function expectPolicy(page, path, formAction) {
  const response = await page.request.get(base + path)
  const headers = response.headers()
  const policy = headers['content-security-policy'] ?? ''
  for (const part of ["default-src 'none'", "script-src 'self'", "frame-ancestors 'none'", 'form-action ' + formAction]) {
    if (!policy.includes(part)) {
      failures.push(path + ' Content-Security-Policy lacks ' + part + ': ' + JSON.stringify(policy))
    }
  }
  if (headers['set-cookie']) {
    failures.push(path + ' sets a cookie')
  }
  console.log('ok policy ' + path)
}

export async function smoke(name, body) {
  if (shots) {
    mkdirSync(shots, { recursive: true })
  }
  const browser = await chromium.launch({ executablePath: chrome, headless: true, args: ['--no-sandbox'] })
  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'en-US' })
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: base })
    const page = await context.newPage()
    page.on('pageerror', (error) => failures.push('page error: ' + error.message))
    page.on('console', (message) => {
      if (message.text().includes('Content Security Policy')) {
        failures.push('CSP violation: ' + message.text())
      }
    })
    await body(page, context)
  } catch (error) {
    const page = browser.contexts()[0]?.pages()[0]
    if (page) {
      const banner = await page.locator('[data-error-banner]').allTextContents()
      await page.screenshot({ path: 'smoke-failure-' + name + '.png', fullPage: true })
      failures.push('banner: ' + JSON.stringify(banner) + ', screenshot smoke-failure-' + name + '.png')
    }
    failures.push(String(error))
  } finally {
    await browser.close()
  }
  if (failures.length > 0) {
    console.error(failures.join('\n'))
    process.exitCode = 1
    return
  }
  console.log('axe checked ' + audited.length + ' page states')
  console.log(name + ' smoke passed')
}
