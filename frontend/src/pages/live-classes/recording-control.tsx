import { useCallback, useEffect, useState } from 'react'
import { Circle, Loader2, Square } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type LiveSessionResponse, getLiveSession, startRecording, stopRecording } from '@/api/live-classes'
import { Button } from '@/components/ui/button'

/**
 * The host's record button. Starting asks LiveKit Egress to begin a room-composite capture; stopping asks it to
 * finish, which takes a moment before the file is ready - this polls so the status catches up on its own.
 */
export function RecordingControl({ liveSessionId }: { liveSessionId: number }) {
  const [session, setSession] = useState<LiveSessionResponse | null>(null)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(() => {
    getLiveSession(liveSessionId).then(setSession).catch(() => undefined)
  }, [liveSessionId])

  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, 8000)
    return () => window.clearInterval(timer)
  }, [refresh])

  async function toggle() {
    if (!session) return
    setBusy(true)
    try {
      if (session.recording) {
        await stopRecording(liveSessionId)
        toast.success('Stopping the recording - it will be ready shortly')
      } else {
        await startRecording(liveSessionId)
        toast.success('Recording started')
      }
      refresh()
    } catch (err) {
      toast.error(apiErrorMessage(err, 'That did not work.'))
    } finally {
      setBusy(false)
    }
  }

  if (!session) return null

  return (
    <div className="flex items-center gap-2 rounded-md border p-3 text-sm">
      <Button size="sm" variant={session.recording ? 'destructive' : 'outline'} disabled={busy} onClick={() => void toggle()}>
        {busy ? <Loader2 className="size-4 animate-spin" /> : session.recording ? <Square className="size-4" /> : <Circle className="size-4" />}
        {session.recording ? 'Stop recording' : 'Record this class'}
      </Button>
      {session.recording && <span className="text-xs text-muted-foreground">Recording&hellip;</span>}
    </div>
  )
}
