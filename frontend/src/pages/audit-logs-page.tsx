import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ChevronLeft, Download } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type AuditLogFilter, exportAuditLogs, searchAuditLogs } from '@/api/reporting'
import { ColumnDialog, initialPicks, saveColumns } from '@/components/column-dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const FORMATS: Array<'CSV' | 'XLSX' | 'PDF'> = ['CSV', 'XLSX', 'PDF']

// The columns of the file the server builds (ReportExportServiceImpl), by the names it expects.
const COLUMNS = ['Time', 'Service', 'Actor', 'Role', 'Action', 'Entity type', 'Entity id']
const COLUMNS_KEY = 'audit-log'

export function AuditLogsPage() {
  const [filter, setFilter] = useState<AuditLogFilter>({})

  const query = useQuery({ queryKey: ['audit-logs', filter], queryFn: () => searchAuditLogs(filter) })

  // The format waiting for the person to choose columns.
  const [choosing, setChoosing] = useState<{ format: 'CSV' | 'XLSX' | 'PDF'; picked: boolean[] } | null>(null)

  const exportMutation = useMutation({
    mutationFn: ({ format, columns }: { format: 'CSV' | 'XLSX' | 'PDF'; columns: string[] }) =>
      exportAuditLogs(filter, format, columns),
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not export the audit trail.')),
  })

  return (
    <div className="flex flex-col gap-6">
      <Link to="/app" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Dashboard
      </Link>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Audit log</h1>
          <p className="text-muted-foreground">One chronological trail across every service (Doc S5, S12).</p>
        </div>
        <div className="flex gap-2">
          {FORMATS.map((format) => (
            <Button
              key={format}
              size="sm"
              variant="outline"
              onClick={() => setChoosing({ format, picked: initialPicks(COLUMNS_KEY, COLUMNS) })}
              disabled={exportMutation.isPending}
            >
              <Download className="size-3.5" />
              {format}
            </Button>
          ))}
        </div>
      </div>

      <ColumnDialog
        open={choosing !== null}
        headers={COLUMNS}
        picked={choosing?.picked ?? []}
        onPickedChange={(picked) => setChoosing((c) => (c ? { ...c, picked } : c))}
        onClose={() => setChoosing(null)}
        onConfirm={() => {
          if (!choosing) return
          const columns = COLUMNS.filter((_, i) => choosing.picked[i])
          saveColumns(COLUMNS_KEY, columns)
          exportMutation.mutate({ format: choosing.format, columns })
          setChoosing(null)
        }}
      />

      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Service (e.g. identity-service)"
          value={filter.serviceName ?? ''}
          onChange={(event) => setFilter({ ...filter, serviceName: event.target.value || undefined })}
          className="max-w-48"
        />
        <Input
          placeholder="Action (e.g. CERTIFICATE_REVOKED)"
          value={filter.action ?? ''}
          onChange={(event) => setFilter({ ...filter, action: event.target.value || undefined })}
          className="max-w-56"
        />
        <Input
          placeholder="Entity type"
          value={filter.entityType ?? ''}
          onChange={(event) => setFilter({ ...filter, entityType: event.target.value || undefined })}
          className="max-w-40"
        />
        <Input
          placeholder="Actor user id"
          type="number"
          onChange={(event) =>
            setFilter({ ...filter, actorUserId: event.target.value ? Number(event.target.value) : undefined })
          }
          className="max-w-40"
        />
      </div>

      {query.isLoading && <Skeleton className="h-96" />}

      {query.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>When</TableHead>
              <TableHead>Service</TableHead>
              <TableHead>Actor</TableHead>
              <TableHead>Action</TableHead>
              <TableHead>Entity</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.content.map((entry) => (
              <TableRow key={entry.id}>
                <TableCell className="whitespace-nowrap">{new Date(entry.occurredAt).toLocaleString()}</TableCell>
                <TableCell>{entry.serviceName}</TableCell>
                <TableCell>{entry.actorEmail ?? 'system'}</TableCell>
                <TableCell>{entry.action}</TableCell>
                <TableCell>
                  {entry.entityType}
                  {entry.entityId && ` #${entry.entityId}`}
                </TableCell>
              </TableRow>
            ))}
            {query.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  No entries match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}
