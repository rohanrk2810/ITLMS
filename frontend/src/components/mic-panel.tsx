import { useEffect, useRef, useState } from 'react'
import { Loader2, Mic, MicOff, Volume2 } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { MIC_PROBLEM_TEXT, MicError, openAnalyser, openMicrophone, readLevel } from '@/lib/microphone'
import { type MicStatus, thresholdFor } from '@/lib/use-mic-monitor'
import { cn } from '@/lib/utils'

interface MicCheckProps {
  stream: MediaStream | null
  onStream: (stream: MediaStream) => void
  /** True once the microphone is on. A level has been measured, so the test knows what counts as sound. */
  onReady: (ready: boolean) => void
  /** The loudness above which sound will count during the test, from this room's own background level. */
  onThreshold: (threshold: number) => void
}

/**
 * Before a microphone test starts: ask for the microphone, show a live level so the student can see it works, and
 * listen to the room for two seconds to learn its background noise. Nothing is recorded.
 */
export function MicCheck({ stream, onStream, onReady, onThreshold }: MicCheckProps) {
  const [problem, setProblem] = useState<string | null>(null)
  const [asking, setAsking] = useState(false)
  const [level, setLevel] = useState(0)
  const [measured, setMeasured] = useState(false)
  const callbacks = useRef({ onReady, onThreshold })
  useEffect(() => {
    callbacks.current = { onReady, onThreshold }
  }, [onReady, onThreshold])

  useEffect(() => {
    if (!stream) {
      callbacks.current.onReady(false)
      return
    }
    let opened: ReturnType<typeof openAnalyser> | undefined
    try {
      opened = openAnalyser(stream)
    } catch {
      // No audio analysis in this browser: the microphone is on, which is what is required.
      callbacks.current.onReady(true)
      return
    }
    const buffer = new Float32Array(opened.analyser.fftSize)
    const samples: number[] = []
    const started = Date.now()
    const timer = setInterval(() => {
      const current = readLevel(opened.analyser, buffer)
      setLevel(current)
      if (Date.now() - started < 2000) {
        samples.push(current)
      } else if (samples.length > 0) {
        const ambient = samples.reduce((a, b) => a + b, 0) / samples.length
        callbacks.current.onThreshold(thresholdFor(ambient))
        setMeasured(true)
        callbacks.current.onReady(true)
        samples.length = 0
      }
    }, 100)
    return () => {
      clearInterval(timer)
      void opened.context.close().catch(() => undefined)
    }
  }, [stream])

  async function allow() {
    setAsking(true)
    setProblem(null)
    try {
      onStream(await openMicrophone())
    } catch (error) {
      setProblem(MIC_PROBLEM_TEXT[error instanceof MicError ? error.problem : 'other'])
    } finally {
      setAsking(false)
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border p-3">
      <div className="flex items-center gap-2 text-sm font-medium">
        <Mic className="size-4" />
        Microphone check
      </div>
      {!stream ? (
        <>
          <p className="text-sm text-muted-foreground">
            This test needs your microphone. It only measures how loud the room is, to notice sound during the test.
            Nothing is recorded: no audio is stored, only a note that sound was heard.
          </p>
          <Button className="self-start" variant="outline" onClick={() => void allow()} disabled={asking}>
            {asking ? <Loader2 className="animate-spin" /> : <Mic />}
            Allow microphone
          </Button>
        </>
      ) : (
        <div className="flex flex-col gap-2">
          <div
            className="h-3 w-64 max-w-full overflow-hidden rounded bg-muted"
            role="meter"
            aria-label="Microphone level"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.min(100, Math.round(level * 400))}
          >
            <div className="h-full bg-emerald-500 transition-[width]" style={{ width: `${Math.min(100, level * 400)}%` }} />
          </div>
          <p className="text-sm" role="status">
            {measured
              ? 'Your microphone is on. Say a word: the bar should move. During the test, please stay quiet.'
              : 'Listening to your room for a moment... please stay quiet.'}
          </p>
        </div>
      )}
      {problem && (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      )}
    </div>
  )
}

const STATUS_TEXT: Record<MicStatus, string> = {
  starting: 'Starting microphone...',
  ok: 'Microphone OK',
  sound: 'Sound detected',
  off: 'Microphone off',
  denied: 'Microphone blocked',
}

/** A small badge during the test saying what the microphone check sees. */
export function MicIndicator({ status, besideCamera }: { status: MicStatus; besideCamera?: boolean }) {
  const bad = status === 'off' || status === 'denied' || status === 'sound'
  const Icon = status === 'off' || status === 'denied' ? MicOff : status === 'sound' ? Volume2 : Mic
  return (
    <div
      className={cn(
        'fixed bottom-3 z-30 flex items-center gap-1 rounded-md border-2 px-2 py-1 text-xs shadow-lg',
        besideCamera ? 'right-[11.5rem]' : 'right-3',
        bad ? 'border-destructive bg-destructive text-white' : 'border-emerald-500 bg-card text-foreground',
      )}
      aria-label="Your microphone"
    >
      <Icon className="size-3" />
      {STATUS_TEXT[status]}
    </div>
  )
}
