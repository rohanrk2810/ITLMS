import { useEffect, useState } from 'react'
import { UserRound } from 'lucide-react'

import { studentPhotoObjectUrl } from '@/api/students'
import { cn } from '@/lib/utils'

/** A student's photo, or a placeholder when none has been added. */
export function StudentPhoto({
  studentId,
  hasPhoto,
  version = 0,
  className,
}: {
  studentId: number
  hasPhoto: boolean
  /** Change it to reload after an upload. */
  version?: number
  className?: string
}) {
  const [url, setUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!hasPhoto) return
    let live = true
    let made: string | null = null
    studentPhotoObjectUrl(studentId)
      .then((u) => {
        if (!live) {
          URL.revokeObjectURL(u)
          return
        }
        made = u
        setUrl(u)
      })
      .catch(() => undefined)
    return () => {
      live = false
      if (made) URL.revokeObjectURL(made)
    }
  }, [studentId, hasPhoto, version])

  return (
    <div className={cn('flex size-24 items-center justify-center overflow-hidden rounded-lg border bg-muted', className)}>
      {hasPhoto && url ? (
        <img src={url} alt="" className="size-full object-cover" />
      ) : (
        <UserRound className="size-10 text-muted-foreground" />
      )}
    </div>
  )
}
