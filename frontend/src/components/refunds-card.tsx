import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { approveRefund, listRefunds, rejectRefund } from '@/api/finance'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

/** Refund requests. The finance desk asks from a payment; only the administrator approves or rejects. */
export function RefundsCard() {
  const queryClient = useQueryClient()
  const isAdmin = hasRole(useAuthStore((state) => state.roles), ['ADMIN'])
  const refunds = useQuery({ queryKey: ['finance', 'refunds'], queryFn: () => listRefunds() })
  const [note, setNote] = useState<Record<number, string>>({})

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['finance'] })
  }
  const approve = useMutation({
    mutationFn: (id: number) => approveRefund(id, note[id]),
    onSuccess: () => {
      toast.success('Refund approved. Hand the money back to the student.')
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not approve the refund.')),
  })
  const reject = useMutation({
    mutationFn: (id: number) => rejectRefund(id, note[id] ?? ''),
    onSuccess: () => {
      toast.success('Refund rejected')
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Write a reason to reject the refund.')),
  })

  if (!refunds.data || refunds.data.length === 0) return null
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Refunds</CardTitle>
        <CardDescription>Asked for from a payment; the administrator decides.</CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Student</TableHead>
              <TableHead>Receipt</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead>Status</TableHead>
              {isAdmin && <TableHead />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {refunds.data.map((r) => (
              <TableRow key={r.id}>
                <TableCell>
                  {r.studentName}
                  <div className="text-xs text-muted-foreground">{formatDate(r.requestedAt)}</div>
                </TableCell>
                <TableCell>{r.receiptNo}</TableCell>
                <TableCell>{formatMoney(r.amount)}</TableCell>
                <TableCell className="max-w-56 whitespace-normal">
                  {r.reason}
                  {r.decisionNote && <div className="text-xs text-muted-foreground">{r.decisionNote}</div>}
                </TableCell>
                <TableCell>
                  <Badge variant={r.status === 'REJECTED' ? 'destructive' : r.status === 'APPROVED' ? 'secondary' : 'default'}>
                    {r.status}
                  </Badge>
                </TableCell>
                {isAdmin && (
                  <TableCell>
                    {r.status === 'REQUESTED' && (
                      <div className="flex items-center gap-1">
                        <Input aria-label="Note" placeholder="Note" className="h-8 w-32" value={note[r.id] ?? ''}
                          onChange={(e) => setNote((n) => ({ ...n, [r.id]: e.target.value }))} />
                        <Button size="sm" onClick={() => approve.mutate(r.id)} disabled={approve.isPending}>Approve</Button>
                        <Button size="sm" variant="outline" onClick={() => reject.mutate(r.id)}
                          disabled={reject.isPending || !(note[r.id] ?? '').trim()}>Reject</Button>
                      </div>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
