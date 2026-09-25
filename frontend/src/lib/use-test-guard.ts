import { useEffect, useRef } from 'react'

import type { ViolationType } from '@/api/assessments'

/** Anything inside an element with this attribute may be copied from and pasted into (the code editor). */
export const CLIPBOARD_OK_ATTRIBUTE = 'data-clipboard-ok'

const BLUR_GRACE_MS = 400
/** Entering or leaving fullscreen makes browsers blur the window for a moment; that is not the student leaving. */
const FULLSCREEN_SETTLE_MS = 1200
/** The same recorded-only event is reported at most this often, so holding a key does not flood the server. */
const REPEAT_MS = 5000

export function isFullscreen(): boolean {
  return document.fullscreenElement != null
}

/** Must be called from a click or key press: browsers refuse fullscreen otherwise. */
export async function enterFullscreen(): Promise<boolean> {
  try {
    await document.documentElement.requestFullscreen()
    return true
  } catch {
    return false
  }
}

export async function leaveFullscreen(): Promise<void> {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
  } catch {
    // Already out, or the browser refused; nothing to do.
  }
}

function insideClipboardZone(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest(`[${CLIPBOARD_OK_ATTRIBUTE}]`) != null
}

interface TestGuardOptions {
  enabled: boolean
  /** Told about everything noticed. The server decides which of it counts. */
  onViolation: (type: ViolationType, detail?: string) => void
  /** Told when the page enters or leaves fullscreen, so it can ask the student to go back in. */
  onFullscreenChange?: (fullscreen: boolean) => void
}

/**
 * Watches a secure test for the ways a student leaves it: another tab or window, leaving fullscreen,
 * copying, right-clicking, developer-tool and print shortcuts. It blocks what a browser lets a page block
 * and reports everything; it cannot stop a screenshot tool, a second device or a determined person, and
 * does not pretend to. The server is the one that counts and decides.
 */
export function useTestGuard({ enabled, onViolation, onFullscreenChange }: TestGuardOptions) {
  const report = useRef(onViolation)
  const fullscreenCallback = useRef(onFullscreenChange)
  useEffect(() => {
    report.current = onViolation
    fullscreenCallback.current = onFullscreenChange
  }, [onViolation, onFullscreenChange])

  useEffect(() => {
    if (!enabled) return

    const lastReported: Partial<Record<ViolationType, number>> = {}
    let fullscreenChangedAt = 0
    let blurTimer: ReturnType<typeof setTimeout> | undefined

    /** Counted kinds go straight through (the server merges duplicates); recorded-only kinds are rate limited here. */
    function send(type: ViolationType, detail?: string) {
      const now = Date.now()
      const recordedOnly = type !== 'TAB_SWITCH' && type !== 'WINDOW_BLUR'
      if (recordedOnly && now - (lastReported[type] ?? 0) < REPEAT_MS) return
      lastReported[type] = now
      report.current(type, detail)
    }

    function onVisibility() {
      if (document.hidden) send('TAB_SWITCH', 'The test tab was hidden')
    }

    function onBlur() {
      clearTimeout(blurTimer)
      blurTimer = setTimeout(() => {
        const settling = Date.now() - fullscreenChangedAt < FULLSCREEN_SETTLE_MS
        if (!document.hidden && !document.hasFocus() && !settling) {
          send('WINDOW_BLUR', 'The test window lost focus')
        }
      }, BLUR_GRACE_MS)
    }

    function onFocus() {
      clearTimeout(blurTimer)
    }

    function onFullscreen() {
      fullscreenChangedAt = Date.now()
      const inFullscreen = isFullscreen()
      fullscreenCallback.current?.(inFullscreen)
      if (!inFullscreen) send('FULLSCREEN_EXIT', 'Left fullscreen')
    }

    function onCopyOrCut(event: ClipboardEvent) {
      if (insideClipboardZone(event.target)) return
      event.preventDefault()
      send('COPY_ATTEMPT', event.type === 'cut' ? 'Tried to cut' : 'Tried to copy')
    }

    function onPaste(event: ClipboardEvent) {
      if (!insideClipboardZone(event.target)) {
        event.preventDefault()
        send('PASTE_ATTEMPT', 'Tried to paste outside the code editor')
        return
      }
      // Pasting into the code editor is allowed - editing needs it - but it is recorded for the trainer.
      send('PASTE_ATTEMPT', 'Pasted into the code editor')
    }

    function onContextMenu(event: MouseEvent) {
      if (insideClipboardZone(event.target)) return
      event.preventDefault()
      send('RIGHT_CLICK', 'Right-clicked')
    }

    function onSelectOrDrag(event: Event) {
      const target = event.target
      const editable = target instanceof Element && target.closest('input, textarea, [contenteditable="true"]') != null
      if (!editable && !insideClipboardZone(target)) event.preventDefault()
    }

    function onKeyDown(event: KeyboardEvent) {
      const key = event.key.toLowerCase()
      const ctrl = event.ctrlKey || event.metaKey
      const inEditor = insideClipboardZone(event.target)

      const devTools = key === 'f12' || (ctrl && event.shiftKey && ['i', 'j', 'c'].includes(key))
      const blockedEverywhere = ctrl && ['p', 's', 'u'].includes(key)
      const selectAllOutsideEditor = ctrl && key === 'a' && !inEditor && !(event.target instanceof HTMLInputElement)
      if (devTools || blockedEverywhere || selectAllOutsideEditor) {
        event.preventDefault()
        send('SHORTCUT_BLOCKED', `Pressed ${event.ctrlKey || event.metaKey ? 'Ctrl+' : ''}${event.key}`)
      }
    }

    function onKeyUp(event: KeyboardEvent) {
      if (event.key === 'PrintScreen') {
        // Some browsers let a page overwrite what print-screen just put on the clipboard.
        void navigator.clipboard?.writeText('').catch(() => undefined)
        send('SHORTCUT_BLOCKED', 'Pressed Print Screen')
      }
    }

    function onBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault()
      event.returnValue = ''
    }

    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', onBlur)
    window.addEventListener('focus', onFocus)
    document.addEventListener('fullscreenchange', onFullscreen)
    document.addEventListener('copy', onCopyOrCut)
    document.addEventListener('cut', onCopyOrCut)
    document.addEventListener('paste', onPaste)
    document.addEventListener('contextmenu', onContextMenu)
    document.addEventListener('selectstart', onSelectOrDrag)
    document.addEventListener('dragstart', onSelectOrDrag)
    document.addEventListener('keydown', onKeyDown, true)
    document.addEventListener('keyup', onKeyUp)
    window.addEventListener('beforeunload', onBeforeUnload)

    return () => {
      clearTimeout(blurTimer)
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('blur', onBlur)
      window.removeEventListener('focus', onFocus)
      document.removeEventListener('fullscreenchange', onFullscreen)
      document.removeEventListener('copy', onCopyOrCut)
      document.removeEventListener('cut', onCopyOrCut)
      document.removeEventListener('paste', onPaste)
      document.removeEventListener('contextmenu', onContextMenu)
      document.removeEventListener('selectstart', onSelectOrDrag)
      document.removeEventListener('dragstart', onSelectOrDrag)
      document.removeEventListener('keydown', onKeyDown, true)
      document.removeEventListener('keyup', onKeyUp)
      window.removeEventListener('beforeunload', onBeforeUnload)
    }
  }, [enabled])
}
