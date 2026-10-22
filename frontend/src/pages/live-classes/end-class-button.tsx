import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { PhoneOff } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { endLiveClass } from '@/api/live-classes'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { BEFORE_CLASS_ENDS } from './captions'

/**
 * Ends the class for everyone: the room closes, students are taken out, and attendance is worked out at once instead
 * of waiting for the room to empty. Asks first, because it cannot be undone. Anything the subtitles have not saved yet
 * is saved before the class closes, since a closed class accepts no more of the transcript.
 */
export function EndClassButton({ liveSessionId }: { liveSessionId: number }) {
  const [open, setOpen] = useState(false)

  const end = useMutation({
    mutationFn: async () => {
      const pending: Promise<unknown>[] = []
      window.dispatchEvent(new CustomEvent(BEFORE_CLASS_ENDS, { detail: { pending } }))
      await Promise.allSettled(pending)
      return endLiveClass(liveSessionId)
    },
    onSuccess: () => {
      setOpen(false)
      toast.success('Class ended. Attendance has been recorded.')
      // Closing the room disconnects everyone, this page included, and the room page takes it from there.
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not end the class. Try again.')),
  })

  return (
    <Dialog open={open} onOpenChange={(next) => !end.isPending && setOpen(next)}>
      <DialogTrigger asChild>
        <button type="button" className="class-room-chip bg-red-600/80 text-white hover:bg-red-600">
          <PhoneOff className="size-3.5" />
          End class
        </button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>End this class for everyone?</DialogTitle>
          <DialogDescription>
            Students are taken out of the room and attendance is recorded now. The class cannot be reopened.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={end.isPending}>
            Keep teaching
          </Button>
          <Button type="button" variant="destructive" onClick={() => end.mutate()} disabled={end.isPending}>
            {end.isPending ? 'Ending...' : 'End class'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
