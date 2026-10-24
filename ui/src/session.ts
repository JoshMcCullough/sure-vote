const actorKey = 'surevote.actor'
const tenantKey = 'surevote.tenant'

export function actorId(): string {
  const existing = localStorage.getItem(actorKey)
  if (existing) {
    return existing
  }
  const created = crypto.randomUUID()
  localStorage.setItem(actorKey, created)
  return created
}

export function tenantId(): string {
  return localStorage.getItem(tenantKey) ?? ''
}

export function rememberTenant(id: string): void {
  localStorage.setItem(tenantKey, id)
}
