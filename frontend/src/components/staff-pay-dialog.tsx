import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type PayHead, saveStaffStructure, type StaffProfile, type StaffRow } from '@/api/payroll'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatMoney } from '@/lib/format'

const EARNING_SUGGESTIONS = ['House Rent Allowance', 'Special Allowance', 'Shift Allowance', 'Travel Allowance', 'Monthly Bonus']
const DEDUCTION_SUGGESTIONS: { name: string; percent?: number }[] = [
  { name: 'Provident Fund', percent: 12 },
  { name: 'Profession Tax' },
  { name: 'TDS' },
  { name: 'Advance recovery' },
]

/** The amount a head comes to: its own amount, or its share of the basic. */
export function headAmount(head: PayHead, basic: number): number {
  return head.percentOfBasic != null ? Math.round(basic * head.percentOfBasic) / 100 : head.amount
}

/** What the pay comes to: gross, deductions and the net, before leave or a month's adjustments. */
export function payTotals(heads: PayHead[]): { gross: number; deductions: number; net: number } {
  const basic = heads.filter((h) => h.kind === 'EARNING' && h.basic).reduce((a, h) => a + h.amount, 0)
  const gross = heads.filter((h) => h.kind === 'EARNING').reduce((a, h) => a + headAmount(h, basic), 0)
  const deductions = heads.filter((h) => h.kind === 'DEDUCTION').reduce((a, h) => a + headAmount(h, basic), 0)
  return { gross, deductions, net: gross - deductions }
}

const emptyProfile: StaffProfile = {
  employeeCode: null, pan: null, bankName: null, bankAccount: null, pfNumber: null, uan: null, esiNumber: null,
  hiredOn: null, department: null, location: null,
}

