import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { searchBatches } from '@/api/batches'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  UPCOMING: 'secondary',
  RUNNING: 'default',
  COMPLETED: 'outline',
  CANCELLED: 'destructive',
}

export function BatchesPage() {
  const [query, setQuery] = useState('')
  const batchesQuery = useQuery({
    queryKey: ['batches', 'search', query],
    queryFn: () => searchBatches({ query: query || undefined }),
  })

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Batches</h1>
        <p className="text-muted-foreground">Running and upcoming batches, their trainer and seats.</p>
      </div>

      <Input
        placeholder="Search batch, course, code..."
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        className="max-w-xs"
      />

      {batchesQuery.isLoading && <Skeleton className="h-64" />}

      {batchesQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Batch</TableHead>
              <TableHead>Course</TableHead>
              <TableHead>Trainer</TableHead>
              <TableHead>Dates</TableHead>
              <TableHead>Seats</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {batchesQuery.data.content.map((batch) => (
              <TableRow key={batch.id}>
                <TableCell>
                  <Link to={`/app/batches/${batch.id}`} className="font-medium hover:underline">
                    {batch.batchCode}
                  </Link>
                  <p className="text-xs text-muted-foreground">{batch.name}</p>
                </TableCell>
                <TableCell>{batch.courseTitle}</TableCell>
                <TableCell>{batch.trainerName ?? '—'}</TableCell>
                <TableCell>
                  {formatDate(batch.startDate)} &ndash; {formatDate(batch.endDate)}
                </TableCell>
                <TableCell>
                  {batch.enrolledCount}/{batch.capacity}
                </TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[batch.status] ?? 'outline'}>{batch.status}</Badge>
                </TableCell>
              </TableRow>
            ))}
            {batchesQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  No batches match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}
