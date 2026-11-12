import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  approveMonth,
  generatePayroll,
  getPayrollMonth,
  getPayrollYear,
  listStaffPay,
  payMonth,
  paySlip,
  saveStaffPay,
  type SlipView,
  type StaffRow,
  updateSlip,
} from '@/api/payroll'
import { DownloadSlipButton } from '@/components/download-slip-button'
import { ExportMenu } from '@/components/export-menu'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatMoney } from '@/lib/format'
import type { Report } from '@/lib/report'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const thisMonth = () => new Date().toISOString().slice(0, 7)
const METHODS = ['BANK_TRANSFER', 'UPI', 'CASH', 'CHEQUE']
const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline'> = { PENDING: 'outline', APPROVED: 'default', PAID: 'secondary' }

/** Staff payroll: who is paid what, each month's salary slips, approval by the administrator, and payment. */
export function PayrollPage() {
  const [month, setMonth] = useState(thisMonth())
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <Link to="/app/finance" className="text-sm text-muted-foreground hover:underline">
            &larr; Finance
          </Link>
          <h1 className="text-2xl font-semibold">Payroll</h1>
          <p className="text-muted-foreground">Staff salaries, a salary slip for each person each month, and what is still to pay.</p>
        </div>
      </div>
      <MonthSection month={month} onMonth={setMonth} />
      <StaffSection />
      <YearSection year={Number(month.slice(0, 4))} />
    </div>
  )
}

