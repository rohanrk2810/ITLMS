import { type FormEvent, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Camera, Download, Plus, Printer, XCircle } from 'lucide-react'
import { toast } from 'sonner'

import { searchBatches } from '@/api/batches'
import { apiErrorMessage } from '@/api/client'
import {
  type IdCardPersonType,
  type IdCardResponse,
  idCardPdfObjectUrl,
  idCardSheetObjectUrl,
  issueIdCard,
  listIdCards,
  openPdf,
  revokeIdCard,
  uploadIdCardPhoto,
} from '@/api/id-cards'
import { searchStudents } from '@/api/students'
import { listTrainers } from '@/api/trainers'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDate } from '@/lib/format'

type TypeFilter = 'ALL' | IdCardPersonType

const TYPE_LABEL: Record<IdCardPersonType, string> = { STUDENT: 'Student', TRAINER: 'Trainer', STAFF: 'Staff' }

/** ADMIN and COORDINATOR: issue cards, add the photo, print one or many, withdraw a card. */
export function IdCardManagementPage() {
  const queryClient = useQueryClient()
  const [type, setType] = useState<TypeFilter>('ALL')
  const [q, setQ] = useState('')
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [issueOpen, setIssueOpen] = useState(false)
  const [revoking, setRevoking] = useState<IdCardResponse | null>(null)

  const query = useQuery({
    queryKey: ['id-cards', 'list', type, q],
    queryFn: () => listIdCards({ type: type === 'ALL' ? undefined : type, q: q.trim() || undefined }),
  })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['id-cards'] })

  const printable = (query.data ?? []).filter((c) => c.status === 'ISSUED')
  const chosen = printable.filter((c) => selected.has(c.id))

  function toggle(id: number, on: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      if (on) {
        next.add(id)
      } else {
        next.delete(id)
      }
      return next
    })
  }

  async function printSheet() {
    try {
      openPdf(await idCardSheetObjectUrl(chosen.map((c) => c.id)))
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not make the sheet.'))
    }
  }

  async function printOne(card: IdCardResponse) {
    try {
      openPdf(await idCardPdfObjectUrl(card.id))
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not open the card.'))
    }
  }

  const photoMutation = useMutation({
    mutationFn: ({ id, file }: { id: number; file: File }) => uploadIdCardPhoto(id, file),
    onSuccess: () => {
      toast.success('Photo saved')
      refresh()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the photo.')),
  })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">ID cards</h1>
          <p className="text-muted-foreground">Issue identity cards for students, trainers and staff, and print them.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" disabled={chosen.length === 0} onClick={() => void printSheet()}>
            <Printer className="size-4" /> Print selected ({chosen.length})
          </Button>
          <Button onClick={() => setIssueOpen(true)}>
            <Plus className="size-4" /> Issue a card
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Tabs value={type} onValueChange={(v) => setType(v as TypeFilter)}>
          <TabsList>
            <TabsTrigger value="ALL">All</TabsTrigger>
            <TabsTrigger value="STUDENT">Students</TabsTrigger>
            <TabsTrigger value="TRAINER">Trainers</TabsTrigger>
            <TabsTrigger value="STAFF">Staff</TabsTrigger>
          </TabsList>
        </Tabs>
        <Input
          className="max-w-xs"
          placeholder="Search name, card number or code"
          value={q}
          onChange={(event) => setQ(event.target.value)}
        />
      </div>

      {query.isLoading && <Skeleton className="h-48" />}
      {query.isSuccess && query.data.length === 0 && (
        <p className="text-sm text-muted-foreground">No cards yet. Use "Issue a card".</p>
      )}
      {query.isSuccess && query.data.length > 0 && (
        <div className="overflow-x-auto rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <Checkbox
                    aria-label="Select all printable cards"
                    checked={printable.length > 0 && chosen.length === printable.length}
                    onCheckedChange={(on) => setSelected(on === true ? new Set(printable.map((c) => c.id)) : new Set())}
                  />
                </TableHead>
                <TableHead>Card</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Valid until</TableHead>
                <TableHead>Photo</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.map((card) => (
                <CardTableRow
                  key={card.id}
                  card={card}
                  checked={selected.has(card.id)}
                  onToggle={(on) => toggle(card.id, on)}
                  onPrint={() => void printOne(card)}
                  onRevoke={() => setRevoking(card)}
                  onPhoto={(file) => photoMutation.mutate({ id: card.id, file })}
                  busy={photoMutation.isPending}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <IssueDialog open={issueOpen} onOpenChange={setIssueOpen} onIssued={refresh} />
      <RevokeDialog card={revoking} onClose={() => setRevoking(null)} onDone={refresh} />
    </div>
  )
}

function CardTableRow({
  card,
  checked,
  onToggle,
  onPrint,
  onRevoke,
  onPhoto,
  busy,
}: {
  card: IdCardResponse
  checked: boolean
  onToggle: (on: boolean) => void
  onPrint: () => void
  onRevoke: () => void
  onPhoto: (file: File) => void
  busy: boolean
}) {
  const fileInput = useRef<HTMLInputElement>(null)
  const usable = card.status === 'ISSUED'
  return (
    <TableRow>
      <TableCell>
        <Checkbox
          aria-label={`Select ${card.fullName}`}
          checked={checked}
          disabled={!usable}
          onCheckedChange={(on) => onToggle(on === true)}
        />
      </TableCell>
      <TableCell>
        <div className="font-medium">{card.cardNo}</div>
        <div className="text-xs text-muted-foreground">{TYPE_LABEL[card.personType]}</div>
      </TableCell>
      <TableCell>
        <div>{card.fullName}</div>
        <div className="text-xs text-muted-foreground">
          {card.designation}
          {card.personCode ? ` · ${card.personCode}` : ''}
          {card.batchName ? ` · ${card.batchName}` : ''}
        </div>
      </TableCell>
      <TableCell>{formatDate(card.validUntil)}</TableCell>
      <TableCell>
        <input
          ref={fileInput}
          type="file"
          accept="image/jpeg,image/png"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) {
              onPhoto(file)
            }
            event.target.value = ''
          }}
        />
        <Button size="sm" variant={card.hasPhoto ? 'ghost' : 'outline'} disabled={busy} onClick={() => fileInput.current?.click()}>
          <Camera className="size-4" /> {card.hasPhoto ? 'Change' : 'Add photo'}
        </Button>
      </TableCell>
      <TableCell>
        <Badge variant={usable ? 'default' : 'destructive'}>{card.status}</Badge>
      </TableCell>
      <TableCell className="text-right">
        <div className="flex justify-end gap-1">
          <Button size="sm" variant="outline" disabled={!usable} onClick={onPrint}>
            <Download className="size-4" /> PDF
          </Button>
          <Button size="sm" variant="ghost" disabled={card.status === 'REVOKED'} onClick={onRevoke}>
            <XCircle className="size-4" /> Withdraw
          </Button>
        </div>
      </TableCell>
    </TableRow>
  )
}

