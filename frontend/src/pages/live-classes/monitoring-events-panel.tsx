import { useEffect, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  MONITORING_SWITCHES,
  type EffectiveMonitoring,
  type MonitoringEvent,
  type MonitoringSwitchKey,
  getClassMonitoring,
  getMonitoringReport,
  listMonitoringEvents,
  saveMonitoringSetting,
} from '@/api/monitoring'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const LABEL: Record<MonitoringEvent['type'], string> = {
  FACE_NOT_DETECTED: 'Face not visible',
  FACE_RESTORED: 'Face back in view',
  MULTIPLE_FACES: 'More than one face',
  CAMERA_DISABLED: 'Camera off',
  CAMERA_PERMISSION_DENIED: 'Camera permission removed',
  MICROPHONE_DISABLED: 'Microphone off',
  MICROPHONE_PERMISSION_DENIED: 'Microphone permission removed',
  TAB_AWAY: 'Left the class tab',
  REMOVED_BY_MONITORING: 'Removed from the class',
}

/** Builds the full setting for this one class from what applies now, with one thing changed. */
function settingFrom(classSessionId: number, current: EffectiveMonitoring | undefined, change: Partial<EffectiveMonitoring>) {
  const c = { ...current, ...change } as Partial<EffectiveMonitoring>
  return {
    scopeType: 'SESSION' as const,
    scopeId: classSessionId,
    enabled: c.enabled ?? false,
    faceVisibility: c.faceVisibility ?? true,
    cameraRequired: c.cameraRequired ?? false,
    microphoneRequired: c.microphoneRequired ?? false,
    warningAfterSeconds: c.warningAfterSeconds ?? 10,
    showWarning: c.showWarning ?? true,
    warningMessage: c.warningMessage ?? '',
    logEvents: c.logEvents ?? true,
    tabSwitchCheck: c.tabSwitchCheck ?? false,
    attendancePenalty: c.attendancePenalty ?? false,
    strictSeverity: c.strictSeverity ?? false,
    autoRemove: c.autoRemove ?? false,
    autoRemoveAfterCritical: c.autoRemoveAfterCritical ?? 3,
    alertTrainer: c.alertTrainer ?? false,
  }
}

/** One row per option; each switch changes only its own option for this class. Wider levels stay with the admin. */
function MonitoringSwitches({ classSessionId }: { classSessionId: number }) {
  const queryClient = useQueryClient()
  const key = ['class-monitoring', classSessionId]
  const current = useQuery({ queryKey: key, queryFn: () => getClassMonitoring(classSessionId) })

  const save = useMutation({
    mutationFn: (change: Partial<EffectiveMonitoring>) =>
      saveMonitoringSetting(settingFrom(classSessionId, current.data, change)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: key }),
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not change monitoring.')),
  })

  const data = current.data
  const busy = current.isLoading || save.isPending
  const master = data?.enabled ?? false

  return (
    <div className="mb-4 flex flex-col gap-1 rounded-md border p-2 text-sm">
      <SwitchRow
        title="Camera monitoring"
        hint="The main switch. Students who join from now on are checked."
        checked={master}
        disabled={busy}
        onChange={(enabled) => save.mutate({ enabled })}
        strong
      />
      {MONITORING_SWITCHES.map((option) => (
        <SwitchRow
          key={option.key}
          title={option.label}
          hint={option.hint}
          checked={data?.[option.key as MonitoringSwitchKey] ?? false}
          disabled={busy || !master}
          onChange={(value) => save.mutate({ [option.key]: value })}
        />
      ))}
      {master && data?.autoRemove && (
        <label className="flex items-center justify-between gap-2 pl-1 text-xs text-muted-foreground">
          Remove after this many critical notes
          <Input
            key={data.autoRemoveAfterCritical}
            className="h-7 w-16"
            type="number"
            min={1}
            max={20}
            defaultValue={data.autoRemoveAfterCritical}
            onBlur={(event) => {
              const value = Math.min(20, Math.max(1, Number(event.target.value) || 3))
              if (value !== data.autoRemoveAfterCritical) save.mutate({ autoRemoveAfterCritical: value })
            }}
          />
        </label>
      )}
    </div>
  )
}

