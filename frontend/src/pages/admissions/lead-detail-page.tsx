import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, UserCheck } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  type AddFollowupInput,
  type ConvertLeadInput,
  addFollowup,
  convertLead,
  getFollowups,
  getLead,
} from '@/api/leads'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

const OUTCOMES = ['CALLED', 'NO_ANSWER', 'VISITED', 'EMAILED', 'MESSAGED', 'MEETING', 'OTHER']

export function LeadDetailPage() {
  const { leadId } = useParams<{ leadId: string }>()
  const queryClient = useQueryClient()
  const [followupForm, setFollowupForm] = useState<AddFollowupInput>({ outcome: 'CALLED', remark: '' })
  const [convertOpen, setConvertOpen] = useState(false)

  const leadQuery = useQuery({ queryKey: ['leads', leadId], queryFn: () => getLead(leadId!), enabled: !!leadId })
  const followupsQuery = useQuery({
    queryKey: ['leads', leadId, 'followups'],
    queryFn: () => getFollowups(leadId!),
    enabled: !!leadId,
  })

  const followupMutation = useMutation({
    mutationFn: () => addFollowup(leadId!, followupForm),
    onSuccess: () => {
      toast.success('Follow-up logged')
      setFollowupForm({ outcome: 'CALLED', remark: '' })
      void queryClient.invalidateQueries({ queryKey: ['leads', leadId] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the follow-up.')),
  })

  function handleFollowupSubmit(event: FormEvent) {
    event.preventDefault()
    followupMutation.mutate()
  }

  if (leadQuery.isLoading) return <Skeleton className="h-96 max-w-2xl" />
  const lead = leadQuery.data
  if (!lead) return null

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <Link to="/app/admissions" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ChevronLeft className="size-4" />
        Admissions
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{lead.name}</h1>
          <p className="text-muted-foreground">
            {lead.phone}
            {lead.email && ` · ${lead.email}`} &middot; {lead.source}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge>{lead.status}</Badge>
          {lead.status !== 'CONVERTED' && !lead.convertedStudentId && (
            <ConvertDialog leadId={lead.id} open={convertOpen} onOpenChange={setConvertOpen} />
          )}
        </div>
      </div>

      {lead.convertedStudentId && (
        <Card>
          <CardContent className="flex items-center gap-2 pt-6 text-sm">
            <UserCheck className="size-4 text-emerald-600" />
            Admitted{lead.convertedAt && ` on ${formatDate(lead.convertedAt)}`} as student #{lead.convertedStudentId}
          </CardContent>
        </Card>
      )}

      {lead.notes && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Notes</CardTitle>
            <CardDescription>{lead.notes}</CardDescription>
          </CardHeader>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Log a follow-up</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-3" onSubmit={handleFollowupSubmit}>
            <div className="flex flex-wrap gap-3">
              <select
                value={followupForm.outcome}
                onChange={(event) => setFollowupForm({ ...followupForm, outcome: event.target.value })}
                className="h-9 rounded-md border bg-transparent px-3 text-sm"
              >
                {OUTCOMES.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
              <Input
                type="datetime-local"
                onChange={(event) =>
                  setFollowupForm({
                    ...followupForm,
                    nextActionAt: event.target.value ? new Date(event.target.value).toISOString() : undefined,
                  })
                }
                className="w-auto"
              />
            </div>
            <Input
              placeholder="Remark"
              value={followupForm.remark ?? ''}
              onChange={(event) => setFollowupForm({ ...followupForm, remark: event.target.value })}
            />
            <Button type="submit" size="sm" className="self-start" disabled={followupMutation.isPending}>
              {followupMutation.isPending ? 'Saving...' : 'Log follow-up'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Follow-up history</h2>
        <div className="flex flex-col gap-3">
          {followupsQuery.data?.map((followup) => (
            <div key={followup.id} className="rounded-md border p-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-medium">{followup.outcome}</span>
                <span className="text-xs text-muted-foreground">{formatDate(followup.contactedAt)}</span>
              </div>
              {followup.remark && <p className="mt-1 text-muted-foreground">{followup.remark}</p>}
              {followup.nextActionAt && (
                <p className="mt-1 text-xs text-muted-foreground">Next: {formatDate(followup.nextActionAt)}</p>
              )}
            </div>
          ))}
          {followupsQuery.isSuccess && followupsQuery.data.length === 0 && (
            <p className="text-sm text-muted-foreground">No follow-ups recorded yet.</p>
          )}
        </div>
      </div>
    </div>
  )
}

function ConvertDialog({
  leadId,
  open,
  onOpenChange,
}: {
  leadId: number
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [form, setForm] = useState<Partial<ConvertLeadInput>>({ installments: 1 })
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: () => convertLead(leadId, form as ConvertLeadInput),
    onSuccess: (result) => {
      toast.success(result.message)
      onOpenChange(false)
      void navigate(`/app/students/${result.studentId}`)
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not complete the admission.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm">
          <UserCheck />
          Admit
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Admit this lead</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="courseId">Course id</Label>
            <Input
              id="courseId"
              type="number"
              required
              onChange={(event) => setForm({ ...form, courseId: Number(event.target.value) })}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="batchId">Batch id (optional)</Label>
            <Input
              id="batchId"
              type="number"
              onChange={(event) =>
                setForm({ ...form, batchId: event.target.value ? Number(event.target.value) : undefined })
              }
            />
          </div>
          <Separator />
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="totalFee">Total fee</Label>
              <Input
                id="totalFee"
                type="number"
                required
                onChange={(event) => setForm({ ...form, totalFee: Number(event.target.value) })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="discount">Discount</Label>
              <Input
                id="discount"
                type="number"
                onChange={(event) => setForm({ ...form, discount: Number(event.target.value) })}
              />
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="installments">Installments</Label>
            <Input
              id="installments"
              type="number"
              min={1}
              value={form.installments ?? 1}
              onChange={(event) => setForm({ ...form, installments: Number(event.target.value) })}
            />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Admitting...' : 'Admit student'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
