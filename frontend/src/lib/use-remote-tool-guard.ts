import { useEffect, useRef, useState } from 'react'

import type { ViolationType } from '@/api/assessments'

/** What the desktop app (desktop/preload.js) offers the page. Absent in a browser. */
declare global {
  interface Window {
    sutraDesktop?: { remoteTools: () => Promise<string[]> }
  }
}

const CHECK_EVERY_MS = 15_000
/** The same finding is reported again no sooner than this, so a program left running does not flood the record. */
const REPORT_AGAIN_MS = 60_000

/** Whether this page runs inside the desktop app, the only place the check is possible. */
export function canCheckRemoteTools(): boolean {
  return typeof window !== 'undefined' && typeof window.sutraDesktop?.remoteTools === 'function'
}

async function lookForRemoteTools(): Promise<string[]> {
  try {
    return (await window.sutraDesktop?.remoteTools()) ?? []
  } catch {
    // The check failing must never stop a test.
    return []
  }
}

/**
 * Before a test starts: the names of remote-control programs running now (checked again every few seconds so the
 * student can close one and carry on). Empty in a browser and when none is found.
 */
export function useRemoteToolsBeforeStart(enabled: boolean): string[] {
  const [found, setFound] = useState<string[]>([])
  useEffect(() => {
    if (!enabled || !canCheckRemoteTools()) return
    let live = true
    const look = () => void lookForRemoteTools().then((names) => live && setFound(names))
    look()
    const timer = setInterval(look, 5_000)
    return () => {
      live = false
      clearInterval(timer)
    }
  }, [enabled])
  return found
}

/** During a secure test: looks every 15 seconds and tells the server when a remote-control program is running. */
export function useRemoteToolGuard(enabled: boolean, onViolation: (type: ViolationType, detail?: string) => void) {
  const report = useRef(onViolation)
  useEffect(() => {
    report.current = onViolation
  }, [onViolation])

  useEffect(() => {
    if (!enabled || !canCheckRemoteTools()) return
    let lastReported = 0
    const look = () =>
      void lookForRemoteTools().then((names) => {
        if (names.length === 0 || Date.now() - lastReported < REPORT_AGAIN_MS) return
        lastReported = Date.now()
        report.current('REMOTE_TOOL', names.join(', '))
      })
    look()
    const timer = setInterval(look, CHECK_EVERY_MS)
    return () => clearInterval(timer)
  }, [enabled])
}
