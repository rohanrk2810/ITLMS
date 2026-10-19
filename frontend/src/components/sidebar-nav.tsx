import { useQuery } from '@tanstack/react-query'
import { NavLink } from 'react-router-dom'

import { fetchCurrentUser } from '@/api/auth'

import { NAV_ITEMS } from '@/components/nav-items'
import { useBranding } from '@/lib/use-branding'
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
  // The administrator chooses which pages a student's menu shows; the dashboard is always there.
  const items = NAV_ITEMS.filter(
    (item) =>
      (!role || item.roles.includes(role)) &&
      !(role === 'STUDENT' && item.to !== '/app' && hiddenForStudents.includes(item.to)),
  )

  return (
    <nav className="flex flex-col gap-1 p-3">
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
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