function SwitchRow({
  title,
  hint,
  checked,
  disabled,
  onChange,
  strong,
}: {
  title: string
  hint: string
  checked: boolean
  disabled: boolean
  onChange: (value: boolean) => void
  strong?: boolean
}) {
  return (
    <div className="flex items-start justify-between gap-3 py-1">
      <div className="min-w-0">
        <p className={strong ? 'font-semibold' : 'font-medium'}>{title}</p>
        <p className="text-xs text-muted-foreground">{hint}</p>
      </div>
      <Switch checked={checked} disabled={disabled} onCheckedChange={onChange} aria-label={title} />
    </div>
  )
}

/** The trainer's/staff's view: the switches, a live list of what monitoring noted, and a per-student summary. */
export function MonitoringEventsPanel({ classSessionId }: { classSessionId: number }) {
  return (
    <div className="flex flex-col gap-4">
      <MonitoringSwitches classSessionId={classSessionId} />
      <MonitoringEvents classSessionId={classSessionId} />
      <div>
        <h3 className="mb-2 text-sm font-semibold">Student summary</h3>
        <MonitoringReportTable classSessionId={classSessionId} compact />
      </div>
    </div>
  )
}

function formatSeconds(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

/** One line per student. Also shown on the class review page after the class. */
export function MonitoringReportTable({ classSessionId, compact }: { classSessionId: number; compact?: boolean }) {
  const query = useQuery({
    queryKey: ['monitoring-report', classSessionId],
    queryFn: () => getMonitoringReport(classSessionId),
    refetchInterval: compact ? 15_000 : false,
  })

  if (query.isLoading) return <Skeleton className="h-16" />
  const rows = query.data ?? []
  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground">No student has been noted.</p>
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Student</TableHead>
          <TableHead>Face away</TableHead>
          <TableHead>Tab left</TableHead>
          {!compact && <TableHead>2+ faces</TableHead>}
          {!compact && <TableHead>Camera</TableHead>}
          <TableHead>Warn / critical</TableHead>
          <TableHead>Penalty</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.studentId}>
            <TableCell className="font-medium">
              {row.studentName ?? `Student #${row.studentId}`}
              {row.removed && (
                <Badge variant="destructive" className="ml-1">
                  removed
                </Badge>
              )}
            </TableCell>
            <TableCell>
              {row.faceAbsences}x · {formatSeconds(row.faceAbsentSeconds)}
            </TableCell>
            <TableCell>
              {row.tabSwitches}x · {formatSeconds(row.tabAwaySeconds)}
            </TableCell>
            {!compact && <TableCell>{row.multipleFaces}</TableCell>}
            {!compact && <TableCell>{row.cameraProblems}</TableCell>}
            <TableCell>
              {row.warnings} / {row.critical}
            </TableCell>
            <TableCell>{row.penaltySeconds > 0 ? `-${formatSeconds(row.penaltySeconds)}` : '-'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function MonitoringEvents({ classSessionId }: { classSessionId: number }) {
  const query = useQuery({
    queryKey: ['monitoring-events', classSessionId],
    queryFn: () => listMonitoringEvents(classSessionId),
    refetchInterval: 10_000,
  })
  const setting = useQuery({
    queryKey: ['class-monitoring', classSessionId],
    queryFn: () => getClassMonitoring(classSessionId),
  })

  // With "alert the trainer" on, each new critical note raises a toast. The first load only records what is already
  // there, so opening the panel mid-class does not replay the whole history.
  const seen = useRef<Set<number> | null>(null)
  const alertOn = setting.data?.alertTrainer ?? false
  useEffect(() => {
    if (!query.data) return
    const known = seen.current
    seen.current = new Set(query.data.map((e) => e.id))
    if (!known || !alertOn) return
    for (const event of query.data) {
      if (!known.has(event.id) && event.severity === 'CRITICAL') {
        toast.warning(`${event.studentName ?? `Student #${event.studentId}`}: ${LABEL[event.type]}`, {
          description: event.durationSeconds != null ? `${event.durationSeconds}s` : undefined,
        })
      }
    }
  }, [query.data, alertOn])

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
