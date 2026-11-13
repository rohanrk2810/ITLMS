import { CalendarDaysIcon, ClockIcon, UsersIcon } from 'lucide-react'

import type { PublicBatch } from '@/api/public-site'
import { BatchActions } from '@/components/batch-actions'
import { formatDate, formatMoney } from '@/lib/format'

/** One batch as a card: the courses in it, when it meets, the seats left, the fee, and the Buy and Inquiry buttons. */
export function BatchTile({ batch }: { batch: PublicBatch }) {
  const title = batch.name?.trim() || batch.batchCode
  return (
    <article className="flex flex-col overflow-hidden rounded-xl border bg-card transition-all hover:-translate-y-1 hover:shadow-lg">
      <div className="bg-gradient-to-br from-primary/80 to-primary/30 p-5 text-primary-foreground">
        <span className="rounded-full bg-background/25 px-2 py-0.5 text-xs font-medium">{batch.batchCode}</span>
        <h3 className="mt-3 text-lg font-semibold leading-snug">{title}</h3>
      </div>
      <div className="flex flex-1 flex-col gap-3 p-5">
        <ul className="flex flex-wrap gap-1.5" aria-label="Courses in this batch">
          {batch.courseTitles.map((course) => (
            <li key={course} className="rounded-md bg-muted px-2 py-0.5 text-xs">
              {course}
            </li>
          ))}
        </ul>
        <ul className="flex flex-col gap-1.5 text-sm text-muted-foreground">
          <li className="flex items-center gap-2">
            <CalendarDaysIcon className="size-4 shrink-0" />
            Starts {formatDate(batch.startDate)}
            {batch.endDate ? ` · ends ${formatDate(batch.endDate)}` : ''}
          </li>
          <li className="flex items-center gap-2">
            <ClockIcon className="size-4 shrink-0" />
            {batch.classDays.join(', ')} · {batch.startTime.slice(0, 5)}–{batch.endTime.slice(0, 5)}
          </li>
          <li className="flex items-center gap-2">
            <UsersIcon className="size-4 shrink-0" />
            {batch.seatsLeft} {batch.seatsLeft === 1 ? 'seat' : 'seats'} left
          </li>
        </ul>
        <p className="mt-auto flex items-center justify-between pt-2 text-sm text-muted-foreground">
          <span>{batch.mode.toLowerCase()}</span>
          <span className="text-base font-semibold text-foreground">{formatMoney(batch.fee)}</span>
        </p>
        <BatchActions batch={batch} />
      </div>
    </article>
  )
}
