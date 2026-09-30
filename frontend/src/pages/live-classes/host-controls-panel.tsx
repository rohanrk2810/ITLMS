import { useCallback, useEffect, useState } from 'react'
import { Camera, Mic, MicOff, MonitorUp, UserX } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  type ParticipantControl,
  type RoomControls,
  type RoomPolicy,
  getRoomControls,
  muteAllStudents,
  muteParticipant,
  removeFromClass,
  updateParticipantPermissions,
  updateRoomPolicy,
} from '@/api/live-classes'
import { Button } from '@/components/ui/button'

/**
 * The class trainer's and staff's controls over what students may switch on. Every button calls the server, which
 * checks the caller is a host of this class; hiding this panel from students is a convenience, not the protection.
 */
export function RoomControlsBody({ liveSessionId }: { liveSessionId: number }) {
  const [controls, setControls] = useState<RoomControls | null>(null)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(() => {
    getRoomControls(liveSessionId)
      .then(setControls)
      .catch(() => undefined)
  }, [liveSessionId])

  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, 8000)
    return () => window.clearInterval(timer)
  }, [refresh])

  async function run(action: () => Promise<RoomControls | number | void>, done?: string) {
    setBusy(true)
    try {
      const result = await action()
      if (result && typeof result === 'object') setControls(result)
      else refresh()
      if (done) toast.success(done)
    } catch (err) {
      toast.error(apiErrorMessage(err, 'That did not work.'))
    } finally {
      setBusy(false)
    }
  }

  if (!controls) return null
  const students = controls.participants.filter((p) => p.role === 'STUDENT')

  function policyToggle(key: keyof RoomPolicy, label: string, icon: React.ReactNode) {
    const on = controls!.policy[key]
    return (
      <Button
        size="sm"
        variant={on ? 'default' : 'outline'}
        disabled={busy}
        onClick={() => void run(() => updateRoomPolicy(liveSessionId, { [key]: !on }))}
      >
        {icon}
        {label}: {on ? 'allowed' : 'off'}
      </Button>
    )
  }

  return (
    <div className="flex flex-col gap-3 text-sm">
      <div>
        <h2 className="font-semibold">Class controls</h2>
        <p className="text-xs text-muted-foreground">What students may switch on. Applies at once.</p>
      </div>
      <div className="flex flex-wrap gap-2">
        {policyToggle('studentsCanMic', 'Student mics', <Mic className="size-4" />)}
        {policyToggle('studentsCanCamera', 'Student cameras', <Camera className="size-4" />)}
        {policyToggle('studentsCanShareScreen', 'Student screen share', <MonitorUp className="size-4" />)}
      </div>
      <Button
        size="sm"
        variant="outline"
        disabled={busy}
        onClick={() => void run(() => muteAllStudents(liveSessionId), 'Student microphones muted')}
      >
        <MicOff className="size-4" />
        Mute all students
      </Button>

      <h3 className="mt-2 font-medium">Students ({students.length})</h3>
      {students.length === 0 && <p className="text-xs text-muted-foreground">Nobody has joined yet.</p>}
      <ul className="flex flex-col gap-2">
        {students.map((s) => (
          <StudentRow
            key={s.userId}
            student={s}
            busy={busy}
            onToggle={(change) => void run(() => updateParticipantPermissions(liveSessionId, s.userId, change))}
            onMute={() => void run(() => muteParticipant(liveSessionId, s.userId, 'MICROPHONE'), 'Muted')}
            onRemove={() => void run(() => removeFromClass(liveSessionId, s.userId), 'Removed from class')}
          />
        ))}
      </ul>
    </div>
  )
}

function StudentRow(props: {
  student: ParticipantControl
  busy: boolean
  onToggle: (change: { microphone?: boolean; camera?: boolean; screenShare?: boolean; followRoom?: boolean }) => void
  onMute: () => void
  onRemove: () => void
}) {
  const { student: s, busy } = props
  const overridden =
    s.microphoneOverride != null || s.cameraOverride != null || s.screenShareOverride != null
  return (
    <li className="rounded-md border p-2">
      <div className="flex items-center justify-between gap-2">
        <span className={s.inRoom ? 'font-medium' : 'text-muted-foreground'}>
          {s.displayName}
          {!s.inRoom && ' (left)'}
        </span>
        {s.inRoom && (
          <span className="flex gap-1">
            <Button size="icon" variant="ghost" title="Mute microphone" disabled={busy} onClick={props.onMute}>
              <MicOff className="size-4" />
            </Button>
            <Button size="icon" variant="ghost" title="Remove from class" disabled={busy} onClick={props.onRemove}>
              <UserX className="size-4" />
            </Button>
          </span>
        )}
      </div>
      <div className="mt-1 flex flex-wrap gap-1">
        <Button size="sm" variant={s.microphone ? 'default' : 'outline'} disabled={busy}
          onClick={() => props.onToggle({ microphone: !s.microphone })}>Mic</Button>
        <Button size="sm" variant={s.camera ? 'default' : 'outline'} disabled={busy}
          onClick={() => props.onToggle({ camera: !s.camera })}>Camera</Button>
        <Button size="sm" variant={s.screenShare ? 'default' : 'outline'} disabled={busy}
          onClick={() => props.onToggle({ screenShare: !s.screenShare })}>Screen</Button>
        {overridden && (
          <Button size="sm" variant="ghost" disabled={busy} onClick={() => props.onToggle({ followRoom: true })}>
            Follow room
          </Button>
        )}
      </div>
    </li>
  )
}
