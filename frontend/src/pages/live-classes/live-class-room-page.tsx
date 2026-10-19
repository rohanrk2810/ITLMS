import { useEffect, useState } from 'react'
import { LiveKitRoom, VideoConference, useParticipants } from '@livekit/components-react'
import { ChevronLeft, Clock, ExternalLink, Users } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import '@livekit/components-styles'
import './live-room.css'

import { type JoinTokenResponse, joinLiveClass } from '@/api/live-classes'
import { apiErrorMessage } from '@/api/client'
import { ClassMonitorGate } from '@/components/class-monitor'
import { ContentProtection } from '@/components/content-protection'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { isJitsiUrl, jitsiEmbedUrl } from '@/lib/jitsi'
import { useAuthStore } from '@/stores/auth-store'
import { HostSidePanel } from './host-side-panel'
import { LiveQuestionPanel } from './live-question-panel'

export function LiveClassRoomPage() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const navigate = useNavigate()
  const [credentials, setCredentials] = useState<JoinTokenResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const user = useAuthStore((s) => s.user)

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

  if (credentials.externalUrl && isJitsiUrl(credentials.externalUrl)) {
    return <JitsiClass credentials={credentials} url={credentials.externalUrl} />
  }

  if (credentials.externalUrl) {
    return <ExternalClass credentials={credentials} url={credentials.externalUrl} />
  }

  const room = (
    <div className="-m-6 flex h-[calc(100dvh-3.5rem)] flex-col">
      <div className="flex min-h-0 flex-1">
        <div className="relative min-w-0 flex-1">
          <LiveKitRoom
            serverUrl={credentials.serverUrl}
            token={credentials.token}
            connect
            video={credentials.canCamera}
            audio={credentials.canMic}
            data-lk-theme="default"
            className="class-room"
            style={{ height: '100%' }}
            onDisconnected={() => void navigate('/app/live-classes')}
          >
            <div className="flex h-full flex-col">
              <RoomBar title={credentials.topic ?? credentials.courseTitle} startsAt={credentials.scheduledStartAt} />
              {/* The recording captures exactly this box: the class itself, without the side panel or the bar. */}
              <div id="class-stage" className="min-h-0 flex-1">
                <ContentProtection
                  active={!credentials.roomAdmin}
                  watermarkLabel={user ? `${user.fullName} · ${user.email}` : ''}
                  className="h-full"
                >
                  <VideoConference />
                </ContentProtection>
              </div>
            </div>
            {!credentials.roomAdmin && <LiveQuestionPanel classSessionId={credentials.classSessionId} />}
          </LiveKitRoom>
        </div>
        {credentials.roomAdmin && (
          <HostSidePanel liveSessionId={credentials.liveSessionId} classSessionId={credentials.classSessionId} />
        )}
      </div>
    </div>
  )

  // Only a student is monitored, and only where an administrator switched it on for this class.
  return credentials.roomAdmin ? (
    room
  ) : (
    <ClassMonitorGate classSessionId={credentials.classSessionId}>{room}</ClassMonitorGate>
  )
}

/** Top bar of the room: a pulsing LIVE badge, the class name, how long it has been running and who is in it. */
function RoomBar({ title, startsAt }: { title: string; startsAt: string }) {
  const people = useParticipants().length
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  const seconds = Math.max(0, Math.floor((now - new Date(startsAt).getTime()) / 1000))
  const clock = [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map((n) => String(n).padStart(2, '0'))
    .join(':')

  return (
    <div className="class-room-bar">
      <Link to="/app/live-classes" className="flex items-center gap-1 text-sm text-white/70 hover:text-white">
        <ChevronLeft className="size-4" />
        Leave
      </Link>
      <span className="live-pill">LIVE</span>
      <span className="truncate text-sm font-semibold">{title}</span>
      <span className="ml-auto flex items-center gap-2">
        <span className="class-room-chip">
          <Clock className="size-3.5" />
          {clock}
        </span>
        <span className="class-room-chip">
          <Users className="size-3.5" />
          {people}
        </span>
      </span>
    </div>
  )
}

/** A Jitsi class is shown inside this page. */
function JitsiClass({ credentials, url }: { credentials: JoinTokenResponse; url: string }) {
  return (
    <div className="-m-6 flex h-[calc(100dvh-3.5rem)] flex-col">
      <div className="flex items-center gap-3 border-b bg-card px-4 py-2">
        <Link to="/app/live-classes" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ChevronLeft className="size-4" />
          Leave
        </Link>
        <span className="text-sm font-medium">{credentials.topic ?? credentials.courseTitle}</span>
      </div>
      <iframe
        title={credentials.topic ?? credentials.courseTitle}
        src={jitsiEmbedUrl(url, credentials.displayName)}
        allow="camera; microphone; display-capture; fullscreen; autoplay"
        className="min-h-0 w-full flex-1 border-0"
      />
    </div>
  )
}

/** The class runs on another platform; browsers refuse to embed Meet/Zoom, so this opens it in a new tab. */
function ExternalClass({ credentials, url }: { credentials: JoinTokenResponse; url: string }) {
  return (
    <div className="flex max-w-2xl flex-col gap-4">
      <BackLink />
      <Card>
        <CardHeader>
          <CardTitle>{credentials.topic ?? credentials.courseTitle}</CardTitle>
          <CardDescription>
            This class is held on an external meeting platform ({new URL(url).hostname}).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild>
            <a href={url} target="_blank" rel="noopener noreferrer">
              <ExternalLink />
              Open meeting
            </a>
          </Button>
        </CardContent>
      </Card>
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