function IssueDialog({
  open,
  onOpenChange,
  onIssued,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onIssued: () => void
}) {
  const [personType, setPersonType] = useState<IdCardPersonType>('STUDENT')
  const [studentQuery, setStudentQuery] = useState('')
  const [studentId, setStudentId] = useState<number | null>(null)
  const [batchId, setBatchId] = useState<number | ''>('')
  const [trainerId, setTrainerId] = useState<number | ''>('')
  const [name, setName] = useState('')
  const [title, setTitle] = useState('')
  const [validUntil, setValidUntil] = useState('')
  const [photo, setPhoto] = useState<File | null>(null)

  const students = useQuery({
    queryKey: ['id-cards', 'students', studentQuery],
    queryFn: () => searchStudents({ query: studentQuery.trim(), status: 'ACTIVE' }),
    enabled: open && personType === 'STUDENT' && studentQuery.trim().length >= 2,
  })
  const batches = useQuery({
    queryKey: ['id-cards', 'batches'],
    queryFn: () => searchBatches({}),
    enabled: open && personType === 'STUDENT',
  })
  const trainers = useQuery({
    queryKey: ['id-cards', 'trainers'],
    queryFn: () => listTrainers({ status: 'ACTIVE' }),
    enabled: open && personType === 'TRAINER',
  })

  const mutation = useMutation({
    mutationFn: async () => {
      const card = await issueIdCard({
        personType,
        personId: personType === 'STUDENT' ? (studentId ?? undefined) : personType === 'TRAINER' ? (trainerId || undefined) : undefined,
        batchId: personType === 'STUDENT' && batchId ? batchId : undefined,
        fullName: personType === 'STAFF' ? name : undefined,
        designation: personType === 'STAFF' && title.trim() ? title : undefined,
        validUntil: validUntil || undefined,
      })
      if (photo) {
        try {
          await uploadIdCardPhoto(card.id, photo)
        } catch (error) {
          toast.error(apiErrorMessage(error, 'The card was issued, but the photo was not saved. Add it from the list.'))
        }
      }
      return card
    },
    onSuccess: (card) => {
      toast.success(`Card ${card.cardNo} issued`)
      onIssued()
      onOpenChange(false)
      setStudentQuery('')
      setStudentId(null)
      setName('')
      setTitle('')
      setValidUntil('')
      setPhoto(null)
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not issue the card.')),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  const ready =
    (personType === 'STUDENT' && studentId !== null) ||
    (personType === 'TRAINER' && trainerId !== '') ||
    (personType === 'STAFF' && name.trim().length > 0)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Issue an ID card</DialogTitle>
        </DialogHeader>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <Tabs value={personType} onValueChange={(v) => setPersonType(v as IdCardPersonType)}>
            <TabsList>
              <TabsTrigger value="STUDENT">Student</TabsTrigger>
              <TabsTrigger value="TRAINER">Trainer</TabsTrigger>
              <TabsTrigger value="STAFF">Staff</TabsTrigger>
            </TabsList>
          </Tabs>

          {personType === 'STUDENT' && (
            <>
              <div className="flex flex-col gap-2">
                <Label htmlFor="card-student">Student</Label>
                <Input
                  id="card-student"
                  placeholder="Type at least 2 letters of the name, code or email"
                  value={studentQuery}
                  onChange={(event) => {
                    setStudentQuery(event.target.value)
                    setStudentId(null)
                  }}
                />
                {students.data?.content.slice(0, 6).map((s) => (
                  <button
                    type="button"
                    key={s.id}
                    onClick={() => {
                      setStudentId(s.id)
                      setStudentQuery(`${s.fullName} (${s.studentCode})`)
                    }}
                    className={`rounded-md border px-3 py-2 text-left text-sm hover:bg-accent ${studentId === s.id ? 'border-primary' : ''}`}
                  >
                    {s.fullName} <span className="text-muted-foreground">{s.studentCode}</span>
                  </button>
                ))}
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="card-batch">Batch (printed on the card)</Label>
                <select
                  id="card-batch"
                  className="h-9 rounded-md border bg-background px-3 text-sm"
                  value={batchId}
                  onChange={(event) => setBatchId(event.target.value ? Number(event.target.value) : '')}
                >
                  <option value="">No batch</option>
                  {batches.data?.content.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.name} ({b.courseTitle})
                    </option>
                  ))}
                </select>
              </div>
            </>
          )}

          {personType === 'TRAINER' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="card-trainer">Trainer</Label>
              <select
                id="card-trainer"
                className="h-9 rounded-md border bg-background px-3 text-sm"
                value={trainerId}
                onChange={(event) => setTrainerId(event.target.value ? Number(event.target.value) : '')}
              >
                <option value="">Choose a trainer</option>
                {trainers.data?.content.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.fullName} ({t.employeeCode})
                  </option>
                ))}
              </select>
            </div>
          )}

          {personType === 'STAFF' && (
            <>
              <div className="flex flex-col gap-2">
                <Label htmlFor="card-name">Name</Label>
                <Input id="card-name" value={name} onChange={(event) => setName(event.target.value)} required />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="card-title">Title (for example Accountant)</Label>
                <Input id="card-title" value={title} onChange={(event) => setTitle(event.target.value)} />
              </div>
            </>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-2">
              <Label htmlFor="card-until">Valid until (optional)</Label>
              <Input id="card-until" type="date" value={validUntil} onChange={(event) => setValidUntil(event.target.value)} />
              <p className="text-xs text-muted-foreground">Empty: the batch end date, or one year.</p>
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="card-photo">Photo (JPEG or PNG, up to 2 MB)</Label>
              <Input
                id="card-photo"
                type="file"
                accept="image/jpeg,image/png"
                onChange={(event) => setPhoto(event.target.files?.[0] ?? null)}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="submit" disabled={!ready || mutation.isPending}>
              Issue card
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function RevokeDialog({
  card,
  onClose,
  onDone,
}: {
  card: IdCardResponse | null
  onClose: () => void
  onDone: () => void
}) {
  const [reason, setReason] = useState('')
  const mutation = useMutation({
    mutationFn: () => revokeIdCard(card!.id, reason),
    onSuccess: () => {
      toast.success('Card withdrawn')
      setReason('')
      onDone()
      onClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not withdraw the card.')),
  })
  return (
    <Dialog open={card !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Withdraw {card?.cardNo}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-2">
          <Label htmlFor="revoke-reason">Reason (the person is told)</Label>
          <Input id="revoke-reason" value={reason} onChange={(event) => setReason(event.target.value)} />
        </div>
        <DialogFooter>
          <Button variant="destructive" disabled={!reason.trim() || mutation.isPending} onClick={() => mutation.mutate()}>
            Withdraw the card
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
