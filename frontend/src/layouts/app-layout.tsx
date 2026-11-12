import { useEffect, useRef, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { ChevronLeft, House, Menu } from 'lucide-react'

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
  const { pathname } = useLocation()
  const mainRef = useRef<HTMLElement>(null)
  // The page area is the one thing that scrolls, so a new page starts at its top.
  useEffect(() => {
    mainRef.current?.scrollTo({ top: 0 })
  }, [pathname])
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

  // Exactly the height of the window (not "at least"), so the menu and the header stay put and only <main> scrolls.
  return (
    <div className="flex h-svh overflow-hidden bg-background">
      {/* The width slides to nothing instead of the menu vanishing; the inner box keeps its size so nothing reflows. */}
      <aside
        className={`relative hidden shrink-0 bg-sidebar text-sidebar-foreground transition-[width] duration-300 ease-in-out md:block ${collapsed ? 'w-0' : 'w-64 border-r'}`}
      >
        <div
          className={`flex h-full w-64 flex-col overflow-hidden transition-opacity duration-200 ${collapsed ? 'pointer-events-none opacity-0' : 'opacity-100'}`}
        >
          <div className="flex h-14 items-center gap-2 border-b px-4 font-semibold">
            <BrandMark />
          </div>
          <SidebarNav />
        </div>
        <button
          type="button"
          aria-label={collapsed ? 'Show the menu' : 'Hide the menu'}
          title={collapsed ? 'Show the menu' : 'Hide the menu'}
          onClick={toggleCollapsed}
          className={`absolute top-16 z-20 flex size-6 items-center justify-center rounded-full border bg-background text-muted-foreground shadow transition-all duration-300 hover:text-foreground ${collapsed ? '-right-7' : '-right-3'}`}
        >
          <ChevronLeft className={`size-4 transition-transform duration-300 ${collapsed ? 'rotate-180' : ''}`} />
        </button>
      </aside>

      <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
        <SheetContent side="left" className="w-64 p-0">
          <SheetTitle className="flex h-14 items-center gap-2 border-b px-4 font-semibold">
            <BrandMark />
          </SheetTitle>
          <SidebarNav onNavigate={() => setMobileNavOpen(false)} />
        </SheetContent>
      </Sheet>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
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
            <Button asChild variant="ghost" size="icon" aria-label="Home" title="Home">
              <Link to="/app">
                <House />
              </Link>
            </Button>
            <NotificationBell />
            <UserMenu />
          </div>
        </header>
        <main ref={mainRef} className="min-h-0 flex-1 overflow-y-auto p-6">
          {/* A new page eases in instead of snapping. */}
          <div key={pathname} className="animate-in fade-in-0 slide-in-from-bottom-1 duration-300">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
