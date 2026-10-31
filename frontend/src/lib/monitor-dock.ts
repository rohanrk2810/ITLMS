import { useSyncExternalStore } from 'react'

/**
 * Where the student's own camera and microphone boxes sit while a test, a live class or a lesson is monitored, and whether
 * the camera box is tucked away to the right edge. One setting for all of them, remembered in this browser, so a
 * student who moved it out of the way does not have to do it again on the next page.
 */
export interface MonitorDock {
  /** Top or bottom of the window. */
  side: 'top' | 'bottom'
  /** The camera box is slid out of sight; only its small tab shows. The camera keeps running and being watched. */
  collapsed: boolean
}

const KEY = 'itilms.monitorDock'

function read(): MonitorDock {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<MonitorDock> | null
    if (saved && (saved.side === 'top' || saved.side === 'bottom')) {
      return { side: saved.side, collapsed: saved.collapsed === true }
    }
  } catch {
    // Storage blocked or unreadable: the defaults are fine.
  }
  return { side: 'bottom', collapsed: false }
}

let current: MonitorDock = read()
const listeners = new Set<() => void>()

function update(next: Partial<MonitorDock>) {
  current = { ...current, ...next }
  try {
    localStorage.setItem(KEY, JSON.stringify(current))
  } catch {
    // Not remembered; it still works for this visit.
  }
  listeners.forEach((listener) => listener())
}

export const monitorDock = {
  toggleCollapsed: () => update({ collapsed: !current.collapsed }),
  toggleSide: () => update({ side: current.side === 'top' ? 'bottom' : 'top' }),
}

export function useMonitorDock(): MonitorDock {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => current,
  )
}
