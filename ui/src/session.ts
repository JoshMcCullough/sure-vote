import { api, KeyedError } from './api'

export type Principal = {
  kind: 'system-admin' | 'official' | 'visitor'
  id: string
  tenantId: string | null
  name: string
}

type Challenge = {
  ceremonyId: string
  publicKey: Record<string, unknown>
}

type Descriptor = { id: string; type: string; transports?: string[] }

export async function currentSession(): Promise<Principal | null> {
  try {
    return await api<Principal>('GET', '/auth/session')
  } catch {
    return null
  }
}

export async function workspaceSession(): Promise<Principal> {
  const current = await currentSession()
  if (current && current.kind !== 'system-admin') {
    return current
  }
  if (current) {
    throw new KeyedError('errors.adminWorkspace')
  }
  return api<Principal>('POST', '/showcase/session')
}

export async function signOut(): Promise<void> {
  await api<null>('POST', '/auth/sign-out')
}

export async function devSignIn(kind: string, id: string, tenantId: string): Promise<Principal> {
  return api<Principal>('POST', '/auth/dev/sign-in', { kind, id, tenantId: tenantId || null })
}

export async function passkeySignIn(): Promise<Principal> {
  const challenge = await api<Challenge>('POST', '/auth/sign-in/start')
  const options = challenge.publicKey
  const publicKey: PublicKeyCredentialRequestOptions = {
    ...(options as object),
    challenge: bytes(options.challenge as string),
    allowCredentials: ((options.allowCredentials as Descriptor[] | undefined) ?? []).map(descriptor),
  }
  const credential = await navigator.credentials.get({ publicKey })
  return finish('/auth/sign-in/finish', challenge.ceremonyId, assertionJson(credential))
}

export async function enrollPasskey(token: string): Promise<Principal> {
  const challenge = await api<Challenge>('POST', '/auth/enrollments/start', { token })
  return register(challenge)
}

export async function addPasskey(): Promise<Principal> {
  const challenge = await api<Challenge>('POST', '/auth/passkeys/start')
  return register(challenge)
}

async function register(challenge: Challenge): Promise<Principal> {
  const options = challenge.publicKey
  const user = options.user as { id: string; name: string; displayName: string }
  const publicKey: PublicKeyCredentialCreationOptions = {
    ...(options as object),
    challenge: bytes(options.challenge as string),
    user: { ...user, id: bytes(user.id) },
    excludeCredentials: ((options.excludeCredentials as Descriptor[] | undefined) ?? []).map(descriptor),
  } as PublicKeyCredentialCreationOptions
  const credential = await navigator.credentials.create({ publicKey })
  return finish('/auth/passkeys/finish', challenge.ceremonyId, registrationJson(credential))
}

function finish(path: string, ceremonyId: string, credential: unknown): Promise<Principal> {
  return api<Principal>('POST', path, { ceremonyId, credential })
}

function registrationJson(credential: Credential | null): unknown {
  const key = required(credential)
  const response = key.response as AuthenticatorAttestationResponse
  return {
    type: key.type,
    id: key.id,
    rawId: text(key.rawId),
    response: {
      clientDataJSON: text(response.clientDataJSON),
      attestationObject: text(response.attestationObject),
      transports: response.getTransports ? response.getTransports() : [],
    },
    clientExtensionResults: key.getClientExtensionResults(),
  }
}

function assertionJson(credential: Credential | null): unknown {
  const key = required(credential)
  const response = key.response as AuthenticatorAssertionResponse
  return {
    type: key.type,
    id: key.id,
    rawId: text(key.rawId),
    response: {
      clientDataJSON: text(response.clientDataJSON),
      authenticatorData: text(response.authenticatorData),
      signature: text(response.signature),
      userHandle: response.userHandle ? text(response.userHandle) : null,
    },
    clientExtensionResults: key.getClientExtensionResults(),
  }
}

function required(credential: Credential | null): PublicKeyCredential {
  if (!credential) {
    throw new KeyedError('errors.noPasskey')
  }
  return credential as PublicKeyCredential
}

function descriptor(item: Descriptor): PublicKeyCredentialDescriptor {
  return { ...item, id: bytes(item.id) } as PublicKeyCredentialDescriptor
}

function bytes(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  const out = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    out[index] = binary.charCodeAt(index)
  }
  return out.buffer
}

function text(buffer: ArrayBuffer): string {
  const view = new Uint8Array(buffer)
  let binary = ''
  for (const value of view) {
    binary += String.fromCharCode(value)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