function MonthSection({ month, onMonth }: { month: string; onMonth: (m: string) => void }) {
  const queryClient = useQueryClient()
  const isAdmin = hasRole(useAuthStore((state) => state.roles), ['ADMIN'])
  const view = useQuery({ queryKey: ['payroll', 'month', month], queryFn: () => getPayrollMonth(month), enabled: month.length === 7 })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['payroll'] })
  const [method, setMethod] = useState('BANK_TRANSFER')

  const generate = useMutation({
    mutationFn: () => generatePayroll(month),
    onSuccess: (r) => {
      toast.success(r.created ? `${r.created} salary slips made` : 'Everyone already has a slip for this month')
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not make the slips.')),
  })
  const approve = useMutation({
    mutationFn: () => approveMonth(month),
    onSuccess: (r) => {
      toast.success(`${r.approved} slips approved`)
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not approve.')),
  })
  const pay = useMutation({
    mutationFn: () => payMonth(month, { method }),
    onSuccess: (r) => {
      toast.success(`${r.paid} slips paid and recorded as salary expense`)
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not record the payment.')),
  })

  const s = view.data?.summary

  async function build(): Promise<Report> {
    const data = view.data ?? (await getPayrollMonth(month))
    return {
      title: `Salary ${month}`,
      fileName: `salary-${month}`,
      info: [
        ['Month', month],
        ['Slips', String(data.summary.slips)],
        ['Total net pay', String(data.summary.totalNet)],
        ['Paid', String(data.summary.paid)],
        ['Still to pay', String(data.summary.toPay)],
      ],
      header: ['Slip no', 'Name', 'Designation', 'Basic', 'Allowances', 'Bonus', 'Fixed deduction', 'Leave days', 'Leave deduction', 'Other deduction', 'Net pay', 'Status', 'Paid on', 'Mode', 'Reference'],
      rows: data.slips.map((p) => [p.slipNo, p.staffName, p.designation, p.basic, p.allowances, p.bonus, p.fixedDeduction, p.lwpDays, p.lwpDeduction, p.otherDeduction, p.net, p.status, p.paidOn, p.payMethod, p.payReference]),
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Salary slips</CardTitle>
        <CardDescription>Make the month's slips, adjust leave and bonus, the administrator approves, then pay.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="pay-month">Month</Label>
            <Input id="pay-month" type="month" className="w-44" max={thisMonth()} value={month} onChange={(e) => onMonth(e.target.value)} />
          </div>
          <Button variant="outline" disabled={generate.isPending || month.length !== 7} onClick={() => generate.mutate()}>
            Make this month's slips
          </Button>
          {isAdmin && (
            <Button disabled={approve.isPending || !s || s.pending === 0} onClick={() => approve.mutate()}>
              Approve all ({s?.pending ?? 0})
            </Button>
          )}
          <select aria-label="Pay by" value={method} onChange={(e) => setMethod(e.target.value)} className="h-9 rounded-md border bg-transparent px-3 text-sm">
            {METHODS.map((m) => <option key={m} value={m}>{m.replace('_', ' ')}</option>)}
          </select>
          <Button disabled={pay.isPending || !s || s.approved === 0} onClick={() => pay.mutate()}>
            Pay all approved ({s?.approved ?? 0})
          </Button>
          <ExportMenu label="Download" build={build} />
        </div>

        {view.isLoading && <Skeleton className="h-24" />}
        {s && (
          <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
            <Stat label="Total salary this month" value={formatMoney(s.totalNet)} />
            <Stat label="Paid" value={formatMoney(s.paid)} hint={`${s.paidCount} of ${s.slips} people`} />
            <Stat label="Still to pay" value={formatMoney(s.toPay)} hint={`${s.pending} not approved, ${s.approved} approved`} />
            <Stat label="People" value={String(s.slips)} />
          </div>
        )}

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Gross</TableHead>
              <TableHead>Deductions</TableHead>
              <TableHead>Net pay</TableHead>
              <TableHead>Status</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {view.data?.slips.map((p) => (
              <TableRow key={p.id}>
                <TableCell>
                  {p.staffName}
                  <div className="text-xs text-muted-foreground">{p.designation}</div>
                </TableCell>
                <TableCell>{formatMoney(p.gross)}</TableCell>
                <TableCell>{formatMoney(p.fixedDeduction + p.lwpDeduction + p.otherDeduction)}</TableCell>
                <TableCell className="font-medium">{formatMoney(p.net)}</TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[p.status] ?? 'outline'}>{p.status}</Badge>
                  {p.paidOn && <div className="text-xs text-muted-foreground">{formatDate(p.paidOn)}</div>}
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap items-center justify-end gap-1">
                    {p.status === 'PENDING' && <EditSlip slip={p} onDone={refresh} />}
                    {p.status === 'APPROVED' && <PayOne slip={p} onDone={refresh} />}
                    <DownloadSlipButton id={p.id} slipNo={p.slipNo} />
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {view.isSuccess && view.data.slips.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  No slips for this month yet. Set each person's pay below, then make the slips.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-lg border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold">{value}</p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

function EditSlip({ slip, onDone }: { slip: SlipView; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [lwp, setLwp] = useState(String(slip.lwpDays))
  const [other, setOther] = useState(String(slip.otherDeduction))
  const [bonus, setBonus] = useState(String(slip.bonus))
  const [note, setNote] = useState(slip.note ?? '')
  const save = useMutation({
    mutationFn: () => updateSlip(slip.id, { lwpDays: Number(lwp) || 0, otherDeduction: Number(other) || 0, bonus: Number(bonus) || 0, note: note.trim() || undefined }),
    onSuccess: () => {
      toast.success('Slip updated')
      setOpen(false)
      onDone()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not update the slip.')),
  })
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="ghost">Adjust</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{slip.staffName}</DialogTitle>
          <DialogDescription>
            Leave without pay is charged at one day's share of {formatMoney(slip.basic + slip.allowances)} for the {slip.daysInMonth} days of the month.
          </DialogDescription>
        </DialogHeader>
        <Label htmlFor={`lwp-${slip.id}`}>Leave without pay (days)</Label>
        <Input id={`lwp-${slip.id}`} type="number" min={0} max={slip.daysInMonth} step="0.5" value={lwp} onChange={(e) => setLwp(e.target.value)} />
        <Label htmlFor={`oth-${slip.id}`}>Other deduction (advance, fine)</Label>
        <Input id={`oth-${slip.id}`} type="number" min={0} value={other} onChange={(e) => setOther(e.target.value)} />
        <Label htmlFor={`bon-${slip.id}`}>Bonus</Label>
        <Input id={`bon-${slip.id}`} type="number" min={0} value={bonus} onChange={(e) => setBonus(e.target.value)} />
        <Label htmlFor={`note-${slip.id}`}>Remarks on the slip</Label>
        <Input id={`note-${slip.id}`} maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} />
        <DialogFooter>
          <Button disabled={save.isPending} onClick={() => save.mutate()}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function PayOne({ slip, onDone }: { slip: SlipView; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [method, setMethod] = useState('BANK_TRANSFER')
  const [reference, setReference] = useState('')
  const pay = useMutation({
    mutationFn: () => paySlip(slip.id, { method, reference: reference.trim() || undefined }),
    onSuccess: () => {
      toast.success('Paid')
      setOpen(false)
      onDone()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not record the payment.')),
  })
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">Pay</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Pay {slip.staffName}</DialogTitle>
          <DialogDescription>{formatMoney(slip.net)} for {slip.month}. It is recorded as a salary expense.</DialogDescription>
        </DialogHeader>
        <select aria-label="Pay by" value={method} onChange={(e) => setMethod(e.target.value)} className="h-9 rounded-md border bg-transparent px-3 text-sm">
          {METHODS.map((m) => <option key={m} value={m}>{m.replace('_', ' ')}</option>)}
        </select>
        <Input aria-label="Reference" placeholder="Transaction or cheque number (optional)" maxLength={80} value={reference} onChange={(e) => setReference(e.target.value)} />
        <DialogFooter>
          <Button disabled={pay.isPending} onClick={() => pay.mutate()}>Mark paid</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function StaffSection() {
  const staff = useQuery({ queryKey: ['payroll', 'staff'], queryFn: listStaffPay })
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Staff pay</CardTitle>
        <CardDescription>The monthly pay of every member of staff. Changes apply to slips made from now on.</CardDescription>
      </CardHeader>
      <CardContent>
        {staff.isLoading && <Skeleton className="h-24" />}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Basic</TableHead>
              <TableHead>Allowances</TableHead>
              <TableHead>Fixed deduction</TableHead>
              <TableHead>On payroll</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {staff.data?.map((row) => <StaffLine key={row.userId} row={row} />)}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

function StaffLine({ row }: { row: StaffRow }) {
  const queryClient = useQueryClient()
  const [basic, setBasic] = useState(String(row.basic))
  const [allowances, setAllowances] = useState(String(row.allowances))
  const [deduction, setDeduction] = useState(String(row.fixedDeduction))
  const [active, setActive] = useState(row.active)
  useEffect(() => {
    setBasic(String(row.basic))
    setAllowances(String(row.allowances))
    setDeduction(String(row.fixedDeduction))
    setActive(row.active)
  }, [row])
  const save = useMutation({
    mutationFn: () => saveStaffPay(row.userId, { basic: Number(basic) || 0, allowances: Number(allowances) || 0, fixedDeduction: Number(deduction) || 0, active }),
    onSuccess: () => {
      toast.success(`${row.name}'s pay saved`)
      void queryClient.invalidateQueries({ queryKey: ['payroll'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save.')),
  })
  return (
    <TableRow>
      <TableCell>
        {row.name}
        <div className="text-xs text-muted-foreground">{row.role}</div>
      </TableCell>
      <TableCell><Input aria-label={`Basic for ${row.name}`} type="number" min={0} className="h-8 w-28" value={basic} onChange={(e) => setBasic(e.target.value)} /></TableCell>
      <TableCell><Input aria-label={`Allowances for ${row.name}`} type="number" min={0} className="h-8 w-28" value={allowances} onChange={(e) => setAllowances(e.target.value)} /></TableCell>
      <TableCell><Input aria-label={`Deduction for ${row.name}`} type="number" min={0} className="h-8 w-28" value={deduction} onChange={(e) => setDeduction(e.target.value)} /></TableCell>
      <TableCell><input type="checkbox" aria-label={`${row.name} on payroll`} checked={active} onChange={(e) => setActive(e.target.checked)} /></TableCell>
      <TableCell><Button size="sm" variant="outline" disabled={save.isPending} onClick={() => save.mutate()}>Save</Button></TableCell>
    </TableRow>
  )
}

function YearSection({ year }: { year: number }) {
  const view = useQuery({ queryKey: ['payroll', 'year', year], queryFn: () => getPayrollYear(year) })
  if (!view.data) return null
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Salary in {year}</CardTitle>
        <CardDescription>
          Total {formatMoney(view.data.totalNet)}, paid {formatMoney(view.data.paid)}, still to pay {formatMoney(view.data.toPay)}.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Month</TableHead>
              <TableHead>People</TableHead>
              <TableHead>Total</TableHead>
              <TableHead>Paid</TableHead>
              <TableHead>Still to pay</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {view.data.months.map((m) => (
              <TableRow key={m.month}>
                <TableCell>{m.month}</TableCell>
                <TableCell>{m.slips}</TableCell>
                <TableCell>{formatMoney(m.totalNet)}</TableCell>
                <TableCell>{formatMoney(m.paid)}</TableCell>
                <TableCell>{formatMoney(m.toPay)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
