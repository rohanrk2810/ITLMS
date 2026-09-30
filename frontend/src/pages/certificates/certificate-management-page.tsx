import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Download, XCircle } from 'lucide-react'
import { toast } from 'sonner'

import {
  type CertificateRequestFilter,
  type CertificateRequestResponse,
  type CertificateResponse,
  approveCertificateRequest,
  certificatePdfObjectUrl,
  getCertificateRequest,
  issueCertificateForRequest,
  listCertificates,
  rejectCertificateRequest,
  revokeCertificate,
  searchCertificateRequests,
} from '@/api/certificates'
import { apiErrorMessage } from '@/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDate } from '@/lib/format'

type Tab = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ISSUED' | 'REVOKED'

const TABS: Array<{ value: Tab; label: string }> = [
  { value: 'PENDING', label: 'Pending Requests' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ISSUED', label: 'Issued Certificates' },
  { value: 'REVOKED', label: 'Revoked Certificates' },
]

/** ADMIN: decide certificate requests, and see or revoke what has been issued. */
export function CertificateManagementPage() {
  const [tab, setTab] = useState<Tab>('PENDING')
  const [q, setQ] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [openId, setOpenId] = useState<number | null>(null)

  const showingCertificates = tab === 'ISSUED' || tab === 'REVOKED'

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Certificate Management</h1>
        <p className="text-muted-foreground">Review requests, issue certificates and revoke them if needed.</p>
      </div>

      <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
        <TabsList className="flex-wrap">
          {TABS.map((t) => (
            <TabsTrigger key={t.value} value={t.value}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground" htmlFor="cert-q">
            Student name or ID
          </label>
          <Input id="cert-q" className="w-60" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        {!showingCertificates && (
          <>
            <div className="flex flex-col gap-1">
              <label className="text-xs text-muted-foreground" htmlFor="cert-from">
                Requested from
              </label>
              <Input id="cert-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs text-muted-foreground" htmlFor="cert-to">
                to
              </label>
              <Input id="cert-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </div>
          </>
        )}
      </div>

      {showingCertificates ? (
        <CertificateTable status={tab === 'ISSUED' ? 'ISSUED' : 'REVOKED'} q={q} />
      ) : (
        <RequestTable status={tab} filter={{ q, from, to }} onOpen={setOpenId} />
      )}

      <RequestDialog id={openId} onClose={() => setOpenId(null)} />
    </div>
  )
}

function RequestTable({
  status,
  filter,
  onOpen,
}: {
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  filter: CertificateRequestFilter
  onOpen: (id: number) => void
}) {
  const query = useQuery({
    queryKey: ['certificates', 'requests', status, filter],
    queryFn: () => searchCertificateRequests({ ...filter, status: [status] }),
  })

  if (query.isLoading) return <Skeleton className="h-40" />
  if (!query.data || query.data.content.length === 0) {
    return <p className="text-sm text-muted-foreground">Nothing here.</p>
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Student</TableHead>
          <TableHead>Student ID</TableHead>
          <TableHead>Course</TableHead>
          <TableHead>Batch</TableHead>
          <TableHead>Requested</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {query.data.content.map((r) => (
          <TableRow key={r.id}>
            <TableCell>{r.studentName}</TableCell>
            <TableCell>{r.studentCode ?? '-'}</TableCell>
            <TableCell>{r.courseTitle}</TableCell>
            <TableCell>{r.batchName ?? '-'}</TableCell>
            <TableCell>{formatDate(r.requestedAt)}</TableCell>
            <TableCell>
              <Badge variant={r.status === 'REJECTED' ? 'destructive' : 'secondary'}>{r.status}</Badge>
            </TableCell>
            <TableCell className="text-right">
              <Button size="sm" variant="outline" onClick={() => onOpen(r.id)}>
                View
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function CertificateTable({ status, q }: { status: 'ISSUED' | 'REVOKED'; q: string }) {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['certificates', 'all'], queryFn: () => listCertificates() })
  const [revoking, setRevoking] = useState<CertificateResponse | null>(null)
  const [reason, setReason] = useState('')

  const revoke = useMutation({
    mutationFn: () => revokeCertificate(revoking!.id, reason),
    onSuccess: () => {
      toast.success('Certificate revoked.')
      setRevoking(null)
      setReason('')
      void queryClient.invalidateQueries({ queryKey: ['certificates'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not revoke the certificate.')),
  })

  async function download(c: CertificateResponse) {
    try {
      const url = await certificatePdfObjectUrl(c.id)
      const link = document.createElement('a')
      link.href = url
      link.download = `${c.certificateNo}.pdf`
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 30_000)
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not download the certificate.'))
    }
  }

  if (query.isLoading) return <Skeleton className="h-40" />
  const needle = q.trim().toLowerCase()
  const rows = (query.data?.content ?? []).filter(
    (c) =>
      c.status === status &&
      (!needle || c.studentName.toLowerCase().includes(needle) || (c.studentCode ?? '').toLowerCase().includes(needle)),
  )
  if (rows.length === 0) return <p className="text-sm text-muted-foreground">Nothing here.</p>

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Certificate ID</TableHead>
            <TableHead>Student</TableHead>
            <TableHead>Course</TableHead>
            <TableHead>Batch</TableHead>
            <TableHead>Issued</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((c) => (
            <TableRow key={c.id}>
              <TableCell>{c.certificateNo}</TableCell>
              <TableCell>{c.studentName}</TableCell>
              <TableCell>{c.courseTitle}</TableCell>
              <TableCell>{c.batchName ?? '-'}</TableCell>
              <TableCell>{formatDate(c.issueDate)}</TableCell>
              <TableCell className="flex justify-end gap-2">
                {c.status === 'ISSUED' ? (
                  <>
                    <Button size="sm" variant="outline" onClick={() => void download(c)}>
                      <Download className="size-3.5" />
                      Download
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setRevoking(c)}>
                      Revoke
                    </Button>
                  </>
                ) : (
                  <span className="text-xs text-muted-foreground">{c.revokedReason}</span>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog open={revoking != null} onOpenChange={(open) => !open && setRevoking(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Revoke {revoking?.certificateNo}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            The certificate stays on record and will verify as REVOKED. The student is notified.
          </p>
          <textarea
            className="min-h-20 rounded-md border bg-transparent p-2 text-sm"
            placeholder="Reason (required)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <DialogFooter>
            <Button variant="destructive" disabled={!reason.trim() || revoke.isPending} onClick={() => revoke.mutate()}>
              Revoke certificate
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function RequestDialog({ id, onClose }: { id: number | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  const detail = useQuery({
    queryKey: ['certificates', 'requests', 'detail', id],
    queryFn: () => getCertificateRequest(id!),
    enabled: id != null,
  })

  function done(message: string) {
    toast.success(message)
    void queryClient.invalidateQueries({ queryKey: ['certificates'] })
  }

  const approve = useMutation({
    mutationFn: async () => {
      await approveCertificateRequest(id!)
      // Approving starts issuance. If issuing fails, the request stays APPROVED and can be retried.
      try {
        await issueCertificateForRequest(id!)
        return true
      } catch (error) {
        toast.error(apiErrorMessage(error, 'Approved, but the certificate could not be issued yet.'))
        return false
      }
    },
    onSuccess: (issued) => {
      done(issued ? 'Approved and certificate issued.' : 'Approved.')
      onClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not approve the request.')),
  })

  const issue = useMutation({
    mutationFn: () => issueCertificateForRequest(id!),
    onSuccess: () => {
      done('Certificate issued.')
      onClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not issue the certificate.')),
  })

  const reject = useMutation({
    mutationFn: () => rejectCertificateRequest(id!, reason),
    onSuccess: () => {
      done('Request rejected.')
      setRejecting(false)
      setReason('')
      onClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not reject the request.')),
  })

  const request: CertificateRequestResponse | undefined = detail.data?.request
  const eligibility = detail.data?.eligibility
  const busy = approve.isPending || issue.isPending || reject.isPending

  return (
    <Dialog open={id != null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Certificate request</DialogTitle>
        </DialogHeader>
        {detail.isLoading && <Skeleton className="h-32" />}
        {request && (
          <div className="flex flex-col gap-4 text-sm">
            <dl className="grid grid-cols-[8rem_1fr] gap-y-1">
              <dt className="text-muted-foreground">Student</dt>
              <dd>
                {request.studentName} ({request.studentCode ?? 'no ID'})
              </dd>
              <dt className="text-muted-foreground">Course</dt>
              <dd>{request.courseTitle}</dd>
              <dt className="text-muted-foreground">Batch</dt>
              <dd>{request.batchName ?? '-'}</dd>
              <dt className="text-muted-foreground">Requested</dt>
              <dd>{formatDate(request.requestedAt)}</dd>
              <dt className="text-muted-foreground">Status</dt>
              <dd>{request.status}</dd>
              {request.rejectionReason && (
                <>
                  <dt className="text-muted-foreground">Reason</dt>
                  <dd>{request.rejectionReason}</dd>
                </>
              )}
            </dl>

            {eligibility && (
              <div>
                <p className="mb-1 font-medium">
                  Eligibility now: {eligibility.eligible ? 'eligible' : 'NOT eligible'}
                </p>
                <ul className="flex flex-col gap-1">
                  {eligibility.criteria
                    .filter((c) => c.outcome !== 'NOT_REQUIRED')
                    .map((c) => (
                      <li key={c.key} className="flex items-center gap-2">
                        {c.outcome === 'MET' ? (
                          <CheckCircle2 className="size-4 text-emerald-600" />
                        ) : (
                          <XCircle className="size-4 text-destructive" />
                        )}
                        {c.name}
                        {c.detail && <span className="text-xs text-muted-foreground">({c.detail})</span>}
                      </li>
                    ))}
                </ul>
              </div>
            )}

            {rejecting && (
              <textarea
                className="min-h-20 rounded-md border bg-transparent p-2 text-sm"
                placeholder="Reason shown to the student (required)"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            )}
          </div>
        )}
        {request && (request.status === 'PENDING' || request.status === 'APPROVED') && (
          <DialogFooter className="gap-2">
            {rejecting ? (
              <Button variant="destructive" disabled={!reason.trim() || busy} onClick={() => reject.mutate()}>
                Confirm rejection
              </Button>
            ) : (
              <Button variant="outline" disabled={busy} onClick={() => setRejecting(true)}>
                Reject
              </Button>
            )}
            {request.status === 'PENDING' ? (
              <Button disabled={busy} onClick={() => approve.mutate()}>
                Approve
              </Button>
            ) : (
              <Button disabled={busy} onClick={() => issue.mutate()}>
                Issue certificate
              </Button>
            )}
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}
