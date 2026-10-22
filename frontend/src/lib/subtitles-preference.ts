import { useSyncExternalStore } from 'react'

const KEY = 'itilms.subtitles'
const listeners = new Set<() => void>()

/** Subtitles are on unless the person turned them off. Storage can be blocked, which just means the choice is not kept. */
function read(): boolean {
  try {
    return localStorage.getItem(KEY) !== 'off'
  } catch {
    return true
  }
}

let current = read()

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function setSubtitles(on: boolean) {
  current = on
  try {
    localStorage.setItem(KEY, on ? 'on' : 'off')
  } catch {
    // The choice still holds for this visit.
  }
  listeners.forEach((listener) => listener())
}

/** Whether this viewer wants subtitles, shared by the button and the captions it controls. */
export function useSubtitlesOn(): boolean {
  return useSyncExternalStore(subscribe, () => current, () => true)
}
