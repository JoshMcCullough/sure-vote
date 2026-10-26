import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const dist = new URL('../dist/', import.meta.url).pathname
const locales = new URL('../../../packages/shared/locales/', import.meta.url).pathname
const forbidden = ['/auth/', '/system/', '/showcase/', '/officials', 'surevote_session', 'document.cookie', 'navigator.credentials']
const problems = []

function files(dir, out) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) {
      files(path, out)
    } else if (/\.(js|html|css)$/.test(name)) {
      out.push(path)
    }
  }
  return out
}

function strings(value, out) {
  for (const child of Object.values(value)) {
    if (child && typeof child === 'object') {
      strings(child, out)
    } else if (typeof child === 'string' && child.length >= 24) {
      out.push(child)
    }
  }
  return out
}

const text = files(dist, []).map((path) => readFileSync(path, 'utf8')).join('\n')
for (const needle of forbidden) {
  if (text.includes(needle)) {
    problems.push('the public bundle contains ' + needle)
  }
}
for (const code of readdirSync(locales)) {
  const workspace = JSON.parse(readFileSync(join(locales, code, 'workspace.json'), 'utf8'))
  delete workspace._meta
  for (const value of strings(workspace, [])) {
    const sample = value.split('{{')[0].slice(0, 40)
    if (sample.length >= 24 && text.includes(sample)) {
      problems.push('the public bundle carries a workspace string (' + code + '): ' + sample)
    }
  }
}

if (problems.length > 0) {
  console.error(problems.join('\n'))
  process.exit(1)
}
console.log('public bundle: no session code, no cookies, no workspace strings')
