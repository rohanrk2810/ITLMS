import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { IdCard as IdCardIcon } from 'lucide-react'

import { idCardPhotoObjectUrl, myIdCards, type IdCardResponse } from '@/api/id-cards'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

/** The signed-in person's own ID card, to see. Printing it is done by the institute office. */
export function MyIdCardPage() {
  const query = useQuery({ queryKey: ['id-cards', 'mine'], queryFn: myIdCards })

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">My ID card</h1>
        <p className="text-muted-foreground">Your institute identity card. The institute office issues and prints it.</p>
      </div>
      {query.isLoading && <Skeleton className="h-40" />}
      {query.isSuccess && query.data.length === 0 && (
        <p className="text-sm text-muted-foreground">
          You do not have an ID card yet. Ask the institute office to issue one.
        </p>
      )}
      <div className="flex flex-col gap-3">
        {query.data?.map((card) => <CardRow key={card.id} card={card} />)}
      </div>
    </div>
  )
}

function CardRow({ card }: { card: IdCardResponse }) {
  const [photo, setPhoto] = useState<string | null>(null)

  useEffect(() => {
    if (!card.hasPhoto) {
      return
    }
    let url: string | null = null
    let cancelled = false
    idCardPhotoObjectUrl(card.id)
      .then((u) => {
        if (cancelled) {
          URL.revokeObjectURL(u)
        } else {
          url = u
          setPhoto(u)
        }
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
      if (url) {
        URL.revokeObjectURL(url)
      }
    }
  }, [card.id, card.hasPhoto])

  const usable = card.status === 'ISSUED'
  return (
    <Card>
      <CardContent className="flex flex-wrap items-center gap-4 py-4">
        <div className="flex size-16 shrink-0 items-center justify-center overflow-hidden rounded-md border bg-muted">
          {photo ? <img src={photo} alt="" className="size-full object-cover" /> : <IdCardIcon className="size-8 text-muted-foreground" />}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2 font-medium">
            {card.fullName}
            <Badge variant={usable ? 'default' : 'destructive'}>{card.status}</Badge>
          </div>
          <p className="text-sm text-muted-foreground">
            {card.designation} · {card.cardNo}
            {card.personCode ? ` · ${card.personCode}` : ''}
          </p>
          <p className="text-sm text-muted-foreground">
            {card.batchName ? `${card.batchName} · ` : ''}Valid until {formatDate(card.validUntil)}
          </p>
          {card.revokedReason && <p className="text-sm text-destructive">{card.revokedReason}</p>}
        </div>
      </CardContent>
    </Card>
  )
}
