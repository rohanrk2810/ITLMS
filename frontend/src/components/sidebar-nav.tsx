import { NavLink } from 'react-router-dom'

import { NAV_ITEMS } from '@/components/nav-items'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/auth-store'

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const role = useAuthStore((state) => state.user?.role)
  const items = NAV_ITEMS.filter((item) => !role || item.roles.includes(role))

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
