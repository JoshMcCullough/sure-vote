import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from '../locales/en/common.json'
import es from '../locales/es/common.json'

export const languages = [
  { code: 'en', name: 'English', reviewed: en._meta.reviewed },
  { code: 'es', name: 'Español', reviewed: es._meta.reviewed },
] as const

type Bundle = Record<string, object>

export type Resources = Record<Language, Bundle>

export type Language = (typeof languages)[number]['code']

const storageKey = 'surevote.lang'
const rtl = new Set(['ar', 'fa', 'he', 'ur'])

export function isRtl(code: string): boolean {
  return rtl.has(code.split('-')[0])
}

export function languageReviewed(code: string): boolean {
  const found = languages.find((language) => language.code === code)
  return found ? found.reviewed : true
}

function supported(code: string | null | undefined): Language | null {
  if (!code) {
    return null
  }
  const base = code.toLowerCase().split('-')[0]
  const found = languages.find((language) => language.code === base)
  return found ? found.code : null
}

function initialLanguage(): Language {
  const query = new URLSearchParams(window.location.search).get('lang')
  const stored = readStored()
  const preferred = navigator.languages.map(supported).find((code) => code !== null)
  return supported(query) ?? supported(stored) ?? preferred ?? 'en'
}

function readStored(): string | null {
  try {
    return localStorage.getItem(storageKey)
  } catch {
    return null
  }
}

function applyDocument(code: string) {
  const root = document.documentElement
  root.lang = code
  root.dir = isRtl(code) ? 'rtl' : 'ltr'
  try {
    localStorage.setItem(storageKey, code)
  } catch {
    root.dataset.langStorage = 'unavailable'
  }
}

export function startI18n(resources: Resources): void {
  const namespaces = Object.keys(resources.en)
  i18next.on('languageChanged', applyDocument)
  void i18next.use(initReactI18next).init({
    resources,
    ns: namespaces,
    defaultNS: namespaces[0],
    fallbackNS: namespaces.slice(1),
    lng: initialLanguage(),
    fallbackLng: 'en',
    supportedLngs: languages.map((language) => language.code),
    interpolation: { escapeValue: false },
    returnNull: false,
  })
}

export function formatDateTime(value: string | number | Date, language: string): string {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return new Intl.DateTimeFormat(language, { dateStyle: 'long', timeStyle: 'short' }).format(date)
}

export function formatNumber(value: number, language: string): string {
  return new Intl.NumberFormat(language).format(value)
}

export default i18next
