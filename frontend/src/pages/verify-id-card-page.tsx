import { useQuery } from '@tanstack/react-query'
import { BadgeCheck, ShieldX } from 'lucide-react'
import { useParams, useSearchParams } from 'react-router-dom'

import { apiErrorMessage } from '@/api/client'
import { verifyIdCard } from '@/api/id-cards'
import { BrandMark } from '@/components/brand-mark'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDate } from '@/lib/format'

/** Public ID card check, opened by scanning the QR code. Shows only the name, role, validity and status. */
export function VerifyIdCardPage() {
  const { cardNo = '' } = useParams()
  const [params] = useSearchParams()
  const code = params.get('code') ?? ''

  const query = useQuery({
    queryKey: ['id-card-verify', cardNo, code],
    queryFn: () => verifyIdCard(cardNo, code),
    enabled: code.length > 0,
    retry: false,
  })

  const valid = query.data?.status === 'VALID'
  return (
    <div className="mx-auto flex min-h-svh max-w-md flex-col justify-center gap-6 p-4">
      <div className="flex justify-center">
        <BrandMark />
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            {valid ? <BadgeCheck className="size-5 text-primary" /> : <ShieldX className="size-5 text-destructive" />}
            ID card check
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2 text-sm">
          {code.length === 0 && <p>The code is missing. Scan the QR code on the card again.</p>}
          {query.isLoading && <p>Checking...</p>}
          {query.isError && <p className="text-destructive">{apiErrorMessage(query.error, 'This card could not be confirmed.')}</p>}
          {query.data && (
            <>
              <p className={valid ? 'font-medium text-primary' : 'font-medium text-destructive'}>
                {valid ? 'This card is valid.' : query.data.status === 'EXPIRED' ? 'This card has expired.' : 'This card has been withdrawn.'}
              </p>
              <p className="text-lg font-semibold">{query.data.fullName}</p>
              <p className="text-muted-foreground">
                {query.data.designation} · {query.data.cardNo}
              </p>
              <p className="text-muted-foreground">Valid until {formatDate(query.data.validUntil)}</p>
              <p className="text-muted-foreground">Issued by {query.data.issuedBy}</p>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
