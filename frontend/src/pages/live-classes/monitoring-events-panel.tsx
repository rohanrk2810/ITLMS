import { useQuery } from '@tanstack/react-query'

import { type MonitoringEvent, listMonitoringEvents } from '@/api/monitoring'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'

const LABEL: Record<MonitoringEvent['type'], string> = {
  FACE_NOT_DETECTED: 'Face not visible',
  FACE_RESTORED: 'Face back in view',
  MULTIPLE_FACES: 'More than one face',
  CAMERA_DISABLED: 'Camera off',
  CAMERA_PERMISSION_DENIED: 'Camera permission removed',
}

/** The trainer's/staff's view of what student monitoring noted, newest first, refreshed while the class runs. */
export function MonitoringEventsPanel({ classSessionId }: { classSessionId: number }) {
  const query = useQuery({
    queryKey: ['monitoring-events', classSessionId],
    queryFn: () => listMonitoringEvents(classSessionId),
    refetchInterval: 10_000,
  })

  if (query.isLoading) return <Skeleton className="h-24" />
  const events = query.data ?? []
  if (events.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Nothing noted. Either monitoring is off for this class or everyone has been in view.
      </p>
    )
  }

  return (
    <ul className="flex flex-col gap-2 text-sm">
      {[...events].reverse().map((event) => (
        <li key={event.id} className="flex flex-col gap-0.5 rounded-md border p-2">
          <div className="flex items-center justify-between gap-2">
            <span className="font-medium">{event.studentName ?? `Student #${event.studentId}`}</span>
            <Badge variant={event.severity === 'CRITICAL' ? 'destructive' : 'secondary'}>{event.severity}</Badge>
          </div>
          <span>
            {LABEL[event.type]}
            {event.durationSeconds != null && ` (${event.durationSeconds}s)`}
          </span>
          <span className="text-xs text-muted-foreground">at {event.offsetLabel} into the class</span>
        </li>
      ))}
    </ul>
  )
}
