/** The video id of a YouTube link (watch, youtu.be, embed, shorts or live); null for anything else. */
export function youtubeIdOf(raw: string): string | null {
  let url: URL
  try {
    const trimmed = raw.trim()
    url = new URL(/^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`)
  } catch {
    return null
  }
  const host = url.hostname.replace(/^(www|m|music)\./, '')
  if (host === 'youtu.be') return url.pathname.slice(1) || null
  if (host !== 'youtube.com') return null
  const id = url.searchParams.get('v')
  if (id) return id
  return url.pathname.match(/^\/(?:embed|shorts|live)\/([\w-]+)/)?.[1] ?? null
}
