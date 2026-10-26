import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const root = new URL('../src/', import.meta.url).pathname
const localeDir = join(root, 'locales')
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

const locales = {}
for (const name of readdirSync(localeDir).filter((file) => file.endsWith('.json'))) {
  const parsed = JSON.parse(readFileSync(join(localeDir, name), 'utf8'))
  if (typeof parsed._meta?.reviewed !== 'boolean') {
    problems.push(name + ': _meta.reviewed must be true or false')
  }
  delete parsed._meta
  locales[name.replace('.json', '')] = flatten(parsed, '', new Set())
}

const english = new Set([...locales.en].map(base))
for (const [code, keys] of Object.entries(locales)) {
  const bases = new Set([...keys].map(base))
  for (const key of english) {
    if (!bases.has(key)) {
      problems.push(code + ': missing ' + key)
    }
  }
  for (const key of bases) {
    if (!english.has(key)) {
      problems.push(code + ': extra ' + key)
    }
  }
  for (const key of keys) {
    if (pluralSuffix.test(key) && !keys.has(base(key) + '_other')) {
      problems.push(code + ': ' + base(key) + ' has plural forms but no _other')
    }
  }
}

for (const file of sources(root, [])) {
  const text = readFileSync(file, 'utf8')
  for (const match of text.matchAll(literal)) {
    if (!english.has(match[1]) && !match[1].endsWith('.')) {
      problems.push(file.replace(root, 'src/') + ': unknown key ' + match[1])
    }
  }
}

if (problems.length > 0) {
  console.error(problems.join('\n'))
  process.exit(1)
}
console.log('i18n: ' + Object.keys(locales).join(', ') + ' match (' + english.size + ' keys)')
