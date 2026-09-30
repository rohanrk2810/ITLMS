import { type FormEvent, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BadgeCheck, ShieldX } from 'lucide-react'
import { useParams, useSearchParams } from 'react-router-dom'

import { verifyCertificate } from '@/api/certificates'
import { apiErrorMessage } from '@/api/client'
import { BrandMark } from '@/components/brand-mark'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { formatDate } from '@/lib/format'

/**
 * Public certificate check. Needs no account. Shows only what is safe to show anyone: the name,
 * course, issuer, date and whether the certificate still stands.
 */
export function VerifyCertificatePage() {
  const { certificateNo = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const code = params.get('code') ?? ''
  const [typed, setTyped] = useState('')

  const query = useQuery({
    queryKey: ['certificate-verify', certificateNo, code],
    queryFn: () => verifyCertificate(certificateNo, code),
    enabled: code.length > 0,
    retry: false,
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setParams({ code: typed.trim() })
  }

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-muted/40 p-6">
      <div className="flex items-center gap-2 text-lg font-semibold">
        <BrandMark className="size-8" />
      </div>
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Certificate verification</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4 text-sm">
          <p>
            <span className="text-muted-foreground">Certificate ID: </span>
            {certificateNo}
          </p>

          {code.length === 0 && (
            <form onSubmit={handleSubmit} className="flex flex-col gap-2">
              <label htmlFor="verify-code" className="text-muted-foreground">
                Enter the verification code printed on the certificate
              </label>
              <Input id="verify-code" value={typed} onChange={(e) => setTyped(e.target.value)} required />
              <Button type="submit" className="self-start">
                Verify
              </Button>
            </form>
          )}

          {query.isLoading && <p className="text-muted-foreground">Checking...</p>}
          {query.isError && (
            <p className="flex items-center gap-2 text-destructive">
              <ShieldX className="size-5" />
              {apiErrorMessage(query.error, 'No certificate matches that ID and code.')}
            </p>
          )}
          {query.data && (
            <div className="flex flex-col gap-2">
              <p
                className={`flex items-center gap-2 text-base font-semibold ${
                  query.data.status === 'VALID' ? 'text-emerald-600' : 'text-destructive'
                }`}
              >
                {query.data.status === 'VALID' ? <BadgeCheck className="size-5" /> : <ShieldX className="size-5" />}
                Certificate Status: {query.data.status}
              </p>
              <dl className="grid grid-cols-[7rem_1fr] gap-y-1">
                <dt className="text-muted-foreground">Student</dt>
                <dd>{query.data.studentName}</dd>
                <dt className="text-muted-foreground">Course</dt>
                <dd>{query.data.courseTitle}</dd>
                <dt className="text-muted-foreground">Institute</dt>
                <dd>{query.data.issuedBy}</dd>
                <dt className="text-muted-foreground">Issue date</dt>
                <dd>{formatDate(query.data.issueDate)}</dd>
              </dl>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
