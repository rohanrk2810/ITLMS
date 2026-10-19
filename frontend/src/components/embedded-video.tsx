import { type MutableRefObject, useCallback, useEffect, useRef, useState } from 'react'
import { Maximize, Pause, Play } from 'lucide-react'

/** What a YouTube frame reports about itself; any field may be missing from a given update. */
export interface EmbeddedPlayback {
  /** YouTube's player state: 0 ended, 1 playing, 2 paused, 3 buffering. */
  state?: number
  current?: number
  duration?: number
}

/** Lets the page start and stop a YouTube video that is shown in this player. */
export interface EmbeddedControl {
  play(): void
  pause(): void
}

interface EmbeddedVideoProps {
  /** Embed URL from toEmbedUrl (YouTube, Vimeo or Google Drive). */
  src: string
  title: string
  /** Told about every YouTube state/time update, so the lesson page can count and monitor viewing. */
  onPlayback?: (info: EmbeddedPlayback) => void
  /** Filled with play/pause once a YouTube frame is shown. */
  controlRef?: MutableRefObject<EmbeddedControl | null>
}

type Provider = 'youtube' | 'vimeo' | 'drive' | 'other'

function providerOf(src: string): Provider {
  if (src.includes('youtube.com/')) return 'youtube'
  if (src.includes('player.vimeo.com/')) return 'vimeo'
  if (src.includes('drive.google.com/')) return 'drive'
  return 'other'
}

/** Strips the provider's own chrome (title, channel, logo, share, pop-out) where the provider lets us. */
function cleanSrc(src: string, provider: Provider): string {
  const sep = src.includes('?') ? '&' : '?'
  switch (provider) {
    case 'youtube':
      return `${src}${sep}controls=0&modestbranding=1&rel=0&iv_load_policy=3&disablekb=1&fs=0&playsinline=1&showinfo=0&enablejsapi=1&origin=${encodeURIComponent(window.location.origin)}`
    case 'vimeo':
      return `${src}${sep}title=0&byline=0&portrait=0&badge=0&dnt=1`
    default:
      return src
  }
}

function formatTime(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds))
  const m = Math.floor(s / 60)
  return `${m}:${String(s % 60).padStart(2, '0')}`
}

/**
 * A video from YouTube, Vimeo or Google Drive that looks like our own player.
 *
 * A transparent layer sits over the video so the provider's title bar, logo, share and
 * "open in new tab" buttons cannot be reached. YouTube gets our own play/pause, seek bar and
 * fullscreen button. Vimeo and Drive keep their own bottom controls (they cannot be driven
 * from outside), with only the top corner covered. The provider's name can still flash
 * briefly while a video is paused or loading; that is the provider's, not ours.
 */
export function EmbeddedVideo({ src, title, onPlayback, controlRef }: EmbeddedVideoProps) {
  const provider = providerOf(src)
  const frameRef = useRef<HTMLIFrameElement>(null)
  const boxRef = useRef<HTMLDivElement>(null)
  const [playing, setPlaying] = useState(false)
  const [current, setCurrent] = useState(0)
  const [duration, setDuration] = useState(0)
  // If YouTube ignores our play command, our click-catching layer is taken away so the student can click the video
  // itself, which YouTube always honours.
  const [stuck, setStuck] = useState(false)
  const playingRef = useRef(false)
  const stuckTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  useEffect(() => () => clearTimeout(stuckTimer.current), [])

  const send = useCallback((func: string, args: unknown[] = []) => {
    frameRef.current?.contentWindow?.postMessage(JSON.stringify({ event: 'command', func, args }), '*')
  }, [])

  const playbackRef = useRef(onPlayback)
  useEffect(() => {
    playbackRef.current = onPlayback
  }, [onPlayback])

  useEffect(() => {
    if (!controlRef || provider !== 'youtube') return
    controlRef.current = { play: () => send('playVideo'), pause: () => send('pauseVideo') }
    return () => {
      controlRef.current = null
    }
  }, [controlRef, provider, send])

  useEffect(() => {
    if (provider !== 'youtube') return
    function onMessage(e: MessageEvent) {
      if (e.source !== frameRef.current?.contentWindow || typeof e.data !== 'string') return
      let data: { event?: string; info?: { currentTime?: number; duration?: number; playerState?: number } }
      try {
        data = JSON.parse(e.data)
      } catch {
        return
      }
      const info = data.info
      if (data.event !== 'infoDelivery' || !info) return
      if (typeof info.currentTime === 'number') setCurrent(info.currentTime)
      if (typeof info.duration === 'number' && info.duration > 0) setDuration(info.duration)
      if (typeof info.playerState === 'number') {
        setPlaying(info.playerState === 1)
        playingRef.current = info.playerState === 1
        if (info.playerState === 1) {
          clearTimeout(stuckTimer.current)
          setStuck(false)
        }
      }
      playbackRef.current?.({ state: info.playerState, current: info.currentTime, duration: info.duration })
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [provider])

  function handleLoad() {
    // Ask YouTube to start sending time and state updates.
    frameRef.current?.contentWindow?.postMessage(JSON.stringify({ event: 'listening', id: 1 }), '*')
  }

  function togglePlay() {
    send(playing ? 'pauseVideo' : 'playVideo')
    if (!playing) {
      clearTimeout(stuckTimer.current)
      stuckTimer.current = setTimeout(() => {
        if (!playingRef.current) setStuck(true)
      }, 1500)
    }
  }

  function fullscreen() {
    if (document.fullscreenElement) void document.exitFullscreen()
    else void boxRef.current?.requestFullscreen()
  }

  return (
    <div ref={boxRef} className="relative aspect-video w-full bg-black">
      <iframe
        ref={frameRef}
        key={src}
        title={title}
        src={cleanSrc(src, provider)}
        className="absolute inset-0 size-full"
        allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
        allowFullScreen
        onLoad={handleLoad}
      />

      {provider === 'youtube' && (
        <>
          {/* Catches every click and hover, so YouTube's title, logo and "watch on" never show. */}
          <button
            type="button"
            aria-label={playing ? 'Pause' : 'Play'}
            onClick={togglePlay}
            className={`absolute inset-x-0 top-0 bottom-12 cursor-pointer ${stuck ? 'pointer-events-none' : ''}`}
          />
          {stuck && !playing && (
            <p className="pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded bg-black/70 px-3 py-1 text-xs text-white">
              Click the video to start it.
            </p>
          )}
          <div className="absolute inset-x-0 bottom-0 flex h-12 items-center gap-3 bg-black/80 px-3 text-white">
            <button type="button" aria-label={playing ? 'Pause' : 'Play'} onClick={togglePlay}>
              {playing ? <Pause className="size-5" /> : <Play className="size-5" />}
            </button>
            <span className="w-24 shrink-0 text-xs tabular-nums">
              {formatTime(current)} / {formatTime(duration)}
            </span>
            <input
              type="range"
              aria-label="Seek"
              min={0}
              max={duration || 1}
              step={1}
              value={Math.min(current, duration || 1)}
              onChange={(e) => {
                setCurrent(Number(e.target.value))
                send('seekTo', [Number(e.target.value), true])
              }}
              className="h-1 flex-1 cursor-pointer accent-white"
            />
            <button type="button" aria-label="Fullscreen" onClick={fullscreen}>
              <Maximize className="size-5" />
            </button>
          </div>
        </>
      )}

      {provider === 'drive' && (
        // Drive's preview puts a pop-out button and its logo in the top-right corner.
        <div className="absolute top-0 right-0 h-14 w-20 bg-black" />
      )}
    </div>
  )
}
