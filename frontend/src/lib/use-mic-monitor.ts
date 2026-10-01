import { useEffect, useRef, useState } from 'react'

import { openAnalyser, readLevel } from '@/lib/microphone'

/** What the microphone looks like right now. */
export type MicStatus = 'starting' | 'ok' | 'sound' | 'off' | 'denied'

export type MicEventType = 'MICROPHONE_DISABLED' | 'MICROPHONE_PERMISSION_DENIED' | 'SPEECH_DETECTED'

const CHECK_EVERY_MS = 200
const MUTE_GRACE_MS = 3000
/** Sound has to carry on this long to count: a cough or a door is shorter. */
const SUSTAIN_MS = 3000
/** A brief pause inside speech does not restart the count. */
const GAP_MS = 600
/** After one report, stay quiet about further sound for this long. */
const COOLDOWN_MS = 30_000

/** The loudness above which sound is counted, from the room's own background level. */
export function thresholdFor(ambient: number): number {
  return Math.min(0.3, Math.max(0.04, ambient * 3 + 0.02))
}

interface MicMonitorOptions {
  enabled: boolean
  stream: MediaStream | null
  /** Loudness (0 to 1) above which sound counts. Measured in the room before the test; see {@link thresholdFor}. */
  threshold: number
  /** False for a live class, where students are meant to speak: only the microphone being on and allowed is watched. */
  detectSound: boolean
  onEvent: (type: MicEventType, detail?: string) => void
}

/**
 * Watches the microphone: is it still on and allowed, and (when asked) is there sustained sound. It measures only
 * loudness, in this browser; nothing is recorded or sent but a short note that sound was heard. It cannot tell speech
 * from a television, and a level meter has false alarms, so it only warns and records, never fails anyone.
 */
export function useMicMonitor({ enabled, stream, threshold, detectSound, onEvent }: MicMonitorOptions): MicStatus {
  const [status, setStatus] = useState<MicStatus>('starting')
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
    const track = stream.getAudioTracks()[0]
    let reportedOff = false

    function micOff(detail: string) {
      if (reportedOff) return
      reportedOff = true
      setStatus('off')
      report.current('MICROPHONE_DISABLED', detail)
    }
    const onEnded = () => micOff('The microphone stopped')
    const onMute = () => {
      clearTimeout(muteTimer)
      muteTimer = setTimeout(() => micOff('The microphone stopped sending sound'), MUTE_GRACE_MS)
    }
    const onUnmute = () => clearTimeout(muteTimer)
    track?.addEventListener('ended', onEnded)
    track?.addEventListener('mute', onMute)
    track?.addEventListener('unmute', onUnmute)

    navigator.permissions
      ?.query({ name: 'microphone' as PermissionName })
      .then((result) => {
        if (cancelled) return
        permission = result
        result.onchange = () => {
          if (result.state === 'denied') {
            setStatus('denied')
            report.current('MICROPHONE_PERMISSION_DENIED', 'Microphone permission was removed')
          }
        }
      })
      .catch(() => undefined)

    let context: AudioContext | undefined
    if (detectSound) {
      try {
        const opened = openAnalyser(stream)
        context = opened.context
        const buffer = new Float32Array(opened.analyser.fftSize)
        let loudSince: number | null = null
        let lastLoud = 0
        let cooldownUntil = 0
        timer = setInterval(() => {
          if (!track || track.readyState === 'ended' || !track.enabled) {
            micOff('The microphone is off')
            return
          }
          reportedOff = false
          const now = Date.now()
          const loud = readLevel(opened.analyser, buffer) > threshold
          if (loud) {
            lastLoud = now
            loudSince ??= now
            if (now - loudSince >= SUSTAIN_MS && now >= cooldownUntil) {
              cooldownUntil = now + COOLDOWN_MS
              setStatus('sound')
              report.current('SPEECH_DETECTED', `About ${Math.round((now - loudSince) / 1000)} seconds of sound`)
              loudSince = null
            }
          } else if (loudSince !== null && now - lastLoud > GAP_MS) {
            loudSince = null
          }
          if (!loud && now > cooldownUntil - COOLDOWN_MS + SUSTAIN_MS) {
            setStatus((current) => (current === 'sound' ? 'ok' : current))
          }
        }, CHECK_EVERY_MS)
        setStatus('ok')
      } catch {
        // No audio analysis here (an old browser): the microphone is still on and still watched for being switched off.
        setStatus('ok')
      }
    } else {
      setStatus('ok')
    }

    return () => {
      cancelled = true
      clearInterval(timer)
      clearTimeout(muteTimer)
      track?.removeEventListener('ended', onEnded)
      track?.removeEventListener('mute', onMute)
      track?.removeEventListener('unmute', onUnmute)
      if (permission) permission.onchange = null
      void context?.close().catch(() => undefined)
    }
  }, [enabled, stream, threshold, detectSound])

  return status
}
