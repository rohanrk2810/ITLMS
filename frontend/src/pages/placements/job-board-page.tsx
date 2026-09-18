import { useQuery } from '@tanstack/react-query'
import { Briefcase, MapPin } from 'lucide-react'
import { Link } from 'react-router-dom'

import { listJobs, myApplications } from '@/api/placements'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

const STAGE_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  APPLIED: 'outline',
  SHORTLISTED: 'secondary',
  INTERVIEW: 'secondary',
  ON_HOLD: 'outline',
  SELECTED: 'default',
  REJECTED: 'destructive',
  WITHDRAWN: 'destructive',
}

export function JobBoardPage() {
  const jobsQuery = useQuery({ queryKey: ['placements', 'jobs', 'board'], queryFn: () => listJobs({}) })
  const applicationsQuery = useQuery({ queryKey: ['placements', 'applications', 'mine'], queryFn: myApplications })

  return (
    <div className="flex max-w-3xl flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold">Placements</h1>
        <p className="text-muted-foreground">Open positions you may apply to (Doc S11).</p>
      </div>

      {applicationsQuery.data && applicationsQuery.data.length > 0 && (
        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-medium text-muted-foreground">My applications</h2>
          {applicationsQuery.data.map((application) => (
            <Link key={application.id} to={`/app/placements/${application.jobId}`}>
              <Card className="transition-colors hover:bg-accent/50">
                <CardContent className="flex flex-wrap items-center justify-between gap-2 pt-6">
                  <div>
                    <p className="font-medium">{application.jobTitle}</p>
                    <p className="text-sm text-muted-foreground">{application.companyName}</p>
                  </div>
                  <Badge variant={STAGE_VARIANT[application.stage] ?? 'outline'}>{application.stage}</Badge>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <div className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-muted-foreground">Open jobs</h2>
        {jobsQuery.isLoading && <Skeleton className="h-24" />}
        {jobsQuery.isSuccess && jobsQuery.data.content.length === 0 && (
          <p className="text-sm text-muted-foreground">Nothing open right now.</p>
        )}
        {jobsQuery.data?.content.map((job) => (
          <Link key={job.id} to={`/app/placements/${job.id}`}>
            <Card className="transition-colors hover:bg-accent/50">
              <CardHeader>
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <CardTitle className="text-base">{job.title}</CardTitle>
                    <p className="text-sm text-muted-foreground">{job.companyName}</p>
                  </div>
                  {job.myApplicationStage ? (
                    <Badge variant={STAGE_VARIANT[job.myApplicationStage] ?? 'outline'}>
                      {job.myApplicationStage}
                    </Badge>
                  ) : job.eligible === false ? (
                    <Badge variant="outline">Not eligible</Badge>
                  ) : (
                    <Badge>Open</Badge>
                  )}
                </div>
              </CardHeader>
              <CardContent className="flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
                <span className="flex items-center gap-1">
                  <Briefcase className="size-3.5" />
                  {job.jobType.replace('_', ' ')}
                </span>
                {job.location && (
                  <span className="flex items-center gap-1">
                    <MapPin className="size-3.5" />
                    {job.location}
                  </span>
                )}
                {job.packageOffered && <span>{job.packageOffered}</span>}
                {job.applicationDeadline && <span>Apply by {formatDate(job.applicationDeadline)}</span>}
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}
