import { useEffect, useState } from 'react'
import { LiveKitRoom, VideoConference } from '@livekit/components-react'
import { ChevronLeft } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import '@livekit/components-styles'

import { type JoinTokenResponse, joinLiveClass } from '@/api/live-classes'
import { apiErrorMessage } from '@/api/client'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function LiveClassRoomPage() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const navigate = useNavigate()
  const [credentials, setCredentials] = useState<JoinTokenResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!sessionId) return
    let cancelled = false
    setCredentials(null)
    setError(null)
    joinLiveClass(sessionId)
      .then((token) => {
        if (!cancelled) setCredentials(token)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(apiErrorMessage(err, 'Could not join this class.'))
      })
    return () => {
      cancelled = true
    }
  }, [sessionId])

  if (error) {
    return (
      <div className="flex max-w-2xl flex-col gap-4">
        <BackLink />
        <Card>
          <CardHeader>
            <CardTitle>Can&apos;t join right now</CardTitle>
            <CardDescription>{error}</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  if (!credentials) {
    return <Skeleton className="h-[70vh] max-w-4xl" />
  }

  return (
    <div className="-m-6 flex h-[calc(100dvh-3.5rem)] flex-col">
      <div className="flex items-center gap-3 border-b bg-card px-4 py-2">
        <Link to="/app/live-classes" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ChevronLeft className="size-4" />
          Leave
        </Link>
        <span className="text-sm font-medium">{credentials.topic ?? credentials.courseTitle}</span>
      </div>
      <div className="flex-1">
        <LiveKitRoom
          serverUrl={credentials.serverUrl}
          token={credentials.token}
          connect
          video={credentials.canPublish}
          audio={credentials.canPublish}
          data-lk-theme="default"
          style={{ height: '100%' }}
          onDisconnected={() => void navigate('/app/live-classes')}
        >
          <VideoConference />
        </LiveKitRoom>
      </div>
    </div>
  )
}

function BackLink() {
  return (
    <Link
      to="/app/live-classes"
      className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
    >
      <ChevronLeft className="size-4" />
      Live classes
    </Link>
  )
}
