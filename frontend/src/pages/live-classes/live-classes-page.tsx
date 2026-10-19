import { useQuery } from '@tanstack/react-query'
import { CalendarClock, History, PlayCircle, Video, Users } from 'lucide-react'
import { Link } from 'react-router-dom'

import './live-room.css'

import { type LiveSessionResponse, pastLiveClasses, upcomingLiveClasses } from '@/api/live-classes'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

/** "in 2 h 10 min", "in 3 days", or "starting now". */
function startsIn(iso: string): string {
  const minutes = Math.round((new Date(iso).getTime() - Date.now()) / 60000)
  if (minutes <= 0) return 'starting now'
  if (minutes < 60) return `in ${minutes} min`
  if (minutes < 60 * 24) return `in ${Math.floor(minutes / 60)} h ${minutes % 60} min`
  return `in ${Math.round(minutes / (60 * 24))} day${minutes >= 60 * 48 ? 's' : ''}`
}

/** A small calendar-page tile: month on top, day number below. */
function DateTile({ iso, live }: { iso: string; live?: boolean }) {
  const date = new Date(iso)
  return (
    <div
      className={
        'flex size-14 shrink-0 flex-col items-center justify-center rounded-xl border text-center leading-none ' +
        (live ? 'border-white/20 bg-white/10 text-white' : 'bg-secondary text-secondary-foreground')
      }
    >
      <span className="text-[0.65rem] font-semibold uppercase tracking-wider opacity-80">
        {date.toLocaleDateString(undefined, { month: 'short' })}
      </span>
      <span className="mt-1 text-xl font-bold">{date.getDate()}</span>
    </div>
  )
}

/** The class that is on right now: dark, glowing, with one obvious button. */
function LiveNowCard({ session }: { session: LiveSessionResponse }) {
  return (
    <div className="class-room relative overflow-hidden rounded-2xl border border-white/10 p-5 text-white shadow-lg">
      <div className="relative flex flex-wrap items-center gap-4">
        <DateTile iso={session.scheduledStartAt} live />
        <div className="min-w-0 flex-1">
          <span className="live-pill">LIVE NOW</span>
          <h2 className="mt-1.5 truncate text-lg font-semibold">{session.topic ?? session.courseTitle}</h2>
          <p className="text-sm text-white/70">
            {session.batchCode} &middot; started {formatTime(session.scheduledStartAt)}
          </p>
        </div>
        <Button asChild size="lg" className="rounded-full px-6">
          <Link to={`/app/live-classes/${session.classSessionId}`}>
            <PlayCircle />
            Join class
          </Link>
        </Button>
      </div>
    </div>
  )
}

function UpcomingCard({ session }: { session: LiveSessionResponse }) {
  return (
    <Card className="transition-shadow hover:shadow-md">
      <CardContent className="flex flex-wrap items-center gap-4 pt-6">
        <DateTile iso={session.scheduledStartAt} />
        <div className="min-w-0 flex-1">
          <h2 className="truncate font-medium">{session.topic ?? session.courseTitle}</h2>
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Users className="size-3.5" />
              {session.batchCode}
            </span>
            <span className="inline-flex items-center gap-1">
              <CalendarClock className="size-3.5" />
              {formatTime(session.scheduledStartAt)} &middot; {startsIn(session.scheduledStartAt)}
            </span>
          </p>
        </div>
        <Button asChild variant={session.joinable ? 'default' : 'outline'} className="rounded-full">
          <Link to={`/app/live-classes/${session.classSessionId}`}>
            <Video />
            {session.joinable ? 'Join' : 'View'}
          </Link>
        </Button>
      </CardContent>
    </Card>
  )
}

export function LiveClassesPage() {
  const query = useQuery({ queryKey: ['live-classes', 'upcoming'], queryFn: upcomingLiveClasses })
  const past = useQuery({ queryKey: ['live-classes', 'past'], queryFn: pastLiveClasses })

  const live = query.data?.filter((s) => s.status === 'LIVE') ?? []
  const later = query.data?.filter((s) => s.status !== 'LIVE') ?? []

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Live classes</h1>
        <p className="text-muted-foreground">Your batch&apos;s online sessions.</p>
      </div>

      {query.isLoading && (
        <div className="flex flex-col gap-3">
          {[0, 1].map((i) => (
            <Skeleton key={i} className="h-24 rounded-2xl" />
          ))}
        </div>
      )}

      {query.isSuccess && query.data.length === 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Nothing scheduled</CardTitle>
            <CardDescription>Your trainer hasn&apos;t scheduled an online class yet.</CardDescription>
          </CardHeader>
        </Card>
      )}

      {live.length > 0 && (
        <div className="flex flex-col gap-3">
          {live.map((session) => (
            <LiveNowCard key={session.id} session={session} />
          ))}
        </div>
      )}

      {later.length > 0 && (
        <div>
          <h2 className="mb-3 text-lg font-semibold">Coming up</h2>
          <div className="flex flex-col gap-3">
            {later.map((session) => (
              <UpcomingCard key={session.id} session={session} />
            ))}
          </div>
        </div>
      )}

      {(past.isLoading || (past.data && past.data.length > 0)) && (
        <div>
          <h2 className="mb-3 text-lg font-semibold">Past classes</h2>
          {past.isLoading && <Skeleton className="h-16" />}
          <div className="flex flex-col gap-3">
            {past.data?.map((session) => (
              <Card key={session.id} className="transition-shadow hover:shadow-md">
                <CardContent className="flex flex-wrap items-center gap-4 pt-6">
                  <DateTile iso={session.scheduledStartAt} />
                  <div className="min-w-0 flex-1">
                    <h3 className="truncate font-medium">{session.topic ?? session.courseTitle}</h3>
                    <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                      {session.batchCode} &middot; {formatTime(session.scheduledStartAt)}
                      {session.recordingUrl ? (
                        <Badge variant="secondary">Recording</Badge>
                      ) : (
                        <span>no recording</span>
                      )}
                    </p>
                  </div>
                  <Button asChild variant="outline" className="rounded-full">
                    <Link to={`/app/live-classes/${session.classSessionId}/review`}>
                      <History />
                      Review
                    </Link>
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
