import { useCallback, useEffect, useRef, useState } from 'react'
import { Circle, Download, Loader2, Square } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  type LiveSessionResponse,
  downloadRecording,
  getLiveSession,
  startBrowserRecording,
  stopBrowserRecording,
  uploadRecordingChunk,
} from '@/api/live-classes'
import { Button } from '@/components/ui/button'

/** How often the recorder hands over a piece, and how many times a piece is retried before the host is warned. */
const TIMESLICE_MS = 10_000
const RETRIES = 6

function pickMimeType(): string | undefined {
  return ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'].find((t) => MediaRecorder.isTypeSupported(t))
}

function megabytes(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** Region Capture (Chrome/Edge): crops a capture of the current tab to one element. Not in the TypeScript DOM types yet. */
type CroppableTrack = MediaStreamTrack & { cropTo?: (target: unknown) => Promise<void> }
declare global {
  interface Window {
    CropTarget?: { fromElement(element: Element): Promise<unknown> }
  }
}

/** The element the recording should show right now: the shared screen if there is one, else the whole class area. */
function recordingTarget(): Element | null {
  return (
    document.querySelector('#class-stage video[data-lk-source="screen_share"]') ??
    document.getElementById('class-stage')
  )
}

/** Keeps the capture cropped to {@link recordingTarget}. Returns a function that stops doing so. */
function followPresentation(track: CroppableTrack | undefined): () => void {
  if (!track?.cropTo || !window.CropTarget) return () => undefined
  const { cropTo } = track
  const fromElement = window.CropTarget.fromElement.bind(window.CropTarget)
  let current: Element | null = null
  let working = false
  const apply = async () => {
    const target = recordingTarget()
    if (working || !target || target === current) return
    working = true
    try {
      await cropTo.call(track, await fromElement(target))
      current = target
    } catch {
      // Another window or screen was chosen instead of this tab; it cannot be cropped.
    } finally {
      working = false
    }
  }
  void apply()
  const timer = window.setInterval(() => void apply(), 1000)
  return () => window.clearInterval(timer)
}

/** Everything that has to be let go of when a recording ends. */
interface Capture {
  recorder: MediaRecorder
  streams: MediaStream[]
  audio: AudioContext
  stopCropping: () => void
}

/**
 * The host's record button. The recording is made in this browser: the class tab (with the sound of everyone in it)
 * and the host's microphone are captured, cut into pieces every few seconds, and sent to the server one piece at a
 * time. If the tab is closed, what was already sent is kept; after stopping, the recording can be downloaded at once.
 */
export function RecordingControl({ liveSessionId }: { liveSessionId: number }) {
  const [session, setSession] = useState<LiveSessionResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [sending, setSending] = useState(false)
  const [warning, setWarning] = useState<string | null>(null)

  const capture = useRef<Capture | null>(null)
  const queue = useRef<Blob[]>([])
  const nextSeq = useRef(0)
  const draining = useRef<Promise<void> | null>(null)

  const refresh = useCallback(() => {
    getLiveSession(liveSessionId).then(setSession).catch(() => undefined)
  }, [liveSessionId])

  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, 8000)
    return () => window.clearInterval(timer)
  }, [refresh])

  /** This browser is the one making the recording (another tab or device may be, if the page was reopened). */
  const recordingHere = capture.current !== null

  // Closing the tab mid-recording loses the piece in progress, so say so first.
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (capture.current) event.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [])

  function release() {
    const current = capture.current
    capture.current = null
    if (!current) return
    current.stopCropping()
    current.streams.forEach((s) => s.getTracks().forEach((t) => t.stop()))
    void current.audio.close().catch(() => undefined)
  }

  /** Sends queued pieces in order, one at a time, retrying a failed piece with a growing pause. */
  function drain(): Promise<void> {
    if (draining.current) return draining.current
    const run = async () => {
      setSending(true)
      try {
        while (queue.current.length > 0) {
          const chunk = queue.current[0]
          let sent = false
          for (let attempt = 0; attempt <= RETRIES && !sent; attempt++) {
            try {
              await uploadRecordingChunk(liveSessionId, nextSeq.current, chunk)
              sent = true
              setWarning(null)
            } catch {
              setWarning('Connection problem - still trying to send the recording.')
              await new Promise((r) => setTimeout(r, Math.min(2000 * 2 ** attempt, 20_000)))
            }
          }
          if (!sent) {
            // Give up on sending, keep the pieces queued; the next piece (or Stop) tries again.
            setWarning('The recording could not be sent. Check the connection; it will retry.')
            return
          }
          queue.current.shift()
          nextSeq.current += 1
        }
      } finally {
        setSending(false)
        draining.current = null
      }
    }
    draining.current = run()
    return draining.current
  }

  async function begin() {
    if (!window.MediaRecorder || !navigator.mediaDevices?.getDisplayMedia) {
      toast.error('This browser cannot record the screen. Use Chrome or Edge on a computer.')
      return
    }
    setBusy(true)
    const streams: MediaStream[] = []
    let stopCropping: () => void = () => undefined
    try {
      toast.message('Choose "This tab" and tick "Share tab audio" - only the class area is recorded.')
      const display = await navigator.mediaDevices.getDisplayMedia({
        video: { frameRate: 15 },
        audio: true,
        preferCurrentTab: true,
      } as DisplayMediaStreamOptions)
      streams.push(display)

      // Record only the presentation: the shared-screen video. While nobody is sharing, the class area (the box
      // with id "class-stage") is recorded instead. The crop follows a share starting or stopping. Chrome and Edge
      // can crop a capture of this tab; anywhere else the whole tab is recorded.
      stopCropping = followPresentation(display.getVideoTracks()[0] as CroppableTrack | undefined)
      let mic: MediaStream | null = null
      try {
        mic = await navigator.mediaDevices.getUserMedia({ audio: true })
        streams.push(mic)
      } catch {
        // No microphone: the class sound alone is still recorded.
      }

      // Mix the tab's sound and the host's microphone into one track.
      const audio = new AudioContext()
      const mix = audio.createMediaStreamDestination()
      for (const source of [display, mic]) {
        if (source && source.getAudioTracks().length > 0) {
          audio.createMediaStreamSource(new MediaStream(source.getAudioTracks())).connect(mix)
        }
      }
      const combined = new MediaStream([...display.getVideoTracks(), ...mix.stream.getAudioTracks()])

      await startBrowserRecording(liveSessionId)

      queue.current = []
      nextSeq.current = 0
      const mimeType = pickMimeType()
      const recorder = new MediaRecorder(combined, mimeType ? { mimeType, videoBitsPerSecond: 1_500_000 } : undefined)
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          queue.current.push(event.data)
          void drain()
        }
      }
      recorder.onstop = () => {
        void finish()
      }
      // The host ended sharing from the browser's own bar: that is the same as pressing Stop.
      display.getVideoTracks()[0]?.addEventListener('ended', () => {
        if (recorder.state !== 'inactive') recorder.stop()
      })

      capture.current = { recorder, streams, audio, stopCropping }
      recorder.start(TIMESLICE_MS)
      toast.success('Recording started')
      refresh()
    } catch (err) {
      stopCropping()
      streams.forEach((s) => s.getTracks().forEach((t) => t.stop()))
      const denied = err instanceof DOMException && (err.name === 'NotAllowedError' || err.name === 'AbortError')
      toast.error(denied ? 'Screen sharing was cancelled, so nothing is being recorded.' : apiErrorMessage(err, 'Could not start recording.'))
    } finally {
      setBusy(false)
    }
  }

  /** Runs once the recorder has delivered its last piece: send what is left, then close the recording. */
  async function finish() {
    release()
    setBusy(true)
    try {
      await drain()
      if (queue.current.length > 0) {
        toast.error('Some of the recording could not be sent. Keep this page open and press Stop again.')
        return
      }
      const state = await stopBrowserRecording(liveSessionId)
      toast.success(state.bytes > 0 ? 'Recording saved - you can download it now' : 'Nothing was recorded')
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not finish the recording.'))
    } finally {
      setBusy(false)
      setWarning(null)
      refresh()
    }
  }

  function stop() {
    const current = capture.current
    if (current && current.recorder.state !== 'inactive') {
      current.recorder.stop()
      return
    }
    // Recording was started from a page that is gone: close it on the server with what it has.
    setBusy(true)
    stopBrowserRecording(liveSessionId)
      .then(() => toast.success('Recording closed'))
      .catch((err: unknown) => toast.error(apiErrorMessage(err, 'Could not stop the recording.')))
      .finally(() => {
        setBusy(false)
        refresh()
      })
  }

  async function save() {
    setDownloading(true)
    try {
      await downloadRecording(liveSessionId)
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not download the recording.'))
    } finally {
      setDownloading(false)
    }
  }

  if (!session) return null

  const active = session.recording
  const canDownload = !active && !!session.recordingUrl

  return (
    <div className="flex flex-col gap-2 rounded-md border p-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Button
          size="sm"
          variant={active ? 'destructive' : 'outline'}
          disabled={busy}
          onClick={() => (active ? stop() : void begin())}
        >
          {busy ? <Loader2 className="size-4 animate-spin" /> : active ? <Square className="size-4" /> : <Circle className="size-4" />}
          {active ? 'Stop recording' : 'Record this class'}
        </Button>
        {canDownload && (
          <Button size="sm" variant="outline" disabled={downloading} onClick={() => void save()}>
            {downloading ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
            Download recording{session.recordingBytes > 0 ? ` (${megabytes(session.recordingBytes)})` : ''}
          </Button>
        )}
        {active && (
          <span className="text-xs text-muted-foreground">
            Recording{recordingHere ? '' : ' (from another window)'}&hellip; {megabytes(session.recordingBytes)} saved
            {sending ? ' · sending' : ''}
          </span>
        )}
      </div>
      {warning && <p className="text-xs text-destructive">{warning}</p>}
      {!active && !canDownload && (
        <p className="text-xs text-muted-foreground">
          Recorded in your browser and saved on the server. When asked, choose this tab and share its audio. Keep this tab open while recording.
        </p>
      )}
    </div>
  )
}
