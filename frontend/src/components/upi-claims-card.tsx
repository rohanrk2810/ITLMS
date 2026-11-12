import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { confirmUpiClaim, listUpiClaims, rejectUpiClaim } from '@/api/finance'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

/** UPI payments students say they made. Match the reference with the bank statement, then confirm or reject. */
export function UpiClaimsCard() {
  const queryClient = useQueryClient()
  const canDecide = hasRole(useAuthStore((state) => state.roles), ['ADMIN', 'FINANCE'])
  const claims = useQuery({ queryKey: ['finance', 'upi-claims'], queryFn: listUpiClaims })
  const [reason, setReason] = useState<Record<number, string>>({})
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['finance'] })

  const confirm = useMutation({
    mutationFn: (id: number) => confirmUpiClaim(id),
    onSuccess: () => {
      toast.success('Payment recorded and the receipt sent')
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not confirm the payment.')),
  })
  const reject = useMutation({
    mutationFn: (id: number) => rejectUpiClaim(id, reason[id] ?? ''),
    onSuccess: () => {
      toast.success('Claim rejected')
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not reject the claim.')),
  })

  if (!claims.data || claims.data.length === 0) return null
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">UPI payments to check</CardTitle>
        <CardDescription>Students who paid the institute's UPI id. Find the reference in the bank statement first.</CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Student</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Reference (UTR)</TableHead>
              <TableHead>Status</TableHead>
              {canDecide && <TableHead />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {claims.data.slice(0, 30).map((c) => (
              <TableRow key={c.id}>
                <TableCell>
                  {c.studentName}
                  <div className="text-xs text-muted-foreground">{formatDate(c.claimedAt)}</div>
                </TableCell>
                <TableCell>{formatMoney(c.amount)}</TableCell>
                <TableCell className="font-mono text-xs">{c.utr}</TableCell>
                <TableCell>
                  <Badge variant={c.status === 'REJECTED' ? 'destructive' : c.status === 'CONFIRMED' ? 'secondary' : 'default'}>
                    {c.status}
                  </Badge>
                  {c.decisionNote && <div className="text-xs text-muted-foreground">{c.decisionNote}</div>}
                </TableCell>
                {canDecide && (
                  <TableCell>
                    {c.status === 'PENDING' && (
                      <div className="flex items-center gap-1">
                        <Button size="sm" onClick={() => confirm.mutate(c.id)} disabled={confirm.isPending}>Confirm</Button>
                        <Input aria-label="Reason" placeholder="Reason to reject" className="h-8 w-36" value={reason[c.id] ?? ''}
                          onChange={(e) => setReason((r) => ({ ...r, [c.id]: e.target.value }))} />
                        <Button size="sm" variant="outline" onClick={() => reject.mutate(c.id)}
                          disabled={reject.isPending || !(reason[c.id] ?? '').trim()}>Reject</Button>
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
