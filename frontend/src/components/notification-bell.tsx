import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, Check, CheckCheck } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  unreadNotificationCount,
} from '@/api/notifications'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Separator } from '@/components/ui/separator'
import { mapActionUrl } from '@/lib/action-url'
import { cn } from '@/lib/utils'

const UNREAD_POLL_MS = 30_000

function timeAgo(iso: string): string {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

/** The bell icon (Doc S11, S16): unread count polls in the background; the list loads only once opened. */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: unreadNotificationCount,
    refetchInterval: UNREAD_POLL_MS,
  })

  const listQuery = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => listNotifications(false),
    enabled: open,
  })

  const unread = unreadQuery.data ?? 0

  function handleOpen(id: number, read: boolean, actionUrl: string | null) {
    setOpen(false)
    void navigate(mapActionUrl(actionUrl))
    if (!read) {
      void markNotificationRead(id).then(() => queryClient.invalidateQueries({ queryKey: ['notifications'] }))
    }
  }

  async function handleMarkAllRead() {
    await markAllNotificationsRead()
    await queryClient.invalidateQueries({ queryKey: ['notifications'] })
  }

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label="Notifications">
          <Bell />
          {unread > 0 && (
            <Badge
              variant="destructive"
              className="absolute -top-1 -right-1 h-4 min-w-4 justify-center rounded-full px-1 text-[10px]"
            >
              {unread > 99 ? '99+' : unread}
            </Badge>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80 p-0">
        <div className="flex items-center justify-between px-3 py-2">
          <span className="text-sm font-medium">Notifications</span>
          {unread > 0 && (
            <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={handleMarkAllRead}>
              <CheckCheck className="size-3.5" />
              Mark all read
            </Button>
          )}
        </div>
        <DropdownMenuSeparator className="m-0" />
        <div className="max-h-96 overflow-y-auto">
          {listQuery.isLoading && (
            <p className="p-4 text-center text-sm text-muted-foreground">Loading...</p>
          )}
          {listQuery.isSuccess && listQuery.data.content.length === 0 && (
            <p className="p-4 text-center text-sm text-muted-foreground">You&apos;re all caught up.</p>
          )}
          {listQuery.data?.content.map((notification, index) => (
            <div key={notification.id}>
              {index > 0 && <Separator />}
              <button
                type="button"
                onClick={() => handleOpen(notification.id, notification.read, notification.actionUrl)}
                className={cn(
                  'flex w-full flex-col gap-0.5 px-3 py-2.5 text-left text-sm hover:bg-accent',
                  !notification.read && 'bg-accent/50',
                )}
              >
                <span className="flex items-center justify-between gap-2">
                  <span className="font-medium">{notification.title}</span>
                  {!notification.read && <span className="size-2 shrink-0 rounded-full bg-primary" />}
                </span>
                <span className="line-clamp-2 text-muted-foreground">{notification.message}</span>
                <span className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                  {notification.read && <Check className="size-3" />}
                  {timeAgo(notification.createdAt)}
                </span>
              </button>
            </div>
          ))}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
