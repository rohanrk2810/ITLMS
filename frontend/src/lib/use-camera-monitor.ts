import { type RefObject, useEffect, useRef, useState } from 'react'

import type { ViolationType } from '@/api/assessments'
import { loadFaceDetector, readFaces } from '@/lib/face-detector'
import { describeFace, distanceFromReference, loadFaceIdentity, SAME_FACE_MAX_DISTANCE } from '@/lib/face-identity'
import { loadObjectDetector, lookAround } from '@/lib/object-detector'

/** What the student's camera looks like right now. */
export type CameraStatus =
  | 'starting'
  | 'ok'
  | 'no-face'
  | 'out-of-oval'
  | 'multiple-faces'
  | 'different-face'
  | 'device'
  | 'off'
  | 'denied'
  | 'no-detector'

const CHECK_EVERY_MS = 1000
/** A face that is missing (or extra) for this many checks in a row is reported; one blink of the detector is not. */
const CHECKS_BEFORE_REPORT = 3
/** A problem that goes on is reported again this often, so the trainer can see how long it lasted. */
const REPEAT_REPORT_MS = 30_000
const MUTE_GRACE_MS = 3000
/** A face that stays outside the oval is warned about again this often. */
const OUTSIDE_REPEAT_MS = 10_000
/** Who is there, and what is in view, is looked at less often than whether a face is there: it costs more. */
const DEEP_CHECK_EVERY_MS = 2000
/** The same finding this many deep checks in a row is reported; a single odd frame is not. */
const DEEP_CHECKS_BEFORE_REPORT = 2
const DEEP_REPEAT_REPORT_MS = 20_000
/** What is in view (a phone) is looked at every second and reported the first time it is seen. */
const PHONE_CHECK_EVERY_MS = 1000
/** A phone that stays in view is reported again this often, so the trainer can see how long it lasted. */
const PHONE_REPEAT_REPORT_MS = 10_000
/** Looks without a phone before the phone counts as gone and the status clears. */
const PHONE_GONE_AFTER = 3

interface CameraMonitorOptions {
  enabled: boolean
  stream: MediaStream | null
  video: RefObject<HTMLVideoElement | null>
  /** Told about each problem. The server records it and answers with the warning to show. */
  onEvent: (type: ViolationType, detail?: string) => void
  /** The face(s) recorded at the start of the test. With them the monitor warns when a different face takes over. */
  reference?: Float32Array[] | null
}

/**
 * Watches a live camera for the things a proctor would notice: no face in view, a second face or person, a
 * different face from the one the test started with, a phone, the camera switched off, its permission taken away. It runs in this browser and can be fooled, so it only records and
 * warns - it never fails a student by itself. Returns the current status for the page to show.
 */
