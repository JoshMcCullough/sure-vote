import {
  base,
  check,
  expectPolicy,
  expectStatus,
  failures,
  mobileMenu,
  smoke,
  timeout,
  visit,
} from '../packages/shared/smoke/helpers.mjs'

const missingElection = '00000000-0000-4000-8000-000000000000'

async function english(page) {
  await page.goto(base + '/voter/?lang=en')
  await page.getByRole('heading', { level: 1, name: 'Check your ballot' }).waitFor({ timeout })
  await page.getByText('This lookup has no sign-in and sets no cookies.').waitFor({ timeout })
  await check(page, 'public-home')
  await mobileMenu(page, 'Lookup')
  await page.getByRole('button', { name: 'Open my ballot' }).click()
  await page.getByText('Enter a value for Access code.').first().waitFor({ timeout })
  await page.getByLabel('Access code').fill('not-a-real-code')
  await page.getByRole('button', { name: 'Open my ballot' }).click()
  await page.locator('.usa-alert--error').first().waitFor({ timeout })
  await check(page, 'public-home-error')
  await visit(page, '/voter/elections/' + missingElection, 'public-lookup-missing', 'Ballot lookup')
  await visit(page, '/voter/missing', 'public-not-found', 'Page not found')
}

async function spanish(page) {
  await page.goto(base + '/voter/')
  await page.getByRole('button', { name: 'Español' }).click()
  await page.getByRole('heading', { level: 1, name: 'Revise su boleta' }).waitFor({ timeout })
  const lang = await page.evaluate(() => document.documentElement.lang)
  if (lang !== 'es') {
    failures.push('html lang is ' + lang + ' after switching to Spanish')
  }
  await check(page, 'es-public-home')
  await visit(page, '/voter/missing', 'es-public-not-found', 'Página no encontrada')
  await page.getByRole('button', { name: 'English' }).click()
  await page.getByRole('heading', { level: 1 }).waitFor({ timeout })
}

async function surface(page) {
  await expectPolicy(page, '/voter/', "'none'")
  await expectStatus(page, 'GET', '/app/', 404)
  await expectStatus(page, 'GET', '/auth/session', 404)
  await expectStatus(page, 'GET', '/auth/config', 404)
  await expectStatus(page, 'POST', '/showcase/session', 404)
  await expectStatus(page, 'GET', '/workspace', 404)
  await expectStatus(page, 'POST', '/persons', 404)
  await expectStatus(page, 'GET', '/system/tenants', 404)
  await expectStatus(page, 'GET', '/health', 200)
}

smoke('public', async (page, context) => {
  await surface(page)
  await english(page)
  await spanish(page)
  const cookies = await context.cookies()
  if (cookies.length > 0) {
    failures.push('the public lookup set cookies: ' + cookies.map((cookie) => cookie.name).join(', '))
  }
  else {
    console.log('ok no cookies')
  }
})
