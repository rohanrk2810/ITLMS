import { useQuery } from '@tanstack/react-query'
import { NavLink } from 'react-router-dom'

import { fetchCurrentUser } from '@/api/auth'

import { NAV_ITEMS } from '@/components/nav-items'
import { useBranding } from '@/lib/use-branding'
import { ALWAYS_SHOWN, useUiConfig } from '@/lib/ui-config'
import { useUnplacedStudent } from '@/lib/use-placement'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/auth-store'

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const role = useAuthStore((state) => state.user?.role)
  const instituteHidden = useBranding().branding?.studentHiddenMenu ?? []
  // A student's own setting (made by an administrator) replaces the institute's list. It is read fresh, so a change
  // shows up without signing in again.
  const me = useQuery({
    queryKey: ['me', 'menu'],
    queryFn: fetchCurrentUser,
    enabled: role === 'STUDENT',
    staleTime: 60_000,
    refetchOnWindowFocus: true,
  })
  const hiddenForStudents = me.data?.hiddenMenu ?? instituteHidden
  // A new student who has not been placed in a batch yet sees only the courses, to ask for one. Once an administrator
  // approves a request the whole menu appears, minus whatever the administrator hid.
  const unplaced = useUnplacedStudent()
  // The administrator chooses which pages a student's menu shows; the dashboard is always there.
  const menu = useUiConfig().menus[role ?? 'STUDENT'] ?? {}
  const filtered = NAV_ITEMS.filter(
    (item) =>
      (!role || item.roles.includes(role)) &&
      (!unplaced || item.to === '/app' || item.to === '/app/courses') &&
      !(role === 'STUDENT' && item.to !== '/app' && hiddenForStudents.includes(item.to)) &&
      !(menu.hidden?.includes(item.to) && !ALWAYS_SHOWN.includes(item.to)),
  )
  // The administrator's order first (as far as it applies to this person), the rest in the usual order.
  const rank = (to: string) => {
    const i = menu.order?.indexOf(to) ?? -1
    return i === -1 ? Number.MAX_SAFE_INTEGER : i
  }
  const items = [...filtered].sort((a, b) => rank(a.to) - rank(b.to))

  return (
    <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto p-3">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/app'}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors',
              isActive
                ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                : 'text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
            )
          }
        >
          <item.icon className="size-4" />
          {menu.labels?.[item.to] || item.label}
        </NavLink>
      ))}
    </nav>
  )
}
