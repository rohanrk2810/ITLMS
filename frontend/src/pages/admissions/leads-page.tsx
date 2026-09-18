import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type CreateLeadInput, createLead, searchLeads } from '@/api/leads'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'

const SOURCES = ['WALK_IN', 'WEBSITE', 'REFERRAL', 'PHONE', 'SOCIAL_MEDIA', 'CAMPAIGN', 'OTHER']

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'outline' | 'destructive'> = {
  NEW: 'secondary',
  CONTACTED: 'outline',
  FOLLOW_UP: 'outline',
  INTERESTED: 'default',
  NOT_INTERESTED: 'destructive',
  LOST: 'destructive',
  CONVERTED: 'default',
}

export function LeadsPage() {
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)

  const leadsQuery = useQuery({
    queryKey: ['leads', 'search', status, query],
    queryFn: () => searchLeads({ status: status || undefined, query: query || undefined }),
  })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Admissions</h1>
          <p className="text-muted-foreground">Leads, follow-ups and conversions.</p>
        </div>
        <NewLeadDialog open={dialogOpen} onOpenChange={setDialogOpen} />
      </div>

      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Search name, phone, email..."
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
          {['NEW', 'CONTACTED', 'FOLLOW_UP', 'INTERESTED', 'NOT_INTERESTED', 'LOST', 'CONVERTED'].map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      {leadsQuery.isLoading && <Skeleton className="h-64" />}

      {leadsQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Phone</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Next follow-up</TableHead>
              <TableHead>Created</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {leadsQuery.data.content.map((lead) => (
              <TableRow key={lead.id} className="cursor-pointer">
                <TableCell>
                  <Link to={`/app/admissions/${lead.id}`} className="font-medium hover:underline">
                    {lead.name}
                  </Link>
                </TableCell>
                <TableCell>{lead.phone}</TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[lead.status] ?? 'outline'}>{lead.status}</Badge>
                </TableCell>
                <TableCell className={lead.overdue ? 'text-destructive' : undefined}>
                  {lead.nextFollowUpAt ? formatDate(lead.nextFollowUpAt) : '—'}
                  {lead.overdue && ' (overdue)'}
                </TableCell>
                <TableCell>{formatDate(lead.createdAt)}</TableCell>
              </TableRow>
            ))}
            {leadsQuery.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  No leads match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

function NewLeadDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const [form, setForm] = useState<CreateLeadInput>({ name: '', phone: '', email: '', source: 'WALK_IN', notes: '' })
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => createLead(form),
    onSuccess: () => {
      toast.success('Lead created')
      onOpenChange(false)
      setForm({ name: '', phone: '', email: '', source: 'WALK_IN', notes: '' })
      void queryClient.invalidateQueries({ queryKey: ['leads'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not create the lead.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button>
          <Plus />
          New lead
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New lead</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="name">Name</Label>
            <Input
              id="name"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="phone">Phone</Label>
              <Input
                id="phone"
                value={form.phone}
                onChange={(event) => setForm({ ...form, phone: event.target.value })}
                required
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={form.email}
                onChange={(event) => setForm({ ...form, email: event.target.value })}
              />
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="source">Source</Label>
            <select
              id="source"
              value={form.source}
              onChange={(event) => setForm({ ...form, source: event.target.value })}
              className="h-9 rounded-md border bg-transparent px-3 text-sm"
            >
              {SOURCES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="notes">Notes</Label>
            <Input
              id="notes"
              value={form.notes}
              onChange={(event) => setForm({ ...form, notes: event.target.value })}
            />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Creating...' : 'Create lead'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