export function useCameraMonitor({ enabled, stream, video, onEvent, reference }: CameraMonitorOptions): CameraStatus {
  const [status, setStatus] = useState<CameraStatus>('starting')
  const report = useRef(onEvent)
  useEffect(() => {
    report.current = onEvent
  }, [onEvent])
  const referenceFaces = useRef(reference)
  useEffect(() => {
    referenceFaces.current = reference
  }, [reference])

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
    let reportedOutside = 0
    let reportedOff = false
    // What the slower check found: shown when the face itself is fine, and counted across checks.
    let deepFlag: CameraStatus | null = null
    let latestFaces = 0
    let differentRun = 0
    let phoneGone = 0
    let peopleRun = 0
    let reportedDifferent = 0
    let reportedPhone = 0
    let reportedPeople = 0
    let deepTimer: ReturnType<typeof setInterval> | undefined
    let phoneTimer: ReturnType<typeof setInterval> | undefined

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
          const { count: faces, inOval } = readFaces(detector, element)
          latestFaces = faces
          const now = Date.now()

          if (faces === 1 && !inOval) {
            // One face, but outside the oval: warned at the first check, not after a wait.
            missing = 0
            crowded = 0
            setStatus('out-of-oval')
            if (now - reportedOutside > OUTSIDE_REPEAT_MS) {
              reportedOutside = now
              report.current('FACE_OUT_OF_FRAME', 'Face outside the oval')
            }
          } else if (faces === 0) {
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
            reportedOutside = 0
            reportedOff = false
            setStatus(deepFlag ?? 'ok')
          }
        }, CHECK_EVERY_MS)
      })
      .catch(() => {
        // The detector could not load (old browser, blocked WebAssembly). The camera is still required and
        // still on; only the face check is unavailable. The student is not locked out for our failure.
        if (!cancelled) setStatus('no-detector')
      })

    // The slower check: is it still the same person, and is there a phone or another person in view? Either model may
    // fail to load; the face check above carries on without it.
    let identity: Awaited<ReturnType<typeof loadFaceIdentity>> | null = null
    let objects: Awaited<ReturnType<typeof loadObjectDetector>> | null = null
    if (referenceFaces.current && referenceFaces.current.length > 0) {
      void loadFaceIdentity()
        .then((loaded) => {
          if (!cancelled) identity = loaded
        })
        .catch((error: unknown) => console.warn('[camera-monitor] the same-face check could not start', error))
    }
    void loadObjectDetector()
      .then((loaded) => {
        if (!cancelled) objects = loaded
      })
      .catch((error: unknown) => console.warn('[camera-monitor] the phone check could not start', error))

    // Who is there, every couple of seconds: it costs the most.
    let deepBusy = false
    deepTimer = setInterval(() => {
      const element = video.current
      if (deepBusy || !element || !track || track.readyState === 'ended' || !track.enabled) return
      deepBusy = true
      void (async () => {
        try {
          const now = Date.now()
          const known = referenceFaces.current

          // Only when exactly one face is in view, so a second face is reported as that and not as "different".
          if (identity && known && known.length > 0 && latestFaces === 1) {
            const face = await describeFace(identity, element)
            if (face) {
              const distance = distanceFromReference(known, face)
              if (distance > SAME_FACE_MAX_DISTANCE) {
                differentRun += 1
                if (differentRun >= DEEP_CHECKS_BEFORE_REPORT) {
                  deepFlag = 'different-face'
                  setStatus('different-face')
                  if (now - reportedDifferent > DEEP_REPEAT_REPORT_MS) {
                    reportedDifferent = now
                    report.current('FACE_MISMATCH', `A different face from the start of the test (distance ${distance.toFixed(2)})`)
                  }
                }
              } else {
                differentRun = 0
                reportedDifferent = 0
                if (deepFlag === 'different-face') deepFlag = null
              }
            }
          }
        } catch {
          // A failed look is skipped; the next one tries again.
        } finally {
          deepBusy = false
        }
      })()
    }, DEEP_CHECK_EVERY_MS)

    // What is in view, every second: a phone is reported the moment it is seen, not after it has been there a while.
    phoneTimer = setInterval(() => {
      const element = video.current
      if (!objects || !element || !track || track.readyState === 'ended' || !track.enabled) return
      try {
        const scene = lookAround(objects, element)
        const now = Date.now()
        if (scene.phone) {
          phoneGone = 0
          if (deepFlag !== 'different-face') deepFlag = 'device'
          setStatus((current) => (current === 'ok' || current === 'device' ? 'device' : current))
          if (now - reportedPhone > PHONE_REPEAT_REPORT_MS) {
            reportedPhone = now
            report.current('DEVICE_DETECTED', 'A mobile phone is in view')
          }
        } else {
          // A phone has to be out of sight for a few looks before it counts as gone, so the status does not flicker.
          phoneGone += 1
          if (phoneGone >= PHONE_GONE_AFTER) {
            reportedPhone = 0
            if (deepFlag === 'device') deepFlag = null
          }
        }
        // Another person's body is a weaker sign than a phone (a poster looks like one), so it takes two looks.
        peopleRun = scene.people >= 2 ? peopleRun + 1 : 0
        if (peopleRun >= 2 && now - reportedPeople > DEEP_REPEAT_REPORT_MS) {
          reportedPeople = now
          report.current('MULTIPLE_FACES', 'Another person is in view')
        }
        if (scene.people < 2) reportedPeople = 0
      } catch {
        // A failed look is skipped; the next one tries again.
      }
    }, PHONE_CHECK_EVERY_MS)

    return () => {
      cancelled = true
      clearInterval(timer)
      clearInterval(deepTimer)
      clearInterval(phoneTimer)
      clearTimeout(muteTimer)
      track?.removeEventListener('ended', onEnded)
      track?.removeEventListener('mute', onMute)
      track?.removeEventListener('unmute', onUnmute)
      if (permission) permission.onchange = null
    }
  }, [enabled, stream, video])

  return status
}
