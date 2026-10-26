import {
  base,
  check,
  expectFocusOnHeading,
  expectPolicy,
  expectStatus,
  failures,
  mobileMenu,
  smoke,
  timeout,
  visit,
} from '../packages/shared/smoke/helpers.mjs'

const admin = process.env.SUREVOTE_SMOKE_ADMIN_ID ?? ''

async function english(page) {
  await page.goto(base + '/app/?lang=en')
  await page.getByRole('heading', { level: 1, name: 'SureVote election workspace' }).waitFor({ timeout })
  await check(page, 'home')
  await mobileMenu(page, 'Workspace')
  await expectFocusOnHeading(page, 'Sign in', 'Sign in')
  await check(page, 'sign-in')
  await page.locator('#dev-kind').selectOption('system-admin')
  await page.locator('#dev-id').fill(admin)
  await page.getByRole('button', { name: 'Sign in without a passkey' }).click()
  await page.getByText('Signed in without a passkey.').waitFor({ timeout })
  console.log('ok dev sign-in as a system admin')
  await visit(page, '/app/admin', 'admin', 'Officials')
  await check(page, 'admin-signed-in')
  await visit(page, '/app/missing', 'not-found', 'Page not found')
}

async function spanish(page) {
  await page.goto(base + '/app/')
  await page.getByRole('button', { name: 'Español' }).click()
  await page.getByRole('heading', { level: 1, name: 'Espacio de trabajo electoral de SureVote' }).waitFor({ timeout })
  await check(page, 'es-home')
  await visit(page, '/app/sign-in', 'es-sign-in', 'Iniciar sesión')
  await visit(page, '/app/admin', 'es-admin', 'Funcionarios')
  await page.getByRole('button', { name: 'English' }).click()
  await page.getByRole('heading', { level: 1 }).waitFor({ timeout })
}

async function surface(page) {
  await expectPolicy(page, '/app/', "'self'")
  await expectStatus(page, 'GET', '/voter/', 404)
  await expectStatus(page, 'POST', '/showcase/session', 404)
  await expectStatus(page, 'GET', '/showcase/tenant', 404)
  await expectStatus(page, 'POST', '/lookup', 404)
  await expectStatus(page, 'GET', '/elections/00000000-0000-4000-8000-000000000000', 404)
}

smoke('workspace', async (page) => {
  if (!admin) {
    failures.push('SUREVOTE_SMOKE_ADMIN_ID is not set')
    return
  }
  await surface(page)
  await english(page)
  await spanish(page)
})