/** Sets one person's pay as named earnings and deductions, and the details their salary slip shows. */
export function StaffPayDialog({ row }: { row: StaffRow }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [heads, setHeads] = useState<PayHead[]>([])
  const [profile, setProfile] = useState<StaffProfile>(emptyProfile)
  const [active, setActive] = useState(true)

  function begin(next: boolean) {
    if (next) {
      setHeads(row.heads.length > 0 ? row.heads.map((h) => ({ ...h })) : [{ kind: 'EARNING', name: 'Basic', amount: row.basic, percentOfBasic: null, basic: true }])
      setProfile({ ...emptyProfile, ...row.profile })
      setActive(row.active)
    }
    setOpen(next)
  }

  const save = useMutation({
    mutationFn: () => saveStaffStructure(row.userId, { heads: heads.filter((h) => h.name.trim()), profile, active }),
    onSuccess: () => {
      toast.success(`${row.name}'s pay saved`)
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['payroll'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the pay.')),
  })

  const set = (index: number, next: Partial<PayHead>) => setHeads((list) => list.map((h, i) => (i === index ? { ...h, ...next } : h)))
  const add = (head: PayHead) => setHeads((list) => [...list, head])
  const field = (key: keyof StaffProfile, label: string, props: { type?: string; placeholder?: string; maxLength?: number } = {}) => (
    <div className="flex flex-col gap-1">
      <Label htmlFor={`sp-${key}`}>{label}</Label>
      <Input id={`sp-${key}`} type={props.type} placeholder={props.placeholder} maxLength={props.maxLength}
        value={profile[key] ?? ''} onChange={(e) => setProfile((p) => ({ ...p, [key]: e.target.value || null }))} />
    </div>
  )
  const { gross, deductions, net } = payTotals(heads)

  return (
    <Dialog open={open} onOpenChange={begin}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">Edit pay</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{row.name}</DialogTitle>
          <DialogDescription>
            The monthly pay as it appears on the salary slip. Changes apply to slips made from now on.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-3 sm:grid-cols-3">
          {field('employeeCode', 'Employee code')}
          {field('department', 'Department')}
          {field('location', 'Location')}
          {field('hiredOn', 'Hire date', { type: 'date' })}
          {field('pan', 'PAN', { placeholder: 'ABCDE1234F', maxLength: 10 })}
          {field('bankName', 'Bank')}
          {field('bankAccount', 'Bank account no.')}
          {field('pfNumber', 'PF number')}
          {field('uan', 'UAN')}
          {field('esiNumber', 'ESI number')}
        </div>

        <HeadList title="Earnings" kind="EARNING" heads={heads} set={set} remove={(i) => setHeads((l) => l.filter((_, j) => j !== i))} />
        <div className="flex flex-wrap gap-2">
          {EARNING_SUGGESTIONS.map((n) => (
            <Button key={n} size="sm" variant="ghost" onClick={() => add({ kind: 'EARNING', name: n, amount: 0, percentOfBasic: null, basic: false })}>
              <Plus className="size-3" /> {n}
            </Button>
          ))}
          <Button size="sm" variant="ghost" onClick={() => add({ kind: 'EARNING', name: '', amount: 0, percentOfBasic: null, basic: false })}><Plus className="size-3" /> Other earning</Button>
        </div>

        <HeadList title="Deductions" kind="DEDUCTION" heads={heads} set={set} remove={(i) => setHeads((l) => l.filter((_, j) => j !== i))} />
        <div className="flex flex-wrap gap-2">
          {DEDUCTION_SUGGESTIONS.map((d) => (
            <Button key={d.name} size="sm" variant="ghost"
              onClick={() => add({ kind: 'DEDUCTION', name: d.name, amount: 0, percentOfBasic: d.percent ?? null, basic: false })}>
              <Plus className="size-3" /> {d.name}{d.percent ? ` (${d.percent}% of basic)` : ''}
            </Button>
          ))}
          <Button size="sm" variant="ghost" onClick={() => add({ kind: 'DEDUCTION', name: '', amount: 0, percentOfBasic: null, basic: false })}><Plus className="size-3" /> Other deduction</Button>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3 text-sm">
          <span>Gross {formatMoney(gross)}</span>
          <span>Deductions {formatMoney(deductions)}</span>
          <span className="font-semibold">Net before leave {formatMoney(net)}</span>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> On payroll
          </label>
          <Button disabled={save.isPending || gross <= 0} onClick={() => save.mutate()}>Save</Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}

function HeadList({ title, kind, heads, set, remove }: {
  title: string
  kind: PayHead['kind']
  heads: PayHead[]
  set: (index: number, next: Partial<PayHead>) => void
  remove: (index: number) => void
}) {
  const basic = heads.filter((h) => h.kind === 'EARNING' && h.basic).reduce((a, h) => a + h.amount, 0)
  return (
    <div className="flex flex-col gap-2">
      <p className="text-sm font-medium">{title}</p>
      {heads.map((h, i) =>
        h.kind !== kind ? null : (
          <div key={i} className="flex flex-wrap items-center gap-2">
            <Input aria-label="Name" placeholder="Name" maxLength={120} className="w-56" value={h.name} onChange={(e) => set(i, { name: e.target.value })} />
            {h.percentOfBasic != null ? (
              <>
                <Input aria-label="Percent of basic" type="number" min={0} max={100} step="0.01" className="w-24" value={h.percentOfBasic}
                  onChange={(e) => set(i, { percentOfBasic: Number(e.target.value) })} />
                <span className="text-xs text-muted-foreground">% of basic = {formatMoney(headAmount(h, basic))}</span>
              </>
            ) : (
              <Input aria-label="Amount" type="number" min={0} className="w-32" value={h.amount || ''} placeholder="Amount"
                onChange={(e) => set(i, { amount: Number(e.target.value) })} />
            )}
            {kind === 'EARNING' && (
              <label className="flex items-center gap-1 text-xs text-muted-foreground">
                <input type="radio" name="basic-head" checked={h.basic} onChange={() => heads.forEach((_, j) => set(j, { basic: j === i }))} /> basic
              </label>
            )}
            {kind === 'DEDUCTION' && (
              <Button size="sm" variant="ghost" className="text-xs"
                onClick={() => set(i, h.percentOfBasic != null ? { percentOfBasic: null } : { percentOfBasic: 12, amount: 0 })}>
                {h.percentOfBasic != null ? 'Use an amount' : 'Use % of basic'}
              </Button>
            )}
            <Button size="icon" variant="ghost" aria-label="Remove" onClick={() => remove(i)}><Trash2 className="size-4" /></Button>
          </div>
        ),
      )}
    </div>
  )
}
