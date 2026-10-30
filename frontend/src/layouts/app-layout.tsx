import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Menu } from 'lucide-react'

import { BrandMark } from '@/components/brand-mark'
import { NotificationBell } from '@/components/notification-bell'
import { SidebarNav } from '@/components/sidebar-nav'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet'
import { UserMenu } from '@/components/user-menu'

const COLLAPSED_KEY = 'sidebar-collapsed'

function storedCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}

/** The signed-in shell: a role-filtered sidebar (desktop) or drawer (mobile), a topbar, and the page content. */
export function AppLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  // The desktop sidebar can be hidden with the arrow on its edge; the choice is remembered in this browser.
  const [collapsed, setCollapsed] = useState(storedCollapsed)

  function toggleCollapsed() {
    setCollapsed((current) => {
      try {
        localStorage.setItem(COLLAPSED_KEY, current ? '0' : '1')
      } catch {
        // Not remembered; the button still works for this visit.
      }
      return !current
    })
  }

  return (
    <div className="flex min-h-svh bg-background">
      <aside
        className={`relative hidden w-64 shrink-0 border-r bg-sidebar text-sidebar-foreground ${collapsed ? '' : 'md:flex md:flex-col'}`}
      >
        <div className="flex h-14 items-center gap-2 border-b px-4 font-semibold">
          <BrandMark />
        </div>
        <SidebarNav />
        <button
          type="button"
          aria-label="Hide the menu"
          title="Hide the menu"
          onClick={toggleCollapsed}
          className="absolute top-16 -right-3 z-20 flex size-6 items-center justify-center rounded-full border bg-background text-muted-foreground shadow hover:text-foreground"
        >
          <ChevronLeft className="size-4" />
        </button>
      </aside>
      {collapsed && (
        <button
          type="button"
          aria-label="Show the menu"
          title="Show the menu"
          onClick={toggleCollapsed}
          className="fixed top-16 left-0 z-20 hidden h-8 w-5 items-center justify-center rounded-r-md border border-l-0 bg-background text-muted-foreground shadow hover:text-foreground md:flex"
        >
          <ChevronRight className="size-4" />
        </button>
      )}

      <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
        <SheetContent side="left" className="w-64 p-0">
          <SheetTitle className="flex h-14 items-center gap-2 border-b px-4 font-semibold">
            <BrandMark />
          </SheetTitle>
          <SidebarNav onNavigate={() => setMobileNavOpen(false)} />
        </SheetContent>
      </Sheet>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center justify-between gap-2 border-b px-4">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            aria-label="Open menu"
            onClick={() => setMobileNavOpen(true)}
          >
            <Menu />
          </Button>
          <span className="hidden text-sm text-muted-foreground md:inline" />
          <div className="ml-auto flex items-center gap-1">
            <ThemeToggle />
            <NotificationBell />
            <UserMenu />
          </div>
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
