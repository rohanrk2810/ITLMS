import { type RefObject, useEffect, useRef, useState } from 'react'

import type { ViolationType } from '@/api/assessments'
import { countFaces, loadFaceDetector } from '@/lib/face-detector'

/** What the student's camera looks like right now. */
export type CameraStatus = 'starting' | 'ok' | 'no-face' | 'multiple-faces' | 'off' | 'denied' | 'no-detector'

const CHECK_EVERY_MS = 1000
/** A face that is missing (or extra) for this many checks in a row is reported; one blink of the detector is not. */
const CHECKS_BEFORE_REPORT = 3
/** A problem that goes on is reported again this often, so the trainer can see how long it lasted. */
const REPEAT_REPORT_MS = 30_000
const MUTE_GRACE_MS = 3000

interface CameraMonitorOptions {
  enabled: boolean
  stream: MediaStream | null
  video: RefObject<HTMLVideoElement | null>
  /** Told about each problem. The server records it and answers with the warning to show. */
  onEvent: (type: ViolationType, detail?: string) => void
}

/**
 * Watches a live camera for the things a proctor would notice: no face in view, a second face, the camera
 * switched off, its permission taken away. It runs in this browser and can be fooled, so it only records and
 * warns - it never fails a student by itself. Returns the current status for the page to show.
 */
export function useCameraMonitor({ enabled, stream, video, onEvent }: CameraMonitorOptions): CameraStatus {
  const [status, setStatus] = useState<CameraStatus>('starting')
  const report = useRef(onEvent)
  useEffect(() => {
    report.current = onEvent
  }, [onEvent])

  useEffect(() => {
    if (!enabled || !stream) return

    let cancelled = false
    let timer: ReturnType<typeof setInterval> | undefined
    let muteTimer: ReturnType<typeof setTimeout> | undefined
    let permission: PermissionStatus | undefined
    const track = stream.getVideoTracks()[0]

    let missing = 0
    let crowded = 0
    let reportedNoFace = 0
    let reportedCrowd = 0
    let reportedOff = false

    function cameraOff(detail: string) {
      if (reportedOff) return
      reportedOff = true
      setStatus('off')
      report.current('CAMERA_DISABLED', detail)
    }

    function onEnded() {
      cameraOff('The camera stopped')
    }
    function onMute() {
      clearTimeout(muteTimer)
      muteTimer = setTimeout(() => cameraOff('The camera stopped sending pictures'), MUTE_GRACE_MS)
    }
    function onUnmute() {
      clearTimeout(muteTimer)
    }
    track?.addEventListener('ended', onEnded)
    track?.addEventListener('mute', onMute)
    track?.addEventListener('unmute', onUnmute)

    // The permission can be taken away while the test is running (the address-bar icon).
    navigator.permissions
      ?.query({ name: 'camera' as PermissionName })
      .then((result) => {
        if (cancelled) return
        permission = result
        result.onchange = () => {
          if (result.state === 'denied') {
            setStatus('denied')
            report.current('CAMERA_PERMISSION_DENIED', 'Camera permission was removed')
          }
        }
      })
      .catch(() => undefined)

    void loadFaceDetector()
      .then((detector) => {
        if (cancelled) return
        setStatus('ok')
        timer = setInterval(() => {
          const element = video.current
          if (!element || !track) return
          if (track.readyState === 'ended' || !track.enabled) {
            cameraOff('The camera is off')
            return
          }
          const faces = countFaces(detector, element)
          const now = Date.now()

          if (faces === 0) {
            missing += 1
            crowded = 0
            if (missing >= CHECKS_BEFORE_REPORT) {
              setStatus('no-face')
              if (now - reportedNoFace > REPEAT_REPORT_MS) {
                reportedNoFace = now
                report.current('FACE_NOT_DETECTED', `No face for ${missing} seconds`)
              }
            }
          } else if (faces > 1) {
            crowded += 1
            missing = 0
            if (crowded >= CHECKS_BEFORE_REPORT) {
              setStatus('multiple-faces')
              if (now - reportedCrowd > REPEAT_REPORT_MS) {
                reportedCrowd = now
                report.current('MULTIPLE_FACES', `${faces} faces in view`)
              }
            }
          } else {
            missing = 0
            crowded = 0
            reportedNoFace = 0
            reportedCrowd = 0
            reportedOff = false
            setStatus('ok')
          }
        }, CHECK_EVERY_MS)
      })
      .catch(() => {
        // The detector could not load (old browser, blocked WebAssembly). The camera is still required and
        // still on; only the face check is unavailable. The student is not locked out for our failure.
        if (!cancelled) setStatus('no-detector')
      })

    return () => {
      cancelled = true
      clearInterval(timer)
      clearTimeout(muteTimer)
      track?.removeEventListener('ended', onEnded)
      track?.removeEventListener('mute', onMute)
      track?.removeEventListener('unmute', onUnmute)
      if (permission) permission.onchange = null
    }
  }, [enabled, stream, video])

  return status
}
