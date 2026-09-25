import { type RefObject, useEffect, useRef, useState } from 'react'
import { Camera, CameraOff, Loader2, ScanFace } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { CAMERA_PROBLEM_TEXT, CameraError, countFaces, loadFaceDetector, openCamera } from '@/lib/face-detector'
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

interface CameraCheckProps {
  stream: MediaStream | null
  onStream: (stream: MediaStream) => void
  /** True once a face has been seen, or when the face check cannot run here (the camera itself is on). */
  onReady: (ready: boolean) => void
}

/**
 * Before a camera test starts: ask for the camera, show the student themselves, and wait until a face is in
 * view. The student cannot start until the camera is on and a face has been seen.
 */
export function CameraCheck({ stream, onStream, onReady }: CameraCheckProps) {
  const video = useRef<HTMLVideoElement>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [asking, setAsking] = useState(false)
  const [faces, setFaces] = useState<number | 'unavailable' | null>(null)
  useStreamOnVideo(stream, video)

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
          const count = countFaces(detector, video.current)
          setFaces(count)
          onReady(count === 1)
        }, 700)
      })
      .catch(() => {
        if (cancelled) return
        setFaces('unavailable')
        onReady(true)
      })
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [stream, onReady])

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
          <video ref={video} muted playsInline className="h-36 w-48 -scale-x-100 rounded-md border bg-black object-cover" />
          <p className="max-w-xs text-sm" role="status">
            {faces === 'unavailable' && 'Your camera is on. The face check is not available in this browser, so you can start.'}
            {faces === null && 'Looking for your face...'}
            {faces === 0 && 'I cannot see your face. Face the camera, in good light, and sit a little closer.'}
            {typeof faces === 'number' && faces > 1 && 'More than one face is visible. Only you should be in view.'}
            {faces === 1 && 'Your face is visible. You can start.'}
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

const STATUS_TEXT: Record<CameraStatus, string> = {
  starting: 'Starting camera...',
  ok: 'Camera OK',
  'no-face': 'Face not visible',
  'multiple-faces': 'More than one face',
  off: 'Camera off',
  denied: 'Camera blocked',
  'no-detector': 'Camera on',
}

interface CameraPreviewProps {
  stream: MediaStream | null
  video: RefObject<HTMLVideoElement | null>
  status: CameraStatus
}

/** A small live picture of the student in the corner of the test, so they can see what the camera sees. */
export function CameraPreview({ stream, video, status }: CameraPreviewProps) {
  useStreamOnVideo(stream, video)
  const bad = status === 'no-face' || status === 'multiple-faces' || status === 'off' || status === 'denied'

  return (
    <div
      className={cn(
        'fixed right-3 bottom-3 z-30 flex w-40 flex-col overflow-hidden rounded-md border-2 bg-black shadow-lg',
        bad ? 'border-destructive' : 'border-emerald-500',
      )}
      aria-label="Your camera"
    >
      <video ref={video} muted playsInline className="h-28 w-full -scale-x-100 object-cover" />
      <div className={cn('flex items-center gap-1 px-2 py-1 text-xs', bad ? 'bg-destructive text-white' : 'bg-card text-foreground')}>
        {bad ? <CameraOff className="size-3" /> : <ScanFace className="size-3" />}
        {STATUS_TEXT[status]}
      </div>
    </div>
  )
}
