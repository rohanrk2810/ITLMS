import { useCallback, useEffect, useRef, useState } from 'react'

/** The parts of the browser's speech recogniser this uses. Chrome and Edge have it; its types are not in TypeScript's library. */
interface Recognition {
  continuous: boolean
  interimResults: boolean
  lang: string
  onresult: ((event: RecognitionEvent) => void) | null
  onerror: ((event: { error: string }) => void) | null
  onend: (() => void) | null
  start(): void
  stop(): void
}

interface RecognitionEvent {
  resultIndex: number
  results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }>
}

type RecognitionConstructor = new () => Recognition

function recogniser(): RecognitionConstructor | null {
  const w = window as unknown as { SpeechRecognition?: RecognitionConstructor; webkitSpeechRecognition?: RecognitionConstructor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

export const CAPTION_LANGUAGES = [
  { code: 'mr-IN', label: 'Marathi' },
  { code: 'hi-IN', label: 'Hindi' },
  { code: 'en-IN', label: 'English' },
] as const

export type CaptionStatus = 'off' | 'listening' | 'unsupported' | 'blocked' | 'network'

interface Options {
  language: string
  /** Told about the phrase being spoken, as it changes. */
  onInterim: (text: string) => void
  /** Told about each finished phrase. */
  onFinal: (text: string) => void
}

/**
 * Turns the trainer's speech into text in this browser, for subtitles. The browser vendor's service does the
 * recognising (the audio goes to it, not to this institute); this app only receives words. It stops by itself after
 * silence or a network blip, so it is started again until the trainer switches it off.
 */
export function useSpeechCaptions({ language, onInterim, onFinal }: Options) {
  const supported = recogniser() !== null
  const [status, setStatus] = useState<CaptionStatus>(supported ? 'off' : 'unsupported')
  const wanted = useRef(false)
  const active = useRef<Recognition | null>(null)
  const restart = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const callbacks = useRef({ onInterim, onFinal })
  useEffect(() => {
    callbacks.current = { onInterim, onFinal }
  }, [onInterim, onFinal])
  const languageRef = useRef(language)
  useEffect(() => {
    languageRef.current = language
  }, [language])

  const begin = useCallback(() => {
    const Recognition = recogniser()
    if (!Recognition || !wanted.current) return
    const next = new Recognition()
    next.continuous = true
    next.interimResults = true
    next.lang = languageRef.current
    next.onresult = (event) => {
      let interim = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i]
        const text = result[0].transcript.trim()
        if (!text) continue
        if (result.isFinal) callbacks.current.onFinal(text)
        else interim += (interim ? ' ' : '') + text
      }
      callbacks.current.onInterim(interim)
    }
    next.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        wanted.current = false
        setStatus('blocked')
      } else if (event.error === 'network') {
        setStatus('network')
      }
      // 'no-speech' and 'aborted' are ordinary: the recogniser simply ends and is started again.
    }
    next.onend = () => {
      active.current = null
      if (wanted.current) restart.current = setTimeout(begin, 400)
    }
    active.current = next
    try {
      next.start()
      setStatus((current) => (current === 'network' ? current : 'listening'))
    } catch {
      // Starting twice throws; the one already running carries on.
    }
  }, [])

  const start = useCallback(() => {
    if (!recogniser()) return
    wanted.current = true
    setStatus('listening')
    begin()
  }, [begin])

  const stop = useCallback(() => {
    wanted.current = false
    clearTimeout(restart.current)
    active.current?.stop()
    active.current = null
    callbacks.current.onInterim('')
    setStatus(supported ? 'off' : 'unsupported')
  }, [supported])

  // A language change takes effect on the next start of the recogniser.
  useEffect(() => {
    if (wanted.current) active.current?.stop()
  }, [language])

  useEffect(
    () => () => {
      wanted.current = false
      clearTimeout(restart.current)
      active.current?.stop()
    },
    [],
  )

  return { status, supported, start, stop }
}
