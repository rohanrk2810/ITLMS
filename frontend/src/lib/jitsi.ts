import { env } from './env'

/** How a scheduled class is delivered. Stored implicitly: no link = built-in, a Jitsi-host link = Jitsi, any other = external. */
export type ClassProvider = 'BUILTIN' | 'JITSI' | 'EXTERNAL'

/** A fresh, unguessable room on the Jitsi server - the name is the only thing keeping outsiders out. */
export function newJitsiRoomUrl(): string {
  return `https://${env.jitsiHost}/itilms-${crypto.randomUUID().replaceAll('-', '')}`
}

export function isJitsiUrl(url: string): boolean {
  try {
    return new URL(url).hostname === env.jitsiHost
  } catch {
    return false
  }
}

/** The room URL with the viewer's name filled in and the pre-join screen skipped. */
export function jitsiEmbedUrl(url: string, displayName: string): string {
  const settings = [
    `userInfo.displayName=${JSON.stringify(displayName)}`,
    'config.prejoinPageEnabled=false',
    'config.disableDeepLinking=true',
  ]
  return `${url}#${settings.join('&')}`
}
