import { useQuery } from '@tanstack/react-query'

import { apiErrorMessage } from '@/api/client'
import { getMyProgress } from '@/api/progress'
import { ProgressReportView } from '@/components/progress-report'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

/** A student's own progress report: how far they have got, how they have done, and what to do next. */
export function MyProgressPage() {
  const query = useQuery({ queryKey: ['progress', 'me'], queryFn: getMyProgress })

  return (
    <div className="flex max-w-5xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">My progress</h1>
        <p className="text-muted-foreground">
          Course progress, attendance, tests, coding and assignments in one place, with what to do next.
        </p>
      </div>

      {query.isLoading && <Skeleton className="h-96" />}
      {query.isError && (
        <Card>
          <CardHeader>
            <CardTitle>Your report could not be loaded</CardTitle>
            <CardDescription>{apiErrorMessage(query.error, 'Try again in a moment.')}</CardDescription>
          </CardHeader>
        </Card>
      )}
      {query.data && <ProgressReportView report={query.data} />}
    </div>
  )
}
