import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const ui = new URL('../../../', import.meta.url).pathname
const localeDir = join(ui, 'packages/shared/locales')
const apps = {
  public: { sources: ['apps/public/src', 'packages/shared/src'], namespaces: ['common', 'lookup'] },
  workspace: { sources: ['apps/workspace/src', 'packages/shared/src'], namespaces: ['common', 'lookup', 'workspace'] },
}
const pluralSuffix = /_(zero|one|two|few|many|other)$/
const literal = /\bt\(\s*'([A-Za-z0-9_.-]+)'/g
const problems = []

function flatten(value, prefix, out) {
  for (const [key, child] of Object.entries(value)) {
    const path = prefix ? prefix + '.' + key : key
    if (child && typeof child === 'object') {
      flatten(child, path, out)
    } else {
      out.add(path)
    }
  }
  return out
}

function base(key) {
  return key.replace(pluralSuffix, '')
}

function sources(dir, out) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) {
      sources(path, out)
    } else if (/\.(ts|tsx)$/.test(name)) {
      out.push(path)
    }
  }
  return out
}

function load(code, namespace) {
  const name = code + '/' + namespace + '.json'
  const parsed = JSON.parse(readFileSync(join(localeDir, name), 'utf8'))
  if (typeof parsed._meta?.reviewed !== 'boolean') {
    problems.push(name + ': _meta.reviewed must be true or false')
  }
  delete parsed._meta
  return flatten(parsed, '', new Set())
}

const codes = readdirSync(localeDir).filter((name) => statSync(join(localeDir, name)).isDirectory())
const namespaces = readdirSync(join(localeDir, 'en')).map((name) => name.replace('.json', ''))
const english = {}
let total = 0
for (const namespace of namespaces) {
  english[namespace] = new Set([...load('en', namespace)].map(base))
  total += english[namespace].size
}

for (const code of codes) {
  for (const namespace of namespaces) {
    const keys = load(code, namespace)
    const bases = new Set([...keys].map(base))
    for (const key of english[namespace]) {
      if (!bases.has(key)) {
        problems.push(code + '/' + namespace + ': missing ' + key)
      }
    }
    for (const key of bases) {
      if (!english[namespace].has(key)) {
        problems.push(code + '/' + namespace + ': extra ' + key)
      }
    }
    for (const key of keys) {
      if (pluralSuffix.test(key) && !keys.has(base(key) + '_other')) {
        problems.push(code + '/' + namespace + ': ' + base(key) + ' has plural forms but no _other')
      }
    }
  }
}

for (const [index, first] of namespaces.entries()) {
  for (const second of namespaces.slice(index + 1)) {
    for (const key of english[first]) {
      if (english[second].has(key)) {
        problems.push(key + ' is in both ' + first + ' and ' + second)
      }
    }
  }
}

for (const [app, setup] of Object.entries(apps)) {
  const known = new Set(setup.namespaces.flatMap((namespace) => [...english[namespace]]))
  for (const dir of setup.sources) {
    for (const file of sources(join(ui, dir), [])) {
      const text = readFileSync(file, 'utf8')
      for (const match of text.matchAll(literal)) {
        if (!known.has(match[1]) && !match[1].endsWith('.')) {
          problems.push(app + ': ' + file.replace(ui, '') + ': unknown key ' + match[1])
        }
      }
    }
  }
}

if (problems.length > 0) {
  console.error(problems.join('\n'))
  process.exit(1)
}
console.log('i18n: ' + codes.join(', ') + ' match in ' + namespaces.join(', ') + ' (' + total + ' keys)')
