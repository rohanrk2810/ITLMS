import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { placementDashboard, placementRecords } from '@/api/placements'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDate } from '@/lib/format'
import { CompaniesPanel } from '@/pages/placements/companies-panel'
import { JobsPanel } from '@/pages/placements/jobs-panel'
import { hasRole, useAuthStore } from '@/stores/auth-store'

export function PlacementDeskPage() {
  const canEdit = hasRole(useAuthStore((state) => state.user?.role), ['ADMIN', 'PLACEMENT'])

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Placements</h1>
        <p className="text-muted-foreground">The recruitment pipeline, end to end (Doc S11, S15).</p>
      </div>

      <Tabs defaultValue="dashboard">
        <TabsList>
          <TabsTrigger value="dashboard">Dashboard</TabsTrigger>
          <TabsTrigger value="jobs">Jobs</TabsTrigger>
          <TabsTrigger value="companies">Companies</TabsTrigger>
          <TabsTrigger value="placements">Placements</TabsTrigger>
        </TabsList>

        <TabsContent value="dashboard">
          <DashboardTab />
        </TabsContent>
        <TabsContent value="jobs">
          <JobsPanel canEdit={canEdit} />
        </TabsContent>
        <TabsContent value="companies">
          <CompaniesPanel canEdit={canEdit} />
        </TabsContent>
        <TabsContent value="placements">
          <PlacementsTab />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function DashboardTab() {
  const query = useQuery({ queryKey: ['placements', 'dashboard'], queryFn: placementDashboard })

  if (query.isLoading) return <Skeleton className="h-48" />
  const data = query.data
  if (!data) return null

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <StatCard label="Open jobs" value={data.openJobs} />
        <StatCard label="Applications" value={data.totalApplications} />
        <StatCard label="Selected" value={data.selected} />
      </div>

      {Object.keys(data.applicationsByStage).length > 0 && (
        <Card>
          <CardContent className="flex flex-wrap gap-4 pt-6 text-sm">
            {Object.entries(data.applicationsByStage).map(([stage, count]) => (
              <span key={stage} className="text-muted-foreground">
                {stage}: <span className="font-medium text-foreground">{count}</span>
              </span>
            ))}
          </CardContent>
        </Card>
      )}

      {data.placementsByCompany.length > 0 && (
        <div>
          <h2 className="mb-2 text-sm font-medium text-muted-foreground">Selections by company</h2>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Company</TableHead>
                <TableHead>Selected</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.placementsByCompany.map((row) => (
                <TableRow key={row.companyId}>
                  <TableCell>{row.companyName}</TableCell>
                  <TableCell>{row.selected}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <CardContent className="pt-6">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className="text-xl font-semibold">{value}</p>
      </CardContent>
    </Card>
  )
}

function PlacementsTab() {
  const query = useQuery({ queryKey: ['placements', 'records'], queryFn: placementRecords })

  if (query.isLoading) return <Skeleton className="h-48" />

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Student</TableHead>
          <TableHead>Job</TableHead>
          <TableHead>Company</TableHead>
          <TableHead>Offer</TableHead>
          <TableHead>Selected on</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {query.data?.map((record) => (
          <TableRow key={record.id}>
            <TableCell className="font-medium">{record.studentName}</TableCell>
            <TableCell>
              <Link to={`/app/placements/${record.jobId}`} className="hover:underline">
                {record.jobTitle}
              </Link>
            </TableCell>
            <TableCell>{record.companyName}</TableCell>
            <TableCell>{record.offerDetails ?? '—'}</TableCell>
            <TableCell>
              <Badge>{record.decidedAt ? formatDate(record.decidedAt) : '—'}</Badge>
            </TableCell>
          </TableRow>
        ))}
        {query.isSuccess && query.data.length === 0 && (
          <TableRow>
            <TableCell colSpan={5} className="text-center text-muted-foreground">
              No placements recorded yet.
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  )
}
