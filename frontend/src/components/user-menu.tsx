import { Link, useNavigate } from 'react-router-dom'
import { KeyRound, UserRound } from 'lucide-react'
import { toast } from 'sonner'

import { logout } from '@/api/auth'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
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

  const firstName = user.fullName.trim().split(/\s+/)[0]

  return (
    <div className="flex items-center gap-2">
      <span className="hidden text-sm sm:inline">
        Welcome <span className="font-medium uppercase text-primary">{firstName}</span>
      </span>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" className="h-10 gap-1 px-1" aria-label={`${user.fullName} menu`}>
            <Avatar className="size-9">
              <AvatarFallback>{initials(user.fullName)}</AvatarFallback>
            </Avatar>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-72 overflow-hidden p-0">
          <div className="flex items-center gap-3 bg-neutral-700 p-4 text-white">
            <Avatar className="size-14">
              <AvatarFallback className="text-lg text-foreground">{initials(user.fullName)}</AvatarFallback>
            </Avatar>
            <div className="min-w-0">
              <p className="break-words font-semibold leading-tight">{user.fullName}</p>
              <p className="mt-1 text-xs text-white/70">{dutiesLine(user)}</p>
            </div>
          </div>
          <div className="space-y-1 p-3">
            {hasRole(user.role, user.extraRoles, 'STUDENT') && (
              <MenuLink to="/app/my-profile" icon={<UserRound className="size-6" />} title="My Profile" hint="Account settings and more" />
            )}
            <MenuLink to="/app/change-password" icon={<KeyRound className="size-6" />} title="Change Password" hint="Update your password" />
            <DropdownMenuItem
              onSelect={handleLogout}
              className="mt-2 w-fit cursor-pointer rounded-md bg-primary px-4 py-2 font-semibold text-primary-foreground focus:bg-primary/90 focus:text-primary-foreground"
            >
              Log Out
            </DropdownMenuItem>
          </div>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}

function MenuLink({ to, icon, title, hint }: { to: string; icon: React.ReactNode; title: string; hint: string }) {
  return (
    <DropdownMenuItem asChild className="cursor-pointer gap-3 px-2 py-2">
      <Link to={to}>
        <span className="text-muted-foreground">{icon}</span>
        <span className="flex flex-col">
          <span className="text-base font-medium">{title}</span>
          <span className="text-sm text-muted-foreground">{hint}</span>
        </span>
      </Link>
    </DropdownMenuItem>
  )
}

function hasRole(role: string, extra: string[] | undefined, wanted: string): boolean {
  return role === wanted || (extra ?? []).includes(wanted)
}

/** "Trainer" or "Trainer + Coordinator": the main role and the extra duties an administrator gave. */
function dutiesLine(user: { roleDisplayName: string; extraRoles?: string[] }): string {
  const extras = (user.extraRoles ?? []).map((r) => r.charAt(0) + r.slice(1).toLowerCase())
  return [user.roleDisplayName, ...extras].join(' + ')
}
