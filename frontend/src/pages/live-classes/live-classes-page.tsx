import { useQuery } from '@tanstack/react-query'
import { History, Radio, Video } from 'lucide-react'
import { Link } from 'react-router-dom'

import { pastLiveClasses, upcomingLiveClasses } from '@/api/live-classes'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  LIVE: 'default',
  SCHEDULED: 'secondary',
  ENDED: 'outline',
  CANCELLED: 'destructive',
}

export function LiveClassesPage() {
  const query = useQuery({ queryKey: ['live-classes', 'upcoming'], queryFn: upcomingLiveClasses })
  const past = useQuery({ queryKey: ['live-classes', 'past'], queryFn: pastLiveClasses })

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Live classes</h1>
        <p className="text-muted-foreground">Your batch&apos;s online sessions.</p>
      </div>

      {query.isLoading && (
        <div className="flex flex-col gap-3">
          {[0, 1].map((i) => (
            <Skeleton key={i} className="h-24" />
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

      <div className="flex flex-col gap-3">
        {query.data?.map((session) => (
          <Card key={session.id}>
            <CardContent className="flex flex-wrap items-center justify-between gap-3 pt-6">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="font-medium">{session.topic ?? session.courseTitle}</h2>
                  <Badge variant={STATUS_VARIANT[session.status] ?? 'outline'}>
                    {session.status === 'LIVE' && <Radio className="size-3" />}
                    {session.status}
                  </Badge>
                </div>
                <p className="text-sm text-muted-foreground">
                  {session.batchCode} &middot; {formatWhen(session.scheduledStartAt)}
                </p>
              </div>
              <Button asChild variant={session.joinable ? 'default' : 'outline'}>
                <Link to={`/app/live-classes/${session.classSessionId}`}>
                  <Video />
                  {session.joinable ? 'Join' : 'View'}
                </Link>
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      {(past.isLoading || (past.data && past.data.length > 0)) && (
        <div>
          <h2 className="mb-3 text-lg font-semibold">Past classes</h2>
          {past.isLoading && <Skeleton className="h-16" />}
          <div className="flex flex-col gap-3">
            {past.data?.map((session) => (
              <Card key={session.id}>
                <CardContent className="flex flex-wrap items-center justify-between gap-3 pt-6">
                  <div>
                    <h3 className="font-medium">{session.topic ?? session.courseTitle}</h3>
                    <p className="text-sm text-muted-foreground">
                      {session.batchCode} &middot; {formatWhen(session.scheduledStartAt)}
                      {!session.recordingUrl && ' · no recording'}
                    </p>
                  </div>
                  <Button asChild variant="outline">
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
