import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Clock, XCircle } from 'lucide-react'
import { Link } from 'react-router-dom'

import { availableQuizzes } from '@/api/assessments'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function TestsPage() {
  const query = useQuery({ queryKey: ['assessments', 'available'], queryFn: availableQuizzes })

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Tests</h1>
        <p className="text-muted-foreground">Everything you can sit, and how you did on the ones you&apos;ve taken.</p>
      </div>

      {query.isLoading && (
        <div className="flex flex-col gap-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
      )}

      {query.isSuccess && query.data.length === 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Nothing to take right now</CardTitle>
            <CardDescription>Tests appear here as soon as a trainer publishes one for your batch.</CardDescription>
          </CardHeader>
        </Card>
      )}

      <div className="flex flex-col gap-3">
        {query.data?.map((quiz) => (
          <Card key={quiz.id}>
            <CardHeader>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <CardTitle className="text-base">{quiz.title}</CardTitle>
                  <CardDescription>
                    {quiz.durationMinutes} min &middot; {quiz.totalMarks} marks &middot; {quiz.passPercentage}% to pass
                  </CardDescription>
                </div>
                {quiz.mandatory && <Badge variant="outline">Mandatory</Badge>}
              </div>
            </CardHeader>
            <CardContent className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2 text-sm">
                {quiz.bestPercentage != null ? (
                  <>
                    {quiz.passed ? (
                      <CheckCircle2 className="size-4 text-emerald-600" />
                    ) : (
                      <XCircle className="size-4 text-destructive" />
                    )}
                    <span>Best score: {quiz.bestPercentage}%</span>
                  </>
                ) : quiz.attemptsUsed > 0 ? (
                  <span className="text-muted-foreground">Result not released yet</span>
                ) : (
                  <span className="text-muted-foreground">Not attempted</span>
                )}
                <span className="text-muted-foreground">
                  &middot; {quiz.attemptsUsed}/{quiz.attemptsAllowed} attempts
                </span>
              </div>
              {quiz.inProgressAttemptId != null ? (
                <Button asChild size="sm">
                  <Link to={`/app/assessments/tests/${quiz.id}`}>
                    <Clock />
                    Resume
                  </Link>
                </Button>
              ) : quiz.canStart ? (
                <Button asChild size="sm">
                  <Link to={`/app/assessments/tests/${quiz.id}`}>Start test</Link>
                </Button>
              ) : quiz.attemptsUsed > 0 ? (
                <Button asChild size="sm" variant="outline">
                  <Link to={`/app/assessments/tests/${quiz.id}`}>View result</Link>
                </Button>
              ) : (
                <Button size="sm" variant="outline" disabled>
                  {quiz.openNow ? 'Not open to you' : 'Not open yet'}
                </Button>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
