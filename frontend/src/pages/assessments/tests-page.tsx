import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Clock, XCircle } from 'lucide-react'
import { Link } from 'react-router-dom'

import { availableQuizzes } from '@/api/assessments'
import { myAssignments } from '@/api/assignments'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDate } from '@/lib/format'

export function TestsPage() {
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Assessments</h1>
        <p className="text-muted-foreground">Tests to sit and assignments to hand in.</p>
      </div>

      <Tabs defaultValue="tests">
        <TabsList>
          <TabsTrigger value="tests">Tests</TabsTrigger>
          <TabsTrigger value="assignments">Assignments</TabsTrigger>
        </TabsList>
        <TabsContent value="tests" className="mt-4">
          <TestsList />
        </TabsContent>
        <TabsContent value="assignments" className="mt-4">
          <AssignmentsList />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function TestsList() {
  const query = useQuery({ queryKey: ['assessments', 'available'], queryFn: availableQuizzes })

  return (
    <div className="flex flex-col gap-3">
      {query.isLoading && [0, 1, 2].map((i) => <Skeleton key={i} className="h-28" />)}

      {query.isSuccess && query.data.length === 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Nothing to take right now</CardTitle>
            <CardDescription>Tests appear here as soon as a trainer publishes one for your batch.</CardDescription>
          </CardHeader>
        </Card>
      )}

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
              <div className="flex items-center gap-2">
                {quiz.secureMode && <Badge variant="outline">Secure test</Badge>}
                {quiz.mandatory && <Badge variant="outline">Mandatory</Badge>}
              </div>
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
  )
}

const ASSIGNMENT_STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  SUBMITTED: 'secondary',
  LATE: 'destructive',
  EVALUATED: 'default',
  RETURNED: 'outline',
}

function AssignmentsList() {
  const query = useQuery({ queryKey: ['assignments', 'mine'], queryFn: myAssignments })

  return (
    <div className="flex flex-col gap-3">
      {query.isLoading && [0, 1].map((i) => <Skeleton key={i} className="h-24" />)}

      {query.isSuccess && query.data.length === 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Nothing set right now</CardTitle>
            <CardDescription>Assignments appear here as soon as a trainer publishes one for your batch.</CardDescription>
          </CardHeader>
        </Card>
      )}

      {query.data?.map((assignment) => (
        <Link key={assignment.id} to={`/app/assessments/assignments/${assignment.id}`}>
          <Card className="transition-colors hover:border-primary">
            <CardContent className="flex flex-wrap items-center justify-between gap-3 pt-6">
              <div>
                <p className="font-medium">{assignment.title}</p>
                <p className="text-sm text-muted-foreground">
                  Due {formatDate(assignment.dueAt)} &middot; {assignment.maxMarks} marks
                  {assignment.overdue && !assignment.mySubmission && ' · Overdue'}
                </p>
              </div>
              <Badge variant={assignment.mySubmission ? (ASSIGNMENT_STATUS_VARIANT[assignment.mySubmission.status] ?? 'outline') : 'outline'}>
                {assignment.mySubmission ? assignment.mySubmission.status : 'Not submitted'}
              </Badge>
            </CardContent>
          </Card>
        </Link>
      ))}
    </div>
  )
}
