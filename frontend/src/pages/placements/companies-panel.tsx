import { type FormEvent, type ReactNode, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type CompanyInput, type CompanyResponse, createCompany, listCompanies, updateCompany } from '@/api/placements'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const EMPTY: CompanyInput = { name: '', active: true }

export function CompaniesPanel({ canEdit }: { canEdit: boolean }) {
  const [activeOnly, setActiveOnly] = useState(false)
  const [editing, setEditing] = useState<CompanyResponse | null>(null)
  const queryClient = useQueryClient()

  const companiesQuery = useQuery({
    queryKey: ['placements', 'companies', activeOnly],
    queryFn: () => listCompanies(activeOnly),
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: ['placements', 'companies'] })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <Checkbox checked={activeOnly} onCheckedChange={(v) => setActiveOnly(v === true)} />
          Active only
        </label>
        {canEdit && (
          <CompanyFormDialog
            trigger={
              <Button size="sm">
                <Plus className="size-3.5" />
                New company
              </Button>
            }
            title="Add a company"
            initial={EMPTY}
            onSave={async (input) => {
              await createCompany(input)
              await invalidate()
            }}
          />
        )}
      </div>

      {companiesQuery.isLoading && <Skeleton className="h-48" />}

      {companiesQuery.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Industry</TableHead>
              <TableHead>Location</TableHead>
              <TableHead>Contact</TableHead>
              <TableHead>Status</TableHead>
              {canEdit && <TableHead />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {companiesQuery.data.map((company) => (
              <TableRow key={company.id}>
                <TableCell className="font-medium">{company.name}</TableCell>
                <TableCell>{company.industry ?? '—'}</TableCell>
                <TableCell>{company.location ?? '—'}</TableCell>
                <TableCell>{company.contactName ?? company.contactEmail ?? '—'}</TableCell>
                <TableCell>
                  <Badge variant={company.active ? 'default' : 'secondary'}>
                    {company.active ? 'Active' : 'Inactive'}
                  </Badge>
                </TableCell>
                {canEdit && (
                  <TableCell>
                    <Button size="sm" variant="ghost" onClick={() => setEditing(company)}>
                      <Pencil className="size-3.5" />
                    </Button>
                  </TableCell>
                )}
              </TableRow>
            ))}
            {companiesQuery.data.length === 0 && (
              <TableRow>
                <TableCell colSpan={canEdit ? 6 : 5} className="text-center text-muted-foreground">
                  No companies yet.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}

      {editing && (
        <CompanyFormDialog
          open
          title="Edit company"
          initial={{
            name: editing.name,
            industry: editing.industry ?? undefined,
            website: editing.website ?? undefined,
            location: editing.location ?? undefined,
            contactName: editing.contactName ?? undefined,
            contactEmail: editing.contactEmail ?? undefined,
            contactPhone: editing.contactPhone ?? undefined,
            notes: editing.notes ?? undefined,
            active: editing.active,
          }}
          onOpenChange={(open) => !open && setEditing(null)}
          onSave={async (input) => {
            await updateCompany(editing.id, input)
            await invalidate()
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

function CompanyFormDialog({
  trigger,
  open,
  onOpenChange,
  title,
  initial,
  onSave,
}: {
  trigger?: ReactNode
  open?: boolean
  onOpenChange?: (open: boolean) => void
  title: string
  initial: CompanyInput
  onSave: (input: CompanyInput) => Promise<void>
}) {
  const [internalOpen, setInternalOpen] = useState(false)
  const [form, setForm] = useState<CompanyInput>(initial)
  const isControlled = open !== undefined
  const isOpen = isControlled ? open : internalOpen

  const mutation = useMutation({
    mutationFn: () => onSave(form),
    onSuccess: () => {
      toast.success('Saved')
      if (!isControlled) {
        setInternalOpen(false)
        setForm(EMPTY)
      }
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the company.')),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  function handleOpenChange(next: boolean) {
    if (isControlled) {
      onOpenChange?.(next)
    } else {
      setInternalOpen(next)
      if (!next) setForm(initial)
    }
  }

  return (
    <Dialog open={isOpen} onOpenChange={handleOpenChange}>
      {trigger && <DialogTrigger asChild>{trigger}</DialogTrigger>}
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2">
            <Label htmlFor="name">Name</Label>
            <Input id="name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="industry">Industry</Label>
              <Input
                id="industry"
                value={form.industry ?? ''}
                onChange={(e) => setForm({ ...form, industry: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="location">Location</Label>
              <Input
                id="location"
                value={form.location ?? ''}
                onChange={(e) => setForm({ ...form, location: e.target.value })}
              />
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="website">Website</Label>
            <Input id="website" value={form.website ?? ''} onChange={(e) => setForm({ ...form, website: e.target.value })} />
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="contactName">Contact name</Label>
              <Input
                id="contactName"
                value={form.contactName ?? ''}
                onChange={(e) => setForm({ ...form, contactName: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="contactEmail">Contact email</Label>
              <Input
                id="contactEmail"
                type="email"
                value={form.contactEmail ?? ''}
                onChange={(e) => setForm({ ...form, contactEmail: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="contactPhone">Contact phone</Label>
              <Input
                id="contactPhone"
                value={form.contactPhone ?? ''}
                onChange={(e) => setForm({ ...form, contactPhone: e.target.value })}
              />
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="notes">Notes</Label>
            <textarea
              id="notes"
              rows={3}
              className="rounded-md border bg-transparent px-3 py-2 text-sm"
              value={form.notes ?? ''}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
            />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox
              checked={form.active ?? true}
              onCheckedChange={(v) => setForm({ ...form, active: v === true })}
            />
            Active
          </label>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Saving...' : 'Save'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
