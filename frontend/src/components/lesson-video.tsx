import { useCallback, useEffect, useRef, useState } from 'react'
import { Camera, Loader2, PauseCircle, ShieldCheck } from 'lucide-react'

import { CameraPreview } from '@/components/camera-panel'
import { type EmbeddedControl, type EmbeddedPlayback, EmbeddedVideo } from '@/components/embedded-video'
import { Button } from '@/components/ui/button'
import { CAMERA_PROBLEM_TEXT, CameraError, countFaces, loadFaceDetector, openCamera, stopStream } from '@/lib/face-detector'
import type { CameraStatus } from '@/lib/use-camera-monitor'

/** How often watch time is saved while a video plays, so a closed tab loses at most this much. */
const SAVE_EVERY_MS = 15_000

export interface PlaybackReport {
  watchedSeconds: number
  playedSeconds: number
  durationSeconds: number
  faceAwaySeconds: number
  facePauses: number
}

/**
 * A video file played in our own player, a YouTube video played through YouTube's player API, or any other link
 * (Google Drive, Vimeo) shown in a frame we cannot control: that one can be watched through the camera but not paused,
 * and what it counts is time spent in front of the camera, not time the video played.
 */
export type VideoSource =
  | { kind: 'file'; src: string }
  | { kind: 'youtube'; id: string }
  | { kind: 'embed'; src: string; title: string; durationSeconds: number }

interface LessonVideoProps {
  source: VideoSource
  /** Share already watched, from an earlier visit, so the figure carries on instead of restarting at zero. */
  watchedPercent: number
  /** The instructor chose to monitor this video. Everything below only applies then. */
  monitored: boolean
  pauseOnNoFace: boolean
  autoResume: boolean
  graceSeconds: number
  onReport: (report: PlaybackReport) => void
  onEnded: () => void
}

/** What the monitor needs from whichever player is on screen. */
interface Controller {
  play(): void
  pause(): void
  playing(): boolean
  time(): number
}

/**
 * A lesson video that counts what was really watched - time it was playing, not how far the student skipped - and,
 * where the instructor switched it on, checks through the camera that someone is there. When the face has been missing
 * for the grace time the video pauses; it carries on by itself or waits for the student, as the instructor chose.
 * The check runs in this browser and nothing but short counts is sent: no picture or video leaves the device.
 */
export function LessonVideo(props: LessonVideoProps) {
  const { monitored } = props
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [asking, setAsking] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)

  useEffect(() => () => stopStream(stream), [stream])

  async function allow() {
    setAsking(true)
    setProblem(null)
    try {
      setStream(await openCamera())
    } catch (error) {
      setProblem(CAMERA_PROBLEM_TEXT[error instanceof CameraError ? error.problem : 'other'])
    } finally {
      setAsking(false)
    }
  }

  if (monitored && !stream) {
    return (
      <div className="flex flex-col gap-3 p-6 text-sm">
        <p className="flex items-center gap-2 font-medium">
          <ShieldCheck className="size-5" />
          This video is watched through your camera
        </p>
        <ul className="list-disc pl-5 text-muted-foreground">
          <li>Your camera only checks that your face is in view while the video plays.</li>
          <li>No picture or video is stored or sent. Only counts, such as seconds away, are kept.</li>
          {props.pauseOnNoFace && (
            <li>
              The video pauses if your face is missing for {props.graceSeconds} seconds
              {props.autoResume ? ' and carries on when you are back.' : '; press Resume when you are back.'}
            </li>
          )}
          <li>Your trainer can see how much you watched.</li>
        </ul>
        {problem && (
          <p role="alert" className="text-destructive">
            {problem}
          </p>
        )}
        <div>
          <Button onClick={() => void allow()} disabled={asking}>
            {asking ? <Loader2 className="animate-spin" /> : <Camera />}
            Allow camera and start
          </Button>
        </div>
      </div>
    )
  }

  return <Player {...props} stream={stream} />
}

