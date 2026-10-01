import { type RefObject, useEffect, useRef, useState } from 'react'

import type { EffectiveMonitoring, MonitoringEventType } from '@/api/monitoring'
import { countFaces, loadFaceDetector } from '@/lib/face-detector'
import type { CameraStatus } from '@/lib/use-camera-monitor'

const CHECK_EVERY_MS = 1000
const MUTE_GRACE_MS = 3000
/** A face gone this long is reported a second time, as the more serious "still not back". */
const ESCALATE_AFTER_S = 60

export interface ClassMonitorOptions {
  stream: MediaStream | null
  video: RefObject<HTMLVideoElement | null>
  settings: EffectiveMonitoring
  onEvent: (type: MonitoringEventType, durationSeconds?: number, detail?: string) => void
}

/**
 * Watches the student's camera during a live class: is a face reasonably visible, is the camera still on and
 * allowed. It runs in this browser, sends only short notes (never a picture), and never blocks the class. The
 * returned `warn` is true while the student should be asked to sit properly, once the face has been gone for the
 * time the administrator configured.
 */
export function useClassMonitor({ stream, video, settings, onEvent }: ClassMonitorOptions) {
  const [status, setStatus] = useState<CameraStatus>('starting')
  const [warn, setWarn] = useState(false)
  const report = useRef(onEvent)
  useEffect(() => {
    report.current = onEvent
  }, [onEvent])

  const { faceVisibility, warningAfterSeconds } = settings

  useEffect(() => {
    if (!stream) return
    let cancelled = false
    let timer: ReturnType<typeof setInterval> | undefined
    let muteTimer: ReturnType<typeof setTimeout> | undefined
    let permission: PermissionStatus | undefined
    const track = stream.getVideoTracks()[0]

    let missingSince: number | null = null
    let reportedAbsence = false
    let escalated = false
    let multiple = 0
    let reportedMultiple = false
    let cameraReportedOff = false

    function cameraOff(detail: string) {
      if (cameraReportedOff) return
      cameraReportedOff = true
      setStatus('off')
      setWarn(true)
      report.current('CAMERA_DISABLED', undefined, detail)
    }

    const onEnded = () => cameraOff('The camera stopped')
    const onMute = () => {
      clearTimeout(muteTimer)
      muteTimer = setTimeout(() => cameraOff('The camera stopped sending pictures'), MUTE_GRACE_MS)
    }
    const onUnmute = () => clearTimeout(muteTimer)
    track?.addEventListener('ended', onEnded)
    track?.addEventListener('mute', onMute)
    track?.addEventListener('unmute', onUnmute)

    navigator.permissions
      ?.query({ name: 'camera' as PermissionName })
      .then((result) => {
        if (cancelled) return
        permission = result
        result.onchange = () => {
          if (result.state === 'denied') {
            setStatus('denied')
            setWarn(true)
            report.current('CAMERA_PERMISSION_DENIED', undefined, 'Camera permission was removed')
          }
        }
      })
      .catch(() => undefined)

    // With the face check off there is nothing to wait for: the camera simply counts as OK (see the return).
    if (faceVisibility) {
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
            cameraReportedOff = false
            const faces = countFaces(detector, element)
            const now = Date.now()

            if (faces === 0) {
              missingSince ??= now
              const gone = Math.round((now - missingSince) / 1000)
              if (gone >= warningAfterSeconds) {
                setStatus('no-face')
                setWarn(true)
                if (!reportedAbsence) {
                  reportedAbsence = true
                  report.current('FACE_NOT_DETECTED', gone, 'Face not visible')
                } else if (!escalated && gone >= ESCALATE_AFTER_S) {
                  escalated = true
                  report.current('FACE_NOT_DETECTED', gone, 'Face still not visible')
                }
              }
              return
            }

            // A face is back (or there are several): close out any absence.
            if (missingSince !== null && reportedAbsence) {
              report.current('FACE_RESTORED', Math.round((now - missingSince) / 1000))
            }
            missingSince = null
            reportedAbsence = false
            escalated = false
            setWarn(false)

            if (faces > 1) {
              multiple += 1
              if (multiple >= 3 && !reportedMultiple) {
                reportedMultiple = true
                setStatus('multiple-faces')
                report.current('MULTIPLE_FACES', undefined, `${faces} faces in view`)
              }
            } else {
              multiple = 0
              reportedMultiple = false
              setStatus('ok')
            }
          }, CHECK_EVERY_MS)
        })
        .catch(() => {
          // The detector could not load. The camera stays on; only the face check is unavailable here.
          if (!cancelled) setStatus('no-detector')
        })
    }

    return () => {
      cancelled = true
      clearInterval(timer)
      clearTimeout(muteTimer)
      track?.removeEventListener('ended', onEnded)
      track?.removeEventListener('mute', onMute)
      track?.removeEventListener('unmute', onUnmute)
      if (permission) permission.onchange = null
    }
  }, [stream, video, faceVisibility, warningAfterSeconds])

  return { status: !faceVisibility && status === 'starting' ? ('ok' as const) : status, warn }
}
