import { useCallback, useEffect, useRef, useState } from 'react'
import { useDataChannel, useRoomContext } from '@livekit/components-react'
import { Captions, CaptionsOff, Mic, MicOff } from 'lucide-react'

import { saveTranscript, type TranscriptPhrase } from '@/api/live-classes'
import { CAPTION_LANGUAGES, useSpeechCaptions } from '@/lib/use-speech-captions'
import { setSubtitles, useSubtitlesOn } from '@/lib/subtitles-preference'

const TOPIC = 'captions'
/** Fired just before the class is ended, so the subtitles can save what they still hold. */
export const BEFORE_CLASS_ENDS = 'itilms:before-class-ends'
const SAVE_EVERY_MS = 4000
const MAX_QUEUED = 200
/** How long subtitles stay up after the trainer stops talking. */
const LINGER_MS = 6000

interface CaptionMessage {
  /** f: a finished phrase, i: the phrase still being spoken. */
  t: 'f' | 'i'
  text: string
}

const encoder = new TextEncoder()
const decoder = new TextDecoder()

function readLanguage(): string {
  try {
    const saved = localStorage.getItem('itilms.caption-language')
    if (saved && CAPTION_LANGUAGES.some((l) => l.code === saved)) return saved
  } catch {
    // Not kept; Marathi is the default.
  }
  return 'mr-IN'
}

/**
 * For the class's trainer: switch on subtitles. The trainer's browser turns speech into words, sends them to everyone
 * in the room as they are spoken, and saves each finished phrase on the server so the class has a written record.
 * No audio leaves this page for the institute's server: only words.
 */
export function CaptionsControl({ liveSessionId }: { liveSessionId: number }) {
  const room = useRoomContext()
  const [language, setLanguage] = useState(readLanguage)
  const [heard, setHeard] = useState('')
  const queue = useRef<TranscriptPhrase[]>([])
  const lastInterimAt = useRef(0)
  const closed = useRef(false)

  const flush = useCallback(async () => {
    if (queue.current.length === 0 || closed.current) return
    const batch = queue.current.splice(0, 40)
    try {
      await saveTranscript(liveSessionId, batch)
    } catch (error) {
      const code = (error as { response?: { data?: { code?: string } } }).response?.data?.code
      if (code === 'TRANSCRIPT_CLOSED' || code === 'TRANSCRIPT_FULL') {
        closed.current = true
        queue.current = []
        return
      }
      // Put them back, in front of anything newer, and try again at the next tick.
      queue.current = [...batch, ...queue.current].slice(-MAX_QUEUED)
    }
  }, [liveSessionId])

  const send = useCallback(
    (message: CaptionMessage, reliable: boolean) => {
      void room.localParticipant
        .publishData(encoder.encode(JSON.stringify(message)), { reliable, topic: TOPIC })
        .catch(() => undefined)
    },
    [room],
  )

  const captions = useSpeechCaptions({
    language,
    onInterim: (text) => {
      const now = Date.now()
      if (now - lastInterimAt.current < 250 && text) return
      lastInterimAt.current = now
      setHeard(text)
      send({ t: 'i', text }, false)
    },
    onFinal: (text) => {
      setHeard(text)
      send({ t: 'f', text }, true)
      queue.current.push({ clientId: crypto.randomUUID(), text, language, spokenAt: Date.now() })
      if (queue.current.length > MAX_QUEUED) queue.current.shift()
    },
  })

  useEffect(() => {
    const timer = setInterval(() => void flush(), SAVE_EVERY_MS)
    return () => {
      clearInterval(timer)
      void flush()
    }
  }, [flush])

  const running = captions.status === 'listening' || captions.status === 'network'

  // Ending the class closes the transcript, so what is still waiting to be saved must go first.
  const { stop } = captions
  useEffect(() => {
    async function saveEverything() {
      stop()
      for (let i = 0; i < 6 && queue.current.length > 0 && !closed.current; i++) {
        await flush()
      }
    }
    function onBeforeEnd(event: Event) {
      const pending = (event as CustomEvent<{ pending: Promise<unknown>[] }>).detail.pending
      pending.push(saveEverything())
    }
    window.addEventListener(BEFORE_CLASS_ENDS, onBeforeEnd)
    return () => window.removeEventListener(BEFORE_CLASS_ENDS, onBeforeEnd)
  }, [stop, flush])

  if (!captions.supported) {
    return (
      <span className="class-room-chip" title="Speech recognition needs Chrome or Edge">
        <CaptionsOff className="size-3.5" />
        Subtitles need Chrome or Edge
      </span>
    )
  }

  return (
    <span className="flex items-center gap-2">
      {!running && (
        <select
          aria-label="Language you teach in"
          value={language}
          onChange={(event) => {
            setLanguage(event.target.value)
            try {
              localStorage.setItem('itilms.caption-language', event.target.value)
            } catch {
              // Not kept.
            }
          }}
          className="h-7 rounded-full border border-white/20 bg-black/40 px-2 text-xs text-white"
        >
          {CAPTION_LANGUAGES.map((l) => (
            <option key={l.code} value={l.code}>
              {l.label}
            </option>
          ))}
        </select>
      )}
      <button
        type="button"
        className="class-room-chip"
        onClick={() => {
          if (running) {
            captions.stop()
            void flush()
          } else {
            closed.current = false
            captions.start()
          }
        }}
        aria-pressed={running}
      >
        {running ? <Mic className="size-3.5 text-red-400" /> : <MicOff className="size-3.5" />}
        {running ? 'Subtitles on - stop' : 'Start subtitles'}
      </button>
      {captions.status === 'blocked' && <span className="text-xs text-red-300">Allow the microphone for this site to use subtitles.</span>}
      {captions.status === 'network' && <span className="text-xs text-amber-300">No connection to the speech service; retrying.</span>}
      {running && heard && <span className="hidden max-w-60 truncate text-xs text-white/70 lg:inline">{heard}</span>}
    </span>
  )
}