function Player({
  source,
  watchedPercent,
  monitored,
  pauseOnNoFace,
  autoResume,
  graceSeconds,
  onReport,
  onEnded,
  stream,
}: LessonVideoProps & { stream: MediaStream | null }) {
  const fileVideo = useRef<HTMLVideoElement>(null)
  const camera = useRef<HTMLVideoElement>(null)
  const controller = useRef<Controller | null>(null)
  const [status, setStatus] = useState<CameraStatus>('starting')
  const [pausedForFace, setPausedForFace] = useState(false)
  const [faceVisible, setFaceVisible] = useState(true)
  const [percent, setPercent] = useState(watchedPercent)
  const [minutes, setMinutes] = useState(0)
  // A frame we cannot pause only gets a warning, shown while the face has been missing for the grace time.
  const [warnNoFace, setWarnNoFace] = useState(false)

  // Counters live in refs: they change every second and must not re-render the player or restart its timers.
  const played = useRef(0)
  const duration = useRef(0)
  const faceAway = useRef(0)
  const pauses = useRef(0)
  const missingFor = useRef(0)
  const blocked = useRef(false)
  const faceNow = useRef(true)
  const report = useRef(onReport)
  const ended = useRef(onEnded)
  useEffect(() => {
    report.current = onReport
    ended.current = onEnded
  }, [onReport, onEnded])

  const send = useCallback(() => {
    const c = controller.current
    if (!c) return
    report.current({
      watchedSeconds: Math.floor(c.time()),
      playedSeconds: Math.floor(played.current),
      durationSeconds: Math.ceil(duration.current),
      faceAwaySeconds: Math.floor(faceAway.current),
      facePauses: pauses.current,
    })
  }, [])

  const setLength = useCallback(
    (seconds: number) => {
      if (!(seconds > 0)) return
      duration.current = seconds
      // Carry on from the share already watched on an earlier visit.
      played.current = Math.max(played.current, (watchedPercent * seconds) / 100)
    },
    [watchedPercent],
  )

  // The frame of a Drive/Vimeo link: no way to play or pause it, so "playing" means the page is on screen.
  const embedSeconds = source.kind === 'embed' ? source.durationSeconds : 0
  const isEmbed = source.kind === 'embed'
  useEffect(() => {
    if (!isEmbed) return
    controller.current = { play: () => undefined, pause: () => undefined, playing: () => !document.hidden, time: () => 0 }
    if (embedSeconds > 0) setLength(embedSeconds)
    return () => {
      controller.current = null
    }
  }, [isEmbed, embedSeconds, setLength])

  // A paused-for-face video stays paused until the student resumes (or it resumes itself when the face is back).
  const refuseIfBlocked = useCallback(() => {
    if (blocked.current) controller.current?.pause()
  }, [])

  // The video file's player.
  useEffect(() => {
    const element = fileVideo.current
    if (source.kind !== 'file' || !element) return
    controller.current = {
      play: () => void element.play().catch(() => undefined),
      pause: () => element.pause(),
      playing: () => !element.paused && !element.ended && !element.seeking && element.readyState >= 3,
      time: () => element.currentTime,
    }
    return () => {
      controller.current = null
    }
  }, [source.kind])

  // YouTube: our own EmbeddedVideo player reports its state and time and can be told to play or pause.
  const youtubeControl = useRef<EmbeddedControl | null>(null)
  const youtubeState = useRef<number | undefined>(undefined)
  const youtubeTime = useRef(0)
  const isYoutube = source.kind === 'youtube'
  useEffect(() => {
    if (!isYoutube) return
    controller.current = {
      play: () => youtubeControl.current?.play(),
      pause: () => youtubeControl.current?.pause(),
      playing: () => youtubeState.current === 1,
      time: () => youtubeTime.current,
    }
    return () => {
      controller.current = null
    }
  }, [isYoutube])

  const onYoutube = useCallback(
    (info: EmbeddedPlayback) => {
      if (typeof info.current === 'number') youtubeTime.current = info.current
      if (info.duration && info.duration > 0 && duration.current <= 0) setLength(info.duration)
      if (typeof info.state !== 'number' || info.state === youtubeState.current) return
      youtubeState.current = info.state
      if (info.state === 1) refuseIfBlocked()
      else if (info.state === 2) send()
      else if (info.state === 0) {
        send()
        ended.current()
      }
    },
    [setLength, refuseIfBlocked, send],
  )

  // Count what is really watched: one second for each second the video is actually playing.
  useEffect(() => {
    const tick = setInterval(() => {
      if (!controller.current?.playing()) return
      // For a frame we cannot read, only time with a face in view counts as watched.
      if (isEmbed && monitored && !faceNow.current) return
      played.current = duration.current > 0 ? Math.min(duration.current, played.current + 1) : played.current + 1
      setPercent(duration.current > 0 ? Math.min(100, Math.floor((played.current * 100) / duration.current)) : 0)
      if (duration.current <= 0) setMinutes(Math.floor(played.current / 60))
    }, 1000)
    const save = setInterval(send, SAVE_EVERY_MS)
    return () => {
      clearInterval(tick)
      clearInterval(save)
      send()
    }
  }, [send, isEmbed, monitored])

  // The camera check.
  useEffect(() => {
    if (!monitored || !stream) return
    let cancelled = false
    let timer: ReturnType<typeof setInterval> | undefined
    const track = stream.getVideoTracks()[0]

    void loadFaceDetector()
      .then((detector) => {
        if (cancelled) return
        setStatus('ok')
        timer = setInterval(() => {
          const preview = camera.current
          const player = controller.current
          if (!preview || !player) return
          if (!track || track.readyState === 'ended' || !track.enabled) {
            setStatus('off')
            setFaceVisible(false)
            return
          }
          const visible = countFaces(detector, preview) > 0
          faceNow.current = visible
          setFaceVisible(visible)
          setStatus(visible ? 'ok' : 'no-face')

          if (visible) {
            missingFor.current = 0
            setWarnNoFace(false)
            if (blocked.current && autoResume) {
              blocked.current = false
              setPausedForFace(false)
              player.play()
            }
            return
          }

          // Time with no face counts only while the video is actually playing.
          if (!player.playing()) return
          faceAway.current += 1
          missingFor.current += 1
          if (isEmbed) {
            if (missingFor.current >= graceSeconds) setWarnNoFace(true)
          } else if (pauseOnNoFace && missingFor.current >= graceSeconds) {
            blocked.current = true
            pauses.current += 1
            setPausedForFace(true)
            player.pause()
            send()
          }
        }, 1000)
      })
      .catch(() => {
        // The detector could not load: the video still plays and is counted, only the face check is unavailable.
        if (!cancelled) setStatus('no-detector')
      })

    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [monitored, stream, pauseOnNoFace, autoResume, graceSeconds, send, isEmbed])

  function resume() {
    blocked.current = false
    missingFor.current = 0
    setPausedForFace(false)
    controller.current?.play()
  }

  return (
    <div className="relative">
      {source.kind === 'file' ? (
        <video
          ref={fileVideo}
          src={source.src}
          controls
          className="aspect-video w-full bg-black"
          onLoadedMetadata={(event) => setLength(event.currentTarget.duration)}
          onPlay={refuseIfBlocked}
          onPause={send}
          onEnded={() => {
            send()
            onEnded()
          }}
        />
      ) : source.kind === 'youtube' ? (
        <EmbeddedVideo
          src={`https://www.youtube.com/embed/${source.id}`}
          title="Lesson video"
          onPlayback={onYoutube}
          controlRef={youtubeControl}
        />
      ) : (
        <EmbeddedVideo src={source.src} title={source.title} />
      )}
      {warnNoFace && (
        <div
          role="status"
          className="pointer-events-none absolute top-3 left-1/2 flex -translate-x-1/2 items-center gap-2 rounded-md border border-amber-500 bg-amber-100 px-4 py-2 text-sm font-medium text-amber-900 shadow-lg"
        >
          Please keep your face in view.
        </div>
      )}
      {pausedForFace && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/80 p-4 text-center text-white">
          <PauseCircle className="size-10" />
          <p className="font-medium">Paused: please keep your face in view.</p>
          {!autoResume && (
            <Button onClick={resume} disabled={!faceVisible}>
              {faceVisible ? 'Resume' : 'Waiting for your face...'}
            </Button>
          )}
        </div>
      )}
      {monitored && stream && <CameraPreview stream={stream} video={camera} status={status} />}
      <p className="border-t px-3 py-2 text-xs text-muted-foreground">
        {isEmbed && embedSeconds <= 0
          ? `Time in front of the camera: ${minutes} min`
          : `${isEmbed ? 'Time in front of the camera' : 'Watched so far'}: ${percent}%`}
      </p>
    </div>
  )
}
