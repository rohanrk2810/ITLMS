import { useCallback, useEffect, useRef } from 'react'

import { sendTypingChunk, type TypingEdit } from '@/api/assessments'

/** What the editor reports for one change. `removed` of -1 means the whole text was replaced (a snapshot). */
export interface EditorEdit {
  offset: number
  removed: number
  text: string
}

const FLUSH_EVERY_MS = 5000
/** A sitting that goes on and on must not send for ever: past this the rest of the answer is not recorded. */
const MAX_EDITS = 20000
/** The server takes 100000 characters of JSON per batch; stay well under it. */
const MAX_BATCH_CHARS = 60000
/** One pasted block larger than this is cut: the replay shows that it appeared, not every character of it. */
const MAX_TEXT_CHARS = 50000
/** A paste is reported by the editor just after the change it made; edits this recent are the paste. */
const PASTE_WINDOW_MS = 300

function readSeq(key: string): number {
  try {
    return Number(sessionStorage.getItem(key)) || 0
  } catch {
    return 0
  }
}

function writeSeq(key: string, value: number) {
  try {
    sessionStorage.setItem(key, String(value))
  } catch {
    // Without it a reloaded page starts numbering again and the server drops those batches: a gap, not an error.
  }
}

interface RecorderOptions {
  enabled: boolean
  attemptId: number
  questionId: number
  /** What the editor holds now; recorded as the starting point, so a resumed answer replays from where it was. */
  initialCode: string
}

/**
 * Records how a coding answer is typed, for the trainer to replay: every edit with its time, sent to the server in
 * batches while the student types. It only watches; a failure to send is ignored and never gets in the student's way.
 * The same answer is the one that is submitted - this is a second record of it, not a replacement.
 */
export function useTypingRecorder({ enabled, attemptId, questionId, initialCode }: RecorderOptions) {
  const buffer = useRef<TypingEdit[]>([])
  const recorded = useRef(0)
  const sending = useRef(false)
  const seqKey = `itilms.typing.${attemptId}.${questionId}`
  const startCode = useRef(initialCode)

  const flush = useCallback(async () => {
    if (sending.current || buffer.current.length === 0) return
    sending.current = true
    try {
      while (buffer.current.length > 0) {
        const batch: TypingEdit[] = []
        let size = 2
        while (buffer.current.length > 0 && (batch.length === 0 || size + JSON.stringify(buffer.current[0]).length < MAX_BATCH_CHARS)) {
          const next = buffer.current.shift()!
          size += JSON.stringify(next).length + 1
          batch.push(next)
        }
        const seq = readSeq(seqKey)
        writeSeq(seqKey, seq + 1)
        await sendTypingChunk(attemptId, questionId, seq, batch).catch(() => undefined)
      }
    } finally {
      sending.current = false
    }
  }, [attemptId, questionId, seqKey])

  const record = useCallback(
    (edits: EditorEdit[]) => {
      if (!enabled) return
      const now = Date.now()
      for (const edit of edits) {
        if (recorded.current >= MAX_EDITS) return
        recorded.current += 1
        buffer.current.push([now, edit.offset, edit.removed, edit.text.slice(0, MAX_TEXT_CHARS), 0])
      }
    },
    [enabled],
  )

  /** The editor says text was just pasted: the edits of the last moment were it. */
  const markPaste = useCallback(() => {
    const since = Date.now() - PASTE_WINDOW_MS
    for (const edit of buffer.current) {
      if (edit[0] >= since && edit[3].length > 0 && edit[2] !== -1) edit[4] = 1
    }
  }, [])

  useEffect(() => {
    if (!enabled) return
    // Where the answer stood when recording began.
    record([{ offset: 0, removed: -1, text: startCode.current }])
    const timer = window.setInterval(() => void flush(), FLUSH_EVERY_MS)
    const onHide = () => {
      if (document.visibilityState === 'hidden') void flush()
    }
    document.addEventListener('visibilitychange', onHide)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onHide)
      void flush()
    }
  }, [enabled, flush, record])

  return { record, markPaste }
}
