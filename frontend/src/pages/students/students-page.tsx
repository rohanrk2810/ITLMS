import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { searchStudents } from '@/api/students'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  ACTIVE: 'default',
  ALUMNI: 'secondary',
  DROPPED: 'destructive',
  SUSPENDED: 'destructive',
}

export function StudentsPage() {
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')

  const studentsQuery = useQuery({
    queryKey: ['students', 'search', status, query],
    queryFn: () => searchStudents({ status: status || undefined, query: query || undefined }),
  })

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Students</h1>
        <p className="text-muted-foreground">Every admitted student.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Search name, code, email..."
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="max-w-xs"
        />
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          className="h-9 rounded-md border bg-transparent px-3 text-sm"
        >
          <option value="">All statuses</option>
          {['ACTIVE', 'ALUMNI', 'DROPPED', 'SUSPENDED'].map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      {studentsQuery.isLoading && <Skeleton className="h-64" />}

      {studentsQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Code</TableHead>
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Phone</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {studentsQuery.data.content.map((student) => (
              <TableRow key={student.id}>
                <TableCell>{student.studentCode}</TableCell>
                <TableCell>
                  <Link to={`/app/students/${student.id}`} className="font-medium hover:underline">
                    {student.fullName}
                  </Link>
                </TableCell>
                <TableCell>{student.email}</TableCell>
                <TableCell>{student.phone}</TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[student.status] ?? 'outline'}>{student.status}</Badge>
                </TableCell>
              </TableRow>
            ))}
            {studentsQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  No students match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}
