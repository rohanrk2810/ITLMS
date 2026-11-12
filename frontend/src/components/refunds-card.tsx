import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { approveRefund, listRefunds, markRefundPaid, rejectRefund } from '@/api/finance'
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
  const roles = useAuthStore((state) => state.roles)
  const isAdmin = hasRole(roles, ['ADMIN'])
  const canPay = hasRole(roles, ['ADMIN', 'FINANCE'])
  const [how, setHow] = useState<Record<number, string>>({})
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

  const paidOut = useMutation({
    mutationFn: (id: number) => markRefundPaid(id, { method: how[id] ?? 'BANK_TRANSFER', reference: note[id]?.trim() || undefined }),
    onSuccess: () => {
      toast.success('Marked as paid')
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not record the payment.')),
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
              {(isAdmin || canPay) && <TableHead />}
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
                  <Badge variant={r.status === 'REJECTED' ? 'destructive' : r.status === 'REQUESTED' ? 'default' : 'secondary'}>
                    {r.status === 'APPROVED' ? 'APPROVED, NOT PAID YET' : r.status}
                  </Badge>
                  {r.paidOn && (
                    <div className="text-xs text-muted-foreground">
                      {formatDate(r.paidOn)} by {(r.payMethod ?? '').replace('_', ' ').toLowerCase()}
                      {r.payReference ? `, ${r.payReference}` : ''}
                    </div>
                  )}
                </TableCell>
                {(isAdmin || canPay) && (
                  <TableCell>
                    {r.status === 'APPROVED' && canPay && (
                      <div className="flex items-center gap-1">
                        <select aria-label="Paid by" value={how[r.id] ?? 'BANK_TRANSFER'} className="h-8 rounded-md border bg-transparent px-2 text-xs"
                          onChange={(e) => setHow((h) => ({ ...h, [r.id]: e.target.value }))}>
                          {['BANK_TRANSFER', 'UPI', 'CASH', 'CHEQUE'].map((m) => <option key={m} value={m}>{m.replace('_', ' ')}</option>)}
                        </select>
                        <Input aria-label="Reference" placeholder="Reference" className="h-8 w-28" value={note[r.id] ?? ''}
                          onChange={(e) => setNote((n) => ({ ...n, [r.id]: e.target.value }))} />
                        <Button size="sm" onClick={() => paidOut.mutate(r.id)} disabled={paidOut.isPending}>Mark paid</Button>
                      </div>
                    )}
                    {r.status === 'REQUESTED' && isAdmin && (
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
