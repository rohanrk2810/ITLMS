import { useNavigate } from 'react-router-dom'
import { LogOut } from 'lucide-react'
import { toast } from 'sonner'

import { logout } from '@/api/auth'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useAuthStore } from '@/stores/auth-store'

function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts[parts.length - 1]?.[0] ?? '')).toUpperCase()
}

export function UserMenu() {
  const user = useAuthStore((state) => state.user)
  const navigate = useNavigate()

  if (!user) return null

  async function handleLogout() {
    // The portal sees the session disappear first; this tells it the person chose to leave, so it sends them to the
    // front page rather than to the sign-in page.
    useAuthStore.setState({ signedOutByUser: true })
    await logout()
    void navigate('/', { replace: true })
    useAuthStore.setState({ signedOutByUser: false }) // only for this sign-out
    toast.success('Signed out')
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="h-9 gap-2 px-2">
          <Avatar className="size-7">
            <AvatarFallback>{initials(user.fullName)}</AvatarFallback>
          </Avatar>
          <span className="hidden text-sm font-medium sm:inline">{user.fullName}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="flex flex-col">
          <span className="font-medium">{user.fullName}</span>
          <span className="text-xs font-normal text-muted-foreground">{user.email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled className="text-xs text-muted-foreground">
          {dutiesLine(user)}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onSelect={handleLogout}>
          <LogOut />
          Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

/** "Trainer" or "Trainer + Coordinator": the main role and the extra duties an administrator gave. */
function dutiesLine(user: { roleDisplayName: string; extraRoles?: string[] }): string {
  const extras = (user.extraRoles ?? []).map((r) => r.charAt(0) + r.slice(1).toLowerCase())
  return [user.roleDisplayName, ...extras].join(' + ')
}
