import {
  audit,
  base,
  cardShot,
  check,
  expectFocusOnHeading,
  expectPolicy,
  expectStatus,
  failures,
  mobileMenu,
  recordedChoice,
  smoke,
  timeout,
  visit,
} from '../packages/shared/smoke/helpers.mjs'

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
  await page.getByRole('heading', { level: 1, name: 'SureVote election workspace' }).waitFor({ timeout })
  await page.getByText('This demonstration runs only in the demo tenant.').waitFor({ timeout })
  await check(page, 'demo-home')
  await mobileMenu(page, 'Workspace')
  const officials = await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Sign in' }).count()
  if (officials !== 0) {
    failures.push('the demo shows the official sign-in link')
  }
  await expectFocusOnHeading(page, 'Workspace', 'Configure an election')
  await visit(page, '/app/missing', 'demo-not-found', 'Page not found')
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
  await recordedChoice(page, 'Mayor', 'Ada One')
  console.log('ok Lookup in the demo workspace')
  await check(page, 'lookup')
  const electionId = page.url().split('/elections/')[1]
  return { code, electionId }
}

async function voterApp(page, found) {
  await page.goto(base + '/voter/?lang=en')
  await page.getByRole('heading', { level: 1, name: 'Check your ballot' }).waitFor({ timeout })
  await page.getByLabel('Access code').fill(found.code)
  await page.getByRole('button', { name: 'Open my ballot' }).click()
  await page.getByRole('button', { name: 'Look up' }).click()
  await page.getByRole('heading', { name: 'Recorded ballot' }).waitFor({ timeout })
  await recordedChoice(page, 'Mayor', 'Ada One')
  await check(page, 'voter-lookup')
  console.log('ok public app lookup on the demo host')
}

async function spanish(page, found) {
  await page.goto(base + '/app/')
  await page.getByRole('button', { name: 'Español' }).click()
  await page.getByRole('heading', { level: 1, name: 'Espacio de trabajo electoral de SureVote' }).waitFor({ timeout })
  const lang = await page.evaluate(() => document.documentElement.lang)
  if (lang !== 'es') {
    failures.push('html lang is ' + lang + ' after switching to Spanish')
  }
  await check(page, 'es-demo-home')
  await visit(page, '/app/missing', 'es-demo-not-found', 'Página no encontrada')
  await visit(page, '/app/workspace', 'es-workspace', 'Configurar una elección')
  await page.getByRole('button', { name: 'Cargar el informe' }).click()
  await page.locator('table[data-report] tbody tr').first().waitFor({ timeout })
  await check(page, 'es-reconciliation')
  await page.goto(base + '/app/elections/' + found.electionId)
  await page.getByLabel('Código de acceso').fill(found.code)
  await page.getByRole('button', { name: 'Consultar' }).click()
  await page.getByRole('heading', { name: 'Boleta registrada' }).waitFor({ timeout })
  await recordedChoice(page, 'Mayor', 'Ada One')
  await check(page, 'es-lookup')
  await page.goto(base + '/voter/')
  await page.getByRole('heading', { level: 1, name: 'Revise su boleta' }).waitFor({ timeout })
  await page.getByLabel('Código de acceso').fill(found.code)
  await page.getByRole('button', { name: 'Abrir mi boleta' }).click()
  await page.getByRole('button', { name: 'Consultar' }).click()
  await page.getByRole('heading', { name: 'Boleta registrada' }).waitFor({ timeout })
  await recordedChoice(page, 'Mayor', 'Ada One')
  await check(page, 'es-voter-lookup')
  await page.getByRole('button', { name: 'English' }).click()
  await page.getByRole('heading', { level: 1 }).waitFor({ timeout })
}

async function surface(page) {
  await expectPolicy(page, '/app/', "'self'")
  await expectPolicy(page, '/voter/', "'none'")
  await expectStatus(page, 'GET', '/system/tenants', 404)
  await expectStatus(page, 'POST', '/auth/dev/sign-in', 404)
  await expectStatus(page, 'POST', '/auth/sign-in/start', 404)
  await expectStatus(page, 'POST', '/ballots', 404)
}

smoke('demo', async (page) => {
  await surface(page)
  const found = await english(page)
  await voterApp(page, found)
  await spanish(page, found)
})
