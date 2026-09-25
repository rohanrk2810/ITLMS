import { useQuery } from '@tanstack/react-query'
import { Megaphone } from 'lucide-react'
import { Link } from 'react-router-dom'

import { announcementCategoryLabel, listAnnouncements } from '@/api/announcements'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

const SHOWN = 5

/**
 * The latest announcements addressed to the signed-in person. The backend already narrows the list to
 * what that person may see, so this only decides how much to show. Reading needs no permission beyond
 * being signed in; publishing is a separate, role-checked action on the announcements page.
 */
export function AnnouncementsPanel() {
  const query = useQuery({ queryKey: ['announcements', 'list'], queryFn: () => listAnnouncements() })
  const items = (query.data?.content ?? []).filter((a) => !a.withdrawn).slice(0, SHOWN)

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="flex items-center gap-2 text-base">
            <Megaphone className="size-4 text-muted-foreground" />
            Announcements
          </CardTitle>
          <Link to="/app/announcements" className="text-xs text-primary hover:underline">
            View all
          </Link>
        </div>
        <CardDescription>Updates from your institute, courses and batches.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {query.isLoading && <Skeleton className="h-24" />}
        {query.isError && <p className="text-sm text-muted-foreground">Announcements could not be loaded.</p>}
        {query.isSuccess && items.length === 0 && <p className="text-sm text-muted-foreground">Nothing new.</p>}
        {items.map((a) => (
          <div key={a.id} className="flex flex-col gap-1 border-b pb-3 last:border-b-0 last:pb-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium">{a.title}</span>
              <Badge variant="secondary">{announcementCategoryLabel(a.category)}</Badge>
            </div>
            <p className="line-clamp-2 text-sm whitespace-pre-wrap text-muted-foreground">{a.message}</p>
            <span className="text-xs text-muted-foreground">{formatDate(a.createdAt)}</span>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
