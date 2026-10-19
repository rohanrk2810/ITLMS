import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'

export interface VideoMonitoringValue {
  videoMonitoring?: boolean
  pauseOnNoFace?: boolean
  autoResume?: boolean
  faceGraceSeconds?: number
}

/**
 * The switches for monitoring one video lesson, each on its own. Pausing needs the camera check, and resuming by
 * itself needs pausing, so a switch that cannot do anything yet is disabled rather than hidden.
 */
export function VideoMonitoringOptions({
  value,
  onChange,
  disabled,
}: {
  value: VideoMonitoringValue
  onChange: (change: VideoMonitoringValue) => void
  disabled?: boolean
}) {
  const on = value.videoMonitoring ?? false
  const pause = on && (value.pauseOnNoFace ?? false)
  return (
    <div className="flex flex-col gap-1 rounded-md border p-2 text-sm">
      <Row
        title="Watch through the camera"
        hint="The student's camera checks that a face is in view while this video plays. Off by default."
        checked={on}
        disabled={disabled}
        onChange={(checked) =>
          onChange(checked ? { videoMonitoring: true } : { videoMonitoring: false, pauseOnNoFace: false, autoResume: false })
        }
      />
      <Row
        title="Pause when the face is missing"
        hint="The video pauses if no face is seen for the time below."
        checked={pause}
        disabled={disabled || !on}
        onChange={(checked) => onChange({ pauseOnNoFace: checked, autoResume: checked ? value.autoResume : false })}
      />
      <Row
        title="Resume by itself"
        hint="When the face is back the video carries on. Off: the student presses Resume."
        checked={pause && (value.autoResume ?? false)}
        disabled={disabled || !pause}
        onChange={(checked) => onChange({ autoResume: checked })}
      />
      {pause && (
        <label className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
          Pause after the face is missing for (seconds)
          <Input
            key={value.faceGraceSeconds}
            className="h-7 w-16"
            type="number"
            min={2}
            max={60}
            disabled={disabled}
            defaultValue={value.faceGraceSeconds ?? 5}
            onBlur={(event) => {
              const seconds = Math.min(60, Math.max(2, Number(event.target.value) || 5))
              if (seconds !== (value.faceGraceSeconds ?? 5)) onChange({ faceGraceSeconds: seconds })
            }}
          />
        </label>
      )}
    </div>
  )
}

function Row({
  title,
  hint,
  checked,
  disabled,
  onChange,
}: {
  title: string
  hint: string
  checked: boolean
  disabled?: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <div className="flex items-start justify-between gap-3 py-1">
      <div className="min-w-0">
        <p className="font-medium">{title}</p>
        <p className="text-xs text-muted-foreground">{hint}</p>
      </div>
      <Switch checked={checked} disabled={disabled} onCheckedChange={onChange} aria-label={title} />
    </div>
  )
}
