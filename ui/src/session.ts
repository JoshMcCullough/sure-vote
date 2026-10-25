const actorKey = 'surevote.actor'

export function actorId(): string {
  const existing = localStorage.getItem(actorKey)
  if (existing) {
    return existing
  }
  const created = crypto.randomUUID()
  localStorage.setItem(actorKey, created)
  return created
}
