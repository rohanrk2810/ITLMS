import { type RefObject, useEffect, useRef, useState } from 'react'
import { ArrowDown, ArrowUp, Camera, CameraOff, ChevronLeft, ChevronRight, Loader2, ScanFace } from 'lucide-react'

import type { CameraCheckName } from '@/api/assessments'
import { Button } from '@/components/ui/button'
import { CAMERA_PROBLEM_TEXT, CameraError, loadFaceDetector, openCamera, OVAL, readFaces } from '@/lib/face-detector'
import { describeFace, loadFaceIdentity, REFERENCE_SAMPLES, samplesAgree } from '@/lib/face-identity'
import { loadObjectDetector } from '@/lib/object-detector'
import { monitorDock, useMonitorDock } from '@/lib/monitor-dock'
import type { CameraStatus } from '@/lib/use-camera-monitor'
import { cn } from '@/lib/utils'

/** Attaches a stream to a <video>. The picture is mirrored, like a mirror, because that is how people expect it. */
function useStreamOnVideo(stream: MediaStream | null, video: RefObject<HTMLVideoElement | null>) {
  useEffect(() => {
    const element = video.current
    if (!element) return
    element.srcObject = stream
    if (stream) void element.play().catch(() => undefined)
  }, [stream, video])
}

/** The oval the face must stay inside, drawn over a 4:3 picture. Everything outside it is dimmed. */
function OvalGuide({ good }: { good: boolean | null }) {
  const colour = good === null ? '#ffffff' : good ? '#10b981' : '#ef4444'
  return (
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="pointer-events-none absolute inset-0 size-full" aria-hidden>
      <defs>
        <mask id="oval-hole">
          <rect width="100" height="100" fill="white" />
          <ellipse cx="50" cy="50" rx={OVAL.rx * 100} ry={OVAL.ry * 100} fill="black" />
        </mask>
      </defs>
      <rect width="100" height="100" fill="black" opacity="0.45" mask="url(#oval-hole)" />
      <ellipse cx="50" cy="50" rx={OVAL.rx * 100} ry={OVAL.ry * 100} fill="none" stroke={colour} strokeWidth="1.2" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

interface CameraCheckProps {
  stream: MediaStream | null
  onStream: (stream: MediaStream) => void
  /** True once a face has been seen and recorded, or when the face check cannot run here (the camera itself is on). */
  onReady: (ready: boolean) => void
  /**
   * The face recorded for this test, as numbers (not a picture), or null when it could not be taken. The test compares
   * who is in front of the camera with it. It stays in this browser.
   */
  onReference?: (reference: Float32Array[] | null) => void
  /** Which checks the trainer switched on. Left out means all of them. */
  checks?: CameraCheckName[]
}

/**
 * Before a camera test starts: ask for the camera, show the student themselves, and wait until a face is in
 * view. The student cannot start until the camera is on and a face has been seen.
 */
export function CameraCheck({ stream, onStream, onReady, onReference, checks }: CameraCheckProps) {
  const watchIdentity = !checks || checks.includes('IDENTITY')
  const watchObjects = !checks || checks.includes('PHONE') || checks.includes('PEOPLE')
  const video = useRef<HTMLVideoElement>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [asking, setAsking] = useState(false)
  const [faces, setFaces] = useState<number | 'unavailable' | null>(null)
  const [inOval, setInOval] = useState(false)
  // Recording whose face this is: a few samples, taken while the face is in the oval, that must agree with each other.
  const [identity, setIdentity] = useState<'waiting' | 'collecting' | 'done' | 'unavailable'>('waiting')
  const [collected, setCollected] = useState(0)
  const [retake, setRetake] = useState(false)
  const faceOk = faces === 1 && inOval
  const samples = useRef<Float32Array[]>([])
  // Whether the phone / second-person check can run in this browser. It loads now so a problem shows before the test.
  const [phoneCheck, setPhoneCheck] = useState<'loading' | 'ready' | 'unavailable'>('loading')
  useStreamOnVideo(stream, video)

  useEffect(() => {
    if (!stream || !watchObjects) return
    let cancelled = false
    loadObjectDetector()
      .then(() => !cancelled && setPhoneCheck('ready'))
      .catch((error: unknown) => {
        console.warn('[camera-check] the phone check could not start', error)
        if (!cancelled) setPhoneCheck('unavailable')
      })
    return () => {
      cancelled = true
    }
  }, [stream, watchObjects])

  useEffect(() => {
    if (!stream) {
      onReady(false)
      return
    }
    let cancelled = false
    let timer: ReturnType<typeof setInterval> | undefined
    loadFaceDetector()
      .then((detector) => {
        if (cancelled) return
        timer = setInterval(() => {
          if (!video.current) return
          const reading = readFaces(detector, video.current)
          setFaces(reading.count)
          setInOval(reading.inOval)
        }, 700)
      })
      .catch(() => {
        if (cancelled) return
        setFaces('unavailable')
      })
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [stream])

  // A new camera means a new recording of the face.
  useEffect(() => {
    samples.current = []
    setCollected(0)
    setIdentity('waiting')
    setRetake(false)
    onReference?.(null)
  }, [stream, onReference])

  // While a single face sits in the oval, take samples of it until there are enough that agree.
  useEffect(() => {
    if (!stream || !faceOk || identity === 'done' || identity === 'unavailable') return
    if (!watchIdentity) {
      // The trainer is not checking who is sitting; nothing to record.
      setIdentity('unavailable')
      return
    }
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | undefined
    setIdentity('collecting')
    loadFaceIdentity()
      .then((identityNets) => {
        const sample = async () => {
          if (cancelled || !video.current) return
          try {
            const face = await describeFace(identityNets, video.current)
            if (cancelled) return
            if (face) {
              samples.current.push(face)
              setCollected(samples.current.length)
              if (samples.current.length >= REFERENCE_SAMPLES) {
                if (samplesAgree(samples.current)) {
                  onReference?.(samples.current)
                  setIdentity('done')
                  return
                }
                // They disagree: the person moved or the light changed. Start the recording again.
                samples.current = []
                setCollected(0)
                setRetake(true)
              }
            }
          } catch {
            // A failed look is skipped.
          }
          timer = setTimeout(() => void sample(), 1100)
        }
        void sample()
      })
      .catch(() => {
        if (cancelled) return
        // The recogniser could not load. The camera is still on and the face is visible; the test goes ahead without
        // checking who it is, and says so to the trainer's record by simply having no reference.
        onReference?.(null)
        setIdentity('unavailable')
      })
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [stream, faceOk, identity, onReference, watchIdentity])

  // Ready once a face is in the oval and recorded (or the face check cannot run here at all).
  useEffect(() => {
    if (!stream) {
      onReady(false)
    } else if (faces === 'unavailable') {
      onReady(true)
    } else {
      onReady(faceOk && (identity === 'done' || identity === 'unavailable'))
    }
  }, [stream, faces, faceOk, identity, onReady])

  async function allow() {
    setAsking(true)
    setProblem(null)
    try {
      onStream(await openCamera())
    } catch (error) {
      setProblem(CAMERA_PROBLEM_TEXT[error instanceof CameraError ? error.problem : 'other'])
    } finally {
      setAsking(false)
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border p-3">
      <div className="flex items-center gap-2 text-sm font-medium">
        <Camera className="size-4" />
        Camera check
      </div>
      {!stream ? (
        <>
          <p className="text-sm text-muted-foreground">
            This test needs your camera. It is used only to check that your face is visible during the test.
            No pictures are stored, only a note when your face is not visible.
          </p>
          <Button className="self-start" variant="outline" onClick={() => void allow()} disabled={asking}>
            {asking ? <Loader2 className="animate-spin" /> : <Camera />}
            Allow camera
          </Button>
        </>
      ) : (
        <div className="flex flex-wrap items-start gap-4">
          <div className="relative aspect-[4/3] w-56 overflow-hidden rounded-md border bg-black">
            <video ref={video} muted playsInline className="size-full -scale-x-100 object-cover" />
            {faces !== 'unavailable' && <OvalGuide good={faces === null ? null : faces === 1 && inOval} />}
          </div>
          <p className="max-w-xs text-sm" role="status">
            {faces === 'unavailable' && 'Your camera is on. The face check is not available in this browser, so you can start.'}
            {faces === null && 'Looking for your face...'}
            {faces === 0 && 'I cannot see your face. Face the camera, in good light, and sit a little closer.'}
            {typeof faces === 'number' && faces > 1 && 'More than one face is visible. Only you should be in view.'}
            {faces === 1 && !inOval && 'Move so your face is inside the oval.'}
            {faces === 1 && inOval && identity !== 'done' && identity !== 'unavailable' &&
              `Checking that it is you (${collected} of ${REFERENCE_SAMPLES}). Look at the camera and hold still.`}
            {faces === 1 && inOval && retake && identity !== 'done' && ' The samples did not match; hold still and face the camera.'}
            {faces === 1 && inOval && identity === 'done' &&
              'Your face is recorded for this test, in this browser only. You can start. Keep your face in the oval, alone in the picture, with no phone in view.'}
            {faces === 1 && inOval && identity === 'unavailable' &&
              'Your face is inside the oval. You can start. Keep it there during the test.'}
          </p>
        </div>
      )}
      {stream && watchObjects && faces !== 'unavailable' && (
        <p className="text-xs text-muted-foreground">
          {phoneCheck === 'loading' && 'Getting the phone check ready...'}
          {phoneCheck === 'ready' && 'Phone and second-person check: ready. Keep phones out of the picture.'}
          {phoneCheck === 'unavailable' && 'The phone check could not start in this browser. The face checks still run.'}
        </p>
      )}
      {problem && (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      )}
    </div>
  )
}

const STATUS_TEXT: Record<CameraStatus, string> = {
  starting: 'Starting camera...',
  ok: 'Camera OK',
  'no-face': 'Face not visible',
  'out-of-oval': 'Keep your face inside the oval',
  'different-face': 'A different face',
  device: 'Phone in view',
  'multiple-faces': 'More than one face',
  off: 'Camera off',
  denied: 'Camera blocked',
  'no-detector': 'Camera on',
}

interface CameraPreviewProps {
  stream: MediaStream | null
  video: RefObject<HTMLVideoElement | null>
  status: CameraStatus
  /** Draw the oval the face must stay inside (camera tests). */
  oval?: boolean
}

/** A small live picture of the student in the corner of the test, so they can see what the camera sees. */
export function CameraPreview({ stream, video, status, oval = false }: CameraPreviewProps) {
  useStreamOnVideo(stream, video)
  const bad =
    status === 'no-face' ||
    status === 'out-of-oval' ||
    status === 'multiple-faces' ||
    status === 'different-face' ||
    status === 'device' ||
    status === 'off' ||
    status === 'denied'

  const { side, collapsed } = useMonitorDock()
  const vertical = side === 'top' ? 'top-[4.25rem]' : 'bottom-3'

  return (
    <>
      {/* Tucked away it slides out of sight but stays on the page, so the camera is still on and still watched. */}
      <div
        className={cn(
          'fixed right-8 z-30 flex flex-col overflow-hidden rounded-md border-2 bg-black shadow-lg transition-transform duration-300',
          vertical,
          oval ? 'w-52' : 'w-40',
          bad ? 'border-destructive' : 'border-emerald-500',
          collapsed && 'pointer-events-none translate-x-[calc(100%+2.5rem)]',
        )}
        aria-label="Your camera"
        aria-hidden={collapsed}
      >
        <div className={cn('relative w-full', oval ? 'aspect-[4/3]' : 'h-28')}>
          <video ref={video} muted playsInline className="size-full -scale-x-100 object-cover" />
          {oval && <OvalGuide good={status === 'ok' ? true : bad ? false : null} />}
        </div>
        <div className={cn('flex items-center gap-1 px-2 py-1 text-xs', bad ? 'bg-destructive text-white' : 'bg-card text-foreground')}>
          {bad ? <CameraOff className="size-3" /> : <ScanFace className="size-3" />}
          {STATUS_TEXT[status]}
        </div>
      </div>
      {/* The tab at the edge: > hides the camera box, < brings it back; the arrows move it to the top or the bottom. */}
      <div
        className={cn(
          'fixed right-0 z-30 flex flex-col items-center overflow-hidden rounded-l-md border-y-2 border-l-2 bg-card shadow-lg',
          vertical,
          bad ? 'border-destructive' : 'border-emerald-500',
        )}
      >
        <button
          type="button"
          onClick={monitorDock.toggleCollapsed}
          aria-label={collapsed ? 'Show my camera' : 'Hide my camera'}
          title={collapsed ? 'Show my camera' : 'Hide my camera (it stays on)'}
          className="flex h-8 w-6 items-center justify-center text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          {collapsed ? <ChevronLeft className="size-4" /> : <ChevronRight className="size-4" />}
        </button>
        <button
          type="button"
          onClick={monitorDock.toggleSide}
          aria-label={side === 'top' ? 'Move to the bottom' : 'Move to the top'}
          title={side === 'top' ? 'Move to the bottom' : 'Move to the top'}
          className="flex h-6 w-6 items-center justify-center text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          {side === 'top' ? <ArrowDown className="size-3.5" /> : <ArrowUp className="size-3.5" />}
        </button>
        <span className={cn('mb-1.5 mt-0.5 size-2 rounded-full', bad ? 'animate-pulse bg-destructive' : 'bg-emerald-500')} />
      </div>
    </>
  )
}