/** For everyone else: a button that shows or hides the subtitles. */
export function SubtitlesToggle() {
  const on = useSubtitlesOn()
  return (
    <button type="button" className="class-room-chip" onClick={() => setSubtitles(!on)} aria-pressed={on}>
      {on ? <Captions className="size-3.5" /> : <CaptionsOff className="size-3.5" />}
      Subtitles {on ? 'on' : 'off'}
    </button>
  )
}

/** Only the class's trainer may speak subtitles: a student could send the same message, and the room must not believe it. */
function isTrainer(metadata: string | undefined): boolean {
  try {
    return (JSON.parse(metadata ?? '{}') as { role?: string }).role === 'TRAINER'
  } catch {
    return false
  }
}

/** The trainer's words, across the bottom of the class, while the viewer wants them. */
export function CaptionsOverlay() {
  const wanted = useSubtitlesOn()
  const [finished, setFinished] = useState<string[]>([])
  const [spoken, setSpoken] = useState('')
  const fade = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const onMessage = useCallback((message: { payload: Uint8Array; from?: { metadata?: string } }) => {
    if (!isTrainer(message.from?.metadata)) return
    let caption: CaptionMessage
    try {
      caption = JSON.parse(decoder.decode(message.payload)) as CaptionMessage
    } catch {
      return
    }
    if (typeof caption.text !== 'string') return
    const text = caption.text.slice(0, 400)
    if (caption.t === 'f') {
      setFinished((prev) => [...prev, text].slice(-2))
      setSpoken('')
    } else {
      setSpoken(text)
    }
    clearTimeout(fade.current)
    fade.current = setTimeout(() => {
      setFinished([])
      setSpoken('')
    }, LINGER_MS)
  }, [])

  useDataChannel(TOPIC, onMessage)
  useEffect(() => () => clearTimeout(fade.current), [])

  const lines = spoken ? [...finished.slice(-1), spoken] : finished
  if (!wanted || lines.length === 0) return null

  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-20 z-20 flex justify-center px-4" role="log" aria-live="polite">
      <div className="max-w-3xl rounded-md bg-black/75 px-4 py-2 text-center text-base leading-snug text-white">
        {lines.map((line, i) => (
          <p key={i} className={i === lines.length - 1 && spoken ? 'text-white/80' : undefined}>
            {line}
          </p>
        ))}
      </div>
    </div>
  )
}
