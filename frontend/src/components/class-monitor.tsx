import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Camera, Loader2, ShieldCheck, TriangleAlert } from 'lucide-react'

import {
  DEFAULT_WARNING,
  type EffectiveMonitoring,
  type MonitoringEventType,
  getClassMonitoring,
  reportMonitoringEvent,
} from '@/api/monitoring'
import { CameraPreview } from '@/components/camera-panel'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { CAMERA_PROBLEM_TEXT, CameraError, openCamera } from '@/lib/face-detector'
import { useClassMonitor } from '@/lib/use-class-monitor'

/**
 * Wraps a student's live-class room. When an administrator has switched monitoring on for this class the student
 * is told so, asked for the camera, and only then let in; where it is off, this renders the room untouched and
 * never asks for the camera.
 */
export function ClassMonitorGate({ classSessionId, children }: { classSessionId: number; children: ReactNode }) {
  const query = useQuery({
    queryKey: ['class-monitoring', classSessionId],
    queryFn: () => getClassMonitoring(classSessionId),
    retry: 1,
  })
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [skipped, setSkipped] = useState(false)
  const [asking, setAsking] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)

  // Stop the camera when leaving the class.
  useEffect(() => () => stream?.getTracks().forEach((track) => track.stop()), [stream])

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

  if (query.isLoading) return <Skeleton className="h-[70vh] max-w-4xl" />
  const settings = query.data
  // If the setting could not be read, behave as "off": a failed lookup must not lock a student out of class.
  if (!settings || !settings.enabled || skipped) return <>{children}</>

  if (!stream) {
    return (
      <Card className="max-w-xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="size-5" />
            Camera monitoring is on for this class
          </CardTitle>
          <CardDescription>Your institute has turned on attendance monitoring for this class.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3 text-sm">
          <ul className="list-disc pl-5 text-muted-foreground">
            <li>Your camera is used only to check that your face is visible.</li>
            <li>
              No pictures or video are stored. Only short notes are kept, such as &quot;face not visible for 20
              seconds&quot;.
            </li>
            <li>Your trainer and the administrator can see those notes.</li>
          </ul>
          {settings.cameraRequired && <p className="font-medium">You need to allow the camera to join this class.</p>}
          {problem && (
            <p role="alert" className="text-destructive">
              {problem}
            </p>
          )}
          <div className="flex gap-2">
            <Button onClick={() => void allow()} disabled={asking}>
              {asking ? <Loader2 className="animate-spin" /> : <Camera />}
              Allow camera and join
            </Button>
            {!settings.cameraRequired && (
              <Button variant="outline" onClick={() => setSkipped(true)}>
                Join without camera monitoring
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <>
      {children}
      <ClassMonitorRunner classSessionId={classSessionId} stream={stream} settings={settings} />
    </>
  )
}

function ClassMonitorRunner({
  classSessionId,
  stream,
  settings,
}: {
  classSessionId: number
  stream: MediaStream
  settings: EffectiveMonitoring
}) {
  const video = useRef<HTMLVideoElement>(null)
  const onEvent = useCallback(
    (type: MonitoringEventType, durationSeconds?: number, detail?: string) => {
      // A lost report is not worth interrupting the class for; the next check reports again.
      void reportMonitoringEvent(classSessionId, { type, durationSeconds, detail }).catch(() => undefined)
    },
    [classSessionId],
  )
  const { status, warn } = useClassMonitor({ stream, video, settings, onEvent })

  return (
    <>
      <CameraPreview stream={stream} video={video} status={status} />
      {settings.showWarning && warn && (
        <div
          role="status"
          className="pointer-events-none fixed top-16 left-1/2 z-40 flex -translate-x-1/2 items-center gap-2 rounded-md border border-amber-500 bg-amber-100 px-4 py-2 text-sm font-medium text-amber-900 shadow-lg dark:bg-amber-950 dark:text-amber-100"
        >
          <TriangleAlert className="size-4 shrink-0" />
          {settings.warningMessage || DEFAULT_WARNING}
        </div>
      )}
    </>
  )
}
